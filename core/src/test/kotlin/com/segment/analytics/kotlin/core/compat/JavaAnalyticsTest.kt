package com.segment.analytics.kotlin.core.compat

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.ContextPlugin
import com.segment.analytics.kotlin.core.platform.plugins.SegmentDestination
import com.segment.analytics.kotlin.core.utilities.SegmentInstant
import com.segment.analytics.kotlin.core.utils.StubPlugin
import com.segment.analytics.kotlin.core.utils.TestRunPlugin
import com.segment.analytics.kotlin.core.utils.mockHTTPClient
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*
import java.util.function.Consumer

internal class JavaAnalyticsTest {
    private lateinit var analytics: JavaAnalytics
    private lateinit var mockPlugin: StubPlugin

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
        mockkObject(SegmentInstant)
        every { SegmentInstant.now() } returns Date(0).toInstant().toString()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
        mockHTTPClient()
    }

    @BeforeEach
    fun setup() {
        val config = ConfigurationBuilder("java-123")
            .setApplication("Test")
            .setAutoAddSegmentDestination(false)
            .build()

        analytics = JavaAnalytics(
            analytics = testAnalytics(config, testScope, testDispatcher)
        )
        mockPlugin = spyk(StubPlugin())
    }

    @Test
    fun `secondary constructor properly setup analytics`() {
        val actual = JavaAnalytics(analytics.analytics)
        assertNotNull(actual.analytics)
        assertEquals(analytics.analytics, actual.analytics)
    }

    @Nested
    inner class Track {

        private val event = "track"

        private val json = buildJsonObject { put("foo", "bar") }

        private val map = mutableMapOf<String, Any>("foo" to "bar")

        private lateinit var track: CapturingSlot<TrackEvent>

        private val serializable = object : JsonSerializable {
            override fun serialize(): JsonObject {
                return json
            }
        }

        @BeforeEach
        internal fun setUp() {
            track = slot()
        }

        @Test
        fun `track with name`() {
            analytics.add(mockPlugin)
            analytics.track(event)

            verify { mockPlugin.track(capture(track)) }
            assertEquals(
                TrackEvent(emptyJsonObject, event).populate(),
                track.captured
            )
        }

        @Test
        fun `track with name and json`() {
            analytics.add(mockPlugin)
            analytics.track(event, json)

            verify { mockPlugin.track(capture(track)) }
            assertEquals(
                TrackEvent(json, event).populate(),
                track.captured
            )
        }

        @Test
        fun `track with name and map`() {
            analytics.add(mockPlugin)
            analytics.track(event, map)

            verify { mockPlugin.track(capture(track)) }
            assertEquals(
                TrackEvent(json, event).populate(),
                track.captured
            )
        }


        @Test
        fun `track with name and serializable`() {
            analytics.add(mockPlugin)
            analytics.track(event, serializable)

            verify { mockPlugin.track(capture(track)) }
            assertEquals(
                TrackEvent(json, event).populate(),
                track.captured
            )
        }
    }

    @Nested
    inner class Screen {

        private val title = "main"

        private val category = "mobile"

        private val json = buildJsonObject { put("foo", "bar") }

        private val map = mutableMapOf<String, Any>("foo" to "bar")

        private lateinit var screen: CapturingSlot<ScreenEvent>

        private val serializable = object : JsonSerializable {
            override fun serialize(): JsonObject {
                return json
            }
        }

        @BeforeEach
        internal fun setUp() {
            screen = slot()
        }

        @Test
        fun `screen with title and category`() {
            analytics.add(mockPlugin)
            analytics.screen(title, category = category)

            verify { mockPlugin.screen(capture(screen)) }
            assertEquals(
                ScreenEvent(title, category, emptyJsonObject).populate(),
                screen.captured
            )
        }

        @Test
        fun `screen with title category and json`() {
            analytics.add(mockPlugin)
            analytics.screen(title, json, category)

            verify { mockPlugin.screen(capture(screen)) }
            assertEquals(
                ScreenEvent(title, category, json).populate(),
                screen.captured
            )
        }

        @Test
        fun `screen with title category and map`() {
            analytics.add(mockPlugin)
            analytics.screen(title, map, category)

            verify { mockPlugin.screen(capture(screen)) }
            assertEquals(
                ScreenEvent(title, category, json).populate(),
                screen.captured
            )
        }

        @Test
        fun `screen with title category and serializable`() {
            analytics.add(mockPlugin)
            analytics.screen(title, serializable, category)

            verify { mockPlugin.screen(capture(screen)) }
            assertEquals(
                ScreenEvent(title, category, json).populate(),
                screen.captured
            )
        }
    }

    @Nested
    inner class Group {

        private val groupId = "high school"

        private val json = buildJsonObject { put("foo", "bar") }

        private val map = mutableMapOf<String, Any>("foo" to "bar")

        private lateinit var group: CapturingSlot<GroupEvent>

        private val serializable = object : JsonSerializable {
            override fun serialize(): JsonObject {
                return json
            }
        }

        @BeforeEach
        internal fun setUp() {
            group = slot()
        }

        @Test
        fun `group with groupId`() {
            analytics.add(mockPlugin)
            analytics.group(groupId)

            verify { mockPlugin.group(capture(group)) }
            assertEquals(
                GroupEvent(groupId, emptyJsonObject).populate(),
                group.captured
            )
        }

        @Test
        fun `group with groupId and json`() {
            analytics.add(mockPlugin)
            analytics.group(groupId, json)

            verify { mockPlugin.group(capture(group)) }
            assertEquals(
                GroupEvent(groupId, json).populate(),
                group.captured
            )
        }

        @Test
        fun `group with groupId and map`() {
            analytics.add(mockPlugin)
            analytics.group(groupId, map)

            verify { mockPlugin.group(capture(group)) }
            assertEquals(
                GroupEvent(groupId, json).populate(),
                group.captured
            )
        }

        @Test
        fun `group with groupId and serializable`() {
            analytics.add(mockPlugin)
            analytics.group(groupId, serializable)

            verify { mockPlugin.group(capture(group)) }
            assertEquals(
                GroupEvent(groupId, json).populate(),
                group.captured
            )
        }
    }

    @Nested
    inner class Alias {

        private val newId = "newId"

        private val previousId = "qwerty-qwerty-123"

        private lateinit var alias: CapturingSlot<AliasEvent>

        @BeforeEach
        internal fun setUp() {
            alias = slot()
        }

        @Test
        fun alias() {
            analytics.add(mockPlugin)
            analytics.identify(previousId)
            analytics.alias(newId)

            verify { mockPlugin.alias(capture(alias)) }
            assertEquals(
                AliasEvent(newId, previousId).populate().apply {
                    userId = "newId"
                },
                alias.captured
            )
        }
    }

    @Nested
    inner class Identify {

        private val userId = "foobar"

        private val json = buildJsonObject { put("name", "bar") }

        private val map = mutableMapOf<String, Any>("name" to "bar")

        private lateinit var identify: CapturingSlot<IdentifyEvent>

        private val serializable = object : JsonSerializable {
            override fun serialize(): JsonObject {
                return json
            }
        }

        @BeforeEach
        internal fun setUp() {
            identify = slot()
        }

        @Test
        fun `identify with userId`() {
            analytics.add(mockPlugin)
            analytics.identify(userId)

            verify { mockPlugin.identify(capture(identify)) }
            assertEquals(
                IdentifyEvent(userId, emptyJsonObject).populate().apply {
                    userId = this@Identify.userId
                },
                identify.captured
            )
        }

        @Test
        fun `identify with userId and json`() {
            analytics.add(mockPlugin)
            analytics.identify(userId, json)

            val identify = slot<IdentifyEvent>()
            verify { mockPlugin.identify(capture(identify)) }
            assertEquals(
                IdentifyEvent(userId, json).populate().apply {
                    userId = this@Identify.userId
                },
                identify.captured
            )
        }

        @Test
        fun `identify with userId and map`() {
            analytics.add(mockPlugin)
            analytics.identify(userId, map)

            val identify = slot<IdentifyEvent>()
            verify { mockPlugin.identify(capture(identify)) }
            assertEquals(
                IdentifyEvent(userId, json).populate().apply {
                    userId = this@Identify.userId
                },
                identify.captured
            )
        }

        @Test
        fun `identify with userId and serializable`() {
            analytics.add(mockPlugin)
            analytics.identify(userId, serializable)

            verify { mockPlugin.identify(capture(identify)) }
            assertEquals(
                IdentifyEvent(userId, json).populate().apply {
                    userId = this@Identify.userId
                },
                identify.captured
            )
        }

        @Test
        fun `identify with only serializable traits`() = runTest {
            analytics.store.dispatch(UserInfo.SetUserIdAction(userId), UserInfo::class)

            analytics.add(mockPlugin)
            analytics.identify(serializable)

            verify { mockPlugin.identify(capture(identify)) }
            assertEquals(
                IdentifyEvent("", json).populate().apply {
                    userId = this@Identify.userId
                },
                identify.captured
            )
        }

        @Test
        fun `identify with only json traits`() = runTest {
            analytics.store.dispatch(UserInfo.SetUserIdAction(userId), UserInfo::class)

            analytics.add(mockPlugin)
            analytics.identify(json)

            val identify = slot<IdentifyEvent>()
            verify { mockPlugin.identify(capture(identify)) }
            assertEquals(
                IdentifyEvent("", json).populate().apply {
                    userId = this@Identify.userId
                },
                identify.captured
            )
        }

        @Test
        fun `identify with only map traits`() = runTest {
            analytics.store.dispatch(UserInfo.SetUserIdAction(userId), UserInfo::class)

            analytics.add(mockPlugin)
            analytics.identify(map)

            val identify = slot<IdentifyEvent>()
            verify { mockPlugin.identify(capture(identify)) }
            assertEquals(
                IdentifyEvent("", json).populate().apply {
                    userId = this@Identify.userId
                },
                identify.captured
            )
        }
    }


    @Nested
    inner class PluginTests {

        private lateinit var middleware: Plugin

        @BeforeEach
        internal fun setUp() {
            middleware = object : Plugin {
                override val type = Plugin.Type.Utility
                override lateinit var analytics: Analytics
            }
        }

        @Test
        fun add() {
            analytics.add(middleware)
            analytics.analytics.timeline.plugins[Plugin.Type.Utility]?.plugins?.let {
                assertEquals(
                    1,
                    it.size
                )
            } ?: fail()
        }

        @Test
        fun find() {
            analytics.add(middleware)
            assertEquals(middleware, analytics.find(middleware.javaClass))
        }

        @Test
        fun remove() {
            analytics.add(middleware)
            analytics.remove(middleware)
            analytics.analytics.timeline.plugins[Plugin.Type.Utility]?.plugins?.let {
                assertEquals(
                    0,
                    it.size
                )
            } ?: fail()
        }

        @Test
        fun `apply closure to plugins with lambda`() {
            val closure = spyk<(Plugin) -> Unit>()

            analytics.add(middleware)
            analytics.applyClosureToPlugins(closure)

            verify { closure.invoke(middleware) }
        }

        @Test
        fun `apply closure plugins with consumer`() {
            val closure = spyk<Consumer<Plugin>>()

            analytics.add(middleware)
            analytics.applyClosureToPlugins(closure)

            verify { closure.accept(middleware) }
        }
    }

    @Nested
    inner class Reset {
        @Test
        fun `reset() overwrites userId and traits also resets event plugin`() {
            val plugin = spyk(StubPlugin())
            analytics.add(plugin)

            analytics.identify("oldUserId",
                buildJsonObject { put("behaviour", "bad") })
            assertEquals(analytics.userId(), "oldUserId")
            assertEquals(analytics.traits(), buildJsonObject { put("behaviour", "bad") })

            analytics.reset()
            assertEquals(analytics.userId(), null)
            assertEquals(analytics.traits(), null)
            assertEquals(analytics.storage.read(Storage.Constants.UserId), null)
            assertEquals(analytics.storage.read(Storage.Constants.Traits), null)
            verify { plugin.reset() }
        }
    }

    @Test
    fun process() {
        val testPlugin1 = spyk(TestRunPlugin {})
        val testPlugin2 = spyk(TestRunPlugin {})
        analytics
            .add(testPlugin1)
            .add(testPlugin2)
            .process(TrackEvent(event = "track", properties = emptyJsonObject))
        verify { testPlugin1.updateState(true) }
        verify { testPlugin2.updateState(true) }
    }

    @Test
    fun flush() {
        val plugin = spyk<DestinationPlugin>()

        analytics.add(plugin)
        analytics.flush()

        verify { plugin.flush() }
    }

    @Test
    fun userId() {
        analytics.identify("userId")
        assertEquals("userId", analytics.userId())
    }

    @Test
    fun traits() {
        val json = buildJsonObject { put("name", "bar") }
        analytics.identify("userId", json)
        assertEquals(json, analytics.traits())
    }

    @Test
    fun settings() = runTest {
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
    fun anonymousId() {
        assertEquals("qwerty-qwerty-123", analytics.anonymousId())
    }

    @Test
    fun version() {
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
        assertEquals(false, state.last().enabled)

        analytics.enabled = true
        analytics.track("test")
        verify(exactly = 1) {
            segmentDestination.track(any())
            segmentDestination.execute(any())
        }
        verify { segmentDestination.onEnableToggled(capture(state)) }
        assertEquals(true, state.last().enabled)
    }

    private fun BaseEvent.populate() = apply {
        anonymousId = "qwerty-qwerty-123"
        messageId = "qwerty-qwerty-123"
        timestamp = epochTimestamp
        context = baseContext
        integrations = emptyJsonObject
        userId = "userId"
    }
}