package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.testAnalytics
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Baseline Integration Tests (Phase 3.5)
 *
 * These tests document the CURRENT behavior of EventPipeline BEFORE Phase 4 integration.
 * They serve as a baseline to ensure Phase 4 changes don't break existing functionality
 * and provide a clear before/after comparison.
 *
 * Current Behavior (Legacy Mode):
 * - All batch files are processed on every flush
 * - No exponential backoff delays
 * - No Retry-After header respect
 * - No rate limiting state
 * - 4xx errors (except 429) → delete batch immediately
 * - 429 errors → keep batch, retry on next flush
 * - 5xx errors → keep batch, retry on next flush
 */
class RetryIntegrationTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var analytics: Analytics
    private lateinit var storage: Storage
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        val config = Configuration(
            writeKey = "test-write-key",
            application = "RetryIntegrationTest",
            apiHost = mockServer.url("/").toString().trimEnd('/')
        )

        clearPersistentStorage(config.writeKey)
        analytics = testAnalytics(config, testScope, testDispatcher)
        storage = analytics.storage
    }

    @AfterEach
    fun teardown() {
        analytics.shutdown()
        mockServer.shutdown()
        clearPersistentStorage("test-write-key")
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

    private fun getBatchFileCount(): Int {
        val fileList = storage.read(Storage.Constants.Events)
        return fileList?.split("\n")?.filter { it.isNotBlank() }?.size ?: 0
    }

    @Test
    fun `baseline - successful upload deletes batch file`() = testScope.runTest {
        // Enqueue successful response
        mockServer.enqueue(MockResponse().setResponseCode(200))

        // Trigger upload with count-based policy (flush after 2 events)
        val pipeline = EventPipeline(
            analytics,
            "test-destination",
            "test-write-key",
            listOf(CountBasedFlushPolicy(2))
        )

        // Add events to trigger flush
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        // Allow time for upload to complete
        testDispatcher.scheduler.advanceUntilIdle()
        delay(100)

        // Verify batch was uploaded and deleted
        val recordedRequest = mockServer.takeRequest()
        assertNotNull(recordedRequest)
        assertEquals("/v1/batch", recordedRequest.path)

        // Verify batch file was removed
        assertEquals(0, getBatchFileCount(), "Batch file should be deleted after successful upload")
    }

    @Test
    fun `baseline - 400 error deletes batch file immediately`() = testScope.runTest {
        // Enqueue 400 Bad Request
        mockServer.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))

        val pipeline = EventPipeline(
            analytics,
            "test-destination",
            "test-write-key",
            listOf(CountBasedFlushPolicy(2))
        )

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        testDispatcher.scheduler.advanceUntilIdle()
        delay(100)

        // Verify request was made
        val recordedRequest = mockServer.takeRequest()
        assertNotNull(recordedRequest)

        // Verify batch file was deleted (current behavior: 4xx = data loss)
        assertEquals(0, getBatchFileCount(), "Batch file should be deleted on 400 error")
    }

    @Test
    fun `baseline - 429 error keeps batch file for retry`() = testScope.runTest {
        // Enqueue 429 Too Many Requests (no Retry-After header in current impl)
        mockServer.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))

        val pipeline = EventPipeline(
            analytics,
            "test-destination",
            "test-write-key",
            listOf(CountBasedFlushPolicy(2))
        )

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        testDispatcher.scheduler.advanceUntilIdle()
        delay(100)

        // Verify request was made
        val recordedRequest = mockServer.takeRequest()
        assertNotNull(recordedRequest)

        // Verify batch file was NOT deleted (current behavior: 429 = keep for retry)
        assertEquals(1, getBatchFileCount(), "Batch file should be kept on 429 error")
    }

    @Test
    fun `baseline - 500 error keeps batch file for retry`() = testScope.runTest {
        // Enqueue 500 Internal Server Error
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val pipeline = EventPipeline(
            analytics,
            "test-destination",
            "test-write-key",
            listOf(CountBasedFlushPolicy(2))
        )

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        testDispatcher.scheduler.advanceUntilIdle()
        delay(100)

        // Verify request was made
        val recordedRequest = mockServer.takeRequest()
        assertNotNull(recordedRequest)

        // Verify batch file was NOT deleted (current behavior: 5xx = keep for retry)
        assertEquals(1, getBatchFileCount(), "Batch file should be kept on 500 error")
    }

    @Test
    fun `baseline - no Retry-After header handling`() = testScope.runTest {
        // Enqueue 429 with Retry-After header
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "60")
                .setBody("Too Many Requests")
        )

        // Enqueue success for immediate retry attempt
        mockServer.enqueue(MockResponse().setResponseCode(200))

        val pipeline = EventPipeline(
            analytics,
            "test-destination",
            "test-write-key",
            listOf(CountBasedFlushPolicy(2))
        )

        // First flush - hits 429
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        testDispatcher.scheduler.advanceUntilIdle()
        delay(100)

        // Verify first request received 429
        val request1 = mockServer.takeRequest()
        assertNotNull(request1)

        // Trigger another flush immediately (current behavior: ignores Retry-After)
        pipeline.put(createTestEvent("event3"))
        pipeline.put(createTestEvent("event4"))

        testDispatcher.scheduler.advanceUntilIdle()
        delay(100)

        // Current behavior: second request is made immediately, ignoring Retry-After
        val request2 = mockServer.takeRequest()
        assertNotNull(request2, "Current behavior allows immediate retry, ignoring Retry-After header")

        // Batch should now be deleted after success
        assertEquals(0, getBatchFileCount(), "Batch should be deleted after successful retry")
    }

    @Test
    fun `baseline - no exponential backoff delays`() = testScope.runTest {
        // Enqueue multiple 500 errors, then success
        mockServer.enqueue(MockResponse().setResponseCode(500))
        mockServer.enqueue(MockResponse().setResponseCode(500))
        mockServer.enqueue(MockResponse().setResponseCode(200))

        val pipeline = EventPipeline(
            analytics,
            "test-destination",
            "test-write-key",
            listOf(CountBasedFlushPolicy(2))
        )

        // First attempt
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))
        testDispatcher.scheduler.advanceUntilIdle()
        delay(100)
        mockServer.takeRequest()

        // Second attempt (immediate, no backoff)
        pipeline.put(createTestEvent("event3"))
        pipeline.put(createTestEvent("event4"))
        testDispatcher.scheduler.advanceUntilIdle()
        delay(100)
        mockServer.takeRequest()

        // Third attempt (still immediate, no backoff)
        pipeline.put(createTestEvent("event5"))
        pipeline.put(createTestEvent("event6"))
        testDispatcher.scheduler.advanceUntilIdle()
        delay(100)
        mockServer.takeRequest()

        // Current behavior: all retries happen immediately without exponential backoff
        assertEquals(0, getBatchFileCount(), "Batches retry immediately without backoff delays")
    }
}
