package com.segment.analytics.kotlin.core.platform.plugins.logger

class ConsoleTarget: LogTarget {
    override fun parseLog(log: LogMessage) {
        var metadata = ""
        val function = log.function
        val line = log.line
        if (function != null && line != null) {
            metadata = " - $function:$line"
        }
        println("[Segment ${log.kind.toString()}${metadata}\n${log.message}")
    }

    override fun flush() { }
}