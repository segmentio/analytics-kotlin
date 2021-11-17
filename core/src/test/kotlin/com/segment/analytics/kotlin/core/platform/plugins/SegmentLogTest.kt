package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utils.spyStore
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

internal class SegmentLogTest {

    private lateinit var analytics: Analytics

    private val testDispatcher = TestCoroutineDispatcher()

    private val testScope = TestCoroutineScope(testDispatcher)
    private val mockLogger = LoggerMockPlugin()

    class LoggerMockPlugin : SegmentLog() {

        var logClosure: ((LogFilterKind, LogMessage) -> Unit)? = null
        var flushClosure: (() -> Unit)? = null

        override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
            super.log(logMessage, destination)
            logClosure?.let { it(logMessage.kind, logMessage) }
        }

        override fun flush() {
            super.flush()
            flushClosure?.let { it() }
        }
    }

    @BeforeEach
    internal fun setUp() {
        clearPersistentStorage()
        val config = Configuration(
            writeKey = "123",
            application = "Test",
            autoAddSegmentDestination = false
        )
        val store = spyStore(testScope, testDispatcher)
        analytics = Analytics(config, store, testScope, testDispatcher, testDispatcher)
        analytics.add(mockLogger)
        SegmentLog.loggingEnabled = true
    }

    @AfterEach
    internal fun tearDown() {
        clearPersistentStorage()
    }

    @Test
    fun `test logging`() {

        // Arrange
        var logPassed = false

        // Assert
        mockLogger.logClosure = { filterKind: LogFilterKind, message: LogMessage ->
            assertEquals(filterKind, LogFilterKind.DEBUG)
            assertEquals(message.message, "Something Other Than Awesome")
            logPassed = true
        }

        analytics.log(message = "Something Other Than Awesome")

        assertTrue(logPassed)
    }

    @Test
    fun `test warning logging`() {

        // Arrange
        var logPassed = false

        // Assert
        mockLogger.logClosure = { filterKind: LogFilterKind, message: LogMessage ->
            assertEquals(filterKind, LogFilterKind.WARNING)
            assertEquals(message.message, "Something Other Than Awesome")
            logPassed = true
        }

        analytics.log(message = "Something Other Than Awesome", kind = LogFilterKind.WARNING)

        assertTrue(logPassed)
    }

    @Test
    fun `test error logging`() {

        // Arrange
        var logPassed = false

        // Assert
        mockLogger.logClosure = { filterKind: LogFilterKind, message: LogMessage ->
            assertEquals(filterKind, LogFilterKind.ERROR)
            assertEquals(message.message, "Something Other Than Awesome")
            logPassed = true
        }

        analytics.log(message = "Something Other Than Awesome", kind = LogFilterKind.ERROR)

        assertTrue(logPassed)
    }

    @Test
    fun `test update settings false`() {
        var settings = Settings(plan = buildJsonObject { put("logging_enabled", true) })
        mockLogger.update(settings = settings, type = Plugin.UpdateType.Refresh)

        assertTrue(SegmentLog.loggingEnabled, "Enabled logging was not set correctly")
    }

    @Test
    fun `test target success`() {
        var targetCalled = false

        val logConsoleTarget = LogConsoleTarget { _ ->
            targetCalled = true
        }
        analytics.add(target = logConsoleTarget, type = LoggingType.log)

        analytics.log("Should hit our proper target")
        assertTrue(targetCalled)
    }

    @Test
    fun `test target failure`() {
        var targetCalled = false

        val logConsoleTarget = LogConsoleTarget { _ ->
            targetCalled = true
            fail("Should not be called as it was registered for history")
        }
        analytics.add(target = logConsoleTarget, type = LoggingType.history)

        analytics.log("Should hit our proper target")
        assertFalse(targetCalled)
    }

    @Test
    fun `test flush`() {
        var flushCalled = false

        mockLogger.flushClosure = {
            flushCalled = true
        }

        analytics.flush()
        assertTrue(flushCalled)
    }

    @Test
    fun `test log flush`() {
        var flushCalled = false

        mockLogger.flushClosure = {
            flushCalled = true
        }

        analytics.logFlush()
        assertTrue(flushCalled)
    }

    @Test
    fun `test internal logging`() {

        // Arrange
        var logPassed = false

        // Assert
        mockLogger.logClosure = { filterKind: LogFilterKind, message: LogMessage ->
            assertEquals(filterKind, LogFilterKind.WARNING)
            assertEquals(message.message, "Something Other Than Awesome")
            logPassed = true
        }

        Analytics.segmentLog(message = "Something Other Than Awesome", kind = LogFilterKind.WARNING)

        assertTrue(logPassed)
    }

    @Test
    fun `test internal metric normal`() {
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
        Analytics.segmentMetric("Counter", "Metric of 5", 5.0, null)

        assertTrue(metricPassed)
    }

    @Test
    fun `test internal metric gauge`() {
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
        Analytics.segmentMetric("Gauge", "Metric of 5", 345.0, null)

        assertTrue(metricPassed)
    }
}

class LogConsoleTarget(var successClosure: ((String) -> Unit)?) : LogTarget {

    override fun parseLog(log: LogMessage) {
        successClosure?.let { it(log.message) }
    }

    override fun flush() {}
}