package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.Timeline
import com.segment.analytics.kotlin.core.utils.mockAnalytics
import io.mockk.spyk
import io.mockk.verify
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PluginTests {

    private val mockAnalytics = mockAnalytics()
    private val timeline: Timeline

    init {
        timeline = Timeline().also { it.analytics = mockAnalytics }
    }

    @Test
    fun `setup is called`() {
        val plugin = object : Plugin {
            override val type: Plugin.Type = Plugin.Type.Before
            override lateinit var analytics: Analytics
        }
        val spy = spyk(plugin)
        timeline.add(spy)
        verify(exactly = 1) { spy.setup(mockAnalytics) }
        Assertions.assertTrue(spy.analytics === mockAnalytics)
    }

    @Test
    fun `plugin processes the event correctly`() {
        val plugin = spyk(object : Plugin {
            override val type: Plugin.Type = Plugin.Type.Before
            override lateinit var analytics: Analytics
        })
        timeline.add(plugin)
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
        Assertions.assertEquals(trackEvent, result)
        verify(exactly = 1) { plugin.execute(trackEvent) }
    }
}