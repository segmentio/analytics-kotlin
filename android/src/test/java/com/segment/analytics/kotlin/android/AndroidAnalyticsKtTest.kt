package com.segment.analytics.kotlin.android

import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class AndroidAnalyticsKtTest {
    @Test
    fun `jvm initializer in android platform should failed`() {
        val exception =  assertThrows<Exception> {
            com.segment.analytics.kotlin.core.Analytics("123") {
                application = "Test"
            }
        }

        assertEquals(exception.message?.contains("Android"), true)
    }
}