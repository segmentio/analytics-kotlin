package com.segment.analytics.kotlin.core.retry

import com.segment.analytics.kotlin.core.retry.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RetryAfterGenericTest {

    private fun makeConfig(maxRetryCount: Int = 100): RetryConfig = RetryConfig(
        rateLimitConfig = RateLimitConfig(
            enabled = true,
            maxRetryCount = maxRetryCount,
            maxRetryInterval = 300
        ),
        backoffConfig = BackoffConfig(
            enabled = true,
            baseBackoffInterval = 0.5,
            maxBackoffInterval = 300,
            maxTotalBackoffDuration = 43200,
            jitterPercent = 0
        )
    )

    private fun makeResponse(
        statusCode: Int,
        batchFile: String = "batch1",
        retryAfterSeconds: Int? = null,
        currentTime: Long = 1_000_000L
    ) = ResponseInfo(
        statusCode = statusCode,
        batchFile = batchFile,
        retryAfterSeconds = retryAfterSeconds,
        currentTime = currentTime
    )

    @Test
    fun `529 with Retry-After triggers pipeline-level pause`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(529, retryAfterSeconds = 30, currentTime = 1_000_000L))

        assertEquals(PipelineState.RATE_LIMITED, newState.pipelineState)
        assertEquals(1_030_000L, newState.waitUntilTime)
        assertEquals(1, newState.globalRetryCount)
        assertNull(newState.batchMetadata["batch1"])
    }

    @Test
    fun `529 with Retry-After does not increment failureCount`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(529, retryAfterSeconds = 30))
        assertNull(newState.batchMetadata["batch1"])
    }

    @Test
    fun `503 with Retry-After triggers pipeline-level pause`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(503, retryAfterSeconds = 60, currentTime = 1_000_000L))

        assertEquals(PipelineState.RATE_LIMITED, newState.pipelineState)
        assertEquals(1_060_000L, newState.waitUntilTime)
        assertEquals(1, newState.globalRetryCount)
    }

    @Test
    fun `408 with Retry-After triggers pipeline-level pause`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(408, retryAfterSeconds = 10, currentTime = 1_000_000L))

        assertEquals(PipelineState.RATE_LIMITED, newState.pipelineState)
        assertEquals(1_010_000L, newState.waitUntilTime)
    }

    @Test
    fun `529 without Retry-After uses exponential backoff`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(529, retryAfterSeconds = null))

        assertEquals(PipelineState.READY, newState.pipelineState)
        assertNull(newState.waitUntilTime)
        assertEquals(0, newState.globalRetryCount)
        assertNotNull(newState.batchMetadata["batch1"])
        assertEquals(1, newState.batchMetadata["batch1"]?.failureCount)
    }

    @Test
    fun `503 without Retry-After uses exponential backoff`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(503, retryAfterSeconds = null))

        assertEquals(PipelineState.READY, newState.pipelineState)
        assertNotNull(newState.batchMetadata["batch1"])
    }

    @Test
    fun `Retry-After clamped at maxRetryInterval`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(529, retryAfterSeconds = 99999, currentTime = 1_000_000L))
        assertEquals(1_300_000L, newState.waitUntilTime)
    }

    @Test
    fun `Retry-After zero sets waitUntilTime to now`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(529, retryAfterSeconds = 0, currentTime = 1_000_000L))

        assertEquals(PipelineState.RATE_LIMITED, newState.pipelineState)
        assertEquals(1_000_000L, newState.waitUntilTime)
    }

    @Test
    fun `globalRetryCount cap drops batch after maxRetryCount`() {
        val config = makeConfig(maxRetryCount = 3)
        val machine = RetryStateMachine(config)

        var state = RetryState()
        for (i in 1..3) {
            state = machine.handleResponse(state, makeResponse(529, retryAfterSeconds = 1, currentTime = i * 100_000L))
        }
        assertEquals(3, state.globalRetryCount)

        val timeProvider = FakeTimeProvider(currentTime = 10_000_000L)
        val dropMachine = RetryStateMachine(config, timeProvider)
        val (decision, _) = dropMachine.shouldUploadBatch(state, "batch1")
        assertEquals(UploadDecision.DropBatch(DropReason.MAX_RETRIES_EXCEEDED), decision)
    }

    @Test
    fun `400 with Retry-After drops immediately`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(400, retryAfterSeconds = 60))

        assertEquals(PipelineState.READY, newState.pipelineState)
        assertNull(newState.batchMetadata["batch1"])
        assertNull(newState.waitUntilTime)
        assertEquals(0, newState.globalRetryCount)
    }

    @Test
    fun `429 behavior unchanged`() {
        val machine = RetryStateMachine(makeConfig())
        val newState = machine.handleResponse(RetryState(), makeResponse(429, retryAfterSeconds = 45, currentTime = 1_000_000L))

        assertEquals(PipelineState.RATE_LIMITED, newState.pipelineState)
        assertEquals(1_045_000L, newState.waitUntilTime)
        assertEquals(1, newState.globalRetryCount)
        assertNull(newState.batchMetadata["batch1"])
    }

    @Test
    fun `pipeline pause holds all batches`() {
        val config = makeConfig()
        val timeProvider = FakeTimeProvider(currentTime = 1_000_000L)
        val machine = RetryStateMachine(config, timeProvider)

        val pausedState = machine.handleResponse(RetryState(), makeResponse(529, retryAfterSeconds = 30, currentTime = 1_000_000L))

        val (decision1, _) = machine.shouldUploadBatch(pausedState, "batch1")
        val (decision2, _) = machine.shouldUploadBatch(pausedState, "batch2")

        assertEquals(UploadDecision.SkipAllBatches, decision1)
        assertEquals(UploadDecision.SkipAllBatches, decision2)
    }
}
