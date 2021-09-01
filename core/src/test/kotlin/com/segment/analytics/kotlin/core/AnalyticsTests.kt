package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.ContextPlugin
import com.segment.analytics.kotlin.core.utils.StubPlugin
import com.segment.analytics.kotlin.core.utils.TestRunPlugin
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import io.mockk.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsTests {
    private lateinit var analytics: Analytics

    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope(testDispatcher)

    private val epochTimestamp = Date(0).toInstant().toString()
    private val context = buildJsonObject {
        val lib = buildJsonObject {
            put(ContextPlugin.LIBRARY_NAME_KEY, "analytics-kotlin")
            put(ContextPlugin.LIBRARY_VERSION_KEY, Constants.LIBRARY_VERSION)
        }
        put(ContextPlugin.LIBRARY_KEY, lib)
    }

    init {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
    }

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        analytics = Analytics(
            Configuration(
                writeKey = "123",
                analyticsScope = testScope,
                ioDispatcher = testDispatcher,
                analyticsDispatcher = testDispatcher,
                application = "Test"
            )
        )
        analytics.configuration.autoAddSegmentDestination = false
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
        fun `adding destination plugin modifies integrations object`() {
            val testPlugin1 = object : DestinationPlugin() {
                override val key: String = "TestDestination"
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
                override val key: String = "TestDestination"
            }
            analytics.add(testPlugin1)
            analytics.remove(testPlugin1)

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
                assertEquals(epochTimestamp, it.timestamp)
                assertEquals(context, it.context)
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
                assertEquals(context, it.context)
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
                assertEquals(it.context, context)
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
    }
}