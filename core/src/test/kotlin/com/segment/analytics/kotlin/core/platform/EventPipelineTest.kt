package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.HTTPClient
import com.segment.analytics.kotlin.core.HTTPException
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import com.segment.analytics.kotlin.core.utils.mockAnalytics
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class EventPipelineTest {

    private lateinit var pipeline: EventPipeline

    private lateinit var analytics: Analytics

    private lateinit var storage: Storage

    @BeforeEach
    internal fun setUp() {
        MockKAnnotations.init(this)
        mockkConstructor(HTTPClient::class)
        mockkConstructor(File::class)

        analytics = mockAnalytics()
        storage = spyk(ConcreteStorageProvider.getStorage(
            analytics,
            analytics.store,
            analytics.configuration.writeKey,
            analytics.fileIODispatcher,
            this))
        every { analytics.storage } returns storage

        pipeline = EventPipeline(analytics,
            "test",
            "123", 2, 0)
        pipeline.start()
    }

    @Test
    fun put() {
        val event = "event 1"
        pipeline.put(event)
        coVerify(timeout = 2000) { storage.write(Storage.Constants.Events, event) }
    }

    @Test
    fun flush() {
        pipeline.put("event 1")
        pipeline.put(EventPipeline.FLUSH_POISON)
        coVerify(timeout = 2000) {
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
        pipeline.put("event 1")
        pipeline.put("event 2")
        coVerify(timeout = 2000) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
            storage.removeFile(any())
        }
    }

    @Test
    fun `enqueuing properly handles 400 http exception`() {
        every { anyConstructed<HTTPClient>().upload(any()) } throws HTTPException(400, "", "")
        pipeline.put("event 1")
        pipeline.put("event 2")
        coVerify(timeout = 2000) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
            storage.removeFile(any())
        }
    }

    @Test
    fun `enqueuing properly handles other http exception`() {
        every { anyConstructed<HTTPClient>().upload(any()) } throws HTTPException(300, "", "")
        pipeline.put("event 1")
        pipeline.put("event 2")
        coVerify(timeout = 2000) {
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
        pipeline.put("event 1")
        pipeline.put("event 2")
        coVerify(timeout = 2000) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
        }
        verify(exactly = 0) {
            storage.removeFile(any())
        }
    }

    @Test
    fun `flushInterval causes regular flushing of events`() = runBlocking {
        //restart flushScheduler
        pipeline = EventPipeline(analytics,
            "test",
            "123", 2, 1_000)
        pipeline.start()
        pipeline.put("event 1")
        delay(2_500)
        coVerify(atLeast = 1, atMost = 2) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
            anyConstructed<HTTPClient>().upload(any())
            storage.removeFile(any())
        }
    }

    @Test
    fun `flush interrupted when no event file exist`() = runBlocking {
        pipeline.put(EventPipeline.FLUSH_POISON)
        coVerify(exactly = 1) {
            storage.rollover()
            storage.read(Storage.Constants.Events)
        }
        verify(exactly = 0) {
            anyConstructed<HTTPClient>().upload(any())
            storage.removeFile(any())
        }
    }
}