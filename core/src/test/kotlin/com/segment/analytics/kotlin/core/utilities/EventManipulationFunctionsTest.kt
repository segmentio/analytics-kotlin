package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class EventManipulationFunctionsTest {

    @Test
    fun `enableIntegration enables integration when not exist`() {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
            }

        trackEvent.enableIntegration("test")

        assertEquals(true, trackEvent.integrations.getBoolean("test"))
    }

    @Test
    fun `enableIntegration enables integration when disable`() {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = buildJsonObject { put("test", false) }
                context = emptyJsonObject
            }

        trackEvent.enableIntegration("test")

        assertEquals(true, trackEvent.integrations.getBoolean("test"))
    }

    @Test
    fun `enableIntegration enables integration when non-boolean`() {
        val jsonObject = buildJsonObject { put("key", "value") }
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = buildJsonObject { put("test", jsonObject) }
                context = emptyJsonObject
            }

        trackEvent.enableIntegration("test")

        assertEquals(jsonObject, trackEvent.integrations["test"])
    }

    @Test
    fun `disableCloudIntegrations with except list`() {
        val jsonObject = buildJsonObject { put("key", "value") }
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = buildJsonObject {
                    put("discard1", true)
                    put("discard2", false)
                    put("discard3", "string")
                    put("discard4", jsonObject)
                    put("keep1", true)
                    put("keep2", false)
                    put("keep3", "string")
                    put("keep4", jsonObject)
                }
                context = emptyJsonObject
            }

        trackEvent.disableCloudIntegrations(exceptKeys = listOf("keep1", "keep2", "keep3", "keep4"))

        assertEquals(5, trackEvent.integrations.size)
        assertEquals(false, trackEvent.integrations.getBoolean(BaseEvent.ALL_INTEGRATIONS_KEY))
        assertEquals(true, trackEvent.integrations.getBoolean("keep1"))
        assertEquals(true, trackEvent.integrations.getBoolean("keep2"))
        assertEquals("string", trackEvent.integrations.getString("keep3"))
        assertEquals(jsonObject, trackEvent.integrations["keep4"])
    }

    @Test
    fun `disableCloudIntegrations with null`() {
        val jsonObject = buildJsonObject { put("key", "value") }
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = buildJsonObject {
                    put("discard1", true)
                    put("discard2", false)
                    put("discard3", "string")
                    put("discard4", jsonObject)
                }
                context = emptyJsonObject
            }

        trackEvent.disableCloudIntegrations()

        assertEquals(1, trackEvent.integrations.size)
        assertEquals(false, trackEvent.integrations.getBoolean(BaseEvent.ALL_INTEGRATIONS_KEY))
    }

    @Test
    fun `enableCloudIntegrations with except list`() {
        val jsonObject = buildJsonObject { put("key", "value") }
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = buildJsonObject {
                    put("discard1", true)
                    put("discard2", false)
                    put("discard3", "string")
                    put("discard4", jsonObject)
                    put("keep1", true)
                    put("keep2", false)
                    put("keep3", "string")
                    put("keep4", jsonObject)
                }
                context = emptyJsonObject
            }

        trackEvent.enableCloudIntegrations(exceptKeys = listOf("keep1", "keep2", "keep3", "keep4"))

        assertEquals(5, trackEvent.integrations.size)
        assertEquals(true, trackEvent.integrations.getBoolean(BaseEvent.ALL_INTEGRATIONS_KEY))
        assertEquals(false, trackEvent.integrations.getBoolean("keep1"))
        assertEquals(false, trackEvent.integrations.getBoolean("keep2"))
        assertEquals(false, trackEvent.integrations.getBoolean("keep3"))
        assertEquals(false, trackEvent.integrations.getBoolean("keep4"))
    }

    @Test
    fun `enableCloudIntegrations with null`() {
        val jsonObject = buildJsonObject { put("key", "value") }
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = buildJsonObject {
                    put("discard1", true)
                    put("discard2", false)
                    put("discard3", "string")
                    put("discard4", jsonObject)
                }
                context = emptyJsonObject
            }

        trackEvent.enableCloudIntegrations()

        assertEquals(1, trackEvent.integrations.size)
        assertEquals(true, trackEvent.integrations.getBoolean(BaseEvent.ALL_INTEGRATIONS_KEY))
    }
}