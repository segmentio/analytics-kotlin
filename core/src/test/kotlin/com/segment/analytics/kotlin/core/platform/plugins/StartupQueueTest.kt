package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.Plugin
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
    fun `no event is stranded when events race with startup`() {
        repeat(10) {
            val startupQueue = StartupQueue()
            val freshAnalytics = testAnalytics(
                Configuration(writeKey = "123", application = "Test", autoAddSegmentDestination = false),
                testScope, testDispatcher
            )
            freshAnalytics.add(startupQueue)
            val replayed = AtomicInteger()
            freshAnalytics.add(countingPlugin(replayed))
            runBlocking { freshAnalytics.store.dispatch(System.ToggleRunningAction(false), System::class) }

            val threads = 4
            val perThread = 200
            val passedDirectly = AtomicInteger()
            val go = CountDownLatch(1)
            val workers = (1..threads).map {
                thread {
                    go.await()
                    repeat(perThread) {
                        if (startupQueue.execute(trackEvent("e")) != null) passedDirectly.incrementAndGet()
                    }
                }
            }
            go.countDown()
            runBlocking { freshAnalytics.store.dispatch(System.ToggleRunningAction(true), System::class) }
            workers.forEach { it.join() }

            assertEquals(threads * perThread, passedDirectly.get() + replayed.get())
        }
    }
}