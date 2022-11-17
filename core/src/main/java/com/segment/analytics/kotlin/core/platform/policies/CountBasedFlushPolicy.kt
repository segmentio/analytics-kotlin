package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.BaseEvent

/**
 * A Count based Flush Policy that instructs the EventPipeline to flush at the
 * given @param[count]. The default value is 20. @param[count] values should
 * be >= 1 or they'll get the default value.
 */
class CountBasedFlushPolicy(count: Int = 20): FlushPolicy {

    val flushAt: Int

    init {
        // Make sure to only take valid counts or fallback to our default.
        flushAt = when {
            count >= 1 -> count
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