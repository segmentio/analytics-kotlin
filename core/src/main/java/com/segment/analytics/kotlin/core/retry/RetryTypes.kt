package com.segment.analytics.kotlin.core.retry

import kotlinx.serialization.Serializable

@Serializable
enum class PipelineState {
    READY,
    RATE_LIMITED
}

@Serializable
enum class RetryBehavior {
    RETRY,
    DROP
}

@Serializable
enum class DropReason {
    MAX_RETRIES_EXCEEDED,
    MAX_DURATION_EXCEEDED,
    NON_RETRYABLE_ERROR
}

sealed class UploadDecision {
    object Proceed : UploadDecision()
    object SkipThisBatch : UploadDecision()
    object SkipAllBatches : UploadDecision()
    data class DropBatch(val reason: DropReason) : UploadDecision()
}

@Serializable
data class ResponseInfo(
    val statusCode: Int,
    val retryAfterSeconds: Int? = null,
    val batchFile: String,
    val currentTime: Long
)
