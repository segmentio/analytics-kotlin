package com.segment.analytics.kotlin.core.platform.plugins.logger

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.*
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import java.lang.Exception
import java.util.*

// Analytics Utility plugin for logging purposes
open class Logger : EventPlugin {

    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var analytics: Analytics

    open var filterKind: LogFilterKind = LogFilterKind.DEBUG

    private val messages = mutableListOf<LogMessage>()
    private var loggingMediator = mutableMapOf<LoggingType, LogTarget>()

    companion object {
        var loggingEnabled = true
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        val enabled = settings.plan["logging_enabled"]?.jsonPrimitive?.boolean ?: false
        loggingEnabled = enabled
    }

    open fun log(logMessage: LogMessage, destination: LoggingType.LogDestination) {

        loggingMediator.forEach { (loggingType, logTarget) ->
            if (loggingType.contains(destination)) {
                logTarget.parseLog(logMessage)
            }
        }

    }

    open fun add(target: LogTarget, loggingType: LoggingType) {

        // Verify the target does not exist, if it does bail out
        val filtered = loggingMediator.filter { (type, existingTarget) -> Boolean
            existingTarget::class == target::class
        }
        if (filtered.isNotEmpty()) {
            throw Exception("Target already exists.")
        }

        // Finally add the target
        loggingMediator[loggingType] = target
    }

    open fun flush() {
        loggingMediator.forEach { (_, target) ->
            target.flush()
        }

        // TODO: Clean up history container here
    }
}

class LogFactory {
    companion object {
        fun buildLog(destination: LoggingType.LogDestination,
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
                LoggingType.LogDestination.LOG ->
                    GenericLog(kind, message, function, line)
                LoggingType.LogDestination.METRIC ->
                    MetricLog(title, message, event, function, line)
                LoggingType.LogDestination.HISTORY ->
                    HistoryLog(message, event, function, line, sender)
            }
        }
    }

    private class GenericLog(override val kind: LogFilterKind,
                             override val message: String,
                             override val function: String?,
                             override val line: Int?,
                             override val event: BaseEvent? = null,
                             override val logType: LoggingType.LogDestination = LoggingType.LogDestination.LOG,
                             override val dateTime: Date = Date()
    ): LogMessage

    private class MetricLog(val title: String,
                            override val message: String,
                            override val event: BaseEvent?,
                            override val function: String?,
                            override val line: Int?,
                            override val kind: LogFilterKind = LogFilterKind.DEBUG,
                            override val logType: LoggingType.LogDestination = LoggingType.LogDestination.METRIC,
                            override val dateTime: Date = Date()
    ): LogMessage

    private class HistoryLog(override val message: String,
                             override val event: BaseEvent?,
                             override val function: String?,
                             override val line: Int?,
                             val sender: Any?,
                             override val kind: LogFilterKind = LogFilterKind.DEBUG,
                             override val logType: LoggingType.LogDestination = LoggingType.LogDestination.HISTORY,
                             override val dateTime: Date = Date()
    ): LogMessage
}

fun LogTarget.flush() { }

//
fun Analytics.segmentLog(message: String, kind: LogFilterKind? = null, function: String = "", line: Int = 0) {
    applyClosureToPlugins { plugin: Plugin ->
        if (plugin is Logger) {
            var filterKind = plugin.filterKind
            if (kind != null) {
                filterKind = kind
            }

            try {
                val log = LogFactory.buildLog(LoggingType.LogDestination.LOG, "", message, filterKind, function, line)
                plugin.log(log, LoggingType.LogDestination.LOG)
            } catch (exception: Exception) {
                // TODO: LOG TO PRIVATE SEGMENT LOG
            }
        }
    }
}

fun Analytics.segmentMetric(type: String, name: String, value: Double, tags: List<String>?) {
    applyClosureToPlugins { plugin: Plugin ->
        if (plugin is Logger) {
            try {
                val log = LogFactory.buildLog(LoggingType.LogDestination.METRIC,
                    type,
                    name,
                    LogFilterKind.DEBUG,
                    null,
                    null,
                    null,
                    null,
                    value,
                    tags)
                plugin.log(log, LoggingType.LogDestination.METRIC)
            } catch (exception: Exception) {
                // TODO: LOG TO PRIVATE SEGMENT LOG
            }
        }
    }
}