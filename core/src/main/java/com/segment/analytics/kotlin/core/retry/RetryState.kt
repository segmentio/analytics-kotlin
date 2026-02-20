package com.segment.analytics.kotlin.core.retry

import kotlinx.serialization.Serializable

@Serializable
data class RetryState(
    val pipelineState: PipelineState = PipelineState.READY,
    val waitUntilTime: Long? = null,
    val globalRetryCount: Int = 0,
    val batchMetadata: Map<String, BatchMetadata> = emptyMap()
) {
    fun isRateLimited(currentTime: Long): Boolean =
        pipelineState == PipelineState.RATE_LIMITED &&
        waitUntilTime?.let { currentTime < it } == true
}

@Serializable
data class BatchMetadata(
    val failureCount: Int = 0,
    val nextRetryTime: Long? = null,
    val firstFailureTime: Long? = null
) {
    fun shouldRetry(currentTime: Long): Boolean =
        nextRetryTime?.let { currentTime >= it } ?: true

    fun exceedsMaxDuration(currentTime: Long, maxDuration: Long): Boolean =
        firstFailureTime?.let { (currentTime - it) > maxDuration } ?: false
}
