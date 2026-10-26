package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.coroutines.launch
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import sovran.kotlin.Subscriber
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Analytics plugin to manage started state of analytics client
 * All events will be held in an in-memory queue until started state is enabled, and once enabled
 * events will be replayed into the analytics timeline
 */
class StartupQueue : Plugin, Subscriber {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics

    private val maxSize = 1000
    private val started: AtomicBoolean = AtomicBoolean(false)
    private val lock = Any()
    private val queuedEvents: Queue<BaseEvent> = ConcurrentLinkedQueue()

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        with(analytics) {
            analyticsScope.launch(analyticsDispatcher) {
                store.subscribe(
                    subscriber = this@StartupQueue,
                    stateClazz = System::class,
                    initialState = true,
                    queue = analyticsDispatcher,
                    handler = this@StartupQueue::runningUpdate
                )
            }
        }
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        // The started check and the enqueue must be atomic with respect to runningUpdate,
        // otherwise an event can be queued after the replay has already drained the queue.
        val queued = synchronized(lock) {
            if (started.get()) {
                false
            } else {
                // timeline hasn't started, so queue it up.
                if (queuedEvents.size >= maxSize) {
                    // if we've exceeded the max queue size start dropping events
                    queuedEvents.remove()
                }
                queuedEvents.offer(event)
                true
            }
        }
        if (queued) {
            // log outside the lock so a slow logger can't block state updates
            analytics.log("SegmentStartupQueue queueing event")
            return null
        }
        // the timeline has started, so let the event pass.
        return event
    }

    // Handler to manage system update
    private fun runningUpdate(state: System) {
        // apply the state before logging, so a delay in logging can't cause a stale
        // value to overwrite a newer one
        val toReplay = synchronized(lock) {
            started.set(state.running)
            if (state.running) drainQueue() else emptyList()
        }
        analytics.log("Analytics starting = ${state.running}")
        replayEvents(toReplay)
    }

    private fun drainQueue(): List<BaseEvent> {
        val drained = mutableListOf<BaseEvent>()
        while (true) {
            drained.add(queuedEvents.poll() ?: break)
        }
        return drained
    }

    private fun replayEvents(events: List<BaseEvent>) {
        // replay the queued events to the instance of Analytics we're working with.
        events.forEach { analytics.process(it, it.enrichment) }
    }
}
