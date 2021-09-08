package com.segment.analytics.kotlin.core.compat

import com.segment.analytics.kotlin.core.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

internal class ConfigurationBuilderTest {

    lateinit var builder: ConfigurationBuilder

    private val writeKey = "123"

    @BeforeEach
    internal fun setUp() {
        builder = ConfigurationBuilder(writeKey)
    }

    @Test
    fun setApplication() {
        val config = builder.setApplication(this).build()

        assertEquals(this, config.application)
    }

    @Test
    fun setAnalyticsScope() {
        val superviseScope = CoroutineScope(SupervisorJob())
        val config = builder.setAnalyticsScope(superviseScope).build()

        assertEquals(superviseScope, config.analyticsScope)
    }

    @Test
    fun setAnalyticsDispatcher() {
        val dispatcher = Dispatchers.Unconfined
        val config = builder.setAnalyticsDispatcher(dispatcher).build()

        assertEquals(dispatcher, config.analyticsDispatcher)
    }

    @Test
    fun setIODispatcher() {
        val dispatcher = Dispatchers.Unconfined
        val config = builder.setIODispatcher(dispatcher).build()

        assertEquals(dispatcher, config.ioDispatcher)
    }

    @Test
    fun setCollectDeviceId() {
        val expected = true
        val config = builder.setCollectDeviceId(expected).build()

        assertEquals(expected, config.collectDeviceId)
    }

    @Test
    fun setTrackApplicationLifecycleEvents() {
        val expected = true
        val config = builder.setTrackApplicationLifecycleEvents(expected).build()

        assertEquals(expected, config.trackApplicationLifecycleEvents)
    }

    @Test
    fun setUseLifecycleObserver() {
        val expected = true
        val config = builder.setUseLifecycleObserver(expected).build()

        assertEquals(expected, config.useLifecycleObserver)
    }

    @Test
    fun setTrackDeepLinks() {
        val expected = true
        val config = builder.setTrackDeepLinks(expected).build()

        assertEquals(expected, config.trackDeepLinks)
    }

    @Test
    fun setFlushAt() {
        val expected = 100
        val config = builder.setFlushAt(expected).build()

        assertEquals(expected, config.flushAt)
    }

    @Test
    fun setFlushInterval() {
        val expected = 200
        val config = builder.setFlushInterval(expected).build()

        assertEquals(expected, config.flushInterval)
    }

    @Test
    fun setAutoAddSegmentDestination() {
        val expected = false
        val config = builder.setAutoAddSegmentDestination(expected).build()

        assertEquals(expected, config.autoAddSegmentDestination)
    }

    @Test
    fun setApiHost() {
        val expected = "test"
        val config = builder.setApiHost(expected).build()

        assertEquals(expected, config.apiHost)
    }

    @Test
    fun setCdnHost() {
        val expected = "test"
        val config = builder.setCdnHost(expected).build()

        assertEquals(expected, config.cdnHost)
    }

    @Test
    fun build() {
        val expected = Configuration(
            writeKey = writeKey,
            application = this,
            analyticsScope = CoroutineScope(SupervisorJob()),
            analyticsDispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            collectDeviceId = true,
            trackApplicationLifecycleEvents = true,
            useLifecycleObserver = true,
            trackDeepLinks = true,
            flushAt = 100,
            flushInterval = 200,
            autoAddSegmentDestination = false,
            apiHost = "test"
        )

        val config = builder.setApplication(expected.application)
            .setAnalyticsScope(expected.analyticsScope)
            .setAnalyticsDispatcher(expected.analyticsDispatcher)
            .setIODispatcher(expected.ioDispatcher)
            .setCollectDeviceId(expected.collectDeviceId)
            .setTrackApplicationLifecycleEvents(expected.trackApplicationLifecycleEvents)
            .setUseLifecycleObserver(expected.useLifecycleObserver)
            .setTrackDeepLinks(expected.trackDeepLinks)
            .setFlushAt(expected.flushAt)
            .setFlushInterval(expected.flushInterval)
            .setAutoAddSegmentDestination(expected.autoAddSegmentDestination)
            .setApiHost(expected.apiHost)
            .build()

        assertEquals(expected, config)
    }
}