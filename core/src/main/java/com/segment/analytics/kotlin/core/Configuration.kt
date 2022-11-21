package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.DEFAULT_API_HOST
import com.segment.analytics.kotlin.core.Constants.DEFAULT_CDN_HOST
import com.segment.analytics.kotlin.core.platform.policies.FlushPolicy
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import kotlinx.coroutines.*
import sovran.kotlin.Store

/**
 * Configuration that analytics can use
 * @property writeKey the Segment writeKey
 * @property application defaults to `null`
 * @property storageProvider Provider for storage class, defaults to `ConcreteStorageProvider`
 * @property collectDeviceId collect deviceId, defaults to `false`
 * @property trackApplicationLifecycleEvents automatically send track for Lifecycle events (eg: Application Opened, Application Backgrounded, etc.), defaults to `false`
 * @property useLifecycleObserver enables the use of LifecycleObserver to track Application lifecycle events. Defaults to `false`.
 * @property trackDeepLinks automatically track [Deep link][https://developer.android.com/training/app-links/deep-linking] opened based on intents, defaults to `false`
 * @property flushAt count of events at which we flush events, defaults to `20`
 * @property flushInterval interval in seconds at which we flush events, defaults to `30 seconds`
 * @property defaultSettings Settings object that will be used as fallback in case of network failure, defaults to empty
 * @property autoAddSegmentDestination automatically add SegmentDestination plugin, defaults to `true`
 * @property apiHost set a default apiHost to which Segment sends events, defaults to `api.segment.io/v1`
 */
data class Configuration(
    val writeKey: String,
    var application: Any? = null,
    val storageProvider: StorageProvider = ConcreteStorageProvider,
    var collectDeviceId: Boolean = false,
    var trackApplicationLifecycleEvents: Boolean = false,
    var useLifecycleObserver: Boolean = false,
    var trackDeepLinks: Boolean = false,
    var flushAt: Int = 20,
    var flushInterval: Int = 30,
    var flushPolicies: Array<FlushPolicy> = emptyArray<FlushPolicy>(),
    val defaultSettings: Settings = Settings(),
    var autoAddSegmentDestination: Boolean = true,
    var apiHost: String = DEFAULT_API_HOST,
    var cdnHost: String = DEFAULT_CDN_HOST
) {
    fun isValid(): Boolean {
        return writeKey.isNotBlank() && application != null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Configuration

        if (writeKey != other.writeKey) return false
        if (application != other.application) return false
        if (storageProvider != other.storageProvider) return false
        if (collectDeviceId != other.collectDeviceId) return false
        if (trackApplicationLifecycleEvents != other.trackApplicationLifecycleEvents) return false
        if (useLifecycleObserver != other.useLifecycleObserver) return false
        if (trackDeepLinks != other.trackDeepLinks) return false
        if (flushAt != other.flushAt) return false
        if (flushInterval != other.flushInterval) return false
        if (!flushPolicies.contentEquals(other.flushPolicies)) return false
        if (defaultSettings != other.defaultSettings) return false
        if (autoAddSegmentDestination != other.autoAddSegmentDestination) return false
        if (apiHost != other.apiHost) return false
        if (cdnHost != other.cdnHost) return false

        return true
    }

    override fun hashCode(): Int {
        var result = writeKey.hashCode()
        result = 31 * result + (application?.hashCode() ?: 0)
        result = 31 * result + storageProvider.hashCode()
        result = 31 * result + collectDeviceId.hashCode()
        result = 31 * result + trackApplicationLifecycleEvents.hashCode()
        result = 31 * result + useLifecycleObserver.hashCode()
        result = 31 * result + trackDeepLinks.hashCode()
        result = 31 * result + flushAt
        result = 31 * result + flushInterval
        result = 31 * result + flushPolicies.contentHashCode()
        result = 31 * result + defaultSettings.hashCode()
        result = 31 * result + autoAddSegmentDestination.hashCode()
        result = 31 * result + apiHost.hashCode()
        result = 31 * result + cdnHost.hashCode()
        return result
    }

}

interface CoroutineConfiguration {
    val store: Store

    val analyticsScope: CoroutineScope

    val analyticsDispatcher: CoroutineDispatcher

    val networkIODispatcher: CoroutineDispatcher

    val fileIODispatcher: CoroutineDispatcher
}