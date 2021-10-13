package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utils.spyStore
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

internal class LoggerTest {

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
        val store = spyStore(testScope, testDispatcher)
        analytics = Analytics(config, store, testScope, testDispatcher, testDispatcher)
    }

    @Test
    fun log() {
        val logger = spyk(Logger())
        analytics.add(logger)
        analytics.log("test", null, LogType.INFO)
        verify { logger.log(LogType.INFO, "test", null) }
    }

    @Test
    fun flush() {
        val logger = spyk(Logger())
        analytics.add(logger)
        analytics.log("test", TrackEvent(emptyJsonObject, "test"), LogType.INFO)
        analytics.logFlush()
        verify { logger.flush() }
    }
}