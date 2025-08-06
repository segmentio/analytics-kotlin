package com.segment.analytics.kotlin.android

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.android.utils.MemorySharedPreferences
import com.segment.analytics.kotlin.android.utils.clearPersistentStorage
import com.segment.analytics.kotlin.android.utils.mockContext
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.Telemetry
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.UserInfo
import com.segment.analytics.kotlin.core.emptyJsonObject
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sovran.kotlin.Action
import sovran.kotlin.Store
import java.io.File
import java.util.Date
import java.util.HashMap

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageTests {
    private val epochTimestamp = Date(0).toInstant().toString()

    @Nested
    inner class Android {
        private var store = Store()
        private lateinit var androidStorage: Storage
        private var mockContext: Context = mockContext()

        init {
            Telemetry.enable = false
        }

        @BeforeEach
        fun setup() = runTest  {
            clearPersistentStorage()
            store.provide(
                UserInfo(
                    anonymousId = "oldAnonId",
                    userId = "oldUserId",
                    traits = buildJsonObject { put("behaviour", "bad") },
                    referrer = "oldReferrer",
                ))

            store.provide(
                System(
                    configuration = Configuration("123"),
                    settings = Settings(),
                    running = false,
                    initializedPlugins = setOf(),
                    enabled = true
                )
            )

            androidStorage = AndroidStorage(
                mockContext,
                store,
                "123",
                UnconfinedTestDispatcher()
            )
            androidStorage.initialize()
        }


        @Test
        fun `userInfo update calls write`() = runTest {
            val action = object : Action<UserInfo> {
                override fun reduce(state: UserInfo): UserInfo {
                    return UserInfo(
                        anonymousId = "newAnonId",
                        userId = "newUserId",
                        traits = emptyJsonObject,
                        referrer = "newReferrer"
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
        fun `userInfo reset action removes userInfo`() = runTest {
            store.dispatch(UserInfo.ResetAction(), UserInfo::class)

            val userId = androidStorage.read(Storage.Constants.UserId)
            val anonId = androidStorage.read(Storage.Constants.AnonymousId)
            val traits = androidStorage.read(Storage.Constants.Traits)

            assertNotNull(anonId)
            assertEquals(null, userId)
            assertEquals(null, traits)
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
                            edgeFunction = emptyJsonObject
                        ),
                        running = false,
                        initializedPlugins = setOf(),
                        enabled = true
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

        @Test
        fun `system reset action removes system`() = runTest {
            val action = object : Action<System> {
                override fun reduce(state: System): System {
                    return System(state.configuration, null, state.running, state.initializedPlugins, state.waitingPlugins, state.enabled)
                }
            }
            store.dispatch(action, System::class)

            val settings = androidStorage.read(Storage.Constants.Settings)

            assertEquals(null, settings)
        }

        @Nested
        inner class KeyValueStorage {
            val map = getWorkingMap(mockContext.getSharedPreferences("", 0))

            @Test
            fun `write updates sharedPreferences`() = runTest {
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

            @Test
            fun `test legacy app build`() = runTest {
                map["build"] = 100
                assertEquals("100", androidStorage.read(Storage.Constants.LegacyAppBuild))
            }
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
                androidStorage.write(Storage.Constants.Events, stringified)
                androidStorage.rollover()
                val storagePath = androidStorage.read(Storage.Constants.Events)?.let{
                    it.split(',')[0]
                }
                assertNotNull(storagePath)
                val storageContents = File(storagePath!!).readText()
                val jsonFormat = Json.decodeFromString(JsonObject.serializer(), storageContents)
                assertEquals(1, jsonFormat["batch"]!!.jsonArray.size)
            }

            @Test
            fun `cannot write more than 32kb as event`() = runTest {
                val stringified: String = "A".repeat(32002)
                val exception = try {
                    androidStorage.write(
                        Storage.Constants.Events,
                        stringified
                    )
                    null
                }
                catch (e: Exception) {
                    e
                }
                assertNotNull(exception)
                androidStorage.rollover()
                assertTrue(androidStorage.read(Storage.Constants.Events).isNullOrEmpty())
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
                androidStorage.write(Storage.Constants.Events, stringified)

                androidStorage.rollover()
                val fileUrl = androidStorage.read(Storage.Constants.Events)
                assertNotNull(fileUrl)
                fileUrl!!.let {
                    val contentsStr = File(it).inputStream().readBytes().toString(Charsets.UTF_8)
                    val contentsJson: JsonObject = Json.decodeFromString(contentsStr)
                    assertEquals(3, contentsJson.size)
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
            fun `reading events with empty storage return empty list`() = runTest {
                androidStorage.rollover()
                val fileUrls = androidStorage.read(Storage.Constants.Events)
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
                androidStorage.write(Storage.Constants.Events, stringified1)
                androidStorage.write(Storage.Constants.Events, stringified2)

                androidStorage.rollover()
                val fileUrl = androidStorage.read(Storage.Constants.Events)
                assertNotNull(fileUrl)
                fileUrl!!.let {
                    val contentsStr = File(it).inputStream().readBytes().toString(Charsets.UTF_8)
                    val contentsJson: JsonObject = Json.decodeFromString(contentsStr)
                    assertEquals(3, contentsJson.size)
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