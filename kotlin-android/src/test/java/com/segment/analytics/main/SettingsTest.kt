package com.segment.analytics.main

import android.content.Context
import com.segment.analytics.*
import com.segment.analytics.main.utils.MemorySharedPreferences
import com.segment.analytics.main.utils.StubPlugin
import com.segment.analytics.main.utils.mockContext
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.util.zip.GZIPOutputStream
import kotlin.text.Charsets.UTF_8

class SettingsTest {

    private lateinit var analytics: Analytics
    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope(testDispatcher)
    private var mockContext: Context = mockContext()

    init {
        mockkConstructor(HTTPClient::class)
        val settingsStream = ByteArrayInputStream(
            """
                {"integrations":{"Segment.io":{"apiKey":"1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ"}},"plan":{},"edgeFunction":{}}
            """.trimIndent().toByteArray()
        )
        val httpConnection: HttpURLConnection = mockk()
        val connection = object : Connection(httpConnection, settingsStream, null) {}
        every { anyConstructed<HTTPClient>().settings(any()) } returns connection
    }

    @BeforeEach
    fun setup() {
        analytics = Analytics(
            Configuration(
                writeKey = "123",
                analyticsScope = testScope,
                ioDispatcher = testDispatcher,
                analyticsDispatcher = testDispatcher,
                application = mockContext
            )
        )
        analytics.configuration.autoAddSegmentDestination = false
    }

    @Test
    fun `checkSettings updates settings`() = runBlocking {
        val system = analytics.store.currentState(System::class)
        val curSettings = system?.settings
        Assertions.assertEquals(
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
                )
            )
        }
    }
}