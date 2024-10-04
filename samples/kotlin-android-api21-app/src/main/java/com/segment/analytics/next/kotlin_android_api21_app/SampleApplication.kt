package com.segment.analytics.next.kotlin_android_api21_app

import android.app.Application
import android.util.Log
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.platform.policies.FrequencyFlushPolicy

class SampleApplication: Application() {

    companion object {
        val TAG = "KotlinApi21Sample"
        lateinit var analytics: Analytics
    }
    
    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "onCreate: Running....")

        analytics = com.segment.analytics.kotlin.android.Analytics(
            "ETcIuuIISx43DvIO1FudJ3qHNHhxkX04",
            applicationContext
        ) {
            this.collectDeviceId = true
            this.trackApplicationLifecycleEvents = true
            this.trackDeepLinks = true
            this.flushPolicies = listOf(
                CountBasedFlushPolicy(1), // Flush after 3 events
//                FrequencyFlushPolicy(5000), // Flush after 5 secs
            )
        }



        Log.d(TAG, "onCreate: Analytics configured")
    }
}