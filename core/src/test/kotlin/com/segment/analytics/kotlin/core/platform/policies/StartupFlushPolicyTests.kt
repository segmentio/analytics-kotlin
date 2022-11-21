package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.Properties
import com.segment.analytics.kotlin.core.ScreenEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StartupFlushPolicyTests {

    @Test
    fun `Policy flushes only first call to shouldFlush`() {

        val startupFlushPolicy = StartupFlushPolicy()

        // Should only flush the first time requested!
        assertTrue(startupFlushPolicy.shouldFlush())

        // Should now not flush any more!
        assertFalse(startupFlushPolicy.shouldFlush())

        // No matter how many times you call shouldFlush()
        for (i in 1..10) {
            assertFalse(startupFlushPolicy.shouldFlush())
        }

        // even you call reset; the policy will not want to flush.
        startupFlushPolicy.reset()
        assertFalse(startupFlushPolicy.shouldFlush())

        // Adding events has no effect and does not cause the policy to flush
        startupFlushPolicy.updateState("event 1")
        assertFalse(startupFlushPolicy.shouldFlush())
    }
}