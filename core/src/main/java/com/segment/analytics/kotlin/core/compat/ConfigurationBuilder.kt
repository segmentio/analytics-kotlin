package com.segment.analytics.kotlin.core.compat

import com.segment.analytics.kotlin.core.Configuration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class ConfigurationBuilder (val writeKey: String) {

    private val configuration: Configuration = Configuration(writeKey)

    fun setApplication(application: Any?) = apply { configuration.application = application }

    fun setAnalyticsScope(analyticsScope: CoroutineScope) = apply { configuration.analyticsScope = analyticsScope }

    fun setAnalyticsDispatcher(analyticsDispatcher: CoroutineDispatcher) = apply { configuration.analyticsDispatcher = analyticsDispatcher }

    fun setIODispatcher(ioDispatcher: CoroutineDispatcher) = apply { configuration.ioDispatcher = ioDispatcher }

    fun setCollectDeviceId(collectDeviceId: Boolean) = apply { configuration.collectDeviceId = collectDeviceId }

    fun setTrackApplicationLifecycleEvents(trackApplicationLifecycleEvents: Boolean) = apply { configuration.trackApplicationLifecycleEvents = trackApplicationLifecycleEvents }

    fun setUseLifecycleObserver(useLifecycleObserver: Boolean) = apply { configuration.useLifecycleObserver = useLifecycleObserver }

    fun setTrackDeepLinks(trackDeepLinks: Boolean) = apply { configuration.trackDeepLinks = trackDeepLinks }

    fun setFlushAt(flushAt: Int) = apply { configuration.flushAt = flushAt }

    fun setFlushInterval(flushInterval: Int) = apply { configuration.flushInterval = flushInterval }

    fun setAutoAddSegmentDestination(autoAddSegmentDestination: Boolean) = configuration.autoAddSegmentDestination

    fun setApiHost(apiHost: String) = configuration.apiHost

    fun build() = configuration
}