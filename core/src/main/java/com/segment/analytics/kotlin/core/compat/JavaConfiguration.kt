package com.segment.analytics.kotlin.core.compat

import com.segment.analytics.kotlin.core.Configuration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class JavaConfiguration (val writeKey: String) {

    internal var configuration: Configuration = Configuration(writeKey)

    fun getApplication() = configuration.application

    fun setApplication(application: Any?) = apply { configuration.application = application }

    fun getAnalyticsScope() = configuration.analyticsScope

    fun setAnalyticsScope(analyticsScope: CoroutineScope) = apply { configuration.analyticsScope = analyticsScope }

    fun getAnalyticsDispatcher() = configuration.analyticsDispatcher

    fun setAnalyticsDispatcher(analyticsDispatcher: CoroutineDispatcher) = apply { configuration.analyticsDispatcher = analyticsDispatcher }

    fun getIODispatcher() = configuration.ioDispatcher

    fun setIODispatcher(ioDispatcher: CoroutineDispatcher) = apply { configuration.ioDispatcher = ioDispatcher }

    fun isCollectDeviceId() = configuration.collectDeviceId

    fun setCollectDeviceId(collectDeviceId: Boolean) = apply { configuration.collectDeviceId = collectDeviceId }

    fun isTrackApplicationLifecycleEvents() = configuration.trackApplicationLifecycleEvents

    fun setTrackApplicationLifecycleEvents(trackApplicationLifecycleEvents: Boolean) = apply { configuration.trackApplicationLifecycleEvents = trackApplicationLifecycleEvents }

    fun isUseLifecycleObserver() = configuration.useLifecycleObserver

    fun setUseLifecycleObserver(useLifecycleObserver: Boolean) = apply { configuration.useLifecycleObserver = useLifecycleObserver }

    fun isTrackDeepLinks() = configuration.trackDeepLinks

    fun setTrackDeepLinks(trackDeepLinks: Boolean) = apply { configuration.trackDeepLinks = trackDeepLinks }

    fun getFlushAt() = configuration.flushAt

    fun setFlushAt(flushAt: Int) = apply { configuration.flushAt = flushAt }

    fun getFlushInterval() = configuration.flushInterval

    fun setFlushInterval(flushInterval: Int) = apply { configuration.flushInterval = flushInterval }

    fun isAutoAddSegmentDestination() = configuration.autoAddSegmentDestination

    fun setAutoAddSegmentDestination(autoAddSegmentDestination: Boolean) = configuration.autoAddSegmentDestination

    fun getApiHost() = configuration.apiHost

    fun setApiHost(apiHost: String) = configuration.apiHost

    fun isValid() = configuration.isValid()
}