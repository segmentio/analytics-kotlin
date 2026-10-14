package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.running
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
    private val queuedEvents: Queue<BaseEvent> = ConcurrentLinkedQueue()

    // Guards `started` + `queuedEvents` so the check-and-enqueue in execute() and the
    // flip-and-drain in runningUpdate() can't interleave.
    private val lock = Any()

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
        // Without the lock an event can be enqueued just after the final drain and never replayed.
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
            analytics.log("SegmentStartupQueue queueing event")
            return null
        }
        // the timeline has started, so let the event pass.
        return event
    }

    // Store.notify runs each handler in its own coroutine, so notifications can be handled out of
    // order and the `state` argument may be stale (a late `running = false` after `running = true`
    // would wedge the queue). Apply the store's current state instead, and re-check afterwards
    // because it can change between the read and the apply.
    @Suppress("UNUSED_PARAMETER")
    internal suspend fun runningUpdate(state: System) {
        do {
            val running = analytics.running()
            analytics.log("Analytics starting = $running")
            val pending = synchronized(lock) {
                started.set(running)
                if (running) drainQueue() else emptyList()
            }
            // Replay outside the lock: process() re-enters execute() and runs arbitrary plugins.
            pending.forEach { analytics.process(it, it.enrichment) }
        } while (analytics.running() != running)
    }

    private fun drainQueue(): List<BaseEvent> {
        val drained = ArrayList<BaseEvent>()
        while (true) {
            drained.add(queuedEvents.poll() ?: break)
        }
        return drained
    }
}
