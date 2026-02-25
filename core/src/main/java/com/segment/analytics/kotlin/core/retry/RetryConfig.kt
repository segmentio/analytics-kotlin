package com.segment.analytics.kotlin.core.retry

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

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
) {
    /**
     * Validate and clamp all numeric values to safe ranges.
     */
    fun validated(): RateLimitConfig = copy(
        maxRetryCount = maxRetryCount.coerceIn(0, 1000),
        maxRetryInterval = maxRetryInterval.coerceIn(1, 3600),
        maxRateLimitDuration = maxRateLimitDuration.coerceIn(0, 604800)
    )
}

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
) {
    /**
     * Validate and clamp all numeric values to safe ranges.
     * Filter status code overrides to valid range (100-599).
     */
    fun validated(): BackoffConfig = copy(
        maxRetryCount = maxRetryCount.coerceIn(0, 1000),
        baseBackoffInterval = baseBackoffInterval.coerceIn(0.1, 60.0),
        maxBackoffInterval = maxBackoffInterval.coerceIn(1, 3600),
        maxTotalBackoffDuration = maxTotalBackoffDuration.coerceIn(0, 604800),
        jitterPercent = jitterPercent.coerceIn(0, 50),
        statusCodeOverrides = statusCodeOverrides.filterKeys { it in 100..599 }
    )
}

/**
 * HTTP configuration for retry handling, parsed from CDN settings.
 *
 * Uses custom serializer to provide defensive error handling:
 * - Corrupt JSON gracefully falls back to defaults
 * - All numeric values are automatically clamped to safe ranges
 * - Invalid status codes are filtered out
 */
@Serializable(with = HttpConfigSerializer::class)
data class HttpConfig(
    val rateLimitConfig: RateLimitConfig = RateLimitConfig(),
    val backoffConfig: BackoffConfig = BackoffConfig()
)

/**
 * Custom serializer for HttpConfig that provides defensive error handling.
 *
 * On deserialization:
 * - Validates and clamps all numeric values to safe ranges
 * - Returns default HttpConfig on any SerializationException
 * - Never throws exceptions
 */
object HttpConfigSerializer : KSerializer<HttpConfig> {
    @Serializable
    @SerialName("HttpConfig")
    private data class HttpConfigSurrogate(
        val rateLimitConfig: RateLimitConfig = RateLimitConfig(),
        val backoffConfig: BackoffConfig = BackoffConfig()
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val descriptor: SerialDescriptor = HttpConfigSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: HttpConfig) {
        val surrogate = HttpConfigSurrogate(
            rateLimitConfig = value.rateLimitConfig,
            backoffConfig = value.backoffConfig
        )
        encoder.encodeSerializableValue(HttpConfigSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): HttpConfig {
        return try {
            // Decode as JsonElement first for more defensive parsing
            val element = decoder.decodeSerializableValue(JsonElement.serializer())
            if (element !is JsonObject) {
                return HttpConfig()
            }

            // Try to deserialize from the JsonElement
            val surrogate = json.decodeFromJsonElement(HttpConfigSurrogate.serializer(), element)
            HttpConfig(
                rateLimitConfig = surrogate.rateLimitConfig.validated(),
                backoffConfig = surrogate.backoffConfig.validated()
            )
        } catch (e: Exception) {
            // Any parse error → return defaults
            HttpConfig()
        }
    }
}
