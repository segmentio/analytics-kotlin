package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Telemetry
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.mockHTTPClient
import com.segment.analytics.kotlin.core.utils.testAnalytics
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContextPluginReferrerTests {
    private lateinit var analytics: Analytics
    private lateinit var plugin: ContextPlugin

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    init {
        Telemetry.enable = false
        mockHTTPClient()
    }

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        analytics = testAnalytics(
            Configuration(writeKey = "123", application = "Test", autoAddSegmentDestination = false),
            testScope,
            testDispatcher
        )
        plugin = ContextPlugin()
        plugin.setup(analytics)
    }

    private fun track(context: kotlinx.serialization.json.JsonObject = emptyJsonObject) =
        plugin.execute(
            TrackEvent(event = "e", properties = emptyJsonObject).apply { this.context = context }
        )

    @Test
    fun `no referrer in context by default`() {
        assertNull(track().context["referrer"])
    }

    @Test
    fun `referrer from last deep link is added to context`() {
        analytics.updateReferrer("myapp://home?x=1")
        val referrer = track().context["referrer"]?.jsonObject
        assertEquals("myapp://home?x=1", referrer?.get("url")?.jsonPrimitive?.content)
    }

    @Test
    fun `referrer is removed from context after reset`() {
        analytics.updateReferrer("myapp://home")
        analytics.reset()
        assertNull(track().context["referrer"])
    }

    @Test
    fun `referrer supplied on the event takes precedence`() {
        analytics.updateReferrer("myapp://home")
        val custom = buildJsonObject {
            put("referrer", buildJsonObject { put("url", "https://custom.example") })
        }
        val referrer = track(custom).context["referrer"]?.jsonObject
        assertEquals("https://custom.example", referrer?.get("url")?.jsonPrimitive?.content)
    }
}
