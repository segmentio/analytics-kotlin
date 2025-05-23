package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Connection
import com.segment.analytics.kotlin.core.HTTPClient
import com.segment.analytics.kotlin.core.HTTPException
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.plugins.logger.LogKind
import com.segment.analytics.kotlin.core.platform.plugins.logger.LogMessage
import com.segment.analytics.kotlin.core.platform.plugins.logger.Logger
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.StorageImpl
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SegmentDestinationTests {
    private lateinit var analytics: Analytics

    private lateinit var segmentDestination: SegmentDestination

    private val epochTimestamp = Date(0).toInstant().toString()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testScope = TestScope(testDispatcher)

    init {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
        mockkStatic(Base64::class)
        mockkConstructor(HTTPClient::class)
    }

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        segmentDestination = SegmentDestination()

        val config = Configuration(
            writeKey = "123",
            application = "Test",
            storageProvider = ConcreteStorageProvider,
            flushAt = 2,
            flushInterval = 0
        )
        analytics = testAnalytics(config, testScope, testDispatcher)
        segmentDestination.setup(analytics)
    }

    @Test
    fun `enqueue adds event to storage`() = runTest {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = buildJsonObject { put("Segment.io", false) }
                context = emptyJsonObject
                timestamp = epochTimestamp
            }

        assertEquals(trackEvent, segmentDestination.track(trackEvent))

        val expectedEvent = EncodeDefaultsJson.encodeToJsonElement(trackEvent).jsonObject.filterNot { (k, v) ->
            // filter out empty userId and traits values
            (k == "userId" && v.jsonPrimitive.content.isBlank()) || (k == "traits" && v == emptyJsonObject)
        }
        val expectedStringPayload = Json.encodeToString(expectedEvent)

        (analytics.storage as StorageImpl).run {
            rollover()
            val storagePath = eventStream.read()[0]
            val storageContents = File(storagePath).readText()
            assertTrue(
                storageContents.contains(
                    expectedStringPayload
                ),
                """storage does not contain expected event
                   expectedEvent = $expectedStringPayload
                   storageContents = $storageContents
                """.trimIndent()
            )
        }
    }

    @Test
    fun `enqueuing a big payload throws error`() {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "a".repeat(32000)
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = epochTimestamp
            }

        val errorAddingPayload = spyk(AtomicBoolean(false))
        val testLogger = object : Logger {
            override fun parseLog(logMessage: LogMessage) {
                if (logMessage.message.contains("Error adding payload") && logMessage.kind == LogKind.ERROR) {
                    errorAddingPayload.set(true)
                }
            }

        }

        Analytics.logger = testLogger
        val destSpy = spyk(segmentDestination)
        assertEquals(trackEvent, destSpy.track(trackEvent))
        verify { errorAddingPayload.set(true) }
    }

    @Test
    fun `flush reads events and deletes when successful`() {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = epochTimestamp
            }
        val destSpy = spyk(segmentDestination)

        val httpConnection: HttpURLConnection = mockk()
        var outputBytes: ByteArray = byteArrayOf()
        val connection = spyk(object : Connection(httpConnection, null, ByteArrayOutputStream()) {
            override fun close() {
                outputBytes = (outputStream as ByteArrayOutputStream).toByteArray()
            }
        })
        every { anyConstructed<HTTPClient>().upload(any()) } returns connection

        assertEquals(trackEvent, destSpy.track(trackEvent))
        destSpy.flush()

        verify { connection.close() }
        with(String(outputBytes)) {
            val contentsJson: JsonObject = Json.decodeFromString(this)
            assertEquals(3, contentsJson.size) // batch, sentAt, writeKey
            assertTrue(contentsJson.containsKey("batch"))
            assertTrue(contentsJson.containsKey("sentAt"))
            assertTrue(contentsJson.containsKey("writeKey"))
            assertEquals(1, contentsJson["batch"]?.jsonArray?.size)
            val eventInFile = contentsJson["batch"]?.jsonArray?.get(0)
            val eventInFile2 =
                Json.decodeFromString(TrackEvent.serializer(), Json.encodeToString(eventInFile))
            assertEquals(trackEvent, eventInFile2)
        }

    }

    @Test
    fun `flush reads events and deletes on payload rejection`() {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = epochTimestamp
            }

        var payloadsRejected = spyk(AtomicBoolean(false))
        val testLogger = object : Logger {
            override fun parseLog(logMessage: LogMessage) {
                if (logMessage.message == "Payloads were rejected by server. Marked for removal." && logMessage.kind == LogKind.ERROR) {
                    payloadsRejected.set(true)
                }
            }
        }
        Analytics.logger = testLogger
        val destSpy = spyk(segmentDestination)

        val httpConnection: HttpURLConnection = mockk()
        val connection = object : Connection(httpConnection, null, ByteArrayOutputStream()) {
            override fun close() {
                throw HTTPException(400, "", null, mutableMapOf())
            }
        }
        every { anyConstructed<HTTPClient>().upload(any()) } returns connection

        assertEquals(trackEvent, destSpy.track(trackEvent))
        destSpy.flush()
        verify { payloadsRejected.set(true) }
    }

    @Test
    fun `flush reads events but does not delete on fail code_429`() = runTest {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = epochTimestamp
            }
        var errorUploading = spyk(AtomicBoolean(false))
        val testLogger = object : Logger {
            override fun parseLog(logMessage: LogMessage) {
                if (logMessage.message == "Error while uploading payloads" && logMessage.kind == LogKind.ERROR) {
                    errorUploading.set(true)
                }
            }
        }
        Analytics.logger = testLogger

        val httpConnection: HttpURLConnection = mockk()
        val connection = object : Connection(httpConnection, null, ByteArrayOutputStream()) {
            override fun close() {
                throw HTTPException(429, "", null, mutableMapOf())
            }
        }
        every { anyConstructed<HTTPClient>().upload(any()) } returns connection

        assertEquals(trackEvent, segmentDestination.track(trackEvent))
        assertDoesNotThrow {
            segmentDestination.flush()
        }
        verify { errorUploading.set(true) }
        (analytics.storage as StorageImpl).run {
            // batch file doesn't get deleted
            rollover()
            assertEquals(1, eventStream.read().size)
        }
    }

    @Test
    fun `flush reads events but does not delete on fail code_500`() = runTest {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = epochTimestamp
            }

        var errorUploading = spyk(AtomicBoolean(false))
        val testLogger = object : Logger {
            override fun parseLog(logMessage: LogMessage) {
                if (logMessage.message == "Error while uploading payloads" && logMessage.kind == LogKind.ERROR) {
                    errorUploading.set(true)
                }
            }
        }
        Analytics.logger = testLogger

        val httpConnection: HttpURLConnection = mockk()
        val connection = object : Connection(httpConnection, null, ByteArrayOutputStream()) {
            override fun close() {
                throw HTTPException(500, "", null, mutableMapOf())
            }
        }
        every { anyConstructed<HTTPClient>().upload(any()) } returns connection

        assertEquals(trackEvent, segmentDestination.track(trackEvent))
        assertDoesNotThrow {
            segmentDestination.flush()
        }
        verify { errorUploading.set(true) }
        (analytics.storage as StorageImpl).run {
            // batch file doesn't get deleted
            rollover()
            assertEquals(1, eventStream.read().size)
        }
    }

    @Test
    fun `flush properly handles upload exception`() {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = epochTimestamp
            }

        val exceptionUploading = spyk(AtomicBoolean(false))
        val testLogger = object : Logger {
            override fun parseLog(logMessage: LogMessage) {
                println("Checking: ${logMessage.message}")
                if (logMessage.message.contains("test") && logMessage.kind == LogKind.ERROR) {
                    exceptionUploading.set(true)
                }
            }
        }
        Analytics.logger = testLogger

        every { anyConstructed<HTTPClient>().upload(any()) } throws Exception("test")
        assertEquals(trackEvent, segmentDestination.track(trackEvent))
        assertDoesNotThrow {
            segmentDestination.flush()
        }
        verify { exceptionUploading.set(true) }
    }
}