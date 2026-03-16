package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.retry.*
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.mockAnalytics
import io.mockk.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Retry Chain Tests - Debug Version
 *
 * Starting with simplest possible tests to verify time manipulation works.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EventPipelineRetryChainTest {

    private lateinit var analytics: Analytics
    private lateinit var storage: Storage
    private lateinit var mockHttpClient: HTTPClient
    private lateinit var fakeTime: FakeTimeProvider
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        fakeTime = FakeTimeProvider(1000L)

        mockHttpClient = spyk(HTTPClient("test-write-key", RequestFactory()))
        analytics = mockAnalytics(testScope, testDispatcher)
        clearPersistentStorage(analytics.configuration.writeKey)
        storage = spyk(ConcreteStorageProvider.createStorage(analytics))
        every { analytics.storage } returns storage
    }

    @AfterEach
    fun teardown() {
        clearPersistentStorage("123")
    }

    private fun createTestEvent(name: String): ScreenEvent {
        return ScreenEvent(name, "", emptyJsonObject).apply {
            messageId = "msg-${UUID.randomUUID()}"
            anonymousId = "anon-123"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = Date(0).toInstant().toString()
        }
    }

    private fun createPipeline(httpConfig: HttpConfig?): EventPipeline {
        return object : EventPipeline(
            analytics,
            "test-destination",
            "test-write-key",
            listOf(CountBasedFlushPolicy(2)),
            httpConfig = httpConfig,
            timeProvider = fakeTime
        ) {
            override val httpClient: HTTPClient
                get() = mockHttpClient
        }
    }

    @Test
    fun `SIMPLE TEST - verify FakeTimeProvider is used`() {
        val httpConfig = HttpConfig(
            rateLimitConfig = RateLimitConfig(enabled = true),
            backoffConfig = BackoffConfig(enabled = false)
        )
        val pipeline = createPipeline(httpConfig)
        pipeline.start()

        // Trigger 429 with Retry-After
        val headers = mutableMapOf("Retry-After" to mutableListOf("60"))
        every { mockHttpClient.upload(any()) } throws HTTPException(429, "", "", headers)

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))
        Thread.sleep(200)

        // Verify: Rate limit state uses our fake time (1000ms + 60s)
        val state = storage.loadRetryState()
        assertEquals(PipelineState.RATE_LIMITED, state.pipelineState)
        assertEquals(1000L + 60000L, state.waitUntilTime, "Should use FakeTimeProvider's current time")

        pipeline.stop()
    }

    @Test
    fun `SIMPLE TEST - verify time advance blocks second upload`() {
        val httpConfig = HttpConfig(
            rateLimitConfig = RateLimitConfig(enabled = true),
            backoffConfig = BackoffConfig(enabled = false)
        )
        val pipeline = createPipeline(httpConfig)
        pipeline.start()

        // Track upload attempts
        var uploadAttempts = 0
        every { mockHttpClient.upload(any()) } answers {
            uploadAttempts++
            throw HTTPException(429, "", "", mutableMapOf("Retry-After" to mutableListOf("60")))
        }

        // First flush: Should upload
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))
        Thread.sleep(200)

        assertEquals(1, uploadAttempts, "First flush should attempt upload")

        // Advance time by 30 seconds (still within 60s wait)
        fakeTime.setTime(1000L + 30000L)

        // Second flush: Should be blocked
        pipeline.put(createTestEvent("event3"))
        pipeline.put(createTestEvent("event4"))
        Thread.sleep(200)

        assertEquals(1, uploadAttempts, "Second flush should NOT attempt upload (still rate limited)")

        pipeline.stop()
    }
    @Test
    fun `SIMPLE TEST - verify time advance allows third upload after rate limit expires`() {
        val httpConfig = HttpConfig(
            rateLimitConfig = RateLimitConfig(enabled = true),
            backoffConfig = BackoffConfig(enabled = false)
        )
        val pipeline = createPipeline(httpConfig)
        pipeline.start()

        var uploadAttempts = 0
        var successfulUploads = 0

        every { mockHttpClient.upload(any()) } answers {
            uploadAttempts++
            if (uploadAttempts == 1) {
                // First attempt: 429
                throw HTTPException(429, "", "", mutableMapOf("Retry-After" to mutableListOf("60")))
            } else {
                // Subsequent attempts: Success
                successfulUploads++
                mockk {
                    every { connection } returns mockk(relaxed = true)
                    every { outputStream } returns mockk(relaxed = true)
                    every { close() } just Runs
                }
            }
        }

        // First flush: 429 (creates batch file 1)
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))
        Thread.sleep(200)
        assertEquals(1, uploadAttempts, "First flush should attempt upload once")

        // Second flush during rate limit: No upload (creates batch file 2)
        pipeline.put(createTestEvent("event3"))
        pipeline.put(createTestEvent("event4"))
        Thread.sleep(200)
        assertEquals(1, uploadAttempts, "Second flush should not upload (rate limited)")

        // Advance time PAST rate limit (61 seconds)
        fakeTime.setTime(1000L + 61000L)

        // Third flush: Should upload BOTH queued batches (batch 1 retry + batch 2)
        pipeline.put(createTestEvent("event5"))
        pipeline.put(createTestEvent("event6"))
        Thread.sleep(200)

        // We expect: attempt 1 (429) + attempt 2 (batch 1 retry success) + attempt 3 (batch 2 success) + attempt 4 (batch 3 success)
        assertTrue(uploadAttempts >= 2, "Should have attempted uploads after rate limit expired")
        assertTrue(successfulUploads >= 1, "At least one upload should have succeeded")

        // Verify pipeline returned to READY
        val state = storage.loadRetryState()
        assertEquals(PipelineState.READY, state.pipelineState)

        pipeline.stop()
    }

    @Test
    fun `FULL CHAIN - 429 then 429 then 200`() {
        val httpConfig = HttpConfig(
            rateLimitConfig = RateLimitConfig(enabled = true),
            backoffConfig = BackoffConfig(enabled = false)
        )
        val pipeline = createPipeline(httpConfig)
        pipeline.start()

        var uploadAttempts = 0

        every { mockHttpClient.upload(any()) } answers {
            uploadAttempts++
            when (uploadAttempts) {
                1 -> throw HTTPException(429, "", "", mutableMapOf("Retry-After" to mutableListOf("30")))
                2 -> throw HTTPException(429, "", "", mutableMapOf("Retry-After" to mutableListOf("30")))
                else -> mockk {
                    every { connection } returns mockk(relaxed = true)
                    every { outputStream } returns mockk(relaxed = true)
                    every { close() } just Runs
                }
            }
        }

        // Attempt 1: 429 (rate limit for 30s)
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))
        Thread.sleep(200)
        assertEquals(1, uploadAttempts)

        // Advance past first rate limit
        fakeTime.setTime(1000L + 31000L)

        // Attempt 2: 429 again (rate limit for another 30s)
        pipeline.put(createTestEvent("event3"))
        pipeline.put(createTestEvent("event4"))
        Thread.sleep(200)
        assertTrue(uploadAttempts >= 2, "Second attempt should happen after first rate limit expires")

        // Advance past second rate limit
        val currentTime = fakeTime.currentTimeMillis()
        fakeTime.setTime(currentTime + 31000L)

        // Attempt 3: Success
        pipeline.put(createTestEvent("event5"))
        pipeline.put(createTestEvent("event6"))
        Thread.sleep(200)
        assertTrue(uploadAttempts >= 3, "Third attempt should succeed")

        // Verify back to READY
        val state = storage.loadRetryState()
        assertEquals(PipelineState.READY, state.pipelineState)

        pipeline.stop()
    }

    @Test
    fun `FULL CHAIN - 500 with backoff then 200`() {
        val httpConfig = HttpConfig(
            rateLimitConfig = RateLimitConfig(enabled = false),
            backoffConfig = BackoffConfig(
                enabled = true,
                baseBackoffInterval = 1.0,
                maxBackoffInterval = 300
            )
        )
        val pipeline = createPipeline(httpConfig)
        pipeline.start()

        var uploadAttempts = 0

        every { mockHttpClient.upload(any()) } answers {
            uploadAttempts++
            when {
                uploadAttempts == 1 -> throw HTTPException(500, "", "", mutableMapOf())
                else -> mockk {
                    every { connection } returns mockk(relaxed = true)
                    every { outputStream } returns mockk(relaxed = true)
                    every { close() } just Runs
                }
            }
        }

        // Attempt 1: 500 error
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))
        Thread.sleep(200)
        assertEquals(1, uploadAttempts)

        // Check backoff was set
        var state = storage.loadRetryState()
        assertEquals(1, state.batchMetadata.size, "Should have batch metadata after 500 error")
        val batchFile = state.batchMetadata.keys.first()
        val backoffTime = state.batchMetadata[batchFile]!!.nextRetryTime!!

        // Advance past backoff
        fakeTime.setTime(backoffTime + 100)

        // Attempt 2: Success
        pipeline.put(createTestEvent("event3"))
        pipeline.put(createTestEvent("event4"))
        Thread.sleep(200)
        assertTrue(uploadAttempts >= 2, "Should have at least 2 upload attempts")

        // Verify batch metadata cleared after success
        state = storage.loadRetryState()
        assertTrue(state.batchMetadata.isEmpty(), "Success should clear batch metadata")

        pipeline.stop()
    }
}
