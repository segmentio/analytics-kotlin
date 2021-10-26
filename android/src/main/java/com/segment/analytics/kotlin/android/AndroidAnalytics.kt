package com.segment.analytics.kotlin.android

import android.content.Context
import com.segment.analytics.kotlin.android.plugins.AndroidContextPlugin
import com.segment.analytics.kotlin.android.plugins.AndroidLifecyclePlugin
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.platform.plugins.logger.*

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

// Android specific startup
private fun Analytics.startup() {
    add(SegmentLog())
    add(AndroidContextPlugin())
    add(AndroidLifecyclePlugin())
}