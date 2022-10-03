package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.DestinationMetadata
import com.segment.analytics.kotlin.core.HTTPClient
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.manuallyEnableDestination
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

class DestinationMetadataPluginTests {
    private lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testScope = TestScope(testDispatcher)

    private lateinit var plugin: DestinationMetadataPlugin

    private val epochTimestamp = Date(0).toInstant().toString()

    init {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
        mockkStatic(Base64::class)
        mockkConstructor(HTTPClient::class)
    }

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        plugin = DestinationMetadataPlugin()

        val config = Configuration(
            writeKey = "123",
            application = "Test",
            storageProvider = ConcreteStorageProvider,
            flushAt = 2,
            flushInterval = 0
        )
        analytics = testAnalytics(config, testScope, testDispatcher)
        plugin.setup(analytics)
    }

    @Test
    fun `configureCloudDestinations builds payload with correct metadata`() {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = epochTimestamp
                _metadata = DestinationMetadata()
            }
        val mixpanelDest = object : DestinationPlugin() {
            override val key: String = "Mixpanel"
        }
        analytics.add(mixpanelDest)
        analytics.manuallyEnableDestination(mixpanelDest)
        plugin.update(Settings(
            integrations = buildJsonObject {
                put("Segment.io", buildJsonObject {
                    put("apiKey", "123")
                    put("apiHost", "api.segment.io/v1")
                    put("unbundledIntegrations", buildJsonArray {
                        add("Customer.io")
                        add("Mixpanel")
                        add("Amplitude")
                    })
                })
            }
        ), Plugin.UpdateType.Initial)

        val expected = trackEvent.copy<TrackEvent>().apply {
            _metadata = DestinationMetadata(
                unbundled = listOf("Customer.io","Amplitude"),
                bundled = listOf("Mixpanel"),
                bundledIds = emptyList()
            )
        }
        val actual = plugin.execute(trackEvent)
        assertEquals(expected, actual)
        assertEquals(emptyJsonObject, actual.integrations)
    }

    @Test
    fun `plugin respects both integrations list and unbundled list`() {
        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = epochTimestamp
                _metadata = DestinationMetadata()
            }
        val mixpanelDest = object : DestinationPlugin() {
            override val key: String = "Mixpanel"
        }
        analytics.add(mixpanelDest)
        analytics.manuallyEnableDestination(mixpanelDest)
        plugin.update(Settings(
            integrations = buildJsonObject {
                put("Segment.io", buildJsonObject {
                    put("apiKey", "123")
                    put("apiHost", "api.segment.io/v1")
                    put("unbundledIntegrations", buildJsonArray {
                        add("Customer.io")
                        add("Mixpanel")
                        add("Amplitude")
                        add("dest1")
                    })
                })
                put("dest1", true)
                put("dest2", true)
            }
        ), Plugin.UpdateType.Initial)

        val expected = trackEvent.copy<TrackEvent>().apply {
            _metadata = DestinationMetadata(
                unbundled = listOf("dest1", "dest2", "Customer.io","Amplitude"),
                bundled = listOf("Mixpanel"),
                bundledIds = emptyList()
            )
        }
        val actual = plugin.execute(trackEvent)
        assertEquals(expected, actual)
        assertEquals(emptyJsonObject, actual.integrations)
    }
}