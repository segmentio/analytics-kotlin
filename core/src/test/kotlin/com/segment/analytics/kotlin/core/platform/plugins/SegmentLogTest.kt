package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.testAnalytics
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled

import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

internal class SegmentLogTest {

    @Test
    fun `can call segmentLog() without an analytics reference`() {
        val parseLogCalled = AtomicBoolean(false)
        val testLogger = object : LogTarget {
            override fun parseLog(log: LogMessage) {
                if (log.message.contains("test") && log.kind == LogFilterKind.ERROR) {
                    parseLogCalled.set(true)
                }
            }
        }
        Analytics.staticLogTarget = testLogger

        Analytics.segmentLog("test")

        assertTrue(parseLogCalled.get())
    }

    @Test
    fun `can call segmentLog() with different log filter kind`() {
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

        Analytics.staticLogTarget = testLogger
        Analytics.debugLogsEnabled = true

        Analytics.segmentLog("test") // Default LogFilterKind is ERROR
        Analytics.segmentLog("test", kind = LogFilterKind.WARNING)
        Analytics.segmentLog("test", kind = LogFilterKind.DEBUG)

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

        Analytics.staticLogTarget = testLogger

        // Turn ON debug logs
        Analytics.debugLogsEnabled = true
        Analytics.segmentLog("test", kind = LogFilterKind.DEBUG)

        assertTrue(logSent.get())

        // Turn OFF debug logs
        Analytics.debugLogsEnabled = false
        logSent.set(false)

        Analytics.segmentLog("test", kind = LogFilterKind.DEBUG)
        assertFalse(logSent.get())
    }
}

