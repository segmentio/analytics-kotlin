package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.kotlin.core.utils.StubPlugin
import com.segment.analytics.kotlin.core.utils.mockHTTPClient
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.http.HttpClient
import java.util.concurrent.atomic.AtomicInteger

class SettingsTests {

    private lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    init {
        mockHTTPClient()
    }

    @BeforeEach
    fun setup() {

        analytics = testAnalytics(Configuration(
            writeKey = "123",
            application = "Test"
        ), testScope, testDispatcher)
        analytics.configuration.autoAddSegmentDestination = false
    }

    @Test
    fun `checkSettings updates settings`() = runTest {
        val system = analytics.store.currentState(System::class)
        val curSettings = system?.settings
        assertEquals(
            Settings(
                integrations = buildJsonObject {
                    put(
                        "Segment.io",
                        buildJsonObject { put("apiKey", "1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ") })
                },
                plan = emptyJsonObject,
                edgeFunction = emptyJsonObject,
                middlewareSettings = emptyJsonObject
            ),
            curSettings
        )
    }

    @Test
    fun `settings update updates plugins`() {
        val mockPlugin = spyk(StubPlugin())
        analytics.add(mockPlugin)
        verify {
            mockPlugin.update(
                Settings(
                    integrations = buildJsonObject {
                        put(
                            "Segment.io",
                            buildJsonObject {
                                put(
                                    "apiKey",
                                    "1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ"
                                )
                            })
                    },
                    plan = emptyJsonObject,
                    edgeFunction = emptyJsonObject,
                    middlewareSettings = emptyJsonObject
                ),
                Plugin.UpdateType.Initial
            )
        }
    }

    @Test
    fun `isDestinationEnabled returns true when present`() {
        val settings = Settings(
            integrations = buildJsonObject {
                put("Foo", buildJsonObject {
                    put("configA", true)
                })
                put("Bar", buildJsonObject {
                    put("configC", 10)
                })
            }
        )

        assertTrue(settings.hasIntegrationSettings("Foo"))
        assertTrue(settings.hasIntegrationSettings("Bar"))
    }

    @Test
    fun `isDestinationEnabled returns false when absent`() {
        val settings = Settings(
            integrations = buildJsonObject {
                put("Foo", buildJsonObject {
                    put("configA", true)
                })
            }
        )

        assertTrue(settings.hasIntegrationSettings("Foo"))
        assertFalse(settings.hasIntegrationSettings("Bar"))
    }

    @Serializable
    data class FooConfig(
        var configA: Boolean,
        var configB: Int,
        var configC: String,
    )

    @Test
    fun `can fetch typed settings correctly`() {
        val settings = Settings(
            integrations = buildJsonObject {
                put("Foo", buildJsonObject {
                    put("configA", true)
                    put("configB", 10)
                    put("configC", "something")
                })
            }
        )

        val typedSettings = settings.destinationSettings<FooConfig>("Foo")
        assertNotNull(typedSettings)
        with(typedSettings!!) {
            assertTrue(configA)
            assertEquals(10, configB)
            assertEquals("something", configC)
        }
    }

    @Test
    fun `can manually enable destinations`() {
        val settings = Settings(
            integrations = buildJsonObject {
                put("Foo", buildJsonObject {
                    put("configA", true)
                    put("configB", 10)
                    put("configC", "something")
                })
            }
        )

        val eventCounter = AtomicInteger(0)
        val barDestination = object : DestinationPlugin() {
            override val key: String = "Bar"

            override fun track(payload: TrackEvent): BaseEvent? {
                eventCounter.incrementAndGet()
                return super.track(payload)
            }
        }

        analytics.add(barDestination)
        analytics.update(settings, Plugin.UpdateType.Initial)

        analytics.track("track", buildJsonObject { put("direct", true) })
        assertEquals(0, eventCounter.get())

        analytics.manuallyEnableDestination(barDestination)
        analytics.track("track", buildJsonObject { put("direct", true) })
        assertEquals(1, eventCounter.get())
    }

    @Test
    fun `fetchSettings returns null when Settings string is invalid`() {
        // Null on invalid JSON
        mockHTTPClient("")
        var settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null on invalid JSON
        mockHTTPClient("hello")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null on invalid JSON
        mockHTTPClient("#! /bin/sh")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null on invalid JSON
        mockHTTPClient("<!DOCTYPE html>")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null on invalid JSON
        mockHTTPClient("true")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null on invalid JSON
        mockHTTPClient("[]")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null on invalid JSON
        mockHTTPClient("}{")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null on invalid JSON
        mockHTTPClient("{{{{}}}}")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null on invalid JSON
        mockHTTPClient("{null:\"bar\"}")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)
    }

    @Test
    fun `fetchSettings returns null when Settings string is null for known properties`() {
        // Null if integrations is null
        mockHTTPClient("{\"integrations\":null}")
        var settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null if plan is null
        mockHTTPClient("{\"plan\":null}")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null if edgeFunction is null
        mockHTTPClient("{\"edgeFunction\":null}")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null if middlewareSettings is null
        mockHTTPClient("{\"middlewareSettings\":null}")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)
    }

    @Test
    fun `known Settings property types must match json type`() {

        // integrations must be a JSON object
        mockHTTPClient("{\"integrations\":{}}")
        var settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNotNull(settings)

        // Null if integrations is a number
        mockHTTPClient("{\"integrations\":123}")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null if integrations is a string
        mockHTTPClient("{\"integrations\":\"foo\"}")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null if integrations is an array
        mockHTTPClient("{\"integrations\":[\"foo\"]}")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)

        // Null if integrations is an emoji (UTF-8 string)
        mockHTTPClient("{\"integrations\": 😃}")
        settings = analytics.fetchSettings("foo", "cdn-settings.segment.com/v1")
        assertNull(settings)
    }
}