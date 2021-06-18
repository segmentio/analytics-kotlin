package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent

import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin

enum class LogType(val level: Int) {
    ERROR(0),       // Not Verbose
    WARNING(1),     // Semi-verbose
    INFO(2)         // Verbose
}

data class LogMessage(
    val type: LogType,
    val message: String,
    val event: BaseEvent?
)

// Simple logger plugin
open class Logger(override val name: String) : EventPlugin {

    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var analytics: Analytics

    open var filterType: LogType = LogType.INFO

    private val messages = mutableListOf<LogMessage>()

    open fun log(type: LogType, message: String, event: BaseEvent?) {
        println("$type -- Message: $message")
        val m = LogMessage(type, message, event)
        messages.add(m)
    }

    open fun flush() {
        println("Flushing All Logs")
        for (message in messages) {
            if (message.type.level <= filterType.level) {
                println("[${message.type}] ${message.message}")
            }
        }
        messages.clear()
    }
}

fun Analytics.log(message: String, event: BaseEvent? = null, type: LogType? = null) {
    this.timeline.applyClosure { plugin: Plugin ->
        if (plugin is Logger) {
            plugin.log(type ?: plugin.filterType, message, event)
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