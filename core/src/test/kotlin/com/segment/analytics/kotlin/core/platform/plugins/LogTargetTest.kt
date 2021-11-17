package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utils.spyStore
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

internal class LogTargetTest {

    private lateinit var analytics: Analytics

    private val testDispatcher = TestCoroutineDispatcher()

    private val testScope = TestCoroutineScope(testDispatcher)

    @BeforeEach
    internal fun setUp() {
        clearPersistentStorage()
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
        var metricPassed = false
        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                if (logMessage is LogFactory.MetricLog) {
                    assertEquals(logMessage.message, "Metric of 5")
                    assertEquals(logMessage.kind, LogFilterKind.DEBUG)
                    assertEquals(logMessage.title, "Counter")
                    assertEquals(logMessage.value, 5.0)

                    metricPassed = true
                }
            }
        }
        analytics.add(testLogger)
        SegmentLog.loggingEnabled = true
        analytics.metric("Counter", "Metric of 5", 5.0, null)

        assertTrue(metricPassed)
    }

    @Test
    fun `test metric gauge normal`() {
        var metricPassed = false
        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                if (logMessage is LogFactory.MetricLog) {
                    assertEquals(logMessage.message, "Metric of 5")
                    assertEquals(logMessage.kind, LogFilterKind.DEBUG)
                    assertEquals(logMessage.title, "Gauge")
                    assertEquals(logMessage.value, 345.0)

                    metricPassed = true
                }
            }
        }
        analytics.add(testLogger)
        SegmentLog.loggingEnabled = true
        analytics.metric("Gauge", "Metric of 5", 345.0, null)

        assertTrue(metricPassed)
    }

    @Test
    fun `test history normal`() {
        var historyPassed = false

        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                if (logMessage is LogFactory.HistoryLog) {
                    assertEquals(logMessage.function, "test history normal")
                    assertEquals(logMessage.logType, LoggingType.Filter.HISTORY)

                    historyPassed = true
                }
            }
        }
        analytics.add(testLogger)
        SegmentLog.loggingEnabled = true
        analytics.history(event = TrackEvent(event = "Tester", properties = emptyJsonObject), sender = this)

        assertTrue(historyPassed)
    }

    @Test
    fun `test logging disabled`() {
        var loggingPassed = false

        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                fail<String>("Should not hit this point")
                loggingPassed = true
            }
        }
        analytics.add(testLogger)
        SegmentLog.loggingEnabled = false
        analytics.log("Should NOT hit our proper target")

        assertFalse(loggingPassed)
    }

    @Test
    fun `test metric disabled`() {
        var metricPassed = false

        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                fail<String>("Should not hit this point")
                metricPassed = true
            }
        }
        analytics.add(testLogger)
        SegmentLog.loggingEnabled = false
        analytics.metric("Counter", "Metric of 5", 5.0, null)

        assertFalse(metricPassed)
    }

    @Test
    fun `test history disabled`() {
        var historyPassed = false

        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                fail<String>("Should not hit this point")
                historyPassed = true
            }
        }
        analytics.add(testLogger)
        SegmentLog.loggingEnabled = false
        analytics.history(event = TrackEvent(event = "Tester", properties = emptyJsonObject), sender = this)

        assertFalse(historyPassed)
    }

    @Test
    fun `test logging disabled by default`() {
        SegmentLog.loggingEnabled = false

        Analytics.debugLogsEnabled = true
        assertTrue(SegmentLog.loggingEnabled, "Logging should change to enabled")

        Analytics.debugLogsEnabled = false
        assertFalse(SegmentLog.loggingEnabled, "Logging should reset to disabled")
    }
}
