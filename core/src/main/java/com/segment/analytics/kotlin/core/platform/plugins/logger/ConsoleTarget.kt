package com.segment.analytics.kotlin.core.platform.plugins.logger

class ConsoleTarget: LogTarget {
    override fun parseLog(log: LogMessage) {
        println("[Segment ${log.kind.toString()} ${log.message}")
    }

}