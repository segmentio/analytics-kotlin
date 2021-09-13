package com.segment.analytics.kotlin.android

import android.content.Context
import android.util.Log
import com.segment.analytics.kotlin.android.plugins.AndroidContextPlugin
import com.segment.analytics.kotlin.android.plugins.AndroidLifecyclePlugin
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.platform.plugins.LogType
import com.segment.analytics.kotlin.core.platform.plugins.Logger

// A set of functions tailored to the Android implementation of analytics

@Suppress("FunctionName")
// constructor function to build android specific analytics in dsl format
// Usage: Analytics("$writeKey", applicationContext, applicationScope)
public fun Analytics(
    writeKey: String,
    context: Context
): Analytics {
    require(writeKey.isNotBlank()) { "writeKey cannot be blank " }
    val conf = Configuration(
        writeKey = writeKey,
        application = context,
        storageProvider = AndroidStorageProvider
    )
    return Analytics(conf).apply {
        startup()
    }
}

@Suppress("FunctionName")
// constructor function to build android specific analytics in dsl format with config options
// Usage: Analytics("$writeKey", applicationContext) {
//            this.analyticsScope = applicationScope
//            this.collectDeviceId = false
//            this.flushAt = 10
//        }
public fun Analytics(
    writeKey: String,
    context: Context,
    configs: Configuration.() -> Unit
): Analytics {
    require(writeKey.isNotBlank()) { "writeKey cannot be blank " }
    val conf = Configuration(
        writeKey = writeKey,
        application = context,
        storageProvider = AndroidStorageProvider
    )
    configs.invoke(conf)
    return Analytics(conf).apply {
        startup()
    }
}

// Logger instance that uses the android `Log` class
object AndroidLogger : Logger() {
    override fun log(type: LogType, message: String, event: BaseEvent?) {
        when (type) {
            LogType.ERROR -> {
                Log.e("AndroidAnalyticsLogger", "message=$message, event=$event")
            }
            LogType.WARNING -> {
                Log.w("AndroidAnalyticsLogger", "message=$message, event=$event")
            }
            LogType.INFO -> {
                Log.i("AndroidAnalyticsLogger", "message=$message, event=$event")
            }
        }
    }

    override fun flush() {}
}

// Android specific startup
private fun Analytics.startup() {
    add(AndroidLogger)
    add(AndroidContextPlugin())
    add(AndroidLifecyclePlugin())
}