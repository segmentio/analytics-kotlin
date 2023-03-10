package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

internal class SegmentLogTest {

    @Test
    fun `can call segmentLog() `() {
        val parseLogCalled = AtomicBoolean(false)
        val testLogger = object : Logger {
            override fun parseLog(log: LogMessage) {
                if (log.message.contains("test") && log.kind == LogKind.ERROR) {
                    parseLogCalled.set(true)
                }
            }
        }
        Analytics.logger = testLogger

        Analytics.segmentLog("test")

        assertTrue(parseLogCalled.get())
    }

    @Test
    fun `can call segmentLog() with different log filter kind`() {
        val parseLogErrorCalled = AtomicBoolean(false)
        val parseLogWarnCalled = AtomicBoolean(false)
        val parseLogDebugCalled = AtomicBoolean(false)

        val testLogger = object: Logger {
            override fun parseLog(log: LogMessage) {

                if (log.message.contains("test")) {
                    when (log.kind) {
                        LogKind.ERROR -> {
                            parseLogErrorCalled.set(true)
                        }
                        LogKind.WARNING -> {
                            parseLogWarnCalled.set(true)
                        }
                        LogKind.DEBUG -> {
                            parseLogDebugCalled.set(true)
                        }
                    }
                }
            }
        }

        Analytics.logger = testLogger
        Analytics.debugLogsEnabled = true

        Analytics.segmentLog("test") // Default LogFilterKind is ERROR
        Analytics.segmentLog("test", kind = LogKind.WARNING)
        Analytics.segmentLog("test", kind = LogKind.DEBUG)

        assertTrue(parseLogErrorCalled.get())
        assertTrue(parseLogWarnCalled.get())
        assertTrue(parseLogDebugCalled.get())
    }

    @Test
    fun `debug logging respects debugLogsEnabled flag`() {

        var logSent = AtomicBoolean(false)

        val testLogger = object : Logger {
            override fun parseLog(log: LogMessage) {
                logSent.set(true)
            }
        }

        Analytics.logger = testLogger

        // Turn ON debug logs
        Analytics.debugLogsEnabled = true
        Analytics.segmentLog("test", kind = LogKind.DEBUG)

        assertTrue(logSent.get())

        // Turn OFF debug logs
        Analytics.debugLogsEnabled = false
        logSent.set(false)

        Analytics.segmentLog("test", kind = LogKind.DEBUG)
        assertFalse(logSent.get())
    }
}

