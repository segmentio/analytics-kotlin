package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

internal class StartupQueueTest {

    private lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    internal fun setUp() {
        val config = Configuration(
            writeKey = "123",
            application = "Test",
            autoAddSegmentDestination = false
        )
        analytics = testAnalytics(config, testScope, testDispatcher)
    }

    @Test
    fun `execute when startup queue not ready`() {
        val startupQueue = spyk(StartupQueue())
        every { startupQueue.setup(any()) } answers { startupQueue.analytics = analytics }

        analytics.add(startupQueue)
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
        assertNull(startupQueue.execute(trackEvent))
    }
}