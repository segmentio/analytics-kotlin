package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.utils.mockAnalytics
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.Timeline
import io.mockk.spyk
import io.mockk.verify
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DestinationPluginTests {
    private val mockAnalytics = mockAnalytics()
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
        val destinationPlugin = spyk(object: DestinationPlugin() {
            override val key: String = "TestDestination"
        })
        timeline.add(destinationPlugin)
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
        val beforePlugin = spyk(object: Plugin {
            override lateinit var analytics: Analytics
            override val type: Plugin.Type = Plugin.Type.Before
        })
        val enrichmentPlugin = spyk(object: Plugin {
            override lateinit var analytics: Analytics
            override val type: Plugin.Type = Plugin.Type.Enrichment
        })
        val afterPlugin = spyk(object: Plugin {
            override lateinit var analytics: Analytics
            override val type: Plugin.Type = Plugin.Type.After
        })
        destinationPlugin.add(beforePlugin)
        destinationPlugin.add(enrichmentPlugin)
        destinationPlugin.add(afterPlugin)
        val result = timeline.process(trackEvent)
        assertEquals(trackEvent, result)
        verify(exactly = 1) { beforePlugin.execute(trackEvent) }
        verify(exactly = 1) { enrichmentPlugin.execute(trackEvent) }
        verify(exactly = 1) { afterPlugin.execute(trackEvent) }
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