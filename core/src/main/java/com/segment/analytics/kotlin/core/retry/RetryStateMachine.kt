package com.segment.analytics.kotlin.core.retry

class RetryStateMachine(
    private val config: RetryConfig,
    private val timeProvider: TimeProvider = SystemTimeProvider()
) {

    fun handleResponse(
        state: RetryState,
        response: ResponseInfo
    ): RetryState {
        val behavior = resolveStatusCodeBehavior(response.statusCode)

        return when {
            response.statusCode in 200..299 -> {
                state.copy(
                    pipelineState = PipelineState.READY,
                    waitUntilTime = null,
                    globalRetryCount = 0,
                    batchMetadata = state.batchMetadata - response.batchFile
                )
            }

            behavior == RetryBehavior.RETRY && config.backoffConfig.enabled -> {
                handleRetryableError(state, response, response.currentTime)
            }

            else -> {
                state.removeBatch(response.batchFile)
            }
        }
    }

    private fun handleRetryableError(
        state: RetryState,
        response: ResponseInfo,
        currentTime: Long
    ): RetryState {
        val existingMetadata = state.batchMetadata[response.batchFile]
        val newFailureCount = (existingMetadata?.failureCount ?: 0) + 1
        val firstFailureTime = existingMetadata?.firstFailureTime ?: currentTime
        val nextRetryTime = currentTime + calculateBackoffMs(newFailureCount)

        val newMetadata = BatchMetadata(
            failureCount = newFailureCount,
            nextRetryTime = nextRetryTime,
            firstFailureTime = firstFailureTime
        )

        return state.copy(
            batchMetadata = state.batchMetadata + (response.batchFile to newMetadata)
        )
    }

    private fun calculateBackoffMs(failureCount: Int): Long {
        val base = config.backoffConfig.baseBackoffInterval * 1000
        val max = config.backoffConfig.maxBackoffInterval * 1000L

        val exponentialBackoff = base * Math.pow(2.0, (failureCount - 1).toDouble())
        val cappedBackoff = minOf(exponentialBackoff, max.toDouble())

        val jitterAmount = cappedBackoff * (config.backoffConfig.jitterPercent / 100.0)
        val jitter = (Math.random() * jitterAmount).toLong()

        return (cappedBackoff + jitter).toLong()
    }

    private fun resolveStatusCodeBehavior(code: Int): RetryBehavior {
        config.backoffConfig.statusCodeOverrides[code]?.let { return it }

        return when (code) {
            in 400..499 -> config.backoffConfig.default4xxBehavior
            in 500..599 -> config.backoffConfig.default5xxBehavior
            else -> config.backoffConfig.unknownCodeBehavior
        }
    }
}

private fun RetryState.removeBatch(batchFile: String): RetryState {
    return copy(batchMetadata = batchMetadata - batchFile)
}
