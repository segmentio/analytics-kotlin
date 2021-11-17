package com.segment.analytics.destinations

import android.app.Application
import com.segment.analytics.destinations.plugins.*
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import java.util.concurrent.Executors

class MainApplication : Application() {
    companion object {
        lateinit var analytics: Analytics
    }

    override fun onCreate() {
        super.onCreate()

        analytics = Analytics(BuildConfig.SEGMENT_WRITE_KEY, applicationContext) {
            this.collectDeviceId = true
            this.trackApplicationLifecycleEvents = true
            this.trackDeepLinks = true
            this.flushAt = 1
            this.flushInterval = 0
        }

        analytics.add(MixpanelDestination(applicationContext))

        // A random webhook url to view your events
        analytics.add(
            WebhookPlugin(
                "https://webhook.site/387c1740-f919-4446-a26e-a9a01ed28c8a",
                Executors.newSingleThreadExecutor()
            )
        )

        // Try out amplitude session
        analytics.add(AmplitudeSession())

        // Try out Firebase Destination
        analytics.add(FirebaseDestination(applicationContext))

        // Try out Intercom destination
        analytics.add(IntercomDestination(this))

        val appsflyerDestination = AppsFlyerDestination(applicationContext, true)
        analytics.add(appsflyerDestination)

        appsflyerDestination.conversionListener =
            object : AppsFlyerDestination.ExternalAppsFlyerConversionListener {
                override fun onConversionDataSuccess(map: Map<String, Any>) {
                    // Process Deferred Deep Linking here
                    for (attrName in map.keys) {
                        analytics.log("Appsflyer: attribute: " + attrName + " = " + map[attrName])
                    }
                }

                override fun onConversionDataFail(s: String?) {}
                override fun onAppOpenAttribution(map: Map<String, String>) {
                    // Process Direct Deep Linking here
                    for (attrName in map.keys) {
                        analytics.log("Appsflyer: attribute: " + attrName + " = " + map[attrName])
                    }
                }

                override fun onAttributionFailure(s: String?) {}
            }

    }
}
