package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.TrackEvent
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

internal class StartupQueueTest {

    private lateinit var analytics: Analytics

    private val testDispatcher = TestCoroutineDispatcher()

    private val testScope = TestCoroutineScope(testDispatcher)

    @BeforeEach
    internal fun setUp() {
        val config = Configuration(
            writeKey = "123",
            application = "Tetst",
            autoAddSegmentDestination = false
        )
        config.ioDispatcher = testDispatcher
        config.analyticsDispatcher = testDispatcher
        config.analyticsScope = testScope
        analytics = Analytics(config)
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