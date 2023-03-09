package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.testAnalytics
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

internal class LogTargetTest {

    private lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    internal fun setUp() {
        clearPersistentStorage()
        val config = Configuration(
            writeKey = "123",
            application = "Tetst",
            autoAddSegmentDestination = false
        )
        analytics = testAnalytics(config, testScope, testDispatcher)
    }

    @Test
    fun `can call log() with an analytics reference`() {
        val parseLogCalled = AtomicBoolean(false)
        val testLogger = object : LogTarget {
            override fun parseLog(log: LogMessage) {
                if (log.message.contains("test") && log.kind == LogFilterKind.DEBUG) {
                    parseLogCalled.set(true)
                }
            }
        }
        analytics.logTarget = testLogger
        Analytics.debugLogsEnabled = true

        analytics.log("test") // Default LogFilterKind is DEBUG

        assertTrue(parseLogCalled.get())
    }

    @Test
    fun `can call log() with different log filter kind`() {
        val parseLogErrorCalled = AtomicBoolean(false)
        val parseLogWarnCalled = AtomicBoolean(false)
        val parseLogDebugCalled = AtomicBoolean(false)

        val testLogger = object: LogTarget {
            override fun parseLog(log: LogMessage) {

                if (log.message.contains("test")) {
                    when (log.kind) {
                        LogFilterKind.ERROR -> {
                            parseLogErrorCalled.set(true)
                        }
                        LogFilterKind.WARNING -> {
                            parseLogWarnCalled.set(true)
                        }
                        LogFilterKind.DEBUG -> {
                            parseLogDebugCalled.set(true)
                        }
                    }
                }
            }
        }

        analytics.logTarget = testLogger
        Analytics.debugLogsEnabled = true

        analytics.log("test", kind = LogFilterKind.ERROR)
        analytics.log("test", kind = LogFilterKind.WARNING)
        analytics.log("test", kind = LogFilterKind.DEBUG)

        assertTrue(parseLogErrorCalled.get())
        assertTrue(parseLogWarnCalled.get())
        assertTrue(parseLogDebugCalled.get())
    }

    @Test
    fun `debug logging respects debugLogsEnabled flag`() {

        var logSent = AtomicBoolean(false)

        val testLogger = object : LogTarget {
            override fun parseLog(log: LogMessage) {
                logSent.set(true)
            }
        }

        analytics.logTarget = testLogger

        // Turn ON debug logs
        Analytics.debugLogsEnabled = true
        analytics.log("test", kind = LogFilterKind.DEBUG)

        assertTrue(logSent.get())

        // Turn OFF debug logs
        Analytics.debugLogsEnabled = false
        logSent.set(false)

        analytics.log("test", kind = LogFilterKind.DEBUG)
        assertFalse(logSent.get())
    }

}
