package com.segment.analytics.main

import android.content.Context
import android.util.Base64
import com.segment.analytics.*
import com.segment.analytics.main.utils.MemorySharedPreferences
import com.segment.analytics.main.utils.mockContext
import com.segment.analytics.platform.plugins.LogType
import com.segment.analytics.platform.plugins.Logger
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
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
import java.net.HttpURLConnection
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SegmentDestinationTests {
    private var mockContext: Context = mockContext()
    private lateinit var analytics: Analytics

    private val testDispatcher = TestCoroutineDispatcher()

    // val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val testScope = TestCoroutineScope(testDispatcher)

    private lateinit var segmentDestination: SegmentDestination

    private val epochTimestamp = Date(0).toInstant().toString()

    init {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "123"
        mockkConstructor(HTTPClient::class)
    }

    @BeforeEach
    fun setup() {
        File("/tmp/analytics-android").deleteRecursively()
        File("/tmp/analytics-android").mkdir()
        segmentDestination = SegmentDestination("123", 2, 0)
        analytics = Analytics(
            Configuration(
                writeKey = "123",
                analyticsScope = testScope,
                ioDispatcher = testDispatcher,
                analyticsDispatcher = testDispatcher,
                application = mockContext
            )
        )
        segmentDestination.setup(analytics)
    }

    @Test
    fun `enqueue adds event to storage`() {
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

        assertEquals(trackEvent, segmentDestination.track(trackEvent))

        val expectedEvent = Json {
            encodeDefaults = true
        }.encodeToJsonElement(trackEvent).jsonObject.filterNot { (k, v) ->
            // filter out empty userId and traits values
            (k == "userId" && v.jsonPrimitive.content.isBlank()) || (k == "traits" && v == emptyJsonObject)
        }

        (analytics.storage as AndroidStorage).let {
            assertEquals(1, segmentDestination.eventCount.get())
            val storagePath = it.eventsFile.read()[0]
            val storageContents = File(storagePath).readText()
            assertTrue(
                storageContents.contains(
                    Json.encodeToString(expectedEvent)
                )
            )
        }
    }

    @Test
    fun `enqueuing more than flushCount causes flush`() {
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
        assertEquals(trackEvent, destSpy.track(trackEvent))
        assertEquals(trackEvent, destSpy.track(trackEvent))

        (analytics.storage as AndroidStorage).let {
            assertEquals(0, segmentDestination.eventCount.get())
            verify { destSpy.flush() }
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
        var errorAddingPayload = false
        val testLogger = object : Logger("test") {
            override fun log(type: LogType, message: String, event: BaseEvent?) {
                if (type == LogType.ERROR && message == "Error adding payload") {
                    errorAddingPayload = true
                }
            }
        }
        analytics.add(testLogger)
        val destSpy = spyk(segmentDestination)
        assertEquals(trackEvent, destSpy.track(trackEvent))
        assertTrue(errorAddingPayload)
    }

    @Test
    fun `flushInterval causes regular flushing of events`() = runBlocking {
        //restart flushScheduler
        val destination = SegmentDestination(
            apiKey = "123",
            flushCount = 10,
            flushIntervalInMillis = 1_000
        )
        val destSpy = spyk(destination)
        destSpy.setup(analytics)

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
        assertEquals(trackEvent, destSpy.track(trackEvent))
        delay(2_000)
        delay(2_000)
        verify(atLeast = 2, atMost = 5) { destSpy.flush() }
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
        val testLogger = object : Logger("test") {
            override fun log(type: LogType, message: String, event: BaseEvent?) {
            }
        }
        analytics.add(testLogger)
        val destSpy = spyk(segmentDestination)

        val httpConnection: HttpURLConnection = mockk()
        var outputBytes: ByteArray = byteArrayOf()
        val connection = object : Connection(httpConnection, null, ByteArrayOutputStream()) {
            override fun close() {
                outputBytes = (outputStream as ByteArrayOutputStream).toByteArray()
            }
        }
        every { anyConstructed<HTTPClient>().upload(any(), any()) } returns connection

        assertEquals(trackEvent, destSpy.track(trackEvent))
        assertEquals(1, segmentDestination.eventCount.get())
        destSpy.flush()

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

        (analytics.storage as AndroidStorage).let {
            assertEquals(0, segmentDestination.eventCount.get())
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
        var payloadsRejected = false
        val testLogger = object : Logger("test") {
            override fun log(type: LogType, message: String, event: BaseEvent?) {
                if (type == LogType.ERROR && message == "Payloads were rejected by server. Marked for removal.") {
                    payloadsRejected = true
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
        every { anyConstructed<HTTPClient>().upload(any(), any()) } returns connection

        assertEquals(trackEvent, destSpy.track(trackEvent))
        (analytics.storage as AndroidStorage).let {
            assertEquals(1, segmentDestination.eventCount.get())
        }
        destSpy.flush()
        assertTrue(payloadsRejected)
        (analytics.storage as AndroidStorage).let {
            assertEquals(0, segmentDestination.eventCount.get())
        }
    }

    @Test
    fun `flush reads events but does not delete on fail code_429`() {
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
        var errorUploading = false
        val testLogger = object : Logger("test") {
            override fun log(type: LogType, message: String, event: BaseEvent?) {
                if (type == LogType.ERROR && message == "Error while uploading payloads") {
                    errorUploading = true
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
        every { anyConstructed<HTTPClient>().upload(any(), any()) } returns connection

        assertEquals(trackEvent, destSpy.track(trackEvent))
        (analytics.storage as AndroidStorage).let {
            assertEquals(1, segmentDestination.eventCount.get())
        }
        destSpy.flush()
        assertTrue(errorUploading)
        (analytics.storage as AndroidStorage).let {
            // queued event count gets cleared
            assertEquals(0, segmentDestination.eventCount.get())

            // batch file doesnt get deleted
            assertEquals(1, it.eventsFile.read().size)
        }
    }

    @Test
    fun `flush reads events but does not delete on fail code_500`() {
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
        var errorUploading = false
        val testLogger = object : Logger("test") {
            override fun log(type: LogType, message: String, event: BaseEvent?) {
                if (type == LogType.ERROR && message == "Error while uploading payloads") {
                    errorUploading = true
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
        every { anyConstructed<HTTPClient>().upload(any(), any()) } returns connection

        assertEquals(trackEvent, destSpy.track(trackEvent))
        (analytics.storage as AndroidStorage).let {
            assertEquals(1, segmentDestination.eventCount.get())
        }
        destSpy.flush()
        assertTrue(errorUploading)
        (analytics.storage as AndroidStorage).let {
            // queued event count gets cleared
            assertEquals(0, segmentDestination.eventCount.get())

            // batch file doesnt get deleted
            assertEquals(1, it.eventsFile.read().size)
        }
    }
}