package com.segment.analytics.kotlin.android

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.android.utils.TestRunPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.segment.analytics.kotlin.android.plugins.AndroidLifecyclePlugin
import com.segment.analytics.kotlin.android.utils.mockHTTPClient
import com.segment.analytics.kotlin.android.utils.testAnalytics
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidLifecyclePluginTests {
    private val lifecyclePlugin = AndroidLifecyclePlugin()

    private lateinit var analytics: Analytics
    private val mockContext = spyk(InstrumentationRegistry.getInstrumentation().targetContext)

    init {
        val packageInfo = PackageInfo()
        packageInfo.versionCode = 100
        packageInfo.versionName = "1.0.0"

        val packageManager = mockk<PackageManager> {
            every { getPackageInfo("com.foo", 0) } returns packageInfo
        }
        every { mockContext.packageName } returns "com.foo"
        every { mockContext.packageManager } returns packageManager

        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"

        mockHTTPClient()
    }

    @Before
    fun setup() {
        analytics = testAnalytics(
            Configuration(
                writeKey = "123",
                application = mockContext,
                storageProvider = AndroidStorageProvider
            )
        )
    }

    @Test
    fun `plugins get notified when lifecycle hooks get triggered`() {
        analytics.configuration.trackApplicationLifecycleEvents = false
        analytics.configuration.trackDeepLinks = false
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)

        val test = spyk(object : Plugin, AndroidLifecycle {
            override val type: Plugin.Type = Plugin.Type.Utility
            override lateinit var analytics: Analytics

            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
            }

            override fun onActivityStarted(activity: Activity?) {
            }

            override fun onActivityResumed(activity: Activity?) {
            }

            override fun onActivityPaused(activity: Activity?) {
            }

            override fun onActivityStopped(activity: Activity?) {
            }

            override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
            }

            override fun onActivityDestroyed(activity: Activity?) {
            }
        })
        analytics.add(test)
        val mockActivity = mockk<Activity>()
        val mockBundle = mockk<Bundle>()

        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)
        lifecyclePlugin.onActivityStarted(mockActivity)
        lifecyclePlugin.onActivityResumed(mockActivity)
        lifecyclePlugin.onActivityPaused(mockActivity)
        lifecyclePlugin.onActivityStopped(mockActivity)
        lifecyclePlugin.onActivitySaveInstanceState(mockActivity, mockBundle)
        lifecyclePlugin.onActivityDestroyed(mockActivity)

        verify {
            test.onActivityCreated(mockActivity, mockBundle)
            test.onActivityStarted(mockActivity)
            test.onActivityResumed(mockActivity)
            test.onActivityPaused(mockActivity)
            test.onActivityStopped(mockActivity)
            test.onActivitySaveInstanceState(mockActivity, mockBundle)
            test.onActivityDestroyed(mockActivity)
        }
    }

    @Test
    fun `application opened is tracked`() = runBlocking{
        analytics.configuration.trackApplicationLifecycleEvents = true
        analytics.configuration.trackDeepLinks = false
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)
        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val mockActivity = mockk<Activity>()
        val mockBundle = mockk<Bundle>()

        // Simulate activity startup
        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)
        lifecyclePlugin.onActivityStarted(mockActivity)
        lifecyclePlugin.onActivityResumed(mockActivity)

        verify {  mockPlugin.updateState(true) }
        val tracks = mutableListOf<TrackEvent>()
        verify { mockPlugin.track(capture(tracks)) }
        assertEquals(2, tracks.size)
        with(tracks[1]) {
            assertEquals("Application Opened", event)
            assertEquals(buildJsonObject {
                put("version", "1.0.0")
                put("build", "100")
                put("from_background", false)
            }, properties)
        }
    }

    @Test
    fun `application backgrounded is tracked`() {
        analytics.configuration.trackApplicationLifecycleEvents = true
        analytics.configuration.trackDeepLinks = false
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)
        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val mockActivity = mockk<Activity>()

        // Simulate activity startup
        lifecyclePlugin.onActivityPaused(mockActivity)
        lifecyclePlugin.onActivityStopped(mockActivity)
        lifecyclePlugin.onActivityDestroyed(mockActivity)

        verify {  mockPlugin.updateState(true) }
        val track = slot<TrackEvent>()
        verify { mockPlugin.track(capture(track)) }
        with(track.captured) {
            assertEquals("Application Backgrounded", event)
        }
    }

    @Test
    fun `application installed is tracked`() {
        analytics.configuration.trackApplicationLifecycleEvents = true
        analytics.configuration.trackDeepLinks = false
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)

        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val mockActivity = mockk<Activity>()
        val mockBundle = mockk<Bundle>()

        // Simulate activity startup
        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)

        verify {  mockPlugin.updateState(true) }
        val track = slot<TrackEvent>()
        verify { mockPlugin.track(capture(track)) }
        with(track.captured) {
            assertEquals("Application Installed", event)
            assertEquals(buildJsonObject {
                put("version", "1.0.0")
                put("build", "100")
            }, properties)
        }
    }

    @Test
    fun `application updated is tracked`() = runBlocking {
        analytics.configuration.trackApplicationLifecycleEvents = true
        analytics.configuration.trackDeepLinks = false
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)

        analytics.storage.write(Storage.Constants.AppVersion, "0.9")
        analytics.storage.write(Storage.Constants.AppBuild, "9")

        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val mockActivity = mockk<Activity>()
        val mockBundle = mockk<Bundle>()

        // Simulate activity startup
        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)

        verify {  mockPlugin.updateState(true) }
        val track = slot<TrackEvent>()
        verify { mockPlugin.track(capture(track)) }
        with(track.captured) {
            assertEquals("Application Updated", event)
            assertEquals(buildJsonObject {
                put("version", "1.0.0")
                put("build", "100")
                put("previous_version", "0.9")
                put("previous_build", "9")
            }, properties)
        }
    }

    @Test
    fun `application lifecycle events not tracked when disabled`() {
        analytics.configuration.trackApplicationLifecycleEvents = false
        analytics.configuration.trackDeepLinks = false
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)
        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val mockActivity = mockk<Activity>()
        val mockBundle = mockk<Bundle>()

        // Simulate activity startup
        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)
        lifecyclePlugin.onActivityStarted(mockActivity)
        lifecyclePlugin.onActivityResumed(mockActivity)

        lifecyclePlugin.onActivityPaused(mockActivity)
        lifecyclePlugin.onActivityStopped(mockActivity)
        lifecyclePlugin.onActivityDestroyed(mockActivity)

        assertFalse(mockPlugin.ran)
    }

    @Test
    fun `track deep link when enabled`() {
        analytics.configuration.trackApplicationLifecycleEvents = false
        analytics.configuration.trackDeepLinks = true
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)
        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val mockIntent = mockk<Intent>()
        every { mockIntent.data } returns Uri.parse("app://track.com/open?utm_id=12345&gclid=abcd&nope=")
        val mockActivity = mockk<Activity>()
        every { mockActivity.intent } returns mockIntent
        every { mockActivity.referrer } returns null
        val mockBundle = mockk<Bundle>()

        // Simulate activity startup
        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)

        verify {  mockPlugin.updateState(true) }
        val track = slot<TrackEvent>()
        verify { mockPlugin.track(capture(track)) }
        with(track.captured) {
            assertEquals("Deep Link Opened", event)
            assertEquals(buildJsonObject {
                put("url", "app://track.com/open?utm_id=12345&gclid=abcd&nope=")
                put("utm_id", "12345")
                put("gclid", "abcd")
            }, properties)
        }
    }

    @Config(sdk = [22])
    @Test
    fun `track deep link when enabled sdk=22`() {
        analytics.configuration.trackApplicationLifecycleEvents = false
        analytics.configuration.trackDeepLinks = true
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)
        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val referrer = Uri.parse("android-app:/com.package.app")

        val mockIntent = mockk<Intent>()
        every { mockIntent.data } returns Uri.parse("app://track.com/open?utm_id=12345&gclid=abcd&nope=")
        val mockActivity = mockk<Activity>()
        every { mockActivity.intent } returns mockIntent
        every { mockActivity.referrer } returns referrer
        val mockBundle = mockk<Bundle>()

        // Simulate activity startup
        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)

        verify {  mockPlugin.updateState(true) }
        val track = slot<TrackEvent>()
        verify { mockPlugin.track(capture(track)) }
        with(track.captured) {
            assertEquals("Deep Link Opened", event)
            assertEquals(buildJsonObject {
                put("url", "app://track.com/open?utm_id=12345&gclid=abcd&nope=")
                put("utm_id", "12345")
                put("gclid", "abcd")
                put("referrer", "android-app:/com.package.app")
            }, properties)
        }
    }
