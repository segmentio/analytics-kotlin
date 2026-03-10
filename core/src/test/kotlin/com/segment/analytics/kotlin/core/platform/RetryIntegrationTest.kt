package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.mockAnalytics
import io.mockk.*
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
import java.util.concurrent.TimeUnit

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
    private lateinit var mockRequestFactory: RequestFactory
    private lateinit var mockHttpClient: HTTPClient
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        mockServer = MockWebServer()
        mockServer.start()

        mockRequestFactory = spyk(RequestFactory())
        mockHttpClient = spyk(HTTPClient("test-write-key", mockRequestFactory))

        // Mock the upload method to use our MockWebServer
        every { mockRequestFactory.upload(any()) } answers {
            val conn = mockk<java.net.HttpURLConnection>(relaxed = true)
            val url = mockServer.url("/v1/batch")
            every { conn.url } returns url.toUrl()
            conn
        }

        analytics = mockAnalytics(testScope, testDispatcher)
        clearPersistentStorage(analytics.configuration.writeKey)
        storage = spyk(ConcreteStorageProvider.createStorage(analytics))
        every { analytics.storage } returns storage
    }

    @AfterEach
    fun teardown() {
        mockServer.shutdown()
        clearPersistentStorage("123")
    }

    private fun createPipeline(): EventPipeline {
        return object : EventPipeline(
            analytics,
            "test-destination",
            "test-write-key",
            listOf(CountBasedFlushPolicy(2))
        ) {
            override val httpClient: HTTPClient
                get() = mockHttpClient
        }
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

        val pipeline = createPipeline()

        // Add events to trigger flush
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        // Allow time for upload to complete
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify upload was called
        coVerify { mockHttpClient.upload(any()) }

        // Verify batch file was removed
        coVerify { storage.removeFile(any()) }
    }

    @Test
    fun `baseline - 400 error deletes batch file immediately`() = testScope.runTest {
        // Mock 400 response
        every { mockHttpClient.upload(any()) } throws HTTPException(400, "", "", mutableMapOf())

        val pipeline = createPipeline()

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        testDispatcher.scheduler.advanceUntilIdle()

        // Verify upload was attempted
        coVerify { mockHttpClient.upload(any()) }

        // Verify batch file was deleted (current behavior: 4xx = data loss)
        coVerify { storage.removeFile(any()) }
    }

    @Test
    fun `baseline - 429 error keeps batch file for retry`() = testScope.runTest {
        // Mock 429 response
        every { mockHttpClient.upload(any()) } throws HTTPException(429, "", "", mutableMapOf())

        val pipeline = createPipeline()

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        testDispatcher.scheduler.advanceUntilIdle()

        // Verify upload was attempted
        coVerify { mockHttpClient.upload(any()) }

        // Verify batch file was NOT deleted (current behavior: 429 = keep for retry)
        coVerify(exactly = 0) { storage.removeFile(any()) }
    }

    @Test
    fun `baseline - 500 error keeps batch file for retry`() = testScope.runTest {
        // Mock 500 response
        every { mockHttpClient.upload(any()) } throws HTTPException(500, "", "", mutableMapOf())

        val pipeline = createPipeline()

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        testDispatcher.scheduler.advanceUntilIdle()

        // Verify upload was attempted
        coVerify { mockHttpClient.upload(any()) }

        // Verify batch file was NOT deleted (current behavior: 5xx = keep for retry)
        coVerify(exactly = 0) { storage.removeFile(any()) }
    }

    @Test
    fun `baseline - no Retry-After header handling`() = testScope.runTest {
        // First call throws 429, second succeeds
        every { mockHttpClient.upload(any()) } throws HTTPException(
            429,
            "",
            "",
            mutableMapOf("Retry-After" to "60")
        ) andThenThrows HTTPException(
            429,
            "",
            "",
            mutableMapOf("Retry-After" to "60")
        ) andThen null // success

        val pipeline = createPipeline()

        // First flush - hits 429
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Trigger another flush immediately (current behavior: ignores Retry-After)
        pipeline.put(createTestEvent("event3"))
        pipeline.put(createTestEvent("event4"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Third flush succeeds
        pipeline.put(createTestEvent("event5"))
        pipeline.put(createTestEvent("event6"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Current behavior: retries happen immediately, ignoring Retry-After
        coVerify(atLeast = 3) { mockHttpClient.upload(any()) }

        // Eventually succeeds and removes file
        coVerify { storage.removeFile(any()) }
    }

    @Test
    fun `baseline - no exponential backoff delays`() = testScope.runTest {
        // Multiple 500 errors, then success
        every { mockHttpClient.upload(any()) } throws HTTPException(500, "", "", mutableMapOf()) andThenThrows
                HTTPException(500, "", "", mutableMapOf()) andThen null // success

        val pipeline = createPipeline()

        // First attempt
        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Second attempt (immediate, no backoff)
        pipeline.put(createTestEvent("event3"))
        pipeline.put(createTestEvent("event4"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Third attempt (still immediate, no backoff)
        pipeline.put(createTestEvent("event5"))
        pipeline.put(createTestEvent("event6"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Current behavior: all retries happen immediately without exponential backoff
        coVerify(atLeast = 3) { mockHttpClient.upload(any()) }

        // Eventually succeeds and removes file
        coVerify { storage.removeFile(any()) }
    }
}
