package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog

/**
 * Reports an internal error to the user-defined error handler.
 */
fun Analytics.reportInternalError(error: Throwable) {
    configuration.errorHandler?.invoke(error)
    Analytics.reportInternalError(error)
}

fun reportErrorWithMetrics(analytics: Analytics?, error: Throwable,
                           message: String, metric:String,
                           log: String, buildTags: (MutableMap<String, String>) -> Unit) {
    analytics?.configuration?.errorHandler?.invoke(error)
    var fullMessage = message
    error.message?.let { fullMessage += ": $it"}
    Analytics.segmentLog(fullMessage)
    Telemetry.error(metric, log, buildTags)
}

fun Analytics.Companion.reportInternalError(error: Throwable) {
    error.message?.let {
        Analytics.segmentLog(it)
    }
}

fun Analytics.Companion.reportInternalError(error: String) {
    Analytics.segmentLog(error)
}