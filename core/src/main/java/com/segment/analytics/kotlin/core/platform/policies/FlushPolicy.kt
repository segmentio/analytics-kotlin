package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import kotlinx.coroutines.CoroutineScope

interface FlushPolicy {

    /**
     * Called when the policy becomes active. We should start any periodic flushing
     * we want here.
     */
    fun schedule(analytics: Analytics) = Unit

    /**
     * Called when policy should stop running any scheduled flushes
     */
    fun unschedule() = Unit

    /**
     * Called to check whether or not the events should be flushed.
     */
    fun shouldFlush(): Boolean

    /**
     * Called as events are added to the timeline and JSON Stringified.
     */
    fun updateState(event: BaseEvent) = Unit

    /**
     * Called after the events are flushed.
     */
    fun reset() = Unit
}