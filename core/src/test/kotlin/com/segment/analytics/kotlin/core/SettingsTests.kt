package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utils.StubPlugin
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection

class SettingsTests {

    private lateinit var analytics: Analytics
    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope(testDispatcher)

    init {
        mockkConstructor(HTTPClient::class)
        val settingsStream = ByteArrayInputStream(
            """
                {"integrations":{"Segment.io":{"apiKey":"1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ"}},"plan":{},"edgeFunction":{}}
            """.trimIndent().toByteArray()
        )
        val httpConnection: HttpURLConnection = mockk()
        val connection = object : Connection(httpConnection, settingsStream, null) {}
        every { anyConstructed<HTTPClient>().settings("cdn-settings.segment.com/v1") } returns connection
    }

    @BeforeEach
    fun setup() {
        analytics = Analytics(
            Configuration(
                writeKey = "123",
                application = "Test"
            )
        )
        analytics.configuration.autoAddSegmentDestination = false
    }

    @Test
    fun `checkSettings updates settings`() = runBlocking {
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
    fun `settings update updates plugins`() = runBlocking {
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

        assertTrue(settings.isDestinationEnabled("Foo"))
        assertTrue(settings.isDestinationEnabled("Bar"))
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

        assertTrue(settings.isDestinationEnabled("Foo"))
        assertFalse(settings.isDestinationEnabled("Bar"))
    }

    @Serializable
    data class FooConfig(
        var configA: Boolean,
        var configB: Int,
        var configC: String
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
}