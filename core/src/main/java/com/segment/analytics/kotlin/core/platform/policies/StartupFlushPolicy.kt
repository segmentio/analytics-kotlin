package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import kotlinx.coroutines.CoroutineScope

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
}