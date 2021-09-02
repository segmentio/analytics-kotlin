package com.segment.analytics.kotlin.core.compat

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utils.StubPlugin
import com.segment.analytics.kotlin.core.utils.TestRunPlugin
import io.mockk.CapturingSlot
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.function.Consumer

internal class JavaAnalyticsTest {
    private lateinit var analytics: JavaAnalytics
    private lateinit var mockPlugin: StubPlugin

    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope(testDispatcher)

    @BeforeEach
    fun setup() {
        val config = ConfigurationBuilder("123")
            .setAnalyticsScope(testScope)
            .setIODispatcher(testDispatcher)
            .setAnalyticsDispatcher(testDispatcher)
            .setApplication("Test")
            .setAutoAddSegmentDestination(false)
            .build()

        analytics = JavaAnalytics(config)
        mockPlugin = spyk(StubPlugin())
    }

    @Nested
    inner class Track {

        private val event = "track"

        private val json = buildJsonObject { put("foo", "bar") }

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
                TrackEvent(emptyJsonObject, event),
                track.captured
            )
        }

        @Test
        fun `track with name and json`() {
            analytics.add(mockPlugin)
            analytics.track(event, json)

            verify { mockPlugin.track(capture(track)) }
            assertEquals(
                TrackEvent(json, event),
                track.captured
            )
        }

        @Test
        fun `track with name and serializable`() {
            analytics.add(mockPlugin)
            analytics.track(event, serializable)

            verify { mockPlugin.track(capture(track)) }
            assertEquals(
                TrackEvent(json, event),
                track.captured
            )
        }
    }

    @Nested
    inner class Identify {

        private val userId = "foobar"

        private val json = buildJsonObject { put("name", "bar") }

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
                IdentifyEvent(userId, emptyJsonObject),
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
                IdentifyEvent(userId, json),
                identify.captured
            )
        }

        @Test
        fun `identify with userId and serializable`() {
            analytics.add(mockPlugin)
            analytics.identify(userId, serializable)

            verify { mockPlugin.identify(capture(identify)) }
            assertEquals(
                IdentifyEvent(userId, json),
                identify.captured
            )
        }
    }

    @Nested
    inner class Screen {

        private val title = "main"

        private val category = "mobile"

        private val json = buildJsonObject { put("foo", "bar") }

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
                ScreenEvent(title, category, emptyJsonObject),
                screen.captured
            )
        }

        @Test
        fun `screen with title category and json`() {
            analytics.add(mockPlugin)
            analytics.screen(title, json, category)

            verify { mockPlugin.screen(capture(screen)) }
            assertEquals(
                ScreenEvent(title, category, json),
                screen.captured
            )
        }

        @Test
        fun `screen with title category and serializable`() {
            analytics.add(mockPlugin)
            analytics.screen(title, serializable, category)

            verify { mockPlugin.screen(capture(screen)) }
            assertEquals(
                ScreenEvent(title, category, json),
                screen.captured
            )
        }
    }

    @Nested
    inner class Group {

        private val groupId = "high school"

        private val json = buildJsonObject { put("foo", "bar") }

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
                GroupEvent(groupId, emptyJsonObject),
                group.captured
            )
        }

        @Test
        fun `group with groupId and json`() {
            analytics.add(mockPlugin)
            analytics.group(groupId, json)

            verify { mockPlugin.group(capture(group)) }
            assertEquals(
                GroupEvent(groupId, json),
                group.captured
            )
        }

        @Test
        fun `group with groupId and serializable`() {
            analytics.add(mockPlugin)
            analytics.group(groupId, serializable)

            verify { mockPlugin.group(capture(group)) }
            assertEquals(
                GroupEvent(groupId, json),
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
                AliasEvent(newId, previousId),
                alias.captured
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

    @Test
    fun process() {
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
}