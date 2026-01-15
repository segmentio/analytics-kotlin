package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.platform.policies.FrequencyFlushPolicy
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.mockAnalytics
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*

internal class EventPipelineTest {

    private lateinit var pipeline: EventPipeline

    private lateinit var analytics: Analytics

    private lateinit var storage: Storage

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testScope = TestScope(testDispatcher)

    companion object {
        private val event1 = ScreenEvent("event 1", "", emptyJsonObject).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = Date(0).toInstant().toString()
        }

        private val event2 = ScreenEvent("event 2", "", emptyJsonObject).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = Date(0).toInstant().toString()
        }
    }


    @BeforeEach
    internal fun setUp() {
        MockKAnnotations.init(this)
        mockkConstructor(HTTPClient::class)
        mockkConstructor(File::class)

        analytics = mockAnalytics(testScope, testDispatcher)
        clearPersistentStorage(analytics.configuration.writeKey)
        storage = spyk(ConcreteStorageProvider.createStorage(analytics))
        every { analytics.storage } returns storage

        pipeline = EventPipeline(analytics,
            "test",
            "123",
            listOf(CountBasedFlushPolicy(2), FrequencyFlushPolicy(0))
        )
        pipeline.start()
    }

    @Test
    fun put() {
        pipeline.put(event1)
        coVerify { storage.write(Storage.Constants.Events, pipeline.stringifyBaseEvent(event1)) }
    }

    @Test
    fun flush() {
        pipeline.put(event1)
        pipeline.flush()
        coVerify {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
            storage.removeFile(any())
        }
    }

    @Test
    fun start() {
        assertTrue(pipeline.running)
    }

    @Test
    fun stop() {
        pipeline.stop()
        assertFalse(pipeline.running)
    }

    @Test
    fun `put more than flushCount causes flush`() {
        pipeline.put(event1)
        pipeline.put(event2)
        coVerify {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
            storage.removeFile(any())
        }
    }

    @Test
    fun `enqueuing properly handles 400 http exception`() {
        every { anyConstructed<HTTPClient>().upload(any()) } throws HTTPException(400, "", "", mutableMapOf())
        pipeline.put(event1)
        pipeline.put(event2)
        coVerify {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
            storage.removeFile(any())
        }
    }

    @Test
    fun `enqueuing properly handles other http exception`() {
        every { anyConstructed<HTTPClient>().upload(any()) } throws HTTPException(300, "", "", mutableMapOf())
        pipeline.put(event1)
        pipeline.put(event2)
        coVerify {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
        }
        verify(exactly = 0) {
            storage.removeFile(any())
        }
    }

    @Test
    fun `enqueuing properly handles other exception`() {
        every { anyConstructed<HTTPClient>().upload(any()) } throws Exception()
        pipeline.put(event1)
        pipeline.put(event2)
        coVerify {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
        }
        verify(exactly = 0) {
            storage.removeFile(any())
        }
    }

    @Disabled("Ignore this test since it does not run the way it's designed to be. Better to do a unit test on FrequencyFlushPolicy")
    @Test
    fun `flushInterval causes regular flushing of events`() = runTest {
        //restart flushScheduler
        pipeline = EventPipeline(analytics,
            "test",
            "123", listOf(CountBasedFlushPolicy(2), FrequencyFlushPolicy(1000)))
        every { analytics.flush() } answers { pipeline.flush() }
        pipeline.start()
        pipeline.put(event1)
        delay(2_500)

        coVerify(atLeast = 1, atMost = 2) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
            storage.removeFile(any())
        }
    }

    @Test
    fun `flush interrupted when no event file exist`() = runTest {
        pipeline.flush()
        coVerify(exactly = 1) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
        }
        verify(exactly = 0) {
            anyConstructed<HTTPClient>().upload(any())
            storage.removeFile(any())
        }
    }

    @Test
    fun `flush when pipeline stopped has no effect`() {
        pipeline.stop()
        pipeline.flush()

        coVerify(exactly = 0) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
        }
    }

    @Test
    fun `flush after pipeline restarted has effect`() {
        pipeline.stop()
        pipeline.start()
        pipeline.flush()

        coVerify(exactly = 1) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
        }
    }

    @Test
    fun `upload closes InputStream when exception occurs`() {
        // Create a trackable InputStream wrapper
        var isClosed = false
        val trackableInputStream = object : java.io.InputStream() {
            override fun read(): Int = -1
            override fun close() {
                isClosed = true
                super.close()
            }
        }
        every { storage.readAsStream(any()) } returns trackableInputStream

        // Mock connection.upload to throw exception
        every { anyConstructed<HTTPClient>().upload(any()) } throws RuntimeException("Network error")

        pipeline.put(event1)
        pipeline.put(event2)

        // Give some time for async processing
        Thread.sleep(500)

        // Verify that close() was called on the InputStream even when exception occurred
        assertTrue(isClosed, "InputStream should have been closed when exception occurs")
    }

    @Test
    fun `removeFile is called after InputStream is closed`() {
        // Track order of operations to ensure stream is closed before file deletion
        val operationOrder = mutableListOf<String>()
        var streamClosed = false

        val trackableInputStream = object : java.io.InputStream() {
            override fun read(): Int = -1
            override fun close() {
                streamClosed = true
                operationOrder.add("stream_closed")
                super.close()
            }
        }

        every { storage.readAsStream(any()) } returns trackableInputStream
        every { storage.removeFile(any()) } answers {
            operationOrder.add("file_removed")
            // Verify stream was closed before removeFile is called (Windows file locking requirement)
            assertTrue(streamClosed, "InputStream must be closed before removeFile is called")
            true
        }

        pipeline.put(event1)
        pipeline.put(event2)

        // Give some time for async processing
        Thread.sleep(500)

        // Verify correct operation order
        coVerify {
            storage.removeFile(any())
        }
        assertTrue(operationOrder == listOf("stream_closed", "file_removed"),
            "Expected order: [stream_closed, file_removed], but got: $operationOrder")
    }

    @Test
    fun `removeFile is called when readAsStream returns null`() {
        every { storage.readAsStream(any()) } returns null

        pipeline.put(event1)
        pipeline.put(event2)

        // Give some time for async processing
        Thread.sleep(500)

        // Verify file is still deleted even when stream is null
        coVerify {
            storage.removeFile(any())
        }
    }

    @Test
    fun `InputStream is closed before removeFile even when upload throws exception`() {
        val operationOrder = mutableListOf<String>()
        var streamClosed = false

        val trackableInputStream = object : java.io.InputStream() {
            override fun read(): Int = -1
            override fun close() {
                streamClosed = true
                operationOrder.add("stream_closed")
                super.close()
            }
        }

        every { storage.readAsStream(any()) } returns trackableInputStream
        every { anyConstructed<HTTPClient>().upload(any()) } throws HTTPException(400, "", "", mutableMapOf())
        every { storage.removeFile(any()) } answers {
            operationOrder.add("file_removed")
            assertTrue(streamClosed, "InputStream must be closed before removeFile even when upload fails")
            true
        }

        pipeline.put(event1)
        pipeline.put(event2)

        // Give some time for async processing
        Thread.sleep(500)

        // Verify correct operation order even with exception
        coVerify {
            storage.removeFile(any())
        }
        assertTrue(operationOrder == listOf("stream_closed", "file_removed"),
            "Expected order: [stream_closed, file_removed], but got: $operationOrder")
    }
}