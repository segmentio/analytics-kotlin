package com.segment.analytics.kotlin.core

import com.segment.analytics.*
import com.segment.analytics.kotlin.core.platform.plugins.DeviceToken
import com.segment.analytics.kotlin.core.platform.plugins.setDeviceToken
import com.segment.analytics.kotlin.core.utils.TestRunPlugin
import io.mockk.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceTokenPluginTests {
    private lateinit var analytics: Analytics

    private val testDispatcher = TestCoroutineDispatcher()

    // val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val testScope = TestCoroutineScope(testDispatcher)

    init {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
        mockkConstructor(HTTPClient::class)
    }

    @BeforeEach
    fun setup() {
        analytics = Analytics(
            Configuration(
                writeKey = "123",
                analyticsScope = testScope,
                application = "Test",
                autoAddSegmentDestination = false
            )
        )
    }

    @Test
    fun `setting device token adds the device token plugin`() {
        assertEquals(null, analytics.find(DeviceToken::class))
        analytics.setDeviceToken("deviceToken")
        val deviceTokenPlugin = analytics.find(DeviceToken::class) as DeviceToken
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