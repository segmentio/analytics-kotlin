package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog

fun Analytics.reportInternalError(error: Throwable) {
    configuration.errorHandler?.invoke(error)
    Analytics.reportInternalError(error)
}

fun Analytics.Companion.reportInternalError(error: Throwable) {
    error.message?.let {
        Analytics.segmentLog(it)
    }
}

fun Analytics.Companion.reportInternalError(error: String) {
    Analytics.segmentLog(error)
}