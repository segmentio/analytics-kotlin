package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.retry.*
import com.segment.analytics.kotlin.core.utils.mockAnalytics
import io.mockk.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EventPipelineTransportFailureTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val time = FakeTimeProvider(1000L)
    private val analytics = mockAnalytics(scope, dispatcher)
    private val storage = mockk<Storage>(relaxed = true)
    private val client = mockk<HTTPClient>()
    private val files = (1..12).map { "batch-$it" }.toMutableList()
    private var savedRetryState: String? = null
    private lateinit var pipeline: EventPipeline
    private val config = HttpConfig(backoffConfig = BackoffConfig(
        enabled = true, baseBackoffInterval = 60.0, maxBackoffInterval = 120, jitterPercent = 0
    ))

    @BeforeEach
    fun setup() {
        every { analytics.storage } returns storage
        every { storage.read(Storage.Constants.Events) } answers { files.joinToString(",") }
        every { storage.read(Storage.Constants.RetryState) } answers { savedRetryState }
        coEvery { storage.write(Storage.Constants.RetryState, any()) } coAnswers {
            savedRetryState = secondArg()
        }
        every { storage.readAsStream(any()) } answers { "{}".byteInputStream() }
        every { storage.removeFile(any()) } answers { files.remove(firstArg<String>()) }
    }

    @AfterEach
    fun teardown() {
        if (::pipeline.isInitialized) pipeline.stop()
        scope.cancel()
    }

    private fun start(config: HttpConfig? = null) {
        pipeline = object : EventPipeline(
            analytics, "test", "test-key", emptyList(), httpConfig = config, timeProvider = time
        ) {
            override val httpClient get() = client
        }
        pipeline.start()
    }

    private fun succeed() {
        every { client.upload(any()) } answers {
            object : Connection(mockk(relaxed = true), null, ByteArrayOutputStream()) {
                override fun close() {}
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["dns", "timeout", "tls"])
    fun `transport failure stops the current flush with default settings`(failure: String) {
        every { client.upload(any()) } throws when (failure) {
            "dns" -> UnknownHostException("offline")
            "timeout" -> SocketTimeoutException("timeout")
            else -> SSLException("handshake failed")
        }
        start()
        pipeline.flush()
        verify(exactly = 1) { client.upload(any()) }
        assertEquals(12, files.size)

        // With backoff disabled the next flush can retry immediately.
        pipeline.flush()
        verify(exactly = 2) { client.upload(any()) }
        assertEquals(12, files.size)
        succeed()
        pipeline.flush()
        assertTrue(files.isEmpty())
    }

    @Test
    fun `transport backoff pauses all batches and survives restart until recovery`() {
        every { client.upload(any()) } throws UnknownHostException("offline")
        start(config)
        pipeline.flush()
        verify(exactly = 1) { client.upload(any()) }
        assertEquals(61000L, storage.loadRetryState().waitUntilTime)
        assertEquals(12, files.size)

        // Newly queued batches and repeated flush signals share the cooldown.
        files.add("new-batch")
        repeat(3) { pipeline.flush() }
        pipeline.stop()
        start(config)
        time.setTime(60999L)
        pipeline.flush()
        verify(exactly = 1) { client.upload(any()) }

        time.setTime(61000L)
        pipeline.flush()
        verify(exactly = 2) { client.upload(any()) }
        assertEquals(181000L, storage.loadRetryState().waitUntilTime)
        assertEquals(13, files.size)

        time.setTime(181000L)
        pipeline.flush()
        verify(exactly = 3) { client.upload(any()) }
        assertEquals(301000L, storage.loadRetryState().waitUntilTime, "Backoff is capped")

        time.setTime(301000L)
        succeed()
        pipeline.flush()
        verify(exactly = 16) { client.upload(any()) }
        assertTrue(files.isEmpty())
        assertEquals(RetryState(), storage.loadRetryState())
    }

    @Test
    fun `HTTP server errors retain independent batch backoff`() {
        every { client.upload(any()) } throws HTTPException(500, "", "", mutableMapOf())
        start(config)
        pipeline.flush()
        verify(exactly = 12) { client.upload(any()) }
        assertEquals(12, files.size)
        assertEquals(PipelineState.READY, storage.loadRetryState().pipelineState)
        assertEquals(12, storage.loadRetryState().batchMetadata.size)
    }

    @Test
    fun `transport failure is not classified as an HTTP 500 response`() {
        every { client.upload(any()) } throws UnknownHostException("offline")
        start(config.copy(backoffConfig = config.backoffConfig.copy(
            statusCodeOverrides = mapOf(500 to RetryBehavior.DROP)
        )))
        pipeline.flush()
        pipeline.flush()
        verify(exactly = 1) { client.upload(any()) }
        assertEquals(12, files.size)
        assertEquals(PipelineState.TRANSPORT_BACKOFF, storage.loadRetryState().pipelineState)
    }
}
