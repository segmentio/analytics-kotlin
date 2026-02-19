package com.segment.analytics.kotlin.core.retry

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RetryStateMachineTest {

    private lateinit var config: RetryConfig
    private lateinit var stateMachine: RetryStateMachine
    private lateinit var timeProvider: FakeTimeProvider

    @BeforeEach
    fun setup() {
        timeProvider = FakeTimeProvider(1000L)
        config = RetryConfig()
        stateMachine = RetryStateMachine(config, timeProvider)
    }

    @Test
    fun `4xx codes default to DROP`() {
        val state = RetryState()
        val response = ResponseInfo(400, null, "batch-1", 1000L)

        val newState = stateMachine.handleResponse(state, response)

        assertFalse(newState.batchMetadata.containsKey("batch-1"))
        assertEquals(PipelineState.READY, newState.pipelineState)
    }

    @Test
    fun `408 overrides default 4xx behavior and retries`() {
        val state = RetryState()
        val response = ResponseInfo(408, null, "batch-1", 1000L)

        val newState = stateMachine.handleResponse(state, response)

        assertTrue(newState.batchMetadata.containsKey("batch-1"))
    }

    @Test
    fun `5xx codes default to RETRY`() {
        val state = RetryState()
        val response = ResponseInfo(503, null, "batch-1", 1000L)

        val newState = stateMachine.handleResponse(state, response)

        assertTrue(newState.batchMetadata.containsKey("batch-1"))
    }

    @Test
    fun `501 overrides default 5xx behavior and drops`() {
        val state = RetryState()
        val response = ResponseInfo(501, null, "batch-1", 1000L)

        val newState = stateMachine.handleResponse(state, response)

        assertFalse(newState.batchMetadata.containsKey("batch-1"))
    }

    @Test
    fun `unknown codes use unknownCodeBehavior`() {
        val state = RetryState()
        val response = ResponseInfo(666, null, "batch-1", 1000L)

        val newState = stateMachine.handleResponse(state, response)

        assertFalse(newState.batchMetadata.containsKey("batch-1"))
    }

    @Test
    fun `exponential backoff increases correctly`() {
        var state = RetryState()

        // First failure: should be ~500ms (0.5 * 2^0)
        state = stateMachine.handleResponse(state, ResponseInfo(503, null, "batch-1", 1000L))
        var metadata = state.batchMetadata["batch-1"]!!
        assertEquals(1, metadata.failureCount)
        assertTrue(metadata.nextRetryTime!! in 1450L..1550L)  // 500ms ±10% jitter
        assertEquals(1000L, metadata.firstFailureTime)

        // Second failure: should be ~1000ms (0.5 * 2^1)
        timeProvider.setTime(metadata.nextRetryTime!!)
        state = stateMachine.handleResponse(state, ResponseInfo(503, null, "batch-1", metadata.nextRetryTime!!))
        metadata = state.batchMetadata["batch-1"]!!
        assertEquals(2, metadata.failureCount)
        assertTrue(metadata.nextRetryTime!! > timeProvider.currentTimeMillis() + 900L)
        assertEquals(1000L, metadata.firstFailureTime)  // Should not change
    }

    @Test
    fun `success cleans up batch metadata`() {
        // Create state with existing metadata
        val state = RetryState(
            batchMetadata = mapOf("batch-1" to BatchMetadata(failureCount = 3, nextRetryTime = 5000L, firstFailureTime = 1000L))
        )

        val newState = stateMachine.handleResponse(state, ResponseInfo(200, null, "batch-1", 1000L))

        assertFalse(newState.batchMetadata.containsKey("batch-1"))
        assertEquals(PipelineState.READY, newState.pipelineState)
    }

    @Test
    fun `429 transitions to RATE_LIMITED state`() {
        val state = RetryState()
        val response = ResponseInfo(429, retryAfterSeconds = 60, "batch-1", 1000L)

        val newState = stateMachine.handleResponse(state, response)

        assertEquals(PipelineState.RATE_LIMITED, newState.pipelineState)
        assertEquals(61000L, newState.waitUntilTime)  // 1000 + 60*1000
        assertEquals(1, newState.globalRetryCount)
    }

    @Test
    fun `429 clamps excessive Retry-After to maxRetryInterval`() {
        val state = RetryState()
        // Retry-After=500 seconds, but maxRetryInterval=300
        val response = ResponseInfo(429, retryAfterSeconds = 500, "batch-1", 1000L)

        val newState = stateMachine.handleResponse(state, response)

        assertEquals(PipelineState.RATE_LIMITED, newState.pipelineState)
        assertEquals(301000L, newState.waitUntilTime)  // 1000 + 300*1000 (clamped)
    }

    @Test
    fun `429 uses maxRetryInterval when Retry-After is missing`() {
        val state = RetryState()
        val response = ResponseInfo(429, retryAfterSeconds = null, "batch-1", 1000L)

        val newState = stateMachine.handleResponse(state, response)

        assertEquals(301000L, newState.waitUntilTime)  // defaults to 300s
    }

    @Test
    fun `globalRetryCount resets on successful upload`() {
        val state = RetryState(
            globalRetryCount = 5,
            batchMetadata = mapOf("batch-1" to BatchMetadata(failureCount = 3))
        )

        val response = ResponseInfo(200, null, "batch-1", 1000L)
        val newState = stateMachine.handleResponse(state, response)

        assertEquals(0, newState.globalRetryCount)
        assertFalse(newState.batchMetadata.containsKey("batch-1"))
    }
}
