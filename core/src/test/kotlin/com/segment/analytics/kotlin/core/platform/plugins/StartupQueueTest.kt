package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.ConsoleLogger
import com.segment.analytics.kotlin.core.platform.plugins.logger.LogMessage
import com.segment.analytics.kotlin.core.platform.plugins.logger.Logger
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal class StartupQueueTest {

    private lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    internal fun setUp() {
        val config = Configuration(
            writeKey = "123",
            application = "Test",
            autoAddSegmentDestination = false
        )
        analytics = testAnalytics(config, testScope, testDispatcher)
    }

    @Test
    fun `execute when startup queue not ready`() {
        val startupQueue = spyk(StartupQueue())
        every { startupQueue.setup(any()) } answers { startupQueue.analytics = analytics }

        analytics.add(startupQueue)
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
        assertNull(startupQueue.execute(trackEvent))
    }

    private fun trackEvent(name: String) = TrackEvent(
        event = name,
        properties = buildJsonObject { put("behaviour", "good") })

    private fun countingPlugin(count: AtomicInteger) = object : Plugin {
        override val type = Plugin.Type.Enrichment
        override lateinit var analytics: Analytics
        override fun execute(event: BaseEvent): BaseEvent? {
            count.incrementAndGet()
            return event
        }
    }

    @Test
    fun `stale not-running notification does not stop the queue after running`() {
        val startupQueue = StartupQueue()
        analytics.add(startupQueue)
        val replayed = AtomicInteger()
        analytics.add(countingPlugin(replayed))

        runBlocking { analytics.store.dispatch(System.ToggleRunningAction(false), System::class) }
        assertNull(startupQueue.execute(trackEvent("queued")))
        runBlocking { analytics.store.dispatch(System.ToggleRunningAction(true), System::class) }
        assertEquals(1, replayed.get())

        // a handler coroutine that lost the race delivers an outdated state
        runBlocking { startupQueue.runningUpdate(System(settings = null, running = false)) }

        assertNotNull(startupQueue.execute(trackEvent("after")))
    }

    @Test
    fun `event queued while the startup replay runs is not stranded`() {
        val startupQueue = StartupQueue()
        analytics.add(startupQueue)
        val replayed = AtomicInteger()
        analytics.add(countingPlugin(replayed))
        runBlocking { analytics.store.dispatch(System.ToggleRunningAction(false), System::class) }

        val worker = "startup-queue-worker"
        val workerPaused = CountDownLatch(1)
        val replayDone = CountDownLatch(1)
        // Park the worker at its queueing log for the whole startup replay: a deschedule the
        // scheduler could do on its own, just made deterministic.
        Analytics.debugLogsEnabled = true
        Analytics.setLogger(object : Logger {
            override fun parseLog(log: LogMessage) {
                if (log.message == "SegmentStartupQueue queueing event" && Thread.currentThread().name == worker) {
                    workerPaused.countDown()
                    replayDone.await(5, TimeUnit.SECONDS)
                }
            }
        })
        try {
            var passed: BaseEvent? = null
            val t = thread(name = worker) { passed = startupQueue.execute(trackEvent("e")) }
            assertTrue(workerPaused.await(5, TimeUnit.SECONDS))
            runBlocking { analytics.store.dispatch(System.ToggleRunningAction(true), System::class) }
            replayDone.countDown()
            t.join()

            assertEquals(1, replayed.get() + (if (passed != null) 1 else 0))
        } finally {
            Analytics.debugLogsEnabled = false
            Analytics.setLogger(ConsoleLogger())
        }
    }
}