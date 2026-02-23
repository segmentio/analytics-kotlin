package com.segment.analytics.kotlin.core.retry

import kotlinx.serialization.Serializable

@Serializable
data class RetryConfig(
    val rateLimitConfig: RateLimitConfig = RateLimitConfig(),
    val backoffConfig: BackoffConfig = BackoffConfig()
)

@Serializable
data class RateLimitConfig(
    val enabled: Boolean = true,
    val maxRetryCount: Int = 100,
    val maxRetryInterval: Int = 300,
    val maxRateLimitDuration: Long = 43200
)

@Serializable
data class BackoffConfig(
    val enabled: Boolean = true,
    val maxRetryCount: Int = 100,
    val baseBackoffInterval: Double = 0.5,
    val maxBackoffInterval: Int = 300,
    val maxTotalBackoffDuration: Long = 43200,
    val jitterPercent: Int = 10,
    val default4xxBehavior: RetryBehavior = RetryBehavior.DROP,
    val default5xxBehavior: RetryBehavior = RetryBehavior.RETRY,
    val unknownCodeBehavior: RetryBehavior = RetryBehavior.DROP,
    val statusCodeOverrides: Map<Int, RetryBehavior> = mapOf(
        408 to RetryBehavior.RETRY,
        410 to RetryBehavior.RETRY,
        429 to RetryBehavior.RETRY,
        460 to RetryBehavior.RETRY,
        501 to RetryBehavior.DROP,
        505 to RetryBehavior.DROP
    )
)
