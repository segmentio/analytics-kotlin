package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.Timeline
import com.segment.analytics.kotlin.core.utilities.putInContext
import com.segment.analytics.kotlin.core.utils.mockAnalytics
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.Date

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DestinationPluginTests {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockAnalytics = mockAnalytics(testScope, testDispatcher)
    private val timeline: Timeline

    init {
        timeline = Timeline().also { it.analytics = mockAnalytics }
    }

    @Test
    fun `setup is called`() {
        val destinationPlugin = object: DestinationPlugin() {
            override val key: String = "TestDestination"
        }
        val spy = spyk(destinationPlugin)
        timeline.add(spy)
        verify(exactly = 1) { spy.setup(mockAnalytics) }
        assertTrue(spy.analytics === mockAnalytics)
    }

    @Test
    fun `add will append plugin and set it up`() {
        val destinationPlugin = spyk(object: DestinationPlugin() {
            override val key: String = "TestDestination"
        })
        timeline.add(destinationPlugin)
        val beforePlugin = spyk(object: Plugin {
            override lateinit var analytics: Analytics
            override val type: Plugin.Type = Plugin.Type.Before
        })
        destinationPlugin.add(beforePlugin)
        verify(exactly = 1) { beforePlugin.setup(mockAnalytics) }
        assertTrue(mockAnalytics === beforePlugin.analytics)
    }

    @Test
    fun `remove will delete plugin`() {
        val destinationPlugin = spyk(object: DestinationPlugin() {
            override val key: String = "TestDestination"
        })
        timeline.add(destinationPlugin)
        val beforePlugin = spyk(object: Plugin {
            override lateinit var analytics: Analytics
            override val type: Plugin.Type = Plugin.Type.Before
        })
        destinationPlugin.add(beforePlugin)
        destinationPlugin.remove(beforePlugin)
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = Date(0).toInstant().toString()
            }
        val result = timeline.process(trackEvent)
        assertEquals(trackEvent, result)
        verify(exactly = 0) { beforePlugin.execute(trackEvent) }
    }

    @Test
    fun `execute runs the destination timeline`() {
        val destinationPlugin = spyk(object : DestinationPlugin() {
            override val key: String = "TestDestination"

            init {
                enabled = true
            }

            override fun track(payload: TrackEvent): BaseEvent? {
                return payload.putInContext("processedDestination", true)
            }
        })
        timeline.add(destinationPlugin)

        val beforePlugin = spyk(object: Plugin {
            override lateinit var analytics: Analytics
            override val type: Plugin.Type = Plugin.Type.Before
            override fun execute(event: BaseEvent): BaseEvent? {
                return event.putInContext("processedBefore", true)
            }
        })
        val enrichmentPlugin = spyk(object: Plugin {
            override lateinit var analytics: Analytics
            override val type: Plugin.Type = Plugin.Type.Enrichment
            override fun execute(event: BaseEvent): BaseEvent? {
                return event.putInContext("processedEnrichment", true)
            }
        })
        val afterPlugin = spyk(object: Plugin {
            override lateinit var analytics: Analytics
            override val type: Plugin.Type = Plugin.Type.After
            override fun execute(event: BaseEvent): BaseEvent? {
                return event.putInContext("processedAfter", true)
            }
        })
        destinationPlugin.add(beforePlugin)
        destinationPlugin.add(enrichmentPlugin)
        destinationPlugin.add(afterPlugin)

        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = Date(0).toInstant().toString()
            }

        val expected = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = buildJsonObject {
                    put("processedBefore", true)
                    put("processedEnrichment", true)
                    put("processedDestination", true)
                    put("processedAfter", true)
                }
                timestamp = Date(0).toInstant().toString()
            }

        val result = timeline.process(trackEvent)

        assertEquals(expected, result)
    }

    @Test
    fun `destination processing is skipped when disabled via settings`() {
        val destinationPlugin = spyk(object: DestinationPlugin() {
            override val key: String = "TestDestination"
        })
        // Disable destination via settings
        destinationPlugin.enabled = false
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = Date(0).toInstant().toString()
            }
        val result = destinationPlugin.process(trackEvent)
        assertEquals(null, result)
    }
}