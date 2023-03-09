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
data class LogMessage(
    val kind: LogFilterKind,
    val title: String? = null,
    val message: String,
    val event: BaseEvent? = null,
    val function: String? = null,
    val line: Int? = null,
    val dateTime: Date = Date())

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/**
 * The public logging method for capturing all general types of log messages related to Segment.
 *
 * @property message The main message of the log to be captured.
 * @property kind Usually .error, .warning or .debug, in order of severity. This helps filter logs based on
 * this added metadata.
 * @property function The name of the function the log came from. This will be captured automatically.
 * @property line The line number in the function the log came from. This will be captured automatically.
 */
@JvmOverloads
fun Analytics.log(message: String, kind: LogFilterKind = LogFilterKind.DEBUG, function: String = "", line: Int = -1) {
    val logTarget = this.logTarget
    val logMessage = LogMessage(kind, message=message)
    when (kind){
        LogFilterKind.DEBUG -> {
            if (Analytics.debugLogsEnabled) {
                logTarget.parseLog(logMessage)
            }
        }
        else -> logTarget.parseLog(logMessage)
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
    val logTarget = this.logTarget
    val message = "metric: $name($type)= $value, tags= ${tags?.toString()}"
    val logMessage = LogMessage(kind=LogFilterKind.DEBUG, message=message)

    if (Analytics.debugLogsEnabled) {
        logTarget.parseLog(logMessage)
    }
}
