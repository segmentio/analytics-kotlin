package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.ContextPlugin
import com.segment.analytics.kotlin.core.platform.plugins.SegmentDestination
import com.segment.analytics.kotlin.core.utilities.dateTimeNowString
import com.segment.analytics.kotlin.core.utils.StubPlugin
import com.segment.analytics.kotlin.core.utils.TestRunPlugin
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.mockHTTPClient
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.time.Instant
import java.util.Date
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsTests {
    private lateinit var analytics: Analytics

    private val epochTimestamp = Date(0).toInstant().toString()
    private val baseContext = buildJsonObject {
        val lib = buildJsonObject {
            put(ContextPlugin.LIBRARY_NAME_KEY, "analytics-kotlin")
            put(ContextPlugin.LIBRARY_VERSION_KEY, Constants.LIBRARY_VERSION)
        }
        put(ContextPlugin.LIBRARY_KEY, lib)
        put(ContextPlugin.INSTANCE_ID_KEY, "qwerty-qwerty-123")
    }

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    init {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(::dateTimeNowString)
        every { dateTimeNowString() } returns Date(0).toInstant().toString()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
    }

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        mockHTTPClient()
        val config = Configuration(
            writeKey = "123",
            application = "Test"
        )

        analytics = testAnalytics(config, testScope, testDispatcher)
        analytics.configuration.autoAddSegmentDestination = false
    }

    @Nested
    inner class Init {
        // TODO: Figure out why this test was breaking the identity test
        @Test @Disabled
        fun `jvm initializer in jvm platform should succeed`() {
            mockkStatic("com.segment.analytics.kotlin.core.AnalyticsKt")
            every { isAndroid() } returns false
            assertDoesNotThrow {
                Analytics("123") {
                    application = "Test"
                }
            }
        }

        @Test
        fun `jvm initializer in android platform should failed`() {
            mockkStatic("com.segment.analytics.kotlin.core.AnalyticsKt")
            every { isAndroid() } returns true

            val exception = assertThrows<Exception> {
                Analytics("123") {
                    application = "Test"
                }
            }

            assertEquals(exception.message?.contains("Android"), true)
        }

        @Disabled
        @Test
        fun `analytics should respect remote apiHost`() {
            // need the following block in `init` to inject mock before analytics gets instantiate
            val settingsStream = ByteArrayInputStream(
                """
                {"integrations":{"Segment.io":{"apiKey":"1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ","apiHost":"remote"}},"plan":{},"edgeFunction":{}}
            """.trimIndent().toByteArray()
            )
            val httpConnection: HttpURLConnection = mockk()
            val connection = object : Connection(httpConnection, settingsStream, null) {}
            every { anyConstructed<HTTPClient>().settings("cdn-settings.segment.com/v1") } returns connection

            val config = Configuration(
                writeKey = "123",
                application = "Test",
                apiHost = "local"
            )
            analytics = testAnalytics(config, testScope, testDispatcher)
            analytics.track("test")
            analytics.flush()

            val apiHost = slot<String>()
            verify { anyConstructed<HTTPClient>().upload(capture(apiHost)) }
            assertEquals("remote", apiHost.captured)
        }

        @Disabled
        @Test
        fun `analytics should respect local modified apiHost if remote not presented`() {
            val config = Configuration(
                writeKey = "123",
                application = "Test",
                apiHost = "local"
            )
            analytics = testAnalytics(config, testScope, testDispatcher)
            analytics.track("test")
            analytics.flush()

            val apiHost = slot<String>()
            verify { anyConstructed<HTTPClient>().upload(capture(apiHost)) }
            assertEquals("local", apiHost.captured)
        }
    }

    @Nested
    inner class PluginTests {

        @Test
        fun `Can add plugins to analytics`() {
            val middleware = object : Plugin {
                override val type = Plugin.Type.Utility
                override lateinit var analytics: Analytics
            }
            analytics.add(middleware)
            analytics.timeline.plugins[Plugin.Type.Utility]?.plugins?.let {
                assertEquals(
                    1,
                    it.size
                )
            } ?: Assertions.fail()
        }

        @Test
        fun `Can remove plugins from analytics`() {
            val middleware = object : Plugin {
                override val type = Plugin.Type.Utility
                override lateinit var analytics: Analytics
            }
            analytics.add(middleware)
            analytics.remove(middleware)
            analytics.timeline.plugins[Plugin.Type.Utility]?.plugins?.let {
                assertEquals(
                    0,
                    it.size
                )
            } ?: Assertions.fail()
        }

        @Test
        fun `event runs through chain of plugins`() {
            val testPlugin1 = TestRunPlugin {}
            val testPlugin2 = TestRunPlugin {}
            analytics
                .add(testPlugin1)
                .add(testPlugin2)
                .process(TrackEvent(event = "track", properties = emptyJsonObject))
            assertTrue(testPlugin1.ran)
            assertTrue(testPlugin2.ran)
        }



        @Test
        fun `Can manually enable DestinationPlugin`() = runBlocking {
            val plugin = object : DestinationPlugin() {
                override val type: Plugin.Type = Plugin.Type.Destination
                override lateinit var analytics: Analytics
                override val key: String = "MyDestPlugin"
            }

            analytics.add(plugin)
            assertFalse(plugin.enabled)
            analytics.manuallyEnableDestination(plugin)

            val destinationPlugin = analytics.find(plugin.key)

            assertNotNull(destinationPlugin)
            assertEquals(true, destinationPlugin?.enabled)
            assertEquals(plugin.enabled, destinationPlugin?.enabled)
            println("DestinationPlugin.enabled: ${destinationPlugin?.enabled}")
            println("plugin.enabled: ${plugin.enabled}")
        }
    }

    @Nested
    inner class EventTests {
        @Test
        fun `event defaults get populated`() {
            val mockPlugin = spyk(StubPlugin())
            analytics.add(mockPlugin)
            analytics.track("track", buildJsonObject { put("foo", "bar") })
            val track = slot<TrackEvent>()
            verify { mockPlugin.track(capture(track)) }
            track.captured.let {
                assertTrue(it.anonymousId.isNotBlank())
                assertTrue(it.messageId.isNotBlank())
                assertEquals(epochTimestamp, it.timestamp)
                assertEquals(baseContext, it.context)
                assertEquals(emptyJsonObject, it.integrations)
            }
        }

        @Test
        fun `event defaults get populated with mapOf properties`() {
            val mockPlugin = spyk(StubPlugin())
            analytics.add(mockPlugin)
            analytics.track("track", mapOf("foo" to "bar"))
            val track = slot<TrackEvent>()
            verify { mockPlugin.track(capture(track)) }
            track.captured.let {
                assertTrue(it.anonymousId.isNotBlank())
                assertTrue(it.messageId.isNotBlank())
                assertEquals(epochTimestamp, it.timestamp)
                assertEquals(baseContext, it.context)
                assertEquals(emptyJsonObject, it.integrations)
            }
        }

        @Test
        fun `event defaults get populated with mutableMapOf properties`() {
            val mockPlugin = spyk(StubPlugin())
            analytics.add(mockPlugin)
            analytics.track("track", mutableMapOf("foo" to "bar"))
            val track = slot<TrackEvent>()
            verify { mockPlugin.track(capture(track)) }
            track.captured.let {
                assertTrue(it.anonymousId.isNotBlank())
                assertTrue(it.messageId.isNotBlank())
                assertEquals(epochTimestamp, it.timestamp)
                assertEquals(baseContext, it.context)
                assertEquals(emptyJsonObject, it.integrations)
            }
        }

        @Test
        fun `event defaults get populated with HashMap properties`() {
            val mockPlugin = spyk(StubPlugin())
            analytics.add(mockPlugin)
            analytics.track("track", HashMap<String, Any>().apply { put("foo", "bar") })
            val track = slot<TrackEvent>()
            verify { mockPlugin.track(capture(track)) }
            track.captured.let {
                assertTrue(it.anonymousId.isNotBlank())
                assertTrue(it.messageId.isNotBlank())
                assertEquals(epochTimestamp, it.timestamp)
                assertEquals(baseContext, it.context)
                assertEquals(emptyJsonObject, it.integrations)
            }
        }

        @Test
        fun `event gets populated with empty context`() {
            val mockPlugin = spyk(StubPlugin())
            analytics.add(mockPlugin)
            analytics.track("track", buildJsonObject { put("foo", "bar") })
            val track = slot<TrackEvent>()
            verify { mockPlugin.track(capture(track)) }
            track.captured.let {
                assertTrue(it.anonymousId.isNotBlank())
                assertTrue(it.messageId.isNotBlank())
                assertTrue(it.timestamp == epochTimestamp)
                assertEquals(emptyJsonObject, it.integrations)
                assertEquals(baseContext, it.context)
            }
        }

        @Nested
        inner class Track {
            @Test
            fun `track event with JSON properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.track("track", buildJsonObject { put("foo", "bar") })
                val track = slot<TrackEvent>()
                verify { mockPlugin.track(capture(track)) }
                assertEquals(
                    TrackEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        event = "track"
                    ).populate(),
                    track.captured
                )
            }

            @Test
            fun `track event with mapOf properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.track("track", mapOf<String, Any>("foo" to "bar") )
                val track = slot<TrackEvent>()
                verify { mockPlugin.track(capture(track)) }
                assertEquals(
                    TrackEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        event = "track"
                    ).populate(),
                    track.captured
                )
            }

            @Test
            fun `track event with mutableMapOf properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.track("track", mutableMapOf<String, Any>("foo" to "bar") )
                val track = slot<TrackEvent>()
                verify { mockPlugin.track(capture(track)) }
                assertEquals(
                    TrackEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        event = "track"
                    ).populate(),
                    track.captured
                )
            }

            @Test
            fun `track event with HashMap properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.track("track", HashMap<String, Any>().apply {  put("foo", "bar") })
                val track = slot<TrackEvent>()
                verify { mockPlugin.track(capture(track)) }
                assertEquals(
                    TrackEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        event = "track"
                    ).populate(),
                    track.captured
                )
            }
        }

        @Nested
        inner class Identify {
            @Test
            fun `identify event with JSON properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)

                analytics.identify("foobar", buildJsonObject { put("name", "bar") })
                val identify = slot<IdentifyEvent>()
                verify { mockPlugin.identify(capture(identify)) }
                assertEquals(
                    IdentifyEvent(
                        traits = buildJsonObject { put("name", "bar") },
                        userId = "foobar"
                    ).populate(),
                    identify.captured
                )
            }

            @Test
            fun `identify event with mapOf properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)

                analytics.identify("foobar", mapOf<String, Any>("name" to "bar"))
                val identify = slot<IdentifyEvent>()
                verify { mockPlugin.identify(capture(identify)) }
                assertEquals(
                    IdentifyEvent(
                        traits = buildJsonObject { put("name", "bar") },
                        userId = "foobar"
                    ).populate(),
                    identify.captured
                )
            }

            @Test
            fun `identify event with mutableMapOf properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)

                analytics.identify("foobar", mutableMapOf<String, Any>("name" to "bar"))
                val identify = slot<IdentifyEvent>()
                verify { mockPlugin.identify(capture(identify)) }
                assertEquals(
                    IdentifyEvent(
                        traits = buildJsonObject { put("name", "bar") },
                        userId = "foobar"
                    ).populate(),
                    identify.captured
                )
            }

            @Test
            fun `identify event with HashMap properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)

                analytics.identify("foobar", HashMap<String, Any>().apply { put("name", "bar") })
                val identify = slot<IdentifyEvent>()
                verify { mockPlugin.identify(capture(identify)) }
                assertEquals(
                    IdentifyEvent(
                        traits = buildJsonObject { put("name", "bar") },
                        userId = "foobar"
                    ).populate(),
                    identify.captured
                )
            }

            @Test
            fun `identify() overwrites userId and traits`() = runTest  {
                analytics.store.dispatch(
                    UserInfo.SetUserIdAndTraitsAction(
                        "oldUserId",
                        buildJsonObject { put("behaviour", "bad") }),
                    UserInfo::class
                )
                val curUserInfo = analytics.store.currentState(UserInfo::class)
                assertEquals(
                    UserInfo(
                        userId = "oldUserId",
                        traits = buildJsonObject { put("behaviour", "bad") },
                        anonymousId = "qwerty-qwerty-123"
                    ), curUserInfo
                )

                analytics.identify("newUserId", buildJsonObject { put("behaviour", "good") })

                val newUserInfo = analytics.store.currentState(UserInfo::class)
                assertEquals(
                    UserInfo(
                        userId = "newUserId",
                        traits = buildJsonObject { put("behaviour", "good") },
                        anonymousId = "qwerty-qwerty-123"
                    ), newUserInfo
                )
            }

            @Test
            fun `identify() overwrites traits`() = runTest  {
                analytics.store.dispatch(
                    UserInfo.SetUserIdAndTraitsAction(
                        "oldUserId",
                        buildJsonObject { put("behaviour", "bad") }),
                    UserInfo::class
                )
                val curUserInfo = analytics.store.currentState(UserInfo::class)
                assertEquals(
                    UserInfo(
                        userId = "oldUserId",
                        traits = buildJsonObject { put("behaviour", "bad") },
                        anonymousId = "qwerty-qwerty-123"
                    ), curUserInfo
                )

                analytics.identify( buildJsonObject { put("behaviour", "good") })

                val newUserInfo = analytics.store.currentState(UserInfo::class)
                assertEquals(
                    UserInfo(
                        userId = "oldUserId",
                        traits = buildJsonObject { put("behaviour", "good") },
                        anonymousId = "qwerty-qwerty-123"
                    ), newUserInfo
                )
            }
        }

        @Nested
        inner class Screen {
            @Test
            fun `screen event with JSON properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.screen(
                    title = "main",
                    category = "mobile",
                    properties = buildJsonObject { put("foo", "bar") })
                val screen = slot<ScreenEvent>()
                verify { mockPlugin.screen(capture(screen)) }
                assertEquals(
                    ScreenEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        name = "main",
                        category = "mobile"
                    ).populate(),
                    screen.captured
                )
            }

            @Test
            fun `screen event with mapOf properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.screen(
                    title = "main",
                    category = "mobile",
                    properties = mapOf<String, Any>("foo" to "bar"))
                val screen = slot<ScreenEvent>()
                verify { mockPlugin.screen(capture(screen)) }
                assertEquals(
                    ScreenEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        name = "main",
                        category = "mobile"
                    ).populate(),
                    screen.captured
                )
            }

            @Test
            fun `screen event with mutableMapOf properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.screen(
                    title = "main",
                    category = "mobile",
                    properties = mutableMapOf<String, Any>("foo" to "bar"))
                val screen = slot<ScreenEvent>()
                verify { mockPlugin.screen(capture(screen)) }
                assertEquals(
                    ScreenEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        name = "main",
                        category = "mobile"
                    ).populate(),
                    screen.captured
                )
            }

            @Test
            fun `screen event with HashMap properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.screen(
                    title = "main",
                    category = "mobile",
                    properties = HashMap<String, Any>().apply { put("foo", "bar") })
                val screen = slot<ScreenEvent>()
                verify { mockPlugin.screen(capture(screen)) }
                assertEquals(
                    ScreenEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        name = "main",
                        category = "mobile"
                    ).populate(),
                    screen.captured
                )
            }
        }

        @Nested
        inner class Group {
            @Test
            fun `group event with JSON properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.group("high school", buildJsonObject { put("foo", "bar") })
                val group = slot<GroupEvent>()
                verify { mockPlugin.group(capture(group)) }
                assertEquals(
                    GroupEvent(
                        traits = buildJsonObject { put("foo", "bar") },
                        groupId = "high school"
                    ).populate(),
                    group.captured
                )
            }

            @Test
            fun `group event with mapOf properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.group("high school", mapOf<String, Any>("foo" to "bar"))
                val group = slot<GroupEvent>()
                verify { mockPlugin.group(capture(group)) }
                assertEquals(
                    GroupEvent(
                        traits = buildJsonObject { put("foo", "bar") },
                        groupId = "high school"
                    ).populate(),
                    group.captured
                )
            }

            @Test
            fun `group event with mutableMapOf properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.group("high school", mutableMapOf<String, Any>("foo" to "bar"))
                val group = slot<GroupEvent>()
                verify { mockPlugin.group(capture(group)) }
                assertEquals(
                    GroupEvent(
                        traits = buildJsonObject { put("foo", "bar") },
                        groupId = "high school"
                    ).populate(),
                    group.captured
                )
            }

            @Test
            fun `group event with HashMap properties runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.group("high school", HashMap<String, Any>().apply { put("foo", "bar") })
                val group = slot<GroupEvent>()
                verify { mockPlugin.group(capture(group)) }
                assertEquals(
                    GroupEvent(
                        traits = buildJsonObject { put("foo", "bar") },
                        groupId = "high school"
                    ).populate(),
                    group.captured
                )
            }
        }


        @Nested
        inner class Alias {
            @Test
            fun `alias event runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.alias("newId")
                val alias = slot<AliasEvent>()
                verify { mockPlugin.alias(capture(alias)) }
                assertEquals(
                    AliasEvent(
                        userId = "newId",
                        previousId = "qwerty-qwerty-123"
                    ).populate(),
                    alias.captured
                )
            }

            @Test
            fun `alias gets auto-populated with old userId`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.identify("oldId")
                analytics.alias("newId")
                val alias = slot<AliasEvent>()
                verify { mockPlugin.alias(capture(alias)) }
                assertEquals(
                    AliasEvent(
                        userId = "newId",
                        previousId = "oldId"
                    ).populate(),
                    alias.captured
                )
            }

            @Test
            fun `alias event modifies underlying userId`() = runTest  {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.identify("oldId")
                analytics.alias("newId")
                val newUserInfo = analytics.store.currentState(UserInfo::class)
                assertEquals(
                    UserInfo(
                        userId = "newId",
                        traits = emptyJsonObject,
                        anonymousId = "qwerty-qwerty-123"
                    ), newUserInfo
                )
            }
        }

        @Nested
        inner class Reset {
            @Test
            fun `reset() overwrites userId and traits also resets event plugin`() = runTest  {
                val plugin = spyk(StubPlugin())
                analytics.add(plugin)

                analytics.identify("oldUserId",
                        buildJsonObject { put("behaviour", "bad") })
                assertEquals(analytics.userIdAsync(), "oldUserId")
                assertEquals(analytics.traitsAsync(), buildJsonObject { put("behaviour", "bad") })

                analytics.reset()
                assertEquals(analytics.userIdAsync(), null)
                assertEquals(analytics.traitsAsync(), null)
                verify { plugin.reset() }
            }
        }

        @Nested
        inner class Find {
            @Test
            fun `find() finds the exact plugin`() {
                val expected = StubPlugin()
                analytics.add(expected)

                val actual = analytics.find(StubPlugin::class)

                assertEquals(expected, actual)
            }

            @Test
            fun `find() finds plugin through parent type`() {
                val expected = object: StubPlugin() {}
                analytics.add(expected)

                val actual = analytics.find(StubPlugin::class)

                assertEquals(expected, actual)
            }

            @Test
            fun `findAll() finds exact and sub plugin`() {
                val parent = StubPlugin()
                val child = object: StubPlugin() {}
                analytics.add(parent)
                analytics.add(child)

                val plugins = analytics.findAll(StubPlugin::class)

                assertEquals(plugins.size, 2)
                assertTrue(plugins.contains(parent))
                assertTrue(plugins.contains(child))
            }

            @Test
            fun `find() finds destination plugin by name`() {
                val dest = "testPlugin"
                val expected = object: DestinationPlugin() {
                    override val key = dest
                }
                analytics.add(expected)

                val actual = analytics.find(dest)

                assertEquals(expected, actual)
            }
        }
    }

    @Nested
    inner class AnonymousId {
        @Test
        fun `anonymousId fetches current Analytics anonymousId`() = runTest {
            assertEquals("qwerty-qwerty-123", analytics.anonymousIdAsync())
        }
    }

    @Test
    fun `settings fetches current Analytics Settings`() = runTest {
        val settings = Settings(
            integrations = buildJsonObject {
                put("int1", true)
                put("int2", false)
            },
            plan = emptyJsonObject,
            edgeFunction = emptyJsonObject,
            middlewareSettings = emptyJsonObject
        )
        analytics.store.dispatch(System.UpdateSettingsAction(settings), System::class)
        assertEquals(settings, analytics.settings())
    }

    @Test
    fun `version fetches current Analytics version`() {
        assertEquals(Constants.LIBRARY_VERSION, analytics.version())
    }

    @Test
    fun `disable analytics prevents event being processed`() {
        val segmentDestination = spyk(SegmentDestination())
        analytics.add(segmentDestination)
        val state = mutableListOf<System>()

        analytics.enabled = false
        analytics.track("test")

        verify(exactly = 0) {
            segmentDestination.track(any())
            segmentDestination.execute(any())
        }
        verify { segmentDestination.onEnableToggled(capture(state)) }
        assertEquals(false, state[1].enabled)

        analytics.enabled = true
        analytics.track("test")
        verify(exactly = 1) {
            segmentDestination.track(any())
            segmentDestination.execute(any())
        }
        verify { segmentDestination.onEnableToggled(capture(state)) }
        assertEquals(true, state[2].enabled)
    }

    private fun BaseEvent.populate() = apply {
        anonymousId = "qwerty-qwerty-123"
        messageId = "qwerty-qwerty-123"
        timestamp = epochTimestamp
        context = baseContext
        integrations = emptyJsonObject
    }
}