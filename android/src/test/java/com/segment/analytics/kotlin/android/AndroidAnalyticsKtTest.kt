package com.segment.analytics.kotlin.android

import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

internal class AndroidAnalyticsKtTest {
    @Test
    fun `jvm initializer in android platform should failed`() {
        try {
            com.segment.analytics.kotlin.core.Analytics("123") {
                application = "Test"
            }
            fail()
        }
        catch(e: Exception){
            assertEquals(e.message?.contains("Android"), true)
        }
    }
}