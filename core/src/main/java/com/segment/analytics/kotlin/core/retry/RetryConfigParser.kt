package com.segment.analytics.kotlin.core.retry

import kotlinx.serialization.json.*

/**
 * Parses httpConfig JSON from CDN into RetryConfig with defensive error handling.
 *
 * Never throws exceptions - returns valid configuration even with corrupt/missing data.
 * Clamps all numeric values to safe ranges.
 */
object RetryConfigParser {

    /**
     * Parse httpConfig JSON into RetryConfig.
     *
     * Returns default configuration if JSON is null/corrupt.
     * Clamps all values to valid ranges and provides sensible defaults.
     */
    fun parse(httpConfig: JsonObject?): RetryConfig {
        if (httpConfig == null) {
            return RetryConfig()
        }

        return try {
            RetryConfig(
                rateLimitConfig = parseRateLimitConfig(httpConfig.jsonObject("rateLimitConfig")),
                backoffConfig = parseBackoffConfig(httpConfig.jsonObject("backoffConfig"))
            )
        } catch (e: Exception) {
            // If anything goes wrong, return defaults
            RetryConfig()
        }
    }

    private fun parseRateLimitConfig(json: JsonObject?): RateLimitConfig {
        if (json == null) return RateLimitConfig()

        return try {
            RateLimitConfig(
                enabled = json.jsonPrimitive("enabled")?.booleanOrNull ?: true,
                maxRetryCount = json.jsonPrimitive("maxRetryCount")
                    ?.intOrNull
                    ?.coerceIn(0, 1000)
                    ?: 100,
                maxRetryInterval = json.jsonPrimitive("maxRetryInterval")
                    ?.intOrNull
                    ?.coerceIn(1, 3600) // 1 second to 1 hour
                    ?: 300,
                maxTotalBackoffDuration = json.jsonPrimitive("maxTotalBackoffDuration")
                    ?.longOrNull
                    ?.coerceIn(0, 604800)
                    ?: 43200
            )
        } catch (e: Exception) {
            RateLimitConfig()
        }
    }

    private fun parseBackoffConfig(json: JsonObject?): BackoffConfig {
        if (json == null) return BackoffConfig()

        return try {
            BackoffConfig(
                enabled = json.jsonPrimitive("enabled")?.booleanOrNull ?: true,
                maxRetryCount = json.jsonPrimitive("maxRetryCount")
                    ?.intOrNull
                    ?.coerceIn(0, 1000) // Cap at 1000 retries
                    ?: 100,
                baseBackoffInterval = json.jsonPrimitive("baseBackoffInterval")
                    ?.doubleOrNull
                    ?.coerceIn(0.1, 60.0) // 100ms to 60 seconds
                    ?: 0.5,
                maxBackoffInterval = json.jsonPrimitive("maxBackoffInterval")
                    ?.intOrNull
                    ?.coerceIn(1, 3600) // 1 second to 1 hour
                    ?: 300,
                maxTotalBackoffDuration = json.jsonPrimitive("maxTotalBackoffDuration")
                    ?.longOrNull
                    ?.coerceIn(0, 604800) // 0 to 1 week
                    ?: 43200,
                jitterPercent = json.jsonPrimitive("jitterPercent")
                    ?.intOrNull
                    ?.coerceIn(0, 50) // 0% to 50%
                    ?: 10,
                default4xxBehavior = parseRetryBehavior(
                    json.jsonPrimitive("default4xxBehavior")?.contentOrNull,
                    RetryBehavior.DROP
                ),
                default5xxBehavior = parseRetryBehavior(
                    json.jsonPrimitive("default5xxBehavior")?.contentOrNull,
                    RetryBehavior.RETRY
                ),
                unknownCodeBehavior = parseRetryBehavior(
                    json.jsonPrimitive("unknownCodeBehavior")?.contentOrNull,
                    RetryBehavior.DROP
                ),
                statusCodeOverrides = parseStatusCodeOverrides(json.jsonObject("statusCodeOverrides"))
            )
        } catch (e: Exception) {
            BackoffConfig()
        }
    }

    private fun parseRetryBehavior(value: String?, default: RetryBehavior): RetryBehavior {
        return when (value?.uppercase()) {
            "RETRY" -> RetryBehavior.RETRY
            "DROP" -> RetryBehavior.DROP
            else -> default
        }
    }

    private fun parseStatusCodeOverrides(json: JsonObject?): Map<Int, RetryBehavior> {
        if (json == null) return BackoffConfig().statusCodeOverrides

        return try {
            json.mapNotNull { (key, value) ->
                val statusCode = key.toIntOrNull()?.takeIf { it in 100..599 }
                val behavior = parseRetryBehavior((value as? JsonPrimitive)?.contentOrNull, RetryBehavior.DROP)

                if (statusCode != null) statusCode to behavior else null
            }.toMap()
        } catch (e: Exception) {
            BackoffConfig().statusCodeOverrides
        }
    }

    // Extension helpers
    private fun JsonObject.jsonObject(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.jsonPrimitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
}