/*
    Due to some complications b/w mockk and robolectric this test seems to be failing on the CI
    @Config(sdk = [18])
    @Test
    fun `track deep link when enabled sdk=18`() {
        analytics.configuration.trackApplicationLifecycleEvents = false
        analytics.configuration.trackDeepLinks = true
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)
        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val referrer = Uri.parse("android-app:/com.package.app")

        val mockIntent = mockk<Intent>()
        every { mockIntent.data } returns Uri.parse("app://track.com/open?utm_id=12345&gclid=abcd&nope=")
        every { mockIntent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER) } returns referrer
        val mockActivity = mockk<Activity>()
        every { mockActivity.intent } returns mockIntent
        val mockBundle = mockk<Bundle>()

        // Simulate activity startup
        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)

        verify (timeout = 2000){  mockPlugin.updateState(true) }
        val track = slot<TrackEvent>()
        verify { mockPlugin.track(capture(track)) }
        with(track.captured) {
            assertEquals("Deep Link Opened", event)
            assertEquals(buildJsonObject {
                put("url", "app://track.com/open?utm_id=12345&gclid=abcd&nope=")
                put("utm_id", "12345")
                put("gclid", "abcd")
                put("referrer", "android-app:/com.package.app")
            }, properties)
        }
    }
 */

    @Test
    fun `do not track deep link when disabled`() {
        analytics.configuration.trackApplicationLifecycleEvents = false
        analytics.configuration.trackDeepLinks = false
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)
        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val mockIntent = mockk<Intent>()
        every { mockIntent.data } returns Uri.parse("app://track.com/open?utm_id=12345&gclid=abcd&nope=")
        val mockActivity = mockk<Activity>()
        every { mockActivity.intent } returns mockIntent
        val mockBundle = mockk<Bundle>()

        // Simulate activity startup
        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)

        assertFalse(mockPlugin.ran)
    }

    @Test
    fun `do not track screen views when disabled`() {
        analytics.configuration.trackApplicationLifecycleEvents = false
        analytics.configuration.trackDeepLinks = false
        analytics.configuration.useLifecycleObserver = false
        analytics.add(lifecyclePlugin)
        val mockPlugin = spyk(TestRunPlugin {})
        analytics.add(mockPlugin)

        val mockIntent = mockk<Intent>()
        every { mockIntent.data } returns Uri.parse("app://track.com/open?utm_id=12345&gclid=abcd&nope=")
        val mockActivity = mockk<Activity>()
        every { mockActivity.intent } returns mockIntent
        val mockBundle = mockk<Bundle>()

        // Simulate activity startup
        lifecyclePlugin.onActivityCreated(mockActivity, mockBundle)

        assertFalse(mockPlugin.ran)
    }
}