package com.segment.analytics.kotlin.core.platform.plugins.logger

import com.segment.analytics.kotlin.core.Analytics
import java.util.*

/**
 * The foundation for building out a special logger. If logs need to be directed to a certain area, this is the
 * interface to start off with. For instance a console logger, a networking logger or offline storage logger
 * would all start off with LogTarget.
 */
interface Logger {

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
enum class LogKind {
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
 * The interface to the message being returned to `LogTarget` -> `parseLog()`.
 */
data class LogMessage(
    val kind: LogKind,
    val message: String,
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
fun Analytics.log(message: String, kind: LogKind = LogKind.DEBUG) {
    Analytics.segmentLog(message, kind)
}
