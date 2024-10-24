package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog
import java.net.URL

sealed class AnalyticsError(): Throwable() {
    data class StorageUnableToCreate(override val message: String?): AnalyticsError()
    data class StorageUnableToWrite(override val message: String?): AnalyticsError()
    data class StorageUnableToRename(override val message: String?): AnalyticsError()
    data class StorageUnableToOpen(override val message: String?): AnalyticsError()
    data class StorageUnableToClose(override val message: String?): AnalyticsError()
    data class StorageInvalid(override val message: String?): AnalyticsError()
    data class StorageUnknown(override val cause: Throwable?): AnalyticsError()

    data class NetworkUnexpectedHTTPCode(val uri: URL?, val code: Int): AnalyticsError()
    data class NetworkServerLimited(val uri: URL?, val code: Int): AnalyticsError()
    data class NetworkServerRejected(val uri: URL?, val code: Int): AnalyticsError()
    data class NetworkUnknown(val uri: URL?, override val cause: Throwable?): AnalyticsError()
    data class NetworkInvalidData(override val message: String?): AnalyticsError()

    data class JsonUnableToSerialize(override val cause: Throwable?): AnalyticsError()
    data class JsonUnableToDeserialize(override val cause: Throwable?): AnalyticsError()
    data class JsonUnknown(override val cause: Throwable?): AnalyticsError()

    data class PluginError(override val cause: Throwable?): AnalyticsError()

    data class EnrichmentError(override val message: String): AnalyticsError()

    data class SettingsFail(override val cause: AnalyticsError): AnalyticsError()
    data class BatchUploadFail(override val cause: AnalyticsError): AnalyticsError()
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