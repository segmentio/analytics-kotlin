package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utils.StubPlugin
import com.segment.analytics.kotlin.core.utils.mockHTTPClient
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SettingsTests {

    private lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    init {
        Telemetry.enable = false
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
        assertEquals(true, curSettings?.hasIntegrationSettings("Segment.io"))
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
    fun `plugin added before settings is available updates plugin correctly`() = runTest {
        // forces settings to fail
        mockHTTPClient("")

        analytics = testAnalytics(Configuration(
            writeKey = "123",
            application = "Test",
            autoAddSegmentDestination = false
        ), testScope, testDispatcher)
        val mockPlugin = spyk<StubPlugin>()

        // no settings available, should not be called
        analytics.add(mockPlugin)
        verify (exactly = 0){
            mockPlugin.update(any(), any())
        }

        // load settings
        mockHTTPClient()
        analytics.checkSettings()
        verify (exactly =  1) {
            mockPlugin.update(any(), Plugin.UpdateType.Initial)
        }
        val system = analytics.store.currentState(System::class)
        assertTrue(system!!.initializedPlugins.contains(mockPlugin.hashCode()))

        // load settings again
        mockHTTPClient()
        analytics.checkSettings()
        verify (exactly =  1) {
            mockPlugin.update(any(), Plugin.UpdateType.Refresh)
        }
    }

    @Test
    fun `plugin added after settings is available updates plugin correctly`() = runTest {
        // load settings
        mockHTTPClient()
        analytics = testAnalytics(Configuration(
            writeKey = "123",
            application = "Test",
            autoAddSegmentDestination = false
        ), testScope, testDispatcher)
        val mockPlugin = spyk<StubPlugin>()

        analytics.add(mockPlugin)

        // settings is already available, update with Initial
        verify (exactly =  1) {
            mockPlugin.update(any(), Plugin.UpdateType.Initial)
        }
        val system = analytics.store.currentState(System::class)
        assertTrue(system!!.initializedPlugins.contains(mockPlugin.hashCode()))

        // load settings again
        mockHTTPClient()
        analytics.checkSettings()
        verify (exactly =  1) {
            mockPlugin.update(any(), Plugin.UpdateType.Refresh)
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

    @Disabled
    @Test
    fun `can manually enable destinations`() = runTest {
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
        analytics.update(settings)

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
    fun `fetchSettings returns null when parameters are invalid`() {
        mockHTTPClient("{\"integrations\":{}, \"plan\":{}, \"edgeFunction\": {}, \"middlewareSettings\": {}}")

        // empty host
        var settings = analytics.fetchSettings("foo", "")
        assertNull(settings)

        // not a host name
        settings = analytics.fetchSettings("foo", "http://blah")
        assertNull(settings)

        // emoji
        settings = analytics.fetchSettings("foo", "😃")
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