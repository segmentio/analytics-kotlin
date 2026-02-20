package com.segment.analytics.kotlin.core.retry

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RetryConfigParserTest {

    @Test
    fun `parse returns default config when JSON is null`() {
        val config = RetryConfigParser.parse(null)

        assertTrue(config.rateLimitConfig.enabled)
        assertEquals(300, config.rateLimitConfig.maxRetryInterval)
        assertTrue(config.backoffConfig.enabled)
        assertEquals(100, config.backoffConfig.maxRetryCount)
    }

    @Test
    fun `parse returns default config when JSON is empty`() {
        val json = buildJsonObject {}
        val config = RetryConfigParser.parse(json)

        assertTrue(config.rateLimitConfig.enabled)
        assertTrue(config.backoffConfig.enabled)
    }

    @Test
    fun `parse handles valid complete configuration`() {
        val json = buildJsonObject {
            putJsonObject("rateLimitConfig") {
                put("enabled", true)
                put("maxRetryInterval", 600)
            }
            putJsonObject("backoffConfig") {
                put("enabled", true)
                put("maxRetryCount", 50)
                put("baseBackoffInterval", 1.0)
                put("maxBackoffInterval", 600)
                put("maxTotalBackoffDuration", 7200)
                put("jitterPercent", 20)
                put("default4xxBehavior", "DROP")
                put("default5xxBehavior", "RETRY")
                put("unknownCodeBehavior", "DROP")
            }
        }

        val config = RetryConfigParser.parse(json)

        assertTrue(config.rateLimitConfig.enabled)
        assertEquals(600, config.rateLimitConfig.maxRetryInterval)
        assertTrue(config.backoffConfig.enabled)
        assertEquals(50, config.backoffConfig.maxRetryCount)
        assertEquals(1.0, config.backoffConfig.baseBackoffInterval)
        assertEquals(600, config.backoffConfig.maxBackoffInterval)
        assertEquals(7200L, config.backoffConfig.maxTotalBackoffDuration)
        assertEquals(20, config.backoffConfig.jitterPercent)
        assertEquals(RetryBehavior.DROP, config.backoffConfig.default4xxBehavior)
        assertEquals(RetryBehavior.RETRY, config.backoffConfig.default5xxBehavior)
        assertEquals(RetryBehavior.DROP, config.backoffConfig.unknownCodeBehavior)
    }

    @Test
    fun `parse clamps maxRetryInterval to valid range`() {
        val json = buildJsonObject {
            putJsonObject("rateLimitConfig") {
                put("maxRetryInterval", 5000) // Above max
            }
        }

        val config = RetryConfigParser.parse(json)
        assertEquals(3600, config.rateLimitConfig.maxRetryInterval) // Clamped to 1 hour
    }

    @Test
    fun `parse clamps maxRetryInterval minimum`() {
        val json = buildJsonObject {
            putJsonObject("rateLimitConfig") {
                put("maxRetryInterval", 0) // Below min
            }
        }

        val config = RetryConfigParser.parse(json)
        assertEquals(1, config.rateLimitConfig.maxRetryInterval) // Clamped to 1 second
    }

    @Test
    fun `parse clamps baseBackoffInterval to valid range`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                put("baseBackoffInterval", 100.0) // Above max
            }
        }

        val config = RetryConfigParser.parse(json)
        assertEquals(60.0, config.backoffConfig.baseBackoffInterval) // Clamped to 60 seconds
    }

    @Test
    fun `parse clamps baseBackoffInterval minimum`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                put("baseBackoffInterval", 0.05) // Below min
            }
        }

        val config = RetryConfigParser.parse(json)
        assertEquals(0.1, config.backoffConfig.baseBackoffInterval) // Clamped to 100ms
    }

    @Test
    fun `parse clamps maxRetryCount to valid range`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                put("maxRetryCount", 2000) // Above max
            }
        }

        val config = RetryConfigParser.parse(json)
        assertEquals(1000, config.backoffConfig.maxRetryCount) // Clamped to 1000
    }

    @Test
    fun `parse clamps jitterPercent to valid range`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                put("jitterPercent", 100) // Above max
            }
        }

        val config = RetryConfigParser.parse(json)
        assertEquals(50, config.backoffConfig.jitterPercent) // Clamped to 50%
    }

    @Test
    fun `parse handles disabled configs`() {
        val json = buildJsonObject {
            putJsonObject("rateLimitConfig") {
                put("enabled", false)
            }
            putJsonObject("backoffConfig") {
                put("enabled", false)
            }
        }

        val config = RetryConfigParser.parse(json)

        assertFalse(config.rateLimitConfig.enabled)
        assertFalse(config.backoffConfig.enabled)
    }

    @Test
    fun `parse handles status code overrides`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                putJsonObject("statusCodeOverrides") {
                    put("408", "RETRY")
                    put("410", "RETRY")
                    put("429", "RETRY")
                    put("501", "DROP")
                }
            }
        }

        val config = RetryConfigParser.parse(json)

        assertEquals(RetryBehavior.RETRY, config.backoffConfig.statusCodeOverrides[408])
        assertEquals(RetryBehavior.RETRY, config.backoffConfig.statusCodeOverrides[410])
        assertEquals(RetryBehavior.RETRY, config.backoffConfig.statusCodeOverrides[429])
        assertEquals(RetryBehavior.DROP, config.backoffConfig.statusCodeOverrides[501])
    }

    @Test
    fun `parse ignores invalid status codes in overrides`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                putJsonObject("statusCodeOverrides") {
                    put("99", "RETRY")   // Below valid range
                    put("600", "RETRY")  // Above valid range
                    put("abc", "RETRY")  // Not a number
                    put("408", "RETRY")  // Valid
                }
            }
        }

        val config = RetryConfigParser.parse(json)

        assertNull(config.backoffConfig.statusCodeOverrides[99])
        assertNull(config.backoffConfig.statusCodeOverrides[600])
        assertEquals(RetryBehavior.RETRY, config.backoffConfig.statusCodeOverrides[408])
    }

    @Test
    fun `parse handles invalid retry behavior strings`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                put("default4xxBehavior", "INVALID")
                put("default5xxBehavior", "")
            }
        }

        val config = RetryConfigParser.parse(json)

        // Should fall back to defaults
        assertEquals(RetryBehavior.DROP, config.backoffConfig.default4xxBehavior)
        assertEquals(RetryBehavior.RETRY, config.backoffConfig.default5xxBehavior)
    }

    @Test
    fun `parse handles mixed case retry behavior`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                put("default4xxBehavior", "retry")  // lowercase
                put("default5xxBehavior", "Drop")   // mixed case
            }
        }

        val config = RetryConfigParser.parse(json)

        assertEquals(RetryBehavior.RETRY, config.backoffConfig.default4xxBehavior)
        assertEquals(RetryBehavior.DROP, config.backoffConfig.default5xxBehavior)
    }

    @Test
    fun `parse handles partial configuration`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                put("maxRetryCount", 25)
                // Other fields missing - should use defaults
            }
        }

        val config = RetryConfigParser.parse(json)

        assertEquals(25, config.backoffConfig.maxRetryCount)
        assertEquals(0.5, config.backoffConfig.baseBackoffInterval) // Default
        assertEquals(300, config.backoffConfig.maxBackoffInterval) // Default
        assertEquals(10, config.backoffConfig.jitterPercent) // Default
    }

    @Test
    fun `parse never throws on corrupt JSON`() {
        // This shouldn't throw even with malformed data
        val json = buildJsonObject {
            put("rateLimitConfig", "not an object") // Wrong type
            put("backoffConfig", JsonArray(emptyList())) // Wrong type
        }

        val config = RetryConfigParser.parse(json)

        // Should return defaults without throwing
        assertTrue(config.rateLimitConfig.enabled)
        assertTrue(config.backoffConfig.enabled)
    }

    @Test
    fun `parse handles negative values by clamping`() {
        val json = buildJsonObject {
            putJsonObject("backoffConfig") {
                put("maxRetryCount", -10)
                put("baseBackoffInterval", -1.0)
                put("jitterPercent", -5)
            }
        }

        val config = RetryConfigParser.parse(json)

        assertEquals(0, config.backoffConfig.maxRetryCount) // Clamped to 0
        assertEquals(0.1, config.backoffConfig.baseBackoffInterval) // Clamped to min
        assertEquals(0, config.backoffConfig.jitterPercent) // Clamped to 0
    }
}
