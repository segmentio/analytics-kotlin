package com.segment.analytics.kotlin.core.retry

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RetryStateMachineTest {

    private lateinit var config: RetryConfig
    private lateinit var stateMachine: RetryStateMachine
    private lateinit var timeProvider: FakeTimeProvider
    private val random = Random(42)  // Seeded for deterministic tests

    @BeforeEach
    fun setup() {
        timeProvider = FakeTimeProvider(1000L)
        config = RetryConfig()
        stateMachine = RetryStateMachine(config, timeProvider, random)
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
        assertTrue(metadata.nextRetryTime!! in 1450L..1550L)  // 500ms with up to +10% jitter
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

    @Test
    fun `shouldUploadBatch skips all batches when rate limited`() {
        val rateLimitedState = RetryState(
            pipelineState = PipelineState.RATE_LIMITED,
            waitUntilTime = 61000L
        )

        timeProvider.setTime(30000L)  // Before waitUntilTime

        val (decision, _) = stateMachine.shouldUploadBatch(rateLimitedState, "batch-1")

        assertEquals(UploadDecision.SkipAllBatches, decision)
    }

    @Test
    fun `shouldUploadBatch proceeds when rate limit time passes`() {
        val rateLimitedState = RetryState(
            pipelineState = PipelineState.RATE_LIMITED,
            waitUntilTime = 61000L
        )

        timeProvider.setTime(61001L)  // After waitUntilTime

        val (decision, _) = stateMachine.shouldUploadBatch(rateLimitedState, "batch-1")

        assertEquals(UploadDecision.Proceed, decision)
    }

    @Test
    fun `shouldUploadBatch skips batch waiting for backoff`() {
        val state = RetryState(
            batchMetadata = mapOf(
                "batch-1" to BatchMetadata(
                    failureCount = 2,
                    nextRetryTime = 10000L,
                    firstFailureTime = 1000L
                )
            )
        )

        timeProvider.setTime(5000L)  // Before nextRetryTime

        val (decision, _) = stateMachine.shouldUploadBatch(state, "batch-1")

        assertEquals(UploadDecision.SkipThisBatch, decision)
    }

    @Test
    fun `shouldUploadBatch drops batch after max retries`() {
        val state = RetryState(
            batchMetadata = mapOf(
                "batch-1" to BatchMetadata(
                    failureCount = 100,  // At max
                    nextRetryTime = 1000L,
                    firstFailureTime = 1000L
                )
            )
        )

        timeProvider.setTime(2000L)

        val (decision, newState) = stateMachine.shouldUploadBatch(state, "batch-1")

        assertTrue(decision is UploadDecision.DropBatch)
        assertEquals(DropReason.MAX_RETRIES_EXCEEDED, (decision as UploadDecision.DropBatch).reason)
        assertFalse(newState.batchMetadata.containsKey("batch-1"))
    }

    @Test
    fun `shouldUploadBatch drops batch after max duration`() {
        val state = RetryState(
            batchMetadata = mapOf(
                "batch-1" to BatchMetadata(
                    failureCount = 5,
                    nextRetryTime = 1000L,
                    firstFailureTime = 1000L
                )
            )
        )

        // Advance past max duration (12 hours = 43200 seconds)
        timeProvider.setTime(1000L + (43200 * 1000L) + 1)

        val (decision, newState) = stateMachine.shouldUploadBatch(state, "batch-1")

        assertTrue(decision is UploadDecision.DropBatch)
        assertEquals(DropReason.MAX_DURATION_EXCEEDED, (decision as UploadDecision.DropBatch).reason)
        assertFalse(newState.batchMetadata.containsKey("batch-1"))
    }

    @Test
    fun `getRetryCount returns 0 for new batch`() {
        val state = RetryState()

        val retryCount = stateMachine.getRetryCount(state, "batch-1")

        assertEquals(0, retryCount)
    }

    @Test
    fun `getRetryCount returns per-batch failureCount`() {
        val state = RetryState(
            batchMetadata = mapOf("batch-1" to BatchMetadata(failureCount = 3))
        )

        val retryCount = stateMachine.getRetryCount(state, "batch-1")

        assertEquals(3, retryCount)
    }

    @Test
    fun `getRetryCount returns max of per-batch and global count`() {
        val state = RetryState(
            globalRetryCount = 10,
            batchMetadata = mapOf("batch-1" to BatchMetadata(failureCount = 3))
        )

        val retryCount = stateMachine.getRetryCount(state, "batch-1")

        assertEquals(10, retryCount)  // max(3, 10)
    }

    @Test
    fun `getRetryCount returns global count when no batch metadata`() {
        val state = RetryState(globalRetryCount = 5)

        val retryCount = stateMachine.getRetryCount(state, "batch-1")

        assertEquals(5, retryCount)
    }

    @Test
    fun `legacy mode - 429 does not trigger rate limiting`() {
        val disabledConfig = RetryConfig(
            rateLimitConfig = RateLimitConfig(enabled = false),
            backoffConfig = BackoffConfig(enabled = false)
        )
        val machine = RetryStateMachine(disabledConfig, timeProvider, random)
        val state = RetryState()

        val response = ResponseInfo(429, retryAfterSeconds = 60, "batch-1", 1000L)
        val newState = machine.handleResponse(state, response)

        assertEquals(PipelineState.READY, newState.pipelineState)
        assertFalse(newState.batchMetadata.containsKey("batch-1"))
    }

    @Test
    fun `legacy mode - 5xx does not create metadata`() {
        val disabledConfig = RetryConfig(
            rateLimitConfig = RateLimitConfig(enabled = false),
            backoffConfig = BackoffConfig(enabled = false)
        )
        val machine = RetryStateMachine(disabledConfig, timeProvider, random)
        val state = RetryState()

        val response = ResponseInfo(503, null, "batch-1", 1000L)
        val newState = machine.handleResponse(state, response)

        assertFalse(newState.batchMetadata.containsKey("batch-1"))
    }

    @Test
    fun `legacy mode - 4xx drops batch`() {
        val disabledConfig = RetryConfig(
            rateLimitConfig = RateLimitConfig(enabled = false),
            backoffConfig = BackoffConfig(enabled = false)
        )
        val machine = RetryStateMachine(disabledConfig, timeProvider, random)
        val state = RetryState()

        val response = ResponseInfo(400, null, "batch-1", 1000L)
        val newState = machine.handleResponse(state, response)

        assertFalse(newState.batchMetadata.containsKey("batch-1"))
    }

    @Test
    fun `legacy mode - shouldUploadBatch always proceeds`() {
        val disabledConfig = RetryConfig(
            rateLimitConfig = RateLimitConfig(enabled = false),
            backoffConfig = BackoffConfig(enabled = false)
        )
        val machine = RetryStateMachine(disabledConfig, timeProvider, random)
        val state = RetryState()

        val (decision, _) = machine.shouldUploadBatch(state, "any-batch")

        assertEquals(UploadDecision.Proceed, decision)
    }
}
