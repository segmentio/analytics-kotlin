package com.segment.analytics.destinations

import android.app.Application
import com.segment.analytics.*
import com.segment.analytics.destinations.plugins.AmplitudeSession
import com.segment.analytics.utilities.*
import com.segment.analytics.destinations.plugins.MixpanelDestination
import com.segment.analytics.next.plugins.WebhookPlugin
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

        analytics.add(MixpanelDestination(applicationContext, "MIXPANEL_TOKEN"))

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
