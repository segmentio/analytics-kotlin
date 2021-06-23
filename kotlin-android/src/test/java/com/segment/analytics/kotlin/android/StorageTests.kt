package com.segment.analytics.kotlin.android

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.android.utils.MemorySharedPreferences
import com.segment.analytics.kotlin.android.utils.mockAnalytics
import com.segment.analytics.kotlin.android.utils.mockContext
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sovran.kotlin.Store
import java.io.File
import sovran.kotlin.Action
import java.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageTests {
    private val epochTimestamp = Date(0).toInstant().toString()

    @Nested
    inner class Android {
        private var store = Store()
        private lateinit var androidStorage: AndroidStorage
        private var mockContext: Context = mockContext()

        @BeforeEach
        fun setup() {
            File("/tmp/analytics-android").deleteRecursively()
            File("/tmp/analytics-android").mkdir()
            store.provide(
                UserInfo(
                    anonymousId = "oldAnonId",
                    userId = "oldUserId",
                    traits = buildJsonObject { put("behaviour", "bad") }
                ))

            store.provide(
                System(
                    configuration = Configuration("123"),
                    integrations = emptyJsonObject,
                    settings = Settings(),
                    false
                )
            )

            androidStorage = AndroidStorage(
                mockAnalytics(),
                mockContext,
                store,
                "123",
                TestCoroutineDispatcher()
            )
            androidStorage.subscribeToStore()
        }


        @Test
        fun `userInfo update calls write`() = runBlockingTest {
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
            val userId = androidStorage.read(Storage.Constants.UserId)
            val anonId = androidStorage.read(Storage.Constants.AnonymousId)
            val traits = androidStorage.read(Storage.Constants.Traits)

            assertEquals("newAnonId", anonId)
            assertEquals("newUserId", userId)
            assertEquals("{}", traits)
        }

        @Test
        fun `system update calls write for settings`() = runBlockingTest {
            val action = object : Action<System> {
                override fun reduce(state: System): System {
                    return System(
                        configuration = state.configuration,
                        integrations = state.integrations,
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
                            edgeFunction = emptyJsonObject
                        ),
                        false
                    )
                }
            }
            store.dispatch(action, System::class)
            val settings = androidStorage.read(Storage.Constants.Settings) ?: ""

            assertEquals(
                Settings(
                    integrations = buildJsonObject {
                        put(
                            "Segment.io",
                            buildJsonObject { put("apiKey", "1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ") })
                    },
                    plan = emptyJsonObject,
                    edgeFunction = emptyJsonObject
                ), Json.decodeFromString(Settings.serializer(), settings)
            )
        }

        @Nested
        inner class KeyValueStorage {
            val map = getWorkingMap(mockContext.getSharedPreferences("", 0))

            @Test
            fun `write updates sharedPreferences`() {
                androidStorage.write(Storage.Constants.AppVersion, "100")
                assertEquals("100", map["segment.app.version"])
            }

            @Test
            fun `read fetches from sharedPreferences`() {
                map["segment.app.version"] = "100"
                assertEquals("100", androidStorage.read(Storage.Constants.AppVersion))
            }

            @Test
            fun `remove sets value to null`() {
                androidStorage.remove(Storage.Constants.AppVersion)
                assertEquals(null, map["segment.app.version"])
            }
        }

        @Nested
        inner class EventsStorage() {

            @Test
            fun `writing events writes to eventsFile`() {
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
                androidStorage.write(Storage.Constants.Events, stringified)
                val storagePath = androidStorage.eventsFile.read()[0]
                val storageContents = File(storagePath).readText()
                val jsonFormat = Json.decodeFromString(JsonObject.serializer(), storageContents)
                assertEquals(1, jsonFormat["batch"]!!.jsonArray.size)
            }

            @Test
            fun `cannot write more than 32kb as event`() {
                val stringified: String = "A".repeat(32002)
                assertThrows(Exception::class.java) {
                    androidStorage.write(
                        Storage.Constants.Events,
                        stringified
                    )
                }
                assertTrue(androidStorage.eventsFile.read().isEmpty())
            }

            @Test
            fun `reading events returns a non-null file handle with correct events`() {
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
                androidStorage.write(Storage.Constants.Events, stringified)

                val fileUrl = androidStorage.read(Storage.Constants.Events)
                assertNotNull(fileUrl)
                fileUrl!!.let {
                    val contentsStr = File(it).inputStream().readBytes().toString(Charsets.UTF_8)
                    val contentsJson: JsonObject = Json.decodeFromString(contentsStr)
                    assertEquals(2, contentsJson.size)
                    assertTrue(contentsJson.containsKey("batch"))
                    assertTrue(contentsJson.containsKey("sentAt"))
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
                val fileUrls = androidStorage.read(Storage.Constants.Events)
                assertTrue(fileUrls!!.isEmpty())
            }

            @Test
            fun `can write and read multiple events`() {
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
                androidStorage.write(Storage.Constants.Events, stringified1)
                androidStorage.write(Storage.Constants.Events, stringified2)

                val fileUrl = androidStorage.read(Storage.Constants.Events)
                assertNotNull(fileUrl)
                fileUrl!!.let {
                    val contentsStr = File(it).inputStream().readBytes().toString(Charsets.UTF_8)
                    val contentsJson: JsonObject = Json.decodeFromString(contentsStr)
                    assertEquals(2, contentsJson.size)
                    assertTrue(contentsJson.containsKey("batch"))
                    assertTrue(contentsJson.containsKey("sentAt"))
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

//            @Test
//            fun `remove() deletes temp file and deletes event from queue`() {
//                val event = TrackEvent(
//                    event = "clicked",
//                    properties = buildJsonObject { put("behaviour", "good") })
//                    .apply {
//                        messageId = "qwerty-1234"
//                        anonymousId = "anonId"
//                        integrations = emptyJsonObject
//                        context = emptyJsonObject
//                        timestamp = epochTimestamp
//                    }
//                androidStorage.write(Storage.Constants.Events, Json.encodeToString(event))
//
//                val fileUrl = androidStorage.read(Storage.Constants.Events)
//                assertNotNull(fileUrl)
//                val storagePath = androidStorage.eventsFile.read()[0]
//                val storageContents = File(storagePath).readText()
//                val jsonFormat = Json.decodeFromString(JsonObject.serializer(), storageContents)
//                assertEquals(1, jsonFormat["batch"]!!.jsonArray.size)
//
//                androidStorage.remove(Storage.Constants.Events)
//                assertFalse(File(storagePath).exists())
//            }
//
//            @Test
//            fun `handles exception during remove error`() {
//                val success = androidStorage.remove(Storage.Constants.Events)
//                assertFalse(success)
//            }

        }
    }

    fun getWorkingMap(sharedPreferences: SharedPreferences): HashMap<String, Any?> {
        return (sharedPreferences as MemorySharedPreferences).preferenceMap
    }
}