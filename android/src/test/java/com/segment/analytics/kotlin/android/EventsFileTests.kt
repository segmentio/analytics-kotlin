package com.segment.analytics.kotlin.android

import com.segment.analytics.kotlin.android.utilities.AndroidKVS
import com.segment.analytics.kotlin.android.utils.MemorySharedPreferences
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.EventsFileManager
import com.segment.analytics.kotlin.core.utilities.dateTimeNowString
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Date

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventsFileTests {
    private val epochTimestamp = Date(0).toInstant().toString()
    private val sharedPreferences = MemorySharedPreferences()
    private val kvStore = AndroidKVS(sharedPreferences)
    private val directory = File("/tmp/analytics-android-test/")

    init {
        mockkStatic(Instant::class)
        mockkStatic(::dateTimeNowString)
        every { dateTimeNowString() } returns Date(0).toInstant().toString()
        every { Instant.now() } returns Date(0).toInstant()
    }

    @BeforeEach
    fun setup() {
        directory.deleteRecursively()
        sharedPreferences.preferenceMap.clear()
    }

    @Test
    fun `check if event is stored correctly and creates new file`() = runTest  {
        val file = EventsFileManager(directory, "123", kvStore)
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
        val eventString = EncodeDefaultsJson.encodeToString(trackEvent)
        file.storeEvent(eventString)

        val expectedContents = """{"batch":[${eventString}"""
        val newFile = File(directory, "123-0.tmp")
        assertTrue(newFile.exists())
        val actualContents = newFile.readText()
        assertEquals(expectedContents, actualContents)
    }

    @Test
    fun `storeEvent stores in existing file if available`() = runTest  {
        val file = EventsFileManager(directory, "123", kvStore)
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
        val eventString = EncodeDefaultsJson.encodeToString(trackEvent)
        file.storeEvent(eventString)
        file.storeEvent(eventString)

        val expectedContents = """{"batch":[${eventString},${eventString}"""
        val newFile = File(directory, "123-0.tmp")
        assertTrue(newFile.exists())
        val actualContents = newFile.readText()
        assertEquals(expectedContents, actualContents)
    }

    @Test
    fun `storeEvent creates new file when at capacity and closes other file`() = runTest  {
        val file = EventsFileManager(directory, "123", kvStore)
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
        val eventString = EncodeDefaultsJson.encodeToString(trackEvent)
        file.storeEvent(eventString)
        // artificially add 500kb of data to file
        FileOutputStream(File(directory, "123-0.tmp"), true).write(
            "A".repeat(475_000).toByteArray()
        )

        file.storeEvent(eventString)
        assertFalse(File(directory, "123-0.tmp").exists())
        assertTrue(File(directory, "123-0").exists())
        val expectedContents = """{"batch":[${eventString}"""
        val newFile = File(directory, "123-1.tmp")
        assertTrue(newFile.exists())
        val actualContents = newFile.readText()
        assertEquals(expectedContents, actualContents)

    }

    @Test
    fun `read returns empty list when no events stored`() = runTest {
        val file = EventsFileManager(directory, "123", kvStore)
        file.rollover()
        assertTrue(file.read().isEmpty())
    }

    @Test
    fun `read finishes open file and lists it`() = runTest {
        val file = EventsFileManager(directory, "123", kvStore)
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
        val eventString = EncodeDefaultsJson.encodeToString(trackEvent)
        file.storeEvent(eventString)

        file.rollover()
        val fileUrls = file.read()
        assertEquals(1, fileUrls.size)
        val expectedContents = """ {"batch":[${eventString}],"sentAt":"$epochTimestamp","writeKey":"123"} """.trim()
        val newFile = File(directory, "123-0")
        assertTrue(newFile.exists())
        val actualContents = newFile.readText()
        assertEquals(expectedContents, actualContents)
    }

    @Test
    fun `multiple reads doesnt create extra files`() = runTest {
        val file = EventsFileManager(directory, "123", kvStore)
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
        val eventString = EncodeDefaultsJson.encodeToString(trackEvent)
        file.storeEvent(eventString)

        file.rollover()
        file.read().let {
            assertEquals(1, it.size)
            val expectedContents =
                """ {"batch":[${eventString}],"sentAt":"$epochTimestamp","writeKey":"123"} """.trim()
            val newFile = File(directory, "123-0")
            assertTrue(newFile.exists())
            val actualContents = newFile.readText()
            assertEquals(expectedContents, actualContents)
        }
        // second read is a no-op
        file.rollover()
        file.read().let {
            assertEquals(1, it.size)
            assertEquals(1, directory.list()!!.size)
        }
    }

    @Test
    fun `read lists all available files for writekey`() = runTest {
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
        val eventString = EncodeDefaultsJson.encodeToString(trackEvent)

        val file1 = EventsFileManager(directory, "123", kvStore)
        val file2 = EventsFileManager(directory, "qwerty", kvStore)

        file1.storeEvent(eventString)
        file2.storeEvent(eventString)

        file1.rollover()
        file2.rollover()

        assertEquals(listOf("/tmp/analytics-android-test/123-0"), file1.read())
        assertEquals(listOf("/tmp/analytics-android-test/qwerty-0"), file2.read())
    }

//    @Test
//    fun `testScenario`() {
//        val file = EventsFileManager(directory, "123", sharedPreferences)
//        val trackEvent = TrackEvent(
//            event = "clicked",
//            properties = buildJsonObject { put("behaviour", "good") })
//            .apply {
//                messageId = "qwerty-1234"
//                anonymousId = "anonId"
//                integrations = emptyJsonObject
//                context = emptyJsonObject
//                timestamp = epochTimestamp
//            }
//        val eventString = EncodeDefaultsJson.encodeToString(trackEvent)
//        file.storeEvent(eventString)
//
//        val expectedContents = """{"batch":[${eventString}"""
//
//        val actualContents = File(directory, "123-0.tmp").readText()
//        assertEquals(expectedContents, actualContents)
//
//        println(file.read())
//        println(file.read())
//
//        file.storeEvent(eventString)
//        println(file.read())
//        println(file.read())
//    }

    @Test
    fun `remove deletes file`() = runTest {
        val file = EventsFileManager(directory, "123", kvStore)
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
        val eventString = EncodeDefaultsJson.encodeToString(trackEvent)
        file.storeEvent(eventString)

        file.rollover()
        val list = file.read()
        file.remove(list[0])

        assertFalse(File(list[0]).exists())
    }

}