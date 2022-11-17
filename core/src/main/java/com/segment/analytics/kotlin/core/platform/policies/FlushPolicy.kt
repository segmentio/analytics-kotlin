package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.BaseEvent

interface FlushPolicy {
    /**
     * Called to check whether or not the events should be flushed.
     */
    fun shouldFlush(): Boolean

    /**
     * Called as events are added to the timeline.
     */
    fun updateState(event: BaseEvent)

    /**
     * Called after the events are flushed.
     */
    fun reset(): Unit
}