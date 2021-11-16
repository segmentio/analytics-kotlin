package com.segment.analytics.kotlin.core.platform.plugins.logger

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import java.util.*
import kotlin.reflect.KClass

/**
 * The foundation for building out a special logger. If logs need to be directed to a certain area, this is the
 * interface to start off with. For instance a console logger, a networking logger or offline storage logger
 * would all start off with LogTarget.
 */
interface LogTarget {

    /**
     * Implement this method to process logging messages. This is where the logic for the target will be
     * added. Feel free to add your own data queueing and offline storage.
     * - important: Use the Segment Network stack for Segment library compatibility and simplicity.
     */
    fun parseLog(log: LogMessage)

    /**
     * Optional method to implement. This helps respond to potential queueing events being flushed out.
     * Perhaps responding to backgrounding or networking events, this gives a chance to empty a queue
     * or pump a firehose of logs.
     */
    fun flush() {}
}

/**
 * Used for analytics.log() types. This lets the system know what to filter on and how to set priorities.
 */
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

/**
 * The Segment logging system has three types of logs: log, metric and history. When adding a target that
 * responds to logs, it is possible to adhere to 1 to many. In other words, a LoggingType can be .log &
 * .history. This is used to tell which targets logs are directed to.
 */
class LoggingType(private val types: Set<Filter>) {

    enum class Filter {
        LOG,
        METRIC,
        HISTORY;
    }

    companion object {
        /// Convenience .log logging type
        val log = LoggingType(setOf(Filter.LOG))
        /// Convenience .metric logging type
        val metric = LoggingType(setOf(Filter.METRIC))
        /// Convenience .history logging type
        val history = LoggingType(setOf(Filter.HISTORY))
    }

    /**
     * Convenience method to find if the LoggingType supports a particular destination.
     *
     * @property destination The particular destination being tested for conformance.
     */
    internal fun contains(destination: Filter): Boolean {
        return this.types.contains(destination)
    }
}

/**
 * The interface to the message being returned to `LogTarget` -> `parseLog()`.
 */
interface LogMessage {
    val kind: LogFilterKind
    val title: String?
    val message: String
    val event: BaseEvent?
    val function: String?
    val line: Int?
    val logType: LoggingType.Filter
    val dateTime: Date
}

enum class MetricType() {
    Counter, // Not Verbose
    Gauge;   // Semi-verbose

    override fun toString() = this.name

    companion object {
        fun from(string: String): MetricType {
            var returnType = Counter
            if (string == "Gauge") {
                returnType = Gauge
            }
            return returnType
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/**
 * The public logging method for capturing all general types of log messages related to Segment.
 *
 * @property  message The main message of the log to be captured.
 * @property kind Usually .error, .warning or .debug, in order of severity. This helps filter logs based on
 * this added metadata.
 * @property function The name of the function the log came from. This will be captured automatically.
 * @property line The line number in the function the log came from. This will be captured automatically.
 */
@JvmOverloads
fun Analytics.log(message: String, kind: LogFilterKind? = null, function: String = "", line: Int = -1) {

    // Check if we should send the event
    if (!SegmentLog.loggingEnabled) {
        return
    }

    applyClosureToPlugins { plugin: Plugin ->
        if (plugin is SegmentLog) {
            var filterKind = plugin.filterKind
            if (kind != null) {
                filterKind = kind
            }

            val log = LogFactory.buildLog(LoggingType.Filter.LOG, "", message, filterKind, function, line)
            plugin.log(log, LoggingType.Filter.LOG)
        }
    }
}

/**
 * The public logging method for capturing metrics related to Segment or other libraries.
 *
 * @property type Metric type, usually .counter or .gauge. Select the one that makes sense for the metric.
 * @property name The title of the metric to track.
 * @property value The value associated with the metric. This would be an incrementing counter or time
 * or pressure gauge.
 * @property tags Any tags that should be associated with the metric. Any extra metadata that may help.
 */
@JvmOverloads
fun Analytics.metric(type: String, name: String, value: Double, tags: List<String>? = null) {

    // Check if we should send the event
    if (!SegmentLog.loggingEnabled) {
        return
    }

    applyClosureToPlugins { plugin: Plugin ->
        if (plugin is SegmentLog) {
            val log = LogFactory.buildLog(LoggingType.Filter.METRIC, type, name, value = value, tags = tags)
            plugin.log(log, LoggingType.Filter.METRIC)
        }
    }
}

/**
 * Used to track the history of events as the event data travels through the Segment Event Timeline. As
 * plugins manipulate the data at the `before`, `enrichment`, `destination`,
 * `destination timeline`, and `after` states, an event can be tracked. Starting with the first one
 *
 * @property event The timeline event that is to be processed.
 * @property sender Where the event came from.
 * @property function The name of the function the log came from. This will be captured automatically.
 * @property line The line number in the function the log came from. This will be captured automatically.
 */
@JvmOverloads
fun Analytics.history(event: BaseEvent, sender: Any, function: String = "", line: Int = -1) {

    // Check if we should send the event
    if (!SegmentLog.loggingEnabled) {
        return
    }

    val methodInfo = Analytics.callingMethodDetails(function, line)
    var updatedFunction = function
    var updatedLine = line
    if (function.isEmpty() && line == -1) {
        updatedFunction = methodInfo.first
        updatedLine = methodInfo.second
    }

    applyClosureToPlugins { plugin: Plugin ->
        if (plugin is SegmentLog) {
            val log = LogFactory.buildLog(LoggingType.Filter.HISTORY,
                title = event.toString(),
                message = "",
                function = updatedFunction,
                line = updatedLine,
                event = event,
                sender = sender)
            plugin.log(log, LoggingType.Filter.HISTORY)
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/**
 * Add a logging target to the system. These `targets` can handle logs in various ways. Consider
 * sending logs to the console, the OS and a web service. Three targets can handle these scenarios.
 *
 * @property target A `LogTarget` that has logic to parse and handle log messages.
 * @property type The type consists of `log`, `metric` or `history`. These correspond to the
 * public API on Analytics.
 */
fun Analytics.add(target: LogTarget, type: LoggingType) {
    applyClosureToPlugins { plugin: Plugin ->
        if (plugin is SegmentLog) {
            plugin.add(target, type)
        }
    }
}

/**
 * Removes a logging target based on the class type. Since only one type can be added, we don't need to worry about
 * removing duplicates.
 *
 * @property targetType A `LogTarget` class type that should be removed from the system.
 */
fun <T: LogTarget> Analytics.remove(targetType: KClass<T>) {
    applyClosureToPlugins { plugin: Plugin ->
        if (plugin is SegmentLog) {
            plugin.remove(targetType)
        }
    }
}

/**
 * Expunge all cached logs that have been captured.
 */
fun Analytics.logFlush() {
    this.timeline.applyClosure { plugin: Plugin ->
        if (plugin is SegmentLog) {
            plugin.flush()
        }
    }
}
