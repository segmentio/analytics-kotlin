package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utils.StubPlugin
import com.segment.analytics.kotlin.core.utils.mockHTTPClient
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
                edgeFunction = emptyJsonObject
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
                    edgeFunction = emptyJsonObject
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
}