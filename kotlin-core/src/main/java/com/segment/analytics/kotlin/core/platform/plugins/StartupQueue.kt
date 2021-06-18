package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.System
import sovran.kotlin.Subscriber
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class StartupQueue(): Plugin, Subscriber {
    override val type: Plugin.Type = Plugin.Type.Before
    override val name: String = "Segment_StartupQueue"
    override lateinit var analytics: Analytics

    private val maxSize = 1000
    private val started: AtomicBoolean = AtomicBoolean(false)
    private val queuedEvents: Queue<BaseEvent> = ConcurrentLinkedQueue()

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        analytics.store.subscribe(
            subscriber = this,
            stateClazz = System::class,
            initialState = true,
            handler = this::runningUpdate
        )
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        if (!started.get()) {
            analytics.log("$name queueing event", event = event)
            // timeline hasn't started, so queue it up.
            if (queuedEvents.size >= maxSize) {
                // if we've exceeded the max queue size start dropping events
                queuedEvents.remove()
            }
            queuedEvents.offer(event)
            return null
        }
        // the timeline has started, so let the event pass.
        return event
    }

    // Handler to manage system update
    private fun runningUpdate(state: System) {
        analytics.log("Analytics starting = ${state.running}")
        started.set(state.running)
        if (started.get()) {
            replayEvents()
        }
    }

    private fun replayEvents() {
        // replay the queued events to the instance of Analytics we're working with.
        for (event in queuedEvents) {
            analytics.process(event)
        }
        queuedEvents.clear()
    }
}