package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.BaseEvent

/**
 * A Count based Flush Policy that instructs the EventPipeline to flush at the
 * given @param[flushAt]. The default value is 20. @param[flushAt] values should
 * be >= 1 or they'll get the default value.
 */
class CountBasedFlushPolicy(var flushAt: Int = 20): FlushPolicy {


    init {
        // Make sure to only take valid counts or fallback to our default.
        flushAt = when {
            flushAt >= 1 -> flushAt
            else -> 20
        }
    }

    private var count: Int = 0

    override fun shouldFlush(): Boolean {
        return count >= flushAt
    }

    override fun updateState(event: BaseEvent) {
        count++
    }

    override fun reset() {
        count = 0
    }
}