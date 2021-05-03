package com.segment.analytics.main

import android.content.Context
import android.util.Base64
import com.segment.analytics.*
import com.segment.analytics.main.utils.TestRunPlugin
import com.segment.analytics.main.utils.mockContext
import com.segment.analytics.platform.plugins.DeviceToken
import com.segment.analytics.platform.plugins.setDeviceToken
import io.mockk.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceTokenPluginTest {
    private var mockContext: Context = mockContext()
    private lateinit var analytics: Analytics

    private val testDispatcher = TestCoroutineDispatcher()

    // val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val testScope = TestCoroutineScope(testDispatcher)

    init {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "123"
        mockkConstructor(HTTPClient::class)
    }

    @BeforeEach
    fun setup() {
        analytics = Analytics(
            Configuration(
                writeKey = "123",
                analyticsScope = testScope,
                ioDispatcher = testDispatcher,
                analyticsDispatcher = testDispatcher,
                application = mockContext,
                autoAddSegmentDestination = false
            )
        )
    }

    @Test
    fun `setting device token adds the device token plugin`() {
        assertEquals(null, analytics.find(DeviceToken.SEGMENT_TOKEN_PLUGIN_NAME))
        analytics.setDeviceToken("deviceToken")
        val deviceTokenPlugin = analytics.find(DeviceToken.SEGMENT_TOKEN_PLUGIN_NAME) as DeviceToken
        assertNotEquals(null, deviceTokenPlugin)
        assertEquals("deviceToken", deviceTokenPlugin.token)
    }

    @Test
    fun `plugin will add token to payloads`() {
        analytics.setDeviceToken("deviceToken")
        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        analytics.track("test")
        val track = slot<TrackEvent>()
        verify { mockPlugin.track(capture(track)) }
        with(track.captured) {
            assertEquals("test", event)
            assertEquals(
                "deviceToken",
                context["device"]?.jsonObject?.get("token")?.jsonPrimitive?.content
            )
        }
    }
}