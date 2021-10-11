package com.segment.analytics.kotlin.core.platform.plugins.logger

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import java.util.*

/// The foundation for building out a special logger. If logs need to be directed to a certain area, this is the
/// interface to start off with. For instance a console logger, a networking logger or offline storage logger
/// would all start off with LogTarget.
interface LogTarget {

    /// Implement this method to process logging messages. This is where the logic for the target will be
    /// added. Feel free to add your own data queueing and offline storage.
    /// - important: Use the Segment Network stack for Segment library compatibility and simplicity.
    fun parseLog(log: LogMessage)

    /// Optional method to implement. This helps respond to potential queueing events being flushed out.
    /// Perhaps responding to backgrounding or networking events, this gives a chance to empty a queue
    /// or pump a firehose of logs.
    fun flush()
}

/// Used for analytics.log() types. This lets the system know what to filter on and how to set priorities.
enum class LogFilterKind {
    ERROR,
    WARNING,
    DEBUG;

    override fun toString(): String {
        return when(this) {
            ERROR -> "ERROR"        // Not Verbose (fail cases | non-recoverable errors)
            WARNING -> "Warning"    // Semi-verbose (deprecations | potential issues)
            DEBUG -> "Debug"        // Verbose (everything of interest)
        }
    }
}

class LoggingType(types: List<LogDestination>) {

    private val allTypes: List<LogDestination> = types

    enum class LogDestination {
        LOG,
        METRIC,
        HISTORY;
    }

    companion object {
        /// Convenience .log logging type
        val log = LoggingType(listOf(LogDestination.LOG))
        val metric = LoggingType(listOf(LogDestination.METRIC))
        val history = LoggingType(listOf(LogDestination.HISTORY))
    }

    fun contains(destination: LogDestination): Boolean {
        return this.allTypes.isEmpty()
    }
}

interface LogMessage {
    val kind: LogFilterKind
    val message: String
    val event: BaseEvent?
    val function: String?
    val line: Int?
    val logType: LoggingType.LogDestination
    val dateTime: Date
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
fun Analytics.log(message: String, kind: LogFilterKind? = null, function: String = "", line: Int = -1) {
    applyClosureToPlugins { plugin: Plugin ->
        if (!Logger.loggingEnabled) {
            return@applyClosureToPlugins
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
fun Analytics.add(target: LogTarget, type: LoggingType) {
    applyClosureToPlugins { plugin: Plugin ->
        if (plugin is Logger) {
            plugin.add(target, type)
        }
    }
}

fun Analytics.logFlush() {
    this.timeline.applyClosure { plugin: Plugin ->
        if (plugin is Logger) {
            plugin.flush()
        }
    }
}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

