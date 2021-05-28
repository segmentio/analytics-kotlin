package com.segment.analytics.next

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.segment.analytics.*
import com.segment.analytics.next.plugins.AndroidAdvertisingIdPlugin
import com.segment.analytics.next.plugins.AndroidRecordScreenPlugin
import com.segment.analytics.next.plugins.PushNotificationTracking
import com.segment.analytics.next.plugins.WebhookPlugin
import com.segment.analytics.platform.Plugin
import com.segment.analytics.platform.plugins.android.AndroidLifecycle
import com.segment.analytics.utilities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.Executors

class MainApplication : Application() {
    companion object {
        lateinit var analytics: Analytics
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        analytics = Analytics(BuildConfig.SEGMENT_WRITE_KEY, applicationContext) {
            this.analyticsScope = applicationScope
            this.collectDeviceId = true
            this.trackApplicationLifecycleEvents = true
            this.trackDeepLinks = true
            this.flushAt = 1
            this.flushInterval = 0
        }
        analytics.add(AndroidRecordScreenPlugin())
        analytics.add(object : Plugin {
            override val type: Plugin.Type = Plugin.Type.Enrichment
            override val name: String = "Foo"
            override lateinit var analytics: Analytics

            override fun execute(event: BaseEvent): BaseEvent? {
                event.enableIntegration("AppsFlyer")
                event.disableIntegration("AppBoy")
                event.putInContext("foo", "bar")
                event.putInContextUnderKey("device", "android", true)
                event.removeFromContext("locale")
                return event
            }

        })
        analytics.add(PushNotificationTracking)
        // A random webhook url to view your events
        analytics.add(
            WebhookPlugin(
                "https://webhook.site/387c1740-f919-4446-a26e-a9a01ed28c8a",
                Executors.newSingleThreadExecutor()
            )
        )

        analytics.add(AndroidAdvertisingIdPlugin(this))

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("SegmentSample", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Log and toast
            Log.d("SegmentSample", token)
        })
    }
}