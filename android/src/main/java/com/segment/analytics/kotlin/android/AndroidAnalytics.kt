package com.segment.analytics.kotlin.android

import android.content.Context
import android.content.Intent
import android.util.Log
import com.segment.analytics.kotlin.android.plugins.AndroidContextPlugin
import com.segment.analytics.kotlin.android.plugins.AndroidLifecyclePlugin
import com.segment.analytics.kotlin.android.utilities.DeepLinkUtils
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.platform.plugins.logger.*

// A set of functions tailored to the Android implementation of analytics

@Suppress("FunctionName")
/**
 * constructor function to build android specific analytics in dsl format
 * Usage: Analytics("$writeKey", applicationContext, applicationScope)
 *
 * NOTE: this method should only be used for Android application. Context is required.
 */
public fun Analytics(
    writeKey: String,
    context: Context,
    logger: Logger = AndroidLogger(),
): Analytics {
    require(writeKey.isNotBlank()) { "writeKey cannot be blank " }
    val conf = Configuration(
        writeKey = writeKey,
        application = context,
        storageProvider = AndroidStorageProvider
    )
    return Analytics(conf).apply {
        startup(logger)
    }
}

@Suppress("FunctionName")
/**
 * constructor function to build android specific analytics in dsl format with config options
 * Usage: Analytics("$writeKey", applicationContext) {
 *            this.analyticsScope = applicationScope
 *            this.collectDeviceId = false
 *            this.flushAt = 10
 *        }
 *
 * NOTE: this method should only be used for Android application. Context is required.
 */
public fun Analytics(
    writeKey: String,
    context: Context,
    configs: Configuration.() -> Unit,
    logger: Logger = AndroidLogger(),
): Analytics {
    require(writeKey.isNotBlank()) { "writeKey cannot be blank " }
    val conf = Configuration(
        writeKey = writeKey,
        application = context,
        storageProvider = AndroidStorageProvider
    )
    configs.invoke(conf)
    return Analytics(conf).apply {
        startup(logger)
    }
}

// Android specific startup
private fun Analytics.startup(logger: Logger) {
    add(AndroidContextPlugin())
    add(AndroidLifecyclePlugin())
    Analytics.setLogger(logger)
}

/**
 * Track a deep link manually.
 *
 * This function is meant to be called by the user in places were we need to manually track a
 * deep link opened event. For example, in the cause of an Activity being sent a new intent in it's
 * onNewIntent() function. The URI that triggered the intent will be in the Intent.data property.
 *
 * @param referrer: The string representing the app or url that caused the deep link to be activated.
 * @param intent: The intent received by the Activity's onNewIntent() function.
 */
fun Analytics.openUrl(referrer: String?, intent: Intent?) {
    DeepLinkUtils(this).trackDeepLinkFrom(referrer, intent)
}

class AndroidLogger: Logger {
    override fun parseLog(log: LogMessage) {

        when (log.kind) {
            LogKind.ERROR -> {
                Log.e("AndroidLog", "message=${log.message}")
            }
            LogKind.WARNING -> {
                Log.w("AndroidLog", "message=${log.message}")
            }
            LogKind.DEBUG -> {
                Log.d("AndroidLog", "message=${log.message}")
            }
        }
    }
}
