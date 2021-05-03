package com.segment.analytics

import android.content.Context
import android.util.Log
import com.segment.analytics.platform.plugins.LogType
import com.segment.analytics.platform.plugins.Logger
import com.segment.analytics.platform.plugins.android.AndroidContextPlugin
import com.segment.analytics.platform.plugins.android.AndroidLifecyclePlugin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

// A set of functions tailored to the Android implementation of analytics

@Suppress("FunctionName")
// constructor function to build android specific analytics in dsl format
// Usage: Analytics("$writeKey", applicationContext, applicationScope)
public fun Analytics(
    writeKey: String,
    context: Context,
    coroutineScope: CoroutineScope,
    analyticsDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): Analytics {
    require(writeKey.isNotBlank()) { "writeKey cannot be blank " }
    val conf = Configuration(
        writeKey = writeKey,
        analyticsScope = coroutineScope,
        analyticsDispatcher = analyticsDispatcher,
        ioDispatcher = ioDispatcher,
        application = context
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
        application = context
    )
    configs.invoke(conf)
    return Analytics(conf).apply {
        startup()
    }
}

// Logger instance that uses the android `Log` class
object AndroidLogger: Logger("AndroidLogger") {
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