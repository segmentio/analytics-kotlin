package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.UserInfo
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.spyStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sovran.kotlin.Action
import java.io.File
import java.util.Date

internal class StorageImplTest {

    private val epochTimestamp = Date(0).toInstant().toString()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testScope = TestScope(testDispatcher)

    private var store = spyStore(testScope, testDispatcher)
    private lateinit var storage: StorageImpl

    @BeforeEach
    fun setup() = runTest  {
        clearPersistentStorage()
        store.provide(
            UserInfo(
                anonymousId = "oldAnonId",
                userId = "oldUserId",
                traits = buildJsonObject { put("behaviour", "bad") }
            ))

        store.provide(
            System(
                configuration = Configuration("123"),
                settings = Settings(),
                running = false,
                initialSettingsDispatched = false
            )
        )

        storage = StorageImpl(
            store,
            "123",
            UnconfinedTestDispatcher()
        )
        storage.subscribeToStore()
    }


    @Test
    fun `userInfo update calls write`() = runTest {
        val action = object : Action<UserInfo> {
            override fun reduce(state: UserInfo): UserInfo {
                return UserInfo(
                    anonymousId = "newAnonId",
                    userId = "newUserId",
                    traits = emptyJsonObject
                )
            }
        }
        store.dispatch(action, UserInfo::class)
        val userId = storage.read(Storage.Constants.UserId)
        val anonId = storage.read(Storage.Constants.AnonymousId)
        val traits = storage.read(Storage.Constants.Traits)

        assertEquals("newAnonId", anonId)
        assertEquals("newUserId", userId)
        assertEquals("{}", traits)
    }

    @Test
    fun `system update calls write for settings`() = runTest {
        val action = object : Action<System> {
            override fun reduce(state: System): System {
                return System(
                    configuration = state.configuration,
                    settings = Settings(
                        integrations = buildJsonObject {
                            put(
                                "Segment.io",
                                buildJsonObject {
                                    put(
                                        "apiKey",
                                        "1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ"
                                    )
                                })
                        },
                        plan = emptyJsonObject,
                        edgeFunction = emptyJsonObject,
                        middlewareSettings = emptyJsonObject
                    ),
                    running = false,
                    initialSettingsDispatched = false
                )
            }
        }
        store.dispatch(action, System::class)
        val settings = storage.read(Storage.Constants.Settings) ?: ""

        assertEquals(
            Settings(
                integrations = buildJsonObject {
                    put(
                        "Segment.io",
                        buildJsonObject { put("apiKey", "1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ") })
                },
                plan = emptyJsonObject,
                edgeFunction = emptyJsonObject,
                middlewareSettings = emptyJsonObject
            ), Json.decodeFromString(Settings.serializer(), settings)
        )
    }

