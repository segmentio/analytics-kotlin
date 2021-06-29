package com.segment.analytics.main

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.segment.analytics.*
import com.segment.analytics.platform.plugins.android.AndroidContextPlugin
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidContextCollectorTest {

    val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    val analytics = Analytics(
        Configuration(
            writeKey = "123",
            analyticsScope = CoroutineScope(Dispatchers.Main),
            application = appContext
        )
    )

    @Test
    fun `context fields applied correctly`() {
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
                assertEquals("org.robolectric.default", it["name"].asString())
                assertEquals("undefined", it["version"].asString())
                assertEquals("org.robolectric.default", it["namespace"].asString())
                assertEquals("0", it["build"].asString())
            }
            assertTrue(this.containsKey("library"))
            this["library"]?.jsonObject?.let {
                assertEquals("analytics-kotlin", it["name"].asString())
                assertEquals("1.0.0", it["version"].asString())
            }
            assertTrue(this.containsKey("device"))
            this["device"]?.jsonObject?.let {
                assertEquals("unknown", it["id"].asString())
                assertEquals("unknown", it["manufacturer"].asString())
                assertEquals("robolectric", it["model"].asString())
                assertEquals("robolectric", it["name"].asString())
                assertEquals("android", it["type"].asString())
            }
            assertTrue(this.containsKey("os"))
            this["os"]?.jsonObject?.let {
                assertEquals("Android", it["name"].asString())
                assertEquals("4.1.2", it["version"].asString())
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
    fun `getDeviceId returns anonId when disabled`() {
        analytics.storage.write(Storage.Constants.AnonymousId, "anonId")
        val contextCollector = AndroidContextPlugin()
        contextCollector.setup(analytics)
        val deviceId = contextCollector.getDeviceId(false, appContext)
        assertEquals(deviceId, "anonId")
    }

    private fun JsonElement?.asString(): String? = this?.jsonPrimitive?.content
}