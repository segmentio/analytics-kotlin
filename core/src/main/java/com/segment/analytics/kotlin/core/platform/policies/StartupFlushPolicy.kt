package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.BaseEvent

/**
 * Flush policy that dictates flushing events at app startup.
 */
class StartupFlushPolicy: FlushPolicy {

    private var flushedAtStartup = false

    override fun shouldFlush(): Boolean {
        return when {
            flushedAtStartup -> false
            else -> {
                // Set to 'true' so we never flush again
                flushedAtStartup = true
                true
            }
        }
    }

    override fun updateState(event: BaseEvent) {
        // no-op
    }

    override fun reset() {
        // no-op
    }
}