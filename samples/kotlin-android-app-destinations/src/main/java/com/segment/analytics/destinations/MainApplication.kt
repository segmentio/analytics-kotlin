package com.segment.analytics.destinations

import android.app.Application
import com.segment.analytics.*
import com.segment.analytics.destinations.plugins.AmplitudeSession
import com.segment.analytics.destinations.plugins.WebhookPlugin
import com.segment.analytics.platform.Plugin
import com.segment.analytics.utilities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        // A random webhook url to view your events
        analytics.add(
            WebhookPlugin(
                "https://webhook.site/387c1740-f919-4446-a26e-a9a01ed28c8a",
                Executors.newSingleThreadExecutor()
            )
        )

        // Try out amplitude session
        analytics.add(AmplitudeSession())
    }
}