    @Nested
    inner class EventsStorage() {

        @Test
        fun `writing events writes to eventsFile`() = runTest {
            val event = TrackEvent(
                event = "clicked",
                properties = buildJsonObject { put("behaviour", "good") })
                .apply {
                    messageId = "qwerty-1234"
                    anonymousId = "anonId"
                    integrations = emptyJsonObject
                    context = emptyJsonObject
                    timestamp = epochTimestamp
                }
            val stringified: String = Json.encodeToString(event)
            storage.write(Storage.Constants.Events, stringified)
            storage.rollover()
            val storagePath = storage.eventsFile.read()[0]
            val storageContents = File(storagePath).readText()
            val jsonFormat = Json.decodeFromString(JsonObject.serializer(), storageContents)
            assertEquals(1, jsonFormat["batch"]!!.jsonArray.size)
        }

        @Test
        fun `cannot write more than 32kb as event`() = runTest {
            val stringified: String = "A".repeat(32002)
            val exception = try {
                storage.write(
                    Storage.Constants.Events,
                    stringified
                )
                null
            }
            catch (e : Exception) {
                e
            }
            assertNotNull(exception)
            assertTrue(storage.eventsFile.read().isEmpty())
        }

        @Test
        fun `reading events returns a non-null file handle with correct events`() = runTest {
            val event = TrackEvent(
                event = "clicked",
                properties = buildJsonObject { put("behaviour", "good") })
                .apply {
                    messageId = "qwerty-1234"
                    anonymousId = "anonId"
                    integrations = emptyJsonObject
                    context = emptyJsonObject
                    timestamp = epochTimestamp
                }
            val stringified: String = Json.encodeToString(event)
            storage.write(Storage.Constants.Events, stringified)

            storage.rollover()
            val fileUrl = storage.read(Storage.Constants.Events)
            assertNotNull(fileUrl)
            fileUrl!!.let {
                val contentsStr = File(it).inputStream().readBytes().toString(Charsets.UTF_8)
                val contentsJson: JsonObject = Json.decodeFromString(contentsStr)
                assertEquals(3, contentsJson.size) // batch, sentAt, writeKey
                assertTrue(contentsJson.containsKey("batch"))
                assertTrue(contentsJson.containsKey("sentAt"))
                assertTrue(contentsJson.containsKey("writeKey"))
                assertEquals(1, contentsJson["batch"]?.jsonArray?.size)
                val eventInFile = contentsJson["batch"]?.jsonArray?.get(0)
                val eventInFile2 = Json.decodeFromString(
                    TrackEvent.serializer(),
                    Json.encodeToString(eventInFile)
                )
                assertEquals(event, eventInFile2)
            }
        }

        @Test
        fun `reading events with empty storage return empty list`() {
            val fileUrls = storage.read(Storage.Constants.Events)
            assertTrue(fileUrls!!.isEmpty())
        }

        @Test
        fun `can write and read multiple events`() = runTest {
            val event1 = TrackEvent(
                event = "clicked",
                properties = buildJsonObject { put("behaviour", "good") })
                .apply {
                    messageId = "qwerty-1234"
                    anonymousId = "anonId"
                    integrations = emptyJsonObject
                    context = emptyJsonObject
                    timestamp = epochTimestamp
                }
            val event2 = TrackEvent(
                event = "clicked2",
                properties = buildJsonObject { put("behaviour", "bad") })
                .apply {
                    messageId = "qwerty-12345"
                    anonymousId = "anonId"
                    integrations = emptyJsonObject
                    context = emptyJsonObject
                    timestamp = epochTimestamp
                }
            val stringified1: String = Json.encodeToString(event1)
            val stringified2: String = Json.encodeToString(event2)
            storage.write(Storage.Constants.Events, stringified1)
            storage.write(Storage.Constants.Events, stringified2)

            storage.rollover()
            val fileUrl = storage.read(Storage.Constants.Events)
            assertNotNull(fileUrl)
            fileUrl!!.let {
                val contentsStr = File(it).inputStream().readBytes().toString(Charsets.UTF_8)
                val contentsJson: JsonObject = Json.decodeFromString(contentsStr)
                assertEquals(3, contentsJson.size) // batch, sentAt, writeKey
                assertTrue(contentsJson.containsKey("batch"))
                assertTrue(contentsJson.containsKey("sentAt"))
                assertTrue(contentsJson.containsKey("writeKey"))
                assertEquals(2, contentsJson["batch"]?.jsonArray?.size)
                val eventInFile = contentsJson["batch"]?.jsonArray?.get(0)
                val eventInFile2 = Json.decodeFromString(
                    TrackEvent.serializer(),
                    Json.encodeToString(eventInFile)
                )
                assertEquals(event1, eventInFile2)

                val event2InFile = contentsJson["batch"]?.jsonArray?.get(1)
                val event2InFile2 = Json.decodeFromString(
                    TrackEvent.serializer(),
                    Json.encodeToString(event2InFile)
                )
                assertEquals(event2, event2InFile2)
            }
        }

        @Test
        fun remove() = runTest {
            val action = object : Action<UserInfo> {
                override fun reduce(state: UserInfo): UserInfo {
                    return UserInfo(
                        anonymousId = "newAnonId",
                        userId = "newUserId",
                        traits = emptyJsonObject
                    )
                }
            }
            store.dispatch(action, UserInfo::class)

            val userId = storage.read(Storage.Constants.UserId)
            assertEquals("newUserId", userId)

            storage.remove(Storage.Constants.UserId)
            assertNull(storage.read(Storage.Constants.UserId))
            assertTrue(storage.remove(Storage.Constants.Events))
        }
    }

}