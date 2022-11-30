package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.utils.mockAnalytics
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FrequencyFlushPolicyTests {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockAnalytics = mockAnalytics(testScope, testDispatcher)

    @Test
    fun `Policy should not ever request flush`() {
        val frequencyFlushPolicy = FrequencyFlushPolicy()

        // Should not flush at first
        assertFalse(frequencyFlushPolicy.shouldFlush())

        // Start the policy's scheduler
        frequencyFlushPolicy.schedule(analytics = mockAnalytics)

        // Should still not flush
        assertFalse(frequencyFlushPolicy.shouldFlush())

        // Stop the scheduler
        frequencyFlushPolicy.unschedule()

        // Should still not flush
        assertFalse(frequencyFlushPolicy.shouldFlush())

    }


    @Test
    fun `Policy should flush when first scheduled`() = runTest {

        val frequencyFlushPolicy = FrequencyFlushPolicy()

        // Start the scheduler
        frequencyFlushPolicy.schedule(mockAnalytics)

        // Make sure flush is called just once
        coVerify(exactly = 1) {
            mockAnalytics.flush()
        }
    }


    @Test
    fun `Policy should flush after each flush interval`() = runTest {

        val frequencyFlushPolicy = FrequencyFlushPolicy(1_000)

        frequencyFlushPolicy.schedule(mockAnalytics)

        delay(2_500)

        // Make sure flush is called just once
        coVerify(atLeast = 1, atMost = 2) {
            mockAnalytics.flush()
        }

    }
}