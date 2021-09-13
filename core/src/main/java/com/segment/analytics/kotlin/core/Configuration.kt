package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.DEFAULT_API_HOST
import com.segment.analytics.kotlin.core.Constants.DEFAULT_CDN_HOST
import com.segment.analytics.kotlin.core.utilities.ConcreteStorageProvider
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * Configuration that analytics can use
 * @property writeKey the Segment writeKey
 * @property application defaults to `null`
 * @property analyticsScope CoroutineScope on which all analytics coroutines will run, defaults to `MainScope()`
 * @property analyticsDispatcher Dispatcher running analytics tasks, defaults to `Executors.newSingleThreadExecutor().asCoroutineDispatcher()`
 * @property ioDispatcher Dispatcher running IO tasks, defaults to `Dispatchers.IO`
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
    val defaultSettings: Settings = Settings(),
    var autoAddSegmentDestination: Boolean = true,
    var apiHost: String = DEFAULT_API_HOST,
    var cdnHost: String = DEFAULT_CDN_HOST
) {
    internal var analyticsDispatcher: CoroutineDispatcher = Dispatchers.IO

    internal var ioDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(1)
        .asCoroutineDispatcher()

    internal var analyticsScope: CoroutineScope = CoroutineScope(SupervisorJob())

    fun isValid(): Boolean {
        return writeKey.isNotBlank() && application != null
    }
}