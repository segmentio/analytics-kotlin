package com.segment.analytics.next

import android.app.Application
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.next.plugins.AndroidRecordScreenPlugin
import com.segment.analytics.next.plugins.PushNotificationTracking
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.platform.policies.FrequencyFlushPolicy
import com.segment.analytics.kotlin.core.utilities.*
import java.net.HttpURLConnection

class MainApplication : Application() {
    companion object {
        lateinit var analytics: Analytics
    }

    override fun onCreate() {
        super.onCreate()

        analytics = Analytics("tteOFND0bb5ugJfALOJWpF0wu1tcxYgr", applicationContext) {
            this.collectDeviceId = true
            this.trackApplicationLifecycleEvents = true
            this.trackDeepLinks = true
            this.flushPolicies = listOf(
                CountBasedFlushPolicy(3), // Flush after 3 events
                FrequencyFlushPolicy(5000), // Flush after 5 secs
                UnmeteredFlushPolicy(applicationContext) // Flush if network is not metered
            )
            this.flushPolicies = listOf(UnmeteredFlushPolicy(applicationContext))
            this.requestFactory = object : RequestFactory() {
                override fun upload(apiHost: String): HttpURLConnection {
                    val connection: HttpURLConnection = openConnection("https://$apiHost/b")
                    connection.setRequestProperty("Content-Type", "text/plain")
                    connection.doOutput = true
                    connection.setChunkedStreamingMode(0)
                    return connection
                }
            }
        }
        analytics.add(AndroidRecordScreenPlugin())
        analytics.add(object : Plugin {
            override val type: Plugin.Type = Plugin.Type.Enrichment
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

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("SegmentSample", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result ?: ""

            // Log and toast
            Log.d("SegmentSample", token)
        })
    }
}