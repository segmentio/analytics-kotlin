package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.retry.BackoffConfig
import com.segment.analytics.kotlin.core.retry.HttpConfig
import com.segment.analytics.kotlin.core.retry.RateLimitConfig
import com.segment.analytics.kotlin.core.retry.RetryState
import com.segment.analytics.kotlin.core.retry.loadRetryState
import com.segment.analytics.kotlin.core.retry.saveRetryState
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.mockAnalytics
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Phase 4 Step 1 Tests
 *
 * These tests verify the retry infrastructure initialization in EventPipeline:
 * - httpConfig parameter is accepted
 * - RetryState is loaded from storage during initialization
 * - EventPipeline continues to work normally (no behavior changes yet)
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EventPipelinePhase4Test {

    private lateinit var analytics: Analytics
    private lateinit var storage: Storage
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        analytics = mockAnalytics(testScope, testDispatcher)
        clearPersistentStorage(analytics.configuration.writeKey)
        storage = spyk(ConcreteStorageProvider.createStorage(analytics))
        every { analytics.storage } returns storage
    }

    @AfterEach
    fun teardown() {
        clearPersistentStorage("123")
    }

    @Test
    fun `EventPipeline constructor accepts httpConfig parameter`() {
        // Verify constructor compiles and accepts httpConfig
        val httpConfig = HttpConfig(
            rateLimitConfig = RateLimitConfig(enabled = true),
            backoffConfig = BackoffConfig(enabled = true)
        )

        val pipeline = EventPipeline(
            analytics,
            "test",
            "test-key",
            listOf(CountBasedFlushPolicy(2)),
            httpConfig = httpConfig
        )

        assertNotNull(pipeline)
        assertFalse(pipeline.running) // Not started yet
    }

    @Test
    fun `EventPipeline loads RetryState from storage on initialization`() {
        // Verify loadRetryState() is called during EventPipeline construction
        val pipeline = EventPipeline(
            analytics,
            "test",
            "test-key",
            listOf(CountBasedFlushPolicy(2)),
            httpConfig = null
        )

        // Verify storage.read was called with RetryState constant
        verify(atLeast = 1) { storage.read(Storage.Constants.RetryState) }
    }

    @Test
    fun `EventPipeline with httpConfig null uses legacy mode (no behavior changes)`() {
        // Create pipeline with httpConfig=null
        val pipeline = EventPipeline(
            analytics,
            "test",
            "test-key",
            listOf(CountBasedFlushPolicy(2)),
            httpConfig = null // Legacy mode
        )

        pipeline.start()

        // Verify pipeline starts normally
        assertTrue(pipeline.running)

        pipeline.stop()
        assertFalse(pipeline.running)
    }

    @Test
    fun `EventPipeline with httpConfig enabled=false uses legacy mode`() {
        // Both configs disabled = legacy mode
        val httpConfig = HttpConfig(
            rateLimitConfig = RateLimitConfig(enabled = false),
            backoffConfig = BackoffConfig(enabled = false)
        )

        val pipeline = EventPipeline(
            analytics,
            "test",
            "test-key",
            listOf(CountBasedFlushPolicy(2)),
            httpConfig = httpConfig
        )

        pipeline.start()

        // Verify pipeline starts normally in legacy mode
        assertTrue(pipeline.running)

        pipeline.stop()
        assertFalse(pipeline.running)
    }

    @Test
    fun `EventPipeline loads persisted RATE_LIMITED state from storage`() {
        // Pre-populate storage with a RATE_LIMITED state
        val persistedState = RetryState(
            pipelineState = com.segment.analytics.kotlin.core.retry.PipelineState.RATE_LIMITED,
            waitUntilTime = System.currentTimeMillis() + 60000,
            globalRetryCount = 5
        )
        runBlocking { storage.saveRetryState(persistedState) }

        // Create EventPipeline - should load the persisted state
        val pipeline = EventPipeline(
            analytics,
            "test",
            "test-key",
            listOf(CountBasedFlushPolicy(2)),
            httpConfig = HttpConfig(
                rateLimitConfig = RateLimitConfig(enabled = true),
                backoffConfig = BackoffConfig(enabled = true)
            )
        )

        // Verify storage was read
        verify(atLeast = 1) { storage.read(Storage.Constants.RetryState) }

        // Pipeline should still start normally (state is loaded but not yet used)
        pipeline.start()
        assertTrue(pipeline.running)
        pipeline.stop()
    }

    @Test
    fun `EventPipeline with no persisted state uses defaults`() {
        // Clear storage to ensure no persisted state
        storage.remove(Storage.Constants.RetryState)

        // Create EventPipeline
        val pipeline = EventPipeline(
            analytics,
            "test",
            "test-key",
            listOf(CountBasedFlushPolicy(2)),
            httpConfig = null
        )

        // Verify storage was read (will return null, defaults used)
        verify(atLeast = 1) { storage.read(Storage.Constants.RetryState) }

        // Pipeline should start normally with default state
        pipeline.start()
        assertTrue(pipeline.running)
        pipeline.stop()
    }

    @Test
    fun `EventPipeline with smart retry enabled initializes correctly`() {
        // Full config with smart retry enabled
        val httpConfig = HttpConfig(
            rateLimitConfig = RateLimitConfig(
                enabled = true,
                maxRetryCount = 50,
                maxRetryInterval = 300,
                maxRateLimitDuration = 3600
            ),
            backoffConfig = BackoffConfig(
                enabled = true,
                maxRetryCount = 50,
                baseBackoffInterval = 1.0,
                maxBackoffInterval = 300,
                maxTotalBackoffDuration = 7200
            )
        )

        val pipeline = EventPipeline(
            analytics,
            "test",
            "test-key",
            listOf(CountBasedFlushPolicy(2)),
            httpConfig = httpConfig
        )

        // Verify initialization succeeded
        assertNotNull(pipeline)

        // Verify RetryState was loaded
        verify(atLeast = 1) { storage.read(Storage.Constants.RetryState) }

        // Pipeline should start and stop normally
        pipeline.start()
        assertTrue(pipeline.running)
        pipeline.stop()
        assertFalse(pipeline.running)
    }
}
