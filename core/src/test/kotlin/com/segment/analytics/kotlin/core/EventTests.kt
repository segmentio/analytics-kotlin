package com.segment.analytics.kotlin.core

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.lang.ClassCastException
import java.util.Date

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventTests {
    init {
        Telemetry.enable = false
    }
    private val epochTimestamp = Date(0).toInstant().toString()
    private val defaultContext = buildJsonObject {
        put("key1", "value")
        put("key2", true)
    }
    private val defaultIntegrations = buildJsonObject {
        put("Segment.io", false)
        put("Mixpanel", true)
    }

    @Test
    fun `track event can be shallow copied`() {
        val trackEvent = TrackEvent(
            event = "Defeated",
            properties = buildJsonObject { put("enemy", "Joker") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = defaultIntegrations
                context = defaultContext
                timestamp = epochTimestamp
                userId = "batman"
            }
        val newEvent: TrackEvent = trackEvent.copy()
        assertNotSame(newEvent, trackEvent)
        assertTrue(newEvent == trackEvent)
    }

    @Test
    fun `identify event can be shallow copied`() {
        val identifyEvent = IdentifyEvent(
            userId = "batman",
            traits = buildJsonObject { put("softie", "false") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = defaultIntegrations
                context = defaultContext
                timestamp = epochTimestamp
            }
        val newEvent: IdentifyEvent = identifyEvent.copy()
        assertNotSame(newEvent, identifyEvent)
        assertEquals(newEvent, identifyEvent)
    }

    @Test
    fun `screen event can be shallow copied`() {
        val screenEvent = ScreenEvent(
            name = "Superhero Entrance",
            category = "Entrance",
            properties = buildJsonObject { put("applause", true) })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = defaultIntegrations
                context = defaultContext
                timestamp = epochTimestamp
                userId = "batman"
            }
        val newEvent: ScreenEvent = screenEvent.copy()
        assertNotSame(newEvent, screenEvent)
        assertEquals(newEvent, screenEvent)
    }

    @Test
    fun `group event can be shallow copied`() {
        val groupEvent = GroupEvent(
            groupId = "justice_league",
            traits = buildJsonObject { put("badass", true) })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = defaultIntegrations
                context = defaultContext
                timestamp = epochTimestamp
                userId = "batman"
            }
        val newEvent: GroupEvent = groupEvent.copy()
        assertNotSame(newEvent, groupEvent)
        assertEquals(newEvent, groupEvent)
    }

    @Test
    fun `alias event can be shallow copied`() {
        val aliasEvent = AliasEvent(
            previousId = "batman",
            userId = "bruce_wayne"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = defaultIntegrations
            context = defaultContext
            timestamp = epochTimestamp
        }
        val newEvent: AliasEvent = aliasEvent.copy()
        assertNotSame(newEvent, aliasEvent)
        assertEquals(newEvent, aliasEvent)
    }

    @Test
    fun `copy as another type fails`() {
        val aliasEvent = AliasEvent(
            previousId = "batman",
            userId = "bruce_wayne"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = defaultIntegrations
            context = defaultContext
            timestamp = epochTimestamp
        }
        assertThrows<ClassCastException> {
            val track = aliasEvent.copy<TrackEvent>() // Copy as a trackEvent should throw an error
        }
    }
}