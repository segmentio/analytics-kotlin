package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.utils.testAnalytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.System as CoreSystem
import com.segment.analytics.kotlin.core.platform.plugins.logger.LogMessage
import com.segment.analytics.kotlin.core.platform.plugins.logger.Logger
import com.segment.analytics.kotlin.core.utils.TestRunPlugin
import org.junit.jupiter.api.AfterEach
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

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

    private fun runningUpdate(queue: StartupQueue, running: Boolean) {
        val m = StartupQueue::class.java.getDeclaredMethod("runningUpdate", CoreSystem::class.java)
        m.isAccessible = true
        m.invoke(queue, CoreSystem(settings = null, running = running))
    }

    private fun newQueue(): StartupQueue {
        val queue = StartupQueue()
        queue.analytics = analytics
        return queue
    }

    private fun trackEvent(name: String) = TrackEvent(event = name, properties = buildJsonObject { })

    @AfterEach
    fun restoreLogger() {
        Analytics.debugLogsEnabled = false
        Analytics.setLogger(com.segment.analytics.kotlin.core.platform.plugins.logger.ConsoleLogger())
    }

    // Race 1: an event that is queued must not be stranded when replay runs while the
    // queueing thread is still in its log call.
    @Test
    fun `event queued concurrently with startup is replayed`() {
        val processed = CopyOnWriteArrayList<BaseEvent?>()
        analytics.add(TestRunPlugin { processed.add(it) })
        val queue = newQueue()

        val inLog = CountDownLatch(1)
        val release = CountDownLatch(1)
        Analytics.debugLogsEnabled = true
        Analytics.setLogger(object : Logger {
            override fun parseLog(log: LogMessage) {
                if (log.message == "SegmentStartupQueue queueing event") {
                    inLog.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
        })

        val t = thread { queue.execute(trackEvent("e")) }
        // the queueing thread is now paused in the log; start up and replay meanwhile
        assertTrue(inLog.await(5, TimeUnit.SECONDS))
        runningUpdate(queue, true)
        release.countDown()
        t.join(5000)

        assertEquals(1, processed.size)
    }

    // Race 2: a delayed "false" update must not reset started after a "true" update.
    @Test
    fun `stale not-running update does not reset started`() {
        val queue = newQueue()
        val inLog = CountDownLatch(1)
        val release = CountDownLatch(1)
        Analytics.debugLogsEnabled = true
        Analytics.setLogger(object : Logger {
            override fun parseLog(log: LogMessage) {
                if (log.message == "Analytics starting = false") {
                    inLog.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
        })

        val t = thread { runningUpdate(queue, false) }
        assertTrue(inLog.await(5, TimeUnit.SECONDS))
        runningUpdate(queue, true)
        release.countDown()
        t.join(5000)

        val event = trackEvent("later")
        assertSame(event, queue.execute(event))
    }
}
