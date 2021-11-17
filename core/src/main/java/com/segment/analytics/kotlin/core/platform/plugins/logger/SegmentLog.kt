package com.segment.analytics.kotlin.core.platform.plugins.logger

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import java.lang.Exception
import java.util.*
import kotlin.reflect.KClass

// Analytics Utility plugin for logging purposes
internal open class SegmentLog : EventPlugin {

    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var analytics: Analytics

    internal var filterKind: LogFilterKind = LogFilterKind.DEBUG

    private var loggingMediator = mutableMapOf<LoggingType, MutableList<LogTarget>>()

    companion object {
        var loggingEnabled = false

        // For internal use only. Note: This will contain the last created instance
        // of analytics when used in a multi-analytics environment.
        var sharedAnalytics: Analytics? = null
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        sharedAnalytics = analytics
        add(target = ConsoleTarget(), loggingType = LoggingType.log)
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        val enabled = settings.plan["logging_enabled"]?.jsonPrimitive?.boolean ?: false
        loggingEnabled = enabled
    }

    internal open fun log(logMessage: LogMessage, destination: LoggingType.Filter) {

        loggingMediator.forEach { (loggingType, logTargets) ->
            logTargets.forEach {
                if (loggingType.contains(destination)) {
                    it.parseLog(logMessage)
                }
            }
        }
    }

    internal open fun add(target: LogTarget, loggingType: LoggingType) {

        // Verify the target does not exist, if it does bail out
        val filtered = loggingMediator.filter { (_, existingTarget) -> Boolean
            existingTarget::class == target::class
        }
        if (filtered.isNotEmpty()) {
            throw Exception("Target already exists.")
        }

        // Finally add the target
        if (loggingMediator[loggingType] == null) {
            loggingMediator[loggingType] = mutableListOf(target)
        } else {
            loggingMediator[loggingType]?.add(target)
        }
    }

    internal open fun <T: LogTarget> remove(targetType: KClass<T>) {

        val updatedLoggingMediator = mutableMapOf<LoggingType, MutableList<LogTarget>>() // Used so we don't manipulate in a loop
        loggingMediator.forEach { (type, targets) ->
            targets.forEach {
                if (it::class != targetType) {
                    if (updatedLoggingMediator[type] == null) {
                        updatedLoggingMediator[type] = mutableListOf()
                    }
                    updatedLoggingMediator[type]?.add(it)
                }
            }
        }
        loggingMediator = updatedLoggingMediator
    }

    override fun flush() {
        loggingMediator.forEach { (_, targets) ->
            targets.forEach {
                it.flush()
            }
        }

        // TODO: Clean up history container here
    }
}

class LogFactory {
    companion object {
        fun buildLog(destination: LoggingType.Filter,
                     title: String,
                     message: String,
                     kind: LogFilterKind = LogFilterKind.DEBUG,
                     function: String? = null,
                     line: Int? = null,
                     event: BaseEvent? = null,
                     sender: Any? = null,
                     value: Double? = null,
                     tags: List<String>? = null): LogMessage {
            return when (destination) {
                LoggingType.Filter.LOG ->
                    GenericLog(kind = kind, message = message, function = function, line = line)
                LoggingType.Filter.METRIC ->
                    MetricLog(title = title, message = message, value = ((value ?: 1) as Double), event = event, function = function, line = line)
                LoggingType.Filter.HISTORY ->
                    HistoryLog(message = message, event = event, function = function, line = line, sender = sender)
            }
        }
    }

    internal class GenericLog(override val kind: LogFilterKind,
                             override val title: String? = null,
                             override val message: String,
                             override val function: String?,
                             override val line: Int?,
                             override val event: BaseEvent? = null,
                             override val logType: LoggingType.Filter = LoggingType.Filter.LOG,
                             override val dateTime: Date = Date()
    ): LogMessage

    internal class MetricLog(override val title: String,
                            override val kind: LogFilterKind = LogFilterKind.DEBUG,
                            override val message: String,
                            val value: Double,
                            override val event: BaseEvent?,
                            override val function: String?,
                            override val line: Int?,
                            override val logType: LoggingType.Filter = LoggingType.Filter.METRIC,
                            override val dateTime: Date = Date()
    ): LogMessage

    internal class HistoryLog(override val kind: LogFilterKind = LogFilterKind.DEBUG,
                             override val title: String? = null,
                             override val message: String,
                             override val event: BaseEvent?,
                             override val function: String?,
                             override val line: Int?,
                             val sender: Any?,
                             override val logType: LoggingType.Filter = LoggingType.Filter.HISTORY,
                             override val dateTime: Date = Date()
    ): LogMessage
}

// Internal log usage
fun Analytics.Companion.segmentLog(message: String, kind: LogFilterKind? = LogFilterKind.ERROR, function: String? = null, line: Int? = null) {

    val methodInfo = Analytics.callingMethodDetails(function, line)

    SegmentLog.sharedAnalytics?.applyClosureToPlugins { plugin: Plugin ->
        if (plugin is SegmentLog) {
            var filterKind = plugin.filterKind
            if (kind != null) {
                filterKind = kind
            }

            val log = LogFactory.buildLog(LoggingType.Filter.LOG, "", message, filterKind, methodInfo.first, methodInfo.second)
            plugin.log(log, LoggingType.Filter.LOG)
        }
    }
}

fun Analytics.Companion.segmentMetric(type: String, name: String, value: Double, tags: List<String>?) {
    SegmentLog.sharedAnalytics?.applyClosureToPlugins { plugin: Plugin ->
        if (plugin is SegmentLog) {
            val log = LogFactory.buildLog(LoggingType.Filter.METRIC,
                type,
                name,
                LogFilterKind.DEBUG,
                null,
                null,
                null,
                null,
                value,
                tags)
            plugin.log(log, LoggingType.Filter.METRIC)
            // TODO: Capture function and line
        }
    }
}

internal fun Analytics.Companion.callingMethodDetails(function: String?, lineNumber: Int?): Pair<String, Int> {

    var fromFunction = function ?: ""
    var fromLineNumber = lineNumber ?: 0
    if (function == null || function.isEmpty()) {
        Exception().stackTrace[3].methodName.let {
            fromFunction = it
        }
        Exception().stackTrace[3].lineNumber.let {
            fromLineNumber = it
        }
    }

    return Pair(fromFunction, fromLineNumber)
}