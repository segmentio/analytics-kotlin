package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.EventStream
import com.segment.analytics.kotlin.core.utilities.FileEventStream
import com.segment.analytics.kotlin.core.utilities.InMemoryEventStream
import com.segment.analytics.kotlin.core.utilities.InMemoryPrefs
import com.segment.analytics.kotlin.core.utilities.InMemoryStorageProvider
import com.segment.analytics.kotlin.core.utilities.KVS
import com.segment.analytics.kotlin.core.utilities.PropertiesFile
import com.segment.analytics.kotlin.core.utilities.StorageImpl
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sovran.kotlin.Store
import java.lang.StringBuilder
import java.util.Date

class StorageTest {
    @Nested
    inner class StorageProviderTest {
        private lateinit var analytics: Analytics
        private val testDispatcher = UnconfinedTestDispatcher()
        private val testScope = TestScope(testDispatcher)

        @BeforeEach
        fun setup() {
            clearPersistentStorage()
            val config = Configuration(
                writeKey = "123",
                application = "Test"
            )

            analytics = testAnalytics(config, testScope, testDispatcher)
        }

        @Test
        fun concreteStorageProviderTest() {
            val storage = ConcreteStorageProvider.createStorage(analytics) as StorageImpl
            assertTrue(storage.eventStream is FileEventStream)
            assertTrue(storage.propertiesFile is PropertiesFile)

            val eventStream = storage.eventStream as FileEventStream
            val propertiesFile = (storage.propertiesFile as PropertiesFile).file

            val dir = "/tmp/analytics-kotlin/${analytics.configuration.writeKey}"
            // we don't cache storage directory, but we can use the parent of the event storage to verify
            assertEquals(dir, eventStream.directory.parent)
            assertTrue(eventStream.directory.path.contains(dir))
            assertTrue(propertiesFile.path.contains(dir))
            assertTrue(eventStream.directory.exists())
            assertTrue(propertiesFile.exists())
        }

        @Test
        fun inMemoryStorageProviderTest() {
            val storage = InMemoryStorageProvider.createStorage(analytics) as StorageImpl
            assertTrue(storage.eventStream is InMemoryEventStream)
            assertTrue(storage.propertiesFile is InMemoryPrefs)
        }
    }

    @Nested
    inner class StorageTest {
        private lateinit var storage: StorageImpl

        private lateinit var prefs: KVS

        private lateinit var stream: EventStream

        private lateinit var payload: String

        @BeforeEach
        fun setup() {
            val trackEvent = TrackEvent(
                event = "clicked",
                properties = buildJsonObject { put("foo", "bar") })
                .apply {
                    messageId = "qwerty-1234"
                    anonymousId = "anonId"
                    integrations = emptyJsonObject
                    context = emptyJsonObject
                    timestamp = Date(0).toInstant().toString()
                }
            payload = EncodeDefaultsJson.encodeToString(trackEvent)
            prefs = InMemoryPrefs()
            stream = mockk<EventStream>(relaxed = true)
            storage = StorageImpl(prefs, stream, mockk<Store>(), "test", "key", UnconfinedTestDispatcher())
        }

        @Test
        fun writeNewFileTest() = runTest {
            every { stream.openOrCreate(any()) } returns true
            storage.write(Storage.Constants.Events, payload)
            verify(exactly = 1) {
                stream.write(storage.begin)
                stream.write(payload)
            }
        }

        @Test
        fun rolloverToNewFileTest() = runTest {
            every { stream.openOrCreate(any()) } returns false andThen true
            every { stream.length } returns Storage.MAX_FILE_SIZE + 1L
            every { stream.isOpened } returns true

            storage.write(Storage.Constants.Events, payload)
            assertEquals(1, prefs.get(storage.fileIndexKey, 0))
            verify (exactly = 1) {
                stream.finishAndClose(any())
                stream.write(storage.begin)
                stream.write(payload)
            }

            verify (exactly = 3){
                stream.write(any())
            }
        }

        @Test
        fun largePayloadCauseExceptionTest() = runTest {
            val letters = "abcdefghijklmnopqrstuvwxyz1234567890"
            val largePayload = StringBuilder()
            for (i in 0..1000) {
                largePayload.append(letters)
            }

            assertThrows<Exception> {
                storage.write(Storage.Constants.Events, largePayload.toString())
            }
        }

        @Test
        fun writePrefsAsyncTest() = runTest {
            val expected = "userid"
            assertNull(storage.read(Storage.Constants.UserId))
            storage.write(Storage.Constants.UserId, expected)
            assertEquals(expected, storage.read(Storage.Constants.UserId))
        }

        @Test
        fun writePrefsTest() {
            val expected = "userId"
            assertNull(storage.read(Storage.Constants.UserId))
            storage.writePrefs(Storage.Constants.UserId, expected)
            assertEquals(expected, storage.read(Storage.Constants.UserId))
        }

        @Test
        fun rolloverTest() = runTest {
            every { stream.isOpened } returns true

            storage.rollover()

            verify (exactly = 1) {
                stream.write(any())
                stream.finishAndClose(any())
            }

            assertEquals(1, prefs.get(storage.fileIndexKey, 0))
        }

        @Test
        fun readTest() {
            val files = listOf("test1.tmp", "test2", "test3.tmp", "test4")
            every { stream.read() } returns files
            prefs.put(Storage.Constants.UserId.rawVal, "userId")

            val actual = storage.read(Storage.Constants.Events)
            assertEquals(listOf(files[1], files[3]).joinToString(), actual)
            assertEquals("userId", storage.read(Storage.Constants.UserId))
        }

        @Test
        fun removeTest() {
            prefs.put(Storage.Constants.UserId.rawVal, "userId")
            storage.remove(Storage.Constants.UserId)

            assertTrue(storage.remove(Storage.Constants.Events))
            assertNull(storage.read(Storage.Constants.UserId))
        }

        @Test
        fun removeFileTest() {
            storage.removeFile("file")
            verify (exactly = 1) {
                stream.remove("file")
            }

            every { stream.remove(any()) } throws java.lang.Exception()
            assertFalse(storage.removeFile("file"))
        }

        @Test
        fun readAsStream() {
            storage.readAsStream("file")
            verify (exactly = 1) {
                stream.readAsStream(any())
            }
        }
    }
}