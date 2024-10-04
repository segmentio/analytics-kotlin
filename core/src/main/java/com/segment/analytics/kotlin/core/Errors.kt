package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog
sealed class AnalyticsError(): Throwable() {
    data class StorageUnableToCreate(override val message: String?): AnalyticsError()
    data class StorageUnableToWrite(override val message: String?): AnalyticsError()
    data class StorageUnableToRename(override val message: String?): AnalyticsError()
    data class StorageUnableToOpen(override val message: String?): AnalyticsError()
    data class StorageUnableToClose(override val message: String?): AnalyticsError()
    data class StorageInvalid(override val message: String?): AnalyticsError()
    data class StorageUnknown(override val message: String?, override val cause: Throwable?): AnalyticsError()
    data class NetworkUnexpectedHTTPCode(override val message: String?): AnalyticsError()
    data class NetworkServerLimited(override val message: String?): AnalyticsError()
    data class NetworkServerRejected(override val message: String?): AnalyticsError()
    data class NetworkUnknown(override val message: String?, override val cause: Throwable?): AnalyticsError()
    data class NetworkInvalidData(override val message: String?): AnalyticsError()
    data class JsonUnableToSerialize(override val message: String?, override val cause: Throwable?): AnalyticsError()
    data class JsonUnableToDeserialize(override val message: String?, override val cause: Throwable?): AnalyticsError()
    data class JsonUnknown(override val message: String?, override val cause: Throwable?): AnalyticsError()
    data class PluginError(override val message: String?, override val cause: Throwable?): AnalyticsError()
    data class EnrichmentError(override val message: String?): AnalyticsError()
    data class SettingsFetchError(override val message: String?, override val cause: Throwable?): AnalyticsError()
}

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