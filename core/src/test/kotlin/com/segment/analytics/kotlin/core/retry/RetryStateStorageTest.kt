package com.segment.analytics.kotlin.core.retry

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.utilities.InMemoryStorageProvider
import com.segment.analytics.kotlin.core.utilities.StorageImpl
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.testAnalytics
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RetryStateStorageTest {

    private lateinit var storage: Storage
    private lateinit var analytics: Analytics
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        val config = Configuration(
            writeKey = "test-key",
            application = "Test"
        )
        analytics = testAnalytics(config, testScope, testDispatcher)
        storage = InMemoryStorageProvider().createStorage(analytics)
    }

    @Test
    fun `saveRetryState and loadRetryState roundtrip`() = runTest {
        val state = RetryState(
            pipelineState = PipelineState.RATE_LIMITED,
            waitUntilTime = 5000L,
            globalRetryCount = 3,
            batchMetadata = mapOf(
                "batch-1" to BatchMetadata(
                    failureCount = 2,
                    nextRetryTime = 3000L,
                    firstFailureTime = 1000L
                )
            )
        )

        // Save state
        val saved = storage.saveRetryState(state)
        assertTrue(saved, "Save should succeed")

        // Load state
        val loaded = storage.loadRetryState()

        assertEquals(state.pipelineState, loaded.pipelineState)
        assertEquals(state.waitUntilTime, loaded.waitUntilTime)
        assertEquals(state.globalRetryCount, loaded.globalRetryCount)
        assertEquals(1, loaded.batchMetadata.size)

        val loadedBatch = loaded.batchMetadata["batch-1"]
        assertNotNull(loadedBatch)
        assertEquals(2, loadedBatch?.failureCount)
        assertEquals(3000L, loadedBatch?.nextRetryTime)
        assertEquals(1000L, loadedBatch?.firstFailureTime)
    }

    @Test
    fun `loadRetryState returns default when no data exists`() = runTest {
        val loaded = storage.loadRetryState()

        assertEquals(PipelineState.READY, loaded.pipelineState)
        assertNull(loaded.waitUntilTime)
        assertEquals(0, loaded.globalRetryCount)
        assertTrue(loaded.batchMetadata.isEmpty())
    }

    @Test
    fun `loadRetryState returns default on corrupt JSON`() = runTest {
        // Manually write corrupt data
        storage.write(Storage.Constants.RetryState, "not valid json {")

        val loaded = storage.loadRetryState()

        // Should return defaults without throwing
        assertEquals(PipelineState.READY, loaded.pipelineState)
        assertEquals(0, loaded.globalRetryCount)
    }

    @Test
    fun `loadRetryState returns default on invalid JSON structure`() = runTest {
        // Write JSON that doesn't match RetryState structure
        storage.write(Storage.Constants.RetryState, """{"wrongField": "value"}""")

        val loaded = storage.loadRetryState()

        // Should return defaults
        assertEquals(PipelineState.READY, loaded.pipelineState)
        assertEquals(0, loaded.globalRetryCount)
    }

    @Test
    fun `clearRetryState removes persisted state`() = runTest {
        val state = RetryState(
            pipelineState = PipelineState.RATE_LIMITED,
            globalRetryCount = 5
        )

        // Save state
        storage.saveRetryState(state)

        // Verify it's saved
        val loaded = storage.loadRetryState()
        assertEquals(5, loaded.globalRetryCount)

        // Clear state
        val cleared = storage.clearRetryState()
        assertTrue(cleared, "Clear should succeed")

        // Verify it's gone
        val afterClear = storage.loadRetryState()
        assertEquals(0, afterClear.globalRetryCount)
    }

    @Test
    fun `saveRetryState with empty batchMetadata`() = runTest {
        val state = RetryState(
            pipelineState = PipelineState.READY,
            waitUntilTime = null,
            globalRetryCount = 0,
            batchMetadata = emptyMap()
        )

        storage.saveRetryState(state)
        val loaded = storage.loadRetryState()

        assertEquals(PipelineState.READY, loaded.pipelineState)
        assertTrue(loaded.batchMetadata.isEmpty())
    }

    @Test
    fun `saveRetryState with multiple batch metadata entries`() = runTest {
        val state = RetryState(
            batchMetadata = mapOf(
                "batch-1" to BatchMetadata(failureCount = 1, nextRetryTime = 1000L, firstFailureTime = 100L),
                "batch-2" to BatchMetadata(failureCount = 2, nextRetryTime = 2000L, firstFailureTime = 200L),
                "batch-3" to BatchMetadata(failureCount = 3, nextRetryTime = 3000L, firstFailureTime = 300L)
            )
        )

        storage.saveRetryState(state)
        val loaded = storage.loadRetryState()

        assertEquals(3, loaded.batchMetadata.size)
        assertEquals(1, loaded.batchMetadata["batch-1"]?.failureCount)
        assertEquals(2, loaded.batchMetadata["batch-2"]?.failureCount)
        assertEquals(3, loaded.batchMetadata["batch-3"]?.failureCount)
    }

    @Test
    fun `saveRetryState overwrites previous state`() = runTest {
        // Save initial state
        val state1 = RetryState(globalRetryCount = 5)
        storage.saveRetryState(state1)

        // Overwrite with new state
        val state2 = RetryState(globalRetryCount = 10)
        storage.saveRetryState(state2)

        // Should load the newer state
        val loaded = storage.loadRetryState()
        assertEquals(10, loaded.globalRetryCount)
    }

    @Test
    fun `loadRetryState handles null waitUntilTime`() = runTest {
        val state = RetryState(
            pipelineState = PipelineState.RATE_LIMITED,
            waitUntilTime = null
        )

        storage.saveRetryState(state)
        val loaded = storage.loadRetryState()

        assertEquals(PipelineState.RATE_LIMITED, loaded.pipelineState)
        assertNull(loaded.waitUntilTime)
    }

    @Test
    fun `loadRetryState handles null fields in BatchMetadata`() = runTest {
        val state = RetryState(
            batchMetadata = mapOf(
                "batch-1" to BatchMetadata(
                    failureCount = 1,
                    nextRetryTime = null,
                    firstFailureTime = null
                )
            )
        )

        storage.saveRetryState(state)
        val loaded = storage.loadRetryState()

        val batch = loaded.batchMetadata["batch-1"]
        assertNotNull(batch)
        assertEquals(1, batch?.failureCount)
        assertNull(batch?.nextRetryTime)
        assertNull(batch?.firstFailureTime)
    }
}
