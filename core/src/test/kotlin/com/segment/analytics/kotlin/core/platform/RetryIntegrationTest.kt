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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Baseline Integration Tests (Phase 3.5)
 *
 * These tests document the CURRENT behavior of EventPipeline BEFORE Phase 4 integration.
 * They serve as a baseline to ensure Phase 4 changes don't break existing functionality.
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

    private lateinit var analytics: Analytics
    private lateinit var storage: Storage
    private lateinit var mockRequestFactory: RequestFactory
    private lateinit var mockHttpClient: HTTPClient
    private lateinit var pipeline: EventPipeline
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        mockRequestFactory = spyk(RequestFactory())
        mockHttpClient = spyk(HTTPClient("test-write-key", mockRequestFactory))

        analytics = mockAnalytics(testScope, testDispatcher)
        clearPersistentStorage(analytics.configuration.writeKey)
        storage = spyk(ConcreteStorageProvider.createStorage(analytics))
        every { analytics.storage } returns storage

        pipeline = object : EventPipeline(
            analytics,
            "test-destination",
            "test-write-key",
            listOf(CountBasedFlushPolicy(2))
        ) {
            override val httpClient: HTTPClient
                get() = mockHttpClient
        }

        pipeline.start()
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

    @Test
    fun `baseline - 400 error deletes batch file immediately`() {
        // Current behavior: 4xx errors (except 429) cause immediate data loss
        every { mockHttpClient.upload(any()) } throws HTTPException(400, "", "", mutableMapOf())

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        // Verify batch file was deleted immediately
        coVerify(exactly = 1) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            mockHttpClient.upload(any())
            storage.removeFile(any())
        }
    }

    @Test
    fun `baseline - 429 error keeps batch file for retry`() {
        // Current behavior: 429 keeps file but ignores Retry-After header
        every { mockHttpClient.upload(any()) } throws HTTPException(429, "", "", mutableMapOf())

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        // Verify batch file was NOT deleted (kept for retry)
        coVerify(exactly = 1) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            mockHttpClient.upload(any())
        }
        coVerify(exactly = 0) {
            storage.removeFile(any())
        }
    }

    @Test
    fun `baseline - 500 error keeps batch file for retry`() {
        // Current behavior: 5xx errors keep file for retry (no backoff delay)
        every { mockHttpClient.upload(any()) } throws HTTPException(500, "", "", mutableMapOf())

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        // Verify batch file was NOT deleted (kept for retry)
        coVerify(exactly = 1) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            mockHttpClient.upload(any())
        }
        coVerify(exactly = 0) {
            storage.removeFile(any())
        }
    }

    @Test
    fun `baseline - 429 with Retry-After header is ignored`() {
        // Current behavior: Retry-After header is completely ignored
        val headers = mutableMapOf("Retry-After" to mutableListOf("60"))
        every { mockHttpClient.upload(any()) } throws HTTPException(429, "", "", headers)

        pipeline.put(createTestEvent("event1"))
        pipeline.put(createTestEvent("event2"))

        // File is kept, but Retry-After=60 is ignored (would be honored in Phase 4)
        coVerify(exactly = 1) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            mockHttpClient.upload(any())
        }
        coVerify(exactly = 0) {
            storage.removeFile(any())
        }
    }
}
