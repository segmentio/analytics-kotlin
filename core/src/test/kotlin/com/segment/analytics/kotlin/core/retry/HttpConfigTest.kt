package com.segment.analytics.kotlin.core.retry

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HttpConfigTest {

    @Test
    fun `HttpConfig deserializes from valid JSON`() {
        val json = """
            {
              "rateLimitConfig": {
                "enabled": true,
                "maxRetryCount": 50,
                "maxRetryInterval": 600,
                "maxRateLimitDuration": 7200
              },
              "backoffConfig": {
                "enabled": true,
                "maxRetryCount": 25,
                "baseBackoffInterval": 1.0,
                "maxBackoffInterval": 600,
                "maxTotalBackoffDuration": 3600,
                "jitterPercent": 20,
                "default4xxBehavior": "DROP",
                "default5xxBehavior": "RETRY",
                "unknownCodeBehavior": "DROP",
                "statusCodeOverrides": {
                  "408": "RETRY",
                  "501": "DROP"
                }
              }
            }
        """.trimIndent()

        val config = Json.decodeFromString<HttpConfig>(json)

        assertEquals(true, config.rateLimitConfig.enabled)
        assertEquals(50, config.rateLimitConfig.maxRetryCount)
        assertEquals(600, config.rateLimitConfig.maxRetryInterval)
        assertEquals(7200L, config.rateLimitConfig.maxRateLimitDuration)

        assertEquals(true, config.backoffConfig.enabled)
        assertEquals(25, config.backoffConfig.maxRetryCount)
        assertEquals(1.0, config.backoffConfig.baseBackoffInterval)
        assertEquals(600, config.backoffConfig.maxBackoffInterval)
        assertEquals(3600L, config.backoffConfig.maxTotalBackoffDuration)
        assertEquals(20, config.backoffConfig.jitterPercent)
        assertEquals(RetryBehavior.RETRY, config.backoffConfig.statusCodeOverrides[408])
        assertEquals(RetryBehavior.DROP, config.backoffConfig.statusCodeOverrides[501])
    }

    @Test
    fun `HttpConfig returns defaults on corrupt JSON`() {
        val json = """{"rateLimitConfig": "not an object"}"""

        val config = Json.decodeFromString<HttpConfig>(json)

        // Should return defaults without throwing
        assertEquals(true, config.rateLimitConfig.enabled)
        assertEquals(100, config.rateLimitConfig.maxRetryCount)
        assertEquals(true, config.backoffConfig.enabled)
    }

    @Test
    fun `HttpConfig returns defaults on empty JSON`() {
        val json = "{}"

        val config = Json.decodeFromString<HttpConfig>(json)

        assertEquals(true, config.rateLimitConfig.enabled)
        assertEquals(100, config.rateLimitConfig.maxRetryCount)
        assertEquals(true, config.backoffConfig.enabled)
    }

    @Test
    fun `HttpConfig handles partial configuration`() {
        val json = """
            {
              "rateLimitConfig": {
                "maxRetryCount": 75
              }
            }
        """.trimIndent()

        val config = Json.decodeFromString<HttpConfig>(json)

        assertEquals(75, config.rateLimitConfig.maxRetryCount)
        assertEquals(300, config.rateLimitConfig.maxRetryInterval) // Default
        assertEquals(true, config.backoffConfig.enabled) // Default
    }

    @Test
    fun `RateLimitConfig validation clamps maxRetryCount`() {
        val config = RateLimitConfig(maxRetryCount = 2000)
        val validated = config.validated()

        assertEquals(1000, validated.maxRetryCount) // Clamped to max
    }

    @Test
    fun `RateLimitConfig validation clamps maxRetryInterval to max`() {
        val config = RateLimitConfig(maxRetryInterval = 5000)
        val validated = config.validated()

        assertEquals(3600, validated.maxRetryInterval) // Clamped to 1 hour
    }

    @Test
    fun `RateLimitConfig validation clamps maxRetryInterval to min`() {
        val config = RateLimitConfig(maxRetryInterval = 0)
        val validated = config.validated()

        assertEquals(1, validated.maxRetryInterval) // Clamped to 1 second
    }

    @Test
    fun `RateLimitConfig validation clamps maxRateLimitDuration`() {
        val config = RateLimitConfig(maxRateLimitDuration = 1000000)
        val validated = config.validated()

        assertEquals(604800, validated.maxRateLimitDuration) // Clamped to 1 week
    }

    @Test
    fun `BackoffConfig validation clamps maxRetryCount`() {
        val config = BackoffConfig(maxRetryCount = 2000)
        val validated = config.validated()

        assertEquals(1000, validated.maxRetryCount) // Clamped to max
    }

    @Test
    fun `BackoffConfig validation clamps baseBackoffInterval to max`() {
        val config = BackoffConfig(baseBackoffInterval = 100.0)
        val validated = config.validated()

        assertEquals(60.0, validated.baseBackoffInterval) // Clamped to 60 seconds
    }

    @Test
    fun `BackoffConfig validation clamps baseBackoffInterval to min`() {
        val config = BackoffConfig(baseBackoffInterval = 0.05)
        val validated = config.validated()

        assertEquals(0.1, validated.baseBackoffInterval) // Clamped to 100ms
    }

    @Test
    fun `BackoffConfig validation clamps jitterPercent`() {
        val config = BackoffConfig(jitterPercent = 100)
        val validated = config.validated()

        assertEquals(50, validated.jitterPercent) // Clamped to 50%
    }

    @Test
    fun `BackoffConfig validation filters invalid status codes`() {
        val config = BackoffConfig(
            statusCodeOverrides = mapOf(
                99 to RetryBehavior.RETRY,   // Below valid range
                408 to RetryBehavior.RETRY,  // Valid
                600 to RetryBehavior.RETRY   // Above valid range
            )
        )
        val validated = config.validated()

        assertNull(validated.statusCodeOverrides[99])
        assertNull(validated.statusCodeOverrides[600])
        assertEquals(RetryBehavior.RETRY, validated.statusCodeOverrides[408])
    }

    @Test
    fun `BackoffConfig validation handles negative values`() {
        val config = BackoffConfig(
            maxRetryCount = -10,
            baseBackoffInterval = -1.0,
            jitterPercent = -5
        )
        val validated = config.validated()

        assertEquals(0, validated.maxRetryCount) // Clamped to 0
        assertEquals(0.1, validated.baseBackoffInterval) // Clamped to min
        assertEquals(0, validated.jitterPercent) // Clamped to 0
    }

    @Test
    fun `HttpConfig automatic validation clamps out-of-range values on deserialize`() {
        val json = """
            {
              "rateLimitConfig": {
                "maxRetryCount": 5000,
                "maxRetryInterval": 10000
              },
              "backoffConfig": {
                "jitterPercent": 200
              }
            }
        """.trimIndent()

        val config = Json.decodeFromString<HttpConfig>(json)

        // Values should be automatically clamped during deserialization
        assertEquals(1000, config.rateLimitConfig.maxRetryCount)
        assertEquals(3600, config.rateLimitConfig.maxRetryInterval)
        assertEquals(50, config.backoffConfig.jitterPercent)
    }

    @Test
    fun `HttpConfig handles malformed JSON gracefully`() {
        val malformedJsons = listOf(
            """{"rateLimitConfig": []}""",  // Wrong type
            """{"backoffConfig": 123}""",    // Wrong type
            """{"rateLimitConfig": {"maxRetryCount": "invalid"}}"""  // Wrong value type
        )

        malformedJsons.forEach { json ->
            val config = Json.decodeFromString<HttpConfig>(json)
            // Should return defaults without throwing
            assertNotNull(config)
            assertEquals(100, config.rateLimitConfig.maxRetryCount)
        }
    }
}
