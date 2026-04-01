package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utils.StubDestinationPlugin
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.mockHTTPClient
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*


@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WaitingTests {

    private val writeKey = "waiting-tests"

    private lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    fun setup() {
        clearPersistentStorage(writeKey)
        mockHTTPClient()
        val config = Configuration(
            writeKey = writeKey,
            application = "Test",
            autoAddSegmentDestination = false
        )
        analytics = testAnalytics(config, testScope, testDispatcher)
    }

    @Test
    fun `test resume after timeout`() = testScope.runTest {
        assertTrue(analytics.running())
        analytics.pauseEventProcessing(1000)
        assertFalse(analytics.running())
        advanceTimeBy(2000)
        assertTrue(analytics.running())
    }

    @Test
    fun `test manual resume`() = testScope.runTest {
        assertTrue(analytics.running())
        analytics.pauseEventProcessing()
        assertFalse(analytics.running())
        analytics.resumeEventProcessing()
        assertTrue(analytics.running())
    }


    @Test
    fun `test pause does not dispatch state if already pause`() {
        mockkStatic("com.segment.analytics.kotlin.core.WaitingKt")
        try {
            coEvery { analytics.startProcessingAfterTimeout(any()) } returns Job()

            testScope.runTest {
                analytics.pauseEventProcessing()
                analytics.pauseEventProcessing()
                analytics.pauseEventProcessing()
                coVerify(exactly = 1) {
                    analytics.startProcessingAfterTimeout(any())
                }
            }
        } finally {
            unmockkStatic("com.segment.analytics.kotlin.core.WaitingKt")
        }
    }

    @Test
    fun `test WaitingPlugin makes analytics to wait`() = testScope.runTest {
        assertTrue(analytics.running())
        val waitingPlugin = ExampleWaitingPlugin()
        analytics.add(waitingPlugin)
        testScheduler.runCurrent()
        analytics.track("foo")
        testScheduler.runCurrent()

        assertFalse(analytics.running())
        assertFalse(waitingPlugin.tracked)

        advanceUntilIdle()
        advanceTimeBy(6000)
        advanceUntilIdle()

        assertTrue(analytics.running())
        assertTrue(waitingPlugin.tracked)
    }

    @Test
    fun `test timeout force resume`() = testScope.runTest {
        assertTrue(analytics.running())
        val waitingPlugin = ExampleWaitingPlugin()
        analytics.add(waitingPlugin)
        testScheduler.runCurrent()
        analytics.track("foo")
        testScheduler.runCurrent()

        assertFalse(analytics.running())
        assertFalse(waitingPlugin.tracked)

        advanceUntilIdle()
        advanceTimeBy(6000)
        advanceUntilIdle()

        assertTrue(analytics.running())
        assertTrue(waitingPlugin.tracked)
    }

    @Test
    fun `test multiple WaitingPlugin`() = testScope.runTest {
        assertTrue(analytics.running())
        val plugin1 = ManualResumeWaitingPlugin()
        val plugin2 = ManualResumeWaitingPlugin()
        analytics.add(plugin1)
        analytics.add(plugin2)
        testScheduler.runCurrent()
        analytics.track("foo")
        testScheduler.runCurrent()

        assertFalse(analytics.running())
        assertFalse(plugin1.tracked)
        assertFalse(plugin2.tracked)

        advanceTimeBy(6000)
        testScheduler.runCurrent()

        assertFalse(analytics.running())
        assertFalse(plugin1.tracked)
        assertFalse(plugin2.tracked)

        plugin1.resume()
        testScheduler.runCurrent()

        assertFalse(analytics.running())
        assertFalse(plugin1.tracked)
        assertFalse(plugin2.tracked)

        plugin2.resume()
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertTrue(analytics.running())
        assertTrue(plugin1.tracked)
        assertTrue(plugin2.tracked)
    }

    @Test
    fun `test WaitingPlugin makes analytics to wait on DestinationPlugin`() = testScope.runTest {
        assertTrue(analytics.running())
        val waitingPlugin = ExampleWaitingPlugin()
        val destinationPlugin = StubDestinationPlugin()
        analytics.add(destinationPlugin)
        destinationPlugin.add(waitingPlugin)
        testScheduler.runCurrent()
        analytics.track("foo")
        testScheduler.runCurrent()

        assertFalse(analytics.running())
        assertFalse(waitingPlugin.tracked)

        advanceTimeBy(6000)
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertTrue(analytics.running())
        assertTrue(waitingPlugin.tracked)
    }

    @Test
    fun `test timeout force resume on DestinationPlugin`() = testScope.runTest {
        assertTrue(analytics.running())
        val waitingPlugin = ExampleWaitingPlugin()
        val destinationPlugin = StubDestinationPlugin()
        analytics.add(destinationPlugin)
        destinationPlugin.add(waitingPlugin)
        testScheduler.runCurrent()
        analytics.track("foo")
        testScheduler.runCurrent()

        assertFalse(analytics.running())
        assertFalse(waitingPlugin.tracked)

        advanceTimeBy(6000)
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertTrue(analytics.running())
        assertTrue(waitingPlugin.tracked)
    }

    @Test
    fun `test multiple WaitingPlugin on DestinationPlugin`() = testScope.runTest {
        assertTrue(analytics.running())
        val destinationPlugin = StubDestinationPlugin()
        analytics.add(destinationPlugin)
        val plugin1 = ExampleWaitingPlugin()
        val plugin2 = ManualResumeWaitingPlugin()
        destinationPlugin.add(plugin1)
        destinationPlugin.add(plugin2)
        testScheduler.runCurrent()
        analytics.track("foo")
        testScheduler.runCurrent()

        assertFalse(analytics.running())
        assertFalse(plugin1.tracked)
        assertFalse(plugin2.tracked)

        advanceTimeBy(6000)
        testScheduler.runCurrent()

        assertFalse(analytics.running())
        assertFalse(plugin1.tracked)
        assertFalse(plugin2.tracked)

        plugin2.resume()
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertTrue(analytics.running())
        assertTrue(plugin1.tracked)
        assertTrue(plugin2.tracked)
    }

    class ExampleWaitingPlugin: EventPlugin, WaitingPlugin {
        override val type: Plugin.Type = Plugin.Type.Enrichment
        override lateinit var analytics: Analytics
        var tracked = false

        override fun setup(analytics: Analytics) {
            super<WaitingPlugin>.setup(analytics)
            analytics.analyticsScope.launch(analytics.analyticsDispatcher) {
                delay(3000)
                resume()
            }
        }

        override fun track(payload: TrackEvent): BaseEvent? {
            tracked = true
            return super.track(payload)
        }
    }

    class ManualResumeWaitingPlugin: EventPlugin, WaitingPlugin {
        override val type: Plugin.Type = Plugin.Type.Enrichment
        override lateinit var analytics: Analytics
        var tracked = false

        override fun track(payload: TrackEvent): BaseEvent? {
            tracked = true
            return super.track(payload)
        }
    }
}