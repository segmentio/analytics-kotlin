package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.StorageImpl
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.net.HttpURLConnection
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SegmentDestinationTests {
    private lateinit var analytics: Analytics

    private lateinit var segmentDestination: SegmentDestination

    private val epochTimestamp = Date(0).toInstant().toString()

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
        analytics = testAnalytics(config)
        segmentDestination.setup(analytics)
    }

    @Test
    fun `enqueue adds event to storage`() = runBlocking {
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
            eventsFile.rollover()
            val storagePath = eventsFile.read()[0]
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
        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                if (logMessage.message.contains("Error adding payload") && logMessage.kind == LogFilterKind.ERROR) {
                    errorAddingPayload.set(true)
                }
            }
        }
        analytics.add(testLogger)
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
            assertEquals(2, contentsJson.size)
            assertTrue(contentsJson.containsKey("batch"))
            assertTrue(contentsJson.containsKey("sentAt"))
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
        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                if (logMessage.message == "Payloads were rejected by server. Marked for removal." && logMessage.kind == LogFilterKind.ERROR) {
                    payloadsRejected.set(true)
                }
            }
        }
        analytics.add(testLogger)
        val destSpy = spyk(segmentDestination)

        val httpConnection: HttpURLConnection = mockk()
        val connection = object : Connection(httpConnection, null, ByteArrayOutputStream()) {
            override fun close() {
                throw HTTPException(400, "", null)
            }
        }
        every { anyConstructed<HTTPClient>().upload(any()) } returns connection

        assertEquals(trackEvent, destSpy.track(trackEvent))
        destSpy.flush()
        verify { payloadsRejected.set(true) }
    }

    @Test
    fun `flush reads events but does not delete on fail code_429`() = runBlocking {
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
        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                if (logMessage.message == "Error while uploading payloads" && logMessage.kind == LogFilterKind.ERROR) {
                    errorUploading.set(true)
                }
            }
        }
        analytics.add(testLogger)
        val destSpy = spyk(segmentDestination)

        val httpConnection: HttpURLConnection = mockk()
        val connection = object : Connection(httpConnection, null, ByteArrayOutputStream()) {
            override fun close() {
                throw HTTPException(429, "", null)
            }
        }
        every { anyConstructed<HTTPClient>().upload(any()) } returns connection

        assertEquals(trackEvent, destSpy.track(trackEvent))
        destSpy.flush()
        verify { errorUploading.set(true) }
        (analytics.storage as StorageImpl).run {
            // batch file doesn't get deleted
            eventsFile.rollover()
            assertEquals(1, eventsFile.read().size)
        }
    }

    @Test
    fun `flush reads events but does not delete on fail code_500`() = runBlocking {
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
        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                if (logMessage.message == "Error while uploading payloads" && logMessage.kind == LogFilterKind.ERROR) {
                    errorUploading.set(true)
                }
            }
        }
        analytics.add(testLogger)
        val destSpy = spyk(segmentDestination)

        val httpConnection: HttpURLConnection = mockk()
        val connection = object : Connection(httpConnection, null, ByteArrayOutputStream()) {
            override fun close() {
                throw HTTPException(500, "", null)
            }
        }
        every { anyConstructed<HTTPClient>().upload(any()) } returns connection

        assertEquals(trackEvent, destSpy.track(trackEvent))
        destSpy.flush()
        verify { errorUploading.set(true) }
        (analytics.storage as StorageImpl).run {
            // batch file doesn't get deleted
            eventsFile.rollover()
            assertEquals(1, eventsFile.read().size)
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
        val testLogger = object : SegmentLog() {
            override fun log(logMessage: LogMessage, destination: LoggingType.Filter) {
                super.log(logMessage, destination)
                if (logMessage.message.contains("test") && logMessage.kind == LogFilterKind.ERROR) {
                    exceptionUploading.set(true)
                }
            }
        }
        analytics.add(testLogger)
        val destSpy = spyk(segmentDestination)

        every { anyConstructed<HTTPClient>().upload(any()) } throws Exception("test")

        assertEquals(trackEvent, destSpy.track(trackEvent))
        destSpy.flush()

        verify { exceptionUploading.set(true) }
    }
}