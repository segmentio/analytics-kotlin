package com.segment.analytics.main

import android.content.Context
import com.segment.analytics.*
import com.segment.analytics.main.utils.StubPlugin
import com.segment.analytics.main.utils.TestRunPlugin
import com.segment.analytics.main.utils.mockContext
import com.segment.analytics.platform.DestinationPlugin
import com.segment.analytics.platform.Plugin
import io.mockk.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sovran.kotlin.Action
import java.time.Instant
import java.util.UUID
import java.util.Date

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsTest {
    private var mockContext: Context = mockContext()
    private lateinit var analytics: Analytics

    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope(testDispatcher)

    private val epochTimestamp = Date(0).toInstant().toString()

    init {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
    }

    @BeforeEach
    fun setup() {
        mockContext.getSharedPreferences("", 12).edit().clear()
        analytics = Analytics(
            Configuration(
                writeKey = "123",
                analyticsScope = testScope,
                ioDispatcher = testDispatcher,
                analyticsDispatcher = testDispatcher,
                application = mockContext
            )
        )
        analytics.configuration.autoAddSegmentDestination = false
    }

    @Nested
    inner class PluginTests {

        @Test
        fun `Can add plugins to analytics`() {
            val middleware = object : Plugin {
                override val type: Plugin.Type
                    get() = Plugin.Type.Utility
                override val name: String
                    get() = "middlewarePlugin"
                override lateinit var analytics: Analytics
            }
            analytics.add(middleware)
            analytics.timeline.plugins[Plugin.Type.Utility]?.plugins?.let {
                assertEquals(
                    1,
                    it.size
                )
            } ?: fail()
        }

        @Test
        fun `Can remove plugins from analytics`() {
            val middleware = object : Plugin {
                override val type: Plugin.Type
                    get() = Plugin.Type.Utility
                override val name: String
                    get() = "middlewarePlugin"
                override lateinit var analytics: Analytics
            }
            analytics.add(middleware)
            analytics.remove("middlewarePlugin")
            analytics.timeline.plugins[Plugin.Type.Utility]?.plugins?.let {
                assertEquals(
                    0,
                    it.size
                )
            } ?: fail()
        }

        @Test
        fun `event runs through chain of plugins`() {
            val testPlugin1 = TestRunPlugin("plugin1") {}
            val testPlugin2 = TestRunPlugin("plugin1") {}
            analytics
                .add(testPlugin1)
                .add(testPlugin2)
                .process(TrackEvent(event = "track", properties = emptyJsonObject))
            assertTrue(testPlugin1.ran)
            assertTrue(testPlugin2.ran)
        }

        @Test
        fun `adding destination plugin modifies integrations object`() {
            val testPlugin1 = object : DestinationPlugin() {
                override val name: String = "TestDestination"
            }
            analytics
                .add(testPlugin1)

            val system = analytics.store.currentState(System::class)
            val curIntegrations = system?.integrations
            assertEquals(
                buildJsonObject {
                    put("TestDestination", false)
                },
                curIntegrations
            )
        }

        @Test
        fun `removing destination plugin modifies integrations object`() {
            val testPlugin1 = object : DestinationPlugin() {
                override val name: String = "TestDestination"
            }
            analytics.add(testPlugin1)
            analytics.remove(testPlugin1.name)

            val system = analytics.store.currentState(System::class)
            val curIntegrations = system?.integrations
            assertEquals(
                emptyJsonObject,
                curIntegrations
            )
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
                assertTrue(it.timestamp == epochTimestamp)
                assertTrue(it.context == emptyJsonObject)
                assertTrue(it.integrations == emptyJsonObject)
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
                assertEquals(emptyJsonObject, it.context)
            }
        }

        @Test
        fun `event gets populated with correct integrations`() {
            val mockPlugin = spyk(StubPlugin())
            analytics.add(mockPlugin)
            analytics.store.dispatch(System.AddIntegrationAction("plugin1"), System::class)
            analytics.track("track", buildJsonObject { put("foo", "bar") })
            val track = slot<TrackEvent>()
            verify { mockPlugin.track(capture(track)) }
            track.captured.let {
                assertTrue(it.anonymousId.isNotBlank())
                assertTrue(it.messageId.isNotBlank())
                assertTrue(it.timestamp == epochTimestamp)
                assertEquals(emptyJsonObject, it.context)
                assertEquals(buildJsonObject { put("plugin1", false) }, it.integrations)
            }
        }

        @Nested
        inner class Track {
            @Test
            fun `track event runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.track("track", buildJsonObject { put("foo", "bar") })
                val track = slot<TrackEvent>()
                verify { mockPlugin.track(capture(track)) }
                assertEquals(
                    TrackEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        event = "track"
                    ),
                    track.captured
                )
            }
        }

        @Nested
        inner class Identify {
            @Test
            fun `identify event runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)

                analytics.identify("foobar", buildJsonObject { put("name", "bar") })
                val identify = slot<IdentifyEvent>()
                verify { mockPlugin.identify(capture(identify)) }
                assertEquals(
                    IdentifyEvent(
                        traits = buildJsonObject { put("name", "bar") },
                        userId = "foobar"
                    ),
                    identify.captured
                )
            }

            @Test
            fun `identify() overwrites userId and traits`() {
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
        }

        @Nested
        inner class Screen {
            @Test
            fun `screen event runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.screen(
                    screenTitle = "main",
                    category = "mobile",
                    properties = buildJsonObject { put("foo", "bar") })
                val screen = slot<ScreenEvent>()
                verify { mockPlugin.screen(capture(screen)) }
                assertEquals(
                    ScreenEvent(
                        properties = buildJsonObject { put("foo", "bar") },
                        name = "main",
                        category = "mobile"
                    ),
                    screen.captured
                )
            }
        }

        @Nested
        inner class Group {
            @Test
            fun `group event runs through analytics`() {
                val mockPlugin = spyk(StubPlugin())
                analytics.add(mockPlugin)
                analytics.group("high school", buildJsonObject { put("foo", "bar") })
                val group = slot<GroupEvent>()
                verify { mockPlugin.group(capture(group)) }
                assertEquals(
                    GroupEvent(
                        traits = buildJsonObject { put("foo", "bar") },
                        groupId = "high school"
                    ),
                    group.captured
                )
            }
        }

        /*
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
                        previousId = ""
                    ),
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
                    ),
                    alias.captured
                )
            }

            @Test
            fun `alias event modifies underlying userId`() {
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
        */
    }
}