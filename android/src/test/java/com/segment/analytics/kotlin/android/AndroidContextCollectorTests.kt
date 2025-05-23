package com.segment.analytics.kotlin.android

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.android.plugins.AndroidContextPlugin
import com.segment.analytics.kotlin.android.plugins.getUniqueID
import com.segment.analytics.kotlin.android.utils.MemorySharedPreferences
import com.segment.analytics.kotlin.android.utils.testAnalytics
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sovran.kotlin.Store
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidContextCollectorTests {

    lateinit var appContext: Context
    lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    init {
        Telemetry.enable = false
    }

    @Before
    fun setUp() {
        appContext = spyk(InstrumentationRegistry.getInstrumentation().targetContext)
        val sharedPreferences: SharedPreferences = MemorySharedPreferences()
        every { appContext.getSharedPreferences(any(), any()) } returns sharedPreferences
        mockkStatic("com.segment.analytics.kotlin.android.plugins.AndroidContextPluginKt")
        every { getUniqueID() } returns "unknown"

        analytics  = testAnalytics(
            Configuration(
                writeKey = "123",
                application = appContext,
                storageProvider = AndroidStorageProvider
            ),
            testScope, testDispatcher
        )
    }

    @Test
    fun `context fields applied correctly`()  {
        // Context of the app under test.
        analytics.configuration.collectDeviceId = true
        val contextCollector = AndroidContextPlugin()
        contextCollector.setup(analytics)
        val event = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = Date(0).toInstant().toString()
            }
        contextCollector.execute(event)
        with(event.context) {
            assertTrue(this.containsKey("app"))
            this["app"]?.jsonObject?.let {
                assertEquals("com.segment.analytics.test", it["name"].asString())
                assertEquals("undefined", it["version"].asString())
                assertEquals("com.segment.analytics.test", it["namespace"].asString())
                assertEquals("0", it["build"].asString())
            }
            assertTrue(this.containsKey("device"))
            this["device"]?.jsonObject?.let {
                assertEquals("unknown", it["id"].asString())
                assertEquals("robolectric", it["manufacturer"].asString())
                assertEquals("robolectric", it["model"].asString())
                assertEquals("robolectric", it["name"].asString())
                assertEquals("android", it["type"].asString())
            }
            assertTrue(this.containsKey("os"))
            this["os"]?.jsonObject?.let {
                assertEquals("Android", it["name"].asString())
                assertEquals("12", it["version"].asString())
            }
            assertTrue(this.containsKey("screen"))
            this["screen"]?.jsonObject?.let {
                assertEquals("1.0", it["density"].asString())
                assertEquals("470", it["height"].asString())
                assertEquals("320", it["width"].asString())
            }
            assertTrue(this.containsKey("network"))
//            this["network"]?.jsonObject?.let {
//                assertEquals("false", it["wifi"].asString())
//                assertEquals("false", it["bluetooth"].asString())
//                assertEquals("false", it["cellular"].asString())
//            }

            assertEquals("en-US", this["locale"].asString())
            assertEquals("undefined", this["userAgent"].asString())
            assertTrue(this.containsKey("timezone"))
        }
    }

    @Test
    fun `getDeviceId returns anonId when disabled`() = runTest {
        analytics.storage.write(Storage.Constants.AnonymousId, "anonId")
        val contextCollector = AndroidContextPlugin()
        contextCollector.setup(analytics)
        val deviceId = contextCollector.getDeviceId(false, "")
        assertEquals(deviceId, "anonId")
    }

    @Test
    fun `device id cache is used when presented`() = runTest {
        analytics.storage.write(Storage.Constants.DeviceId, "anonId")

        analytics.configuration.collectDeviceId = true
        val contextCollector = AndroidContextPlugin()
        contextCollector.setup(analytics)

        val event = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = Date(0).toInstant().toString()
            }
        contextCollector.execute(event)

        with(event.context) {
            assertTrue(this.containsKey("device"))
            this["device"]?.jsonObject?.let {
                assertEquals("anonId", it["id"].asString())
            }
        }
    }

    private fun JsonElement?.asString(): String? = this?.jsonPrimitive?.content
}