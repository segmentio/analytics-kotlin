package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utils.spyStore
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

internal class LogTargetTest {

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
    fun `test metric normal`() {
        val logger = spyk(SegmentLog())
        analytics.add(logger)
        analytics.metric("Counter", "Cool", 2.0, null)

        val message = LogFactory.buildLog(LoggingType.Filter.METRIC, "Counter", "Cool", value = 2.0, tags = null)

        verify { logger.log(message, LoggingType.Filter.METRIC) }
    }

//    @Test
//    fun log() {
//        val logger = spyk(SegmentLog())
//        analytics.add(logger)
//        analytics.log("test")
//        verify { logger.log(LogType.INFO, "test", null) }
//    }
//
//    @Test
//    fun flush() {
//        val logger = spyk(Logger())
//        analytics.add(logger)
//        analytics.log("test", TrackEvent(emptyJsonObject, "test"), LogType.INFO)
//        analytics.logFlush()
//        verify { logger.flush() }
//    }

//    internal class LoggerMockPlugin: SegmentLog() {
//
//
//
//        override fun flush() {
//            super.flush()
//
//        }
//    }
}
