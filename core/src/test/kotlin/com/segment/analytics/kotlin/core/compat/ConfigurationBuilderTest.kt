package com.segment.analytics.kotlin.core.compat

import com.segment.analytics.kotlin.core.Configuration
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
            collectDeviceId = true,
            trackApplicationLifecycleEvents = true,
            useLifecycleObserver = true,
            trackDeepLinks = true,
            flushAt = 100,
            flushInterval = 200,
            autoAddSegmentDestination = false,
            apiHost = "test",
            cdnHost = "testCdn"
        )

        val config = builder.setApplication(expected.application)
            .setCollectDeviceId(expected.collectDeviceId)
            .setTrackApplicationLifecycleEvents(expected.trackApplicationLifecycleEvents)
            .setUseLifecycleObserver(expected.useLifecycleObserver)
            .setTrackDeepLinks(expected.trackDeepLinks)
            .setFlushAt(expected.flushAt)
            .setFlushInterval(expected.flushInterval)
            .setAutoAddSegmentDestination(expected.autoAddSegmentDestination)
            .setApiHost(expected.apiHost)
            .setCdnHost(expected.cdnHost)
            .build()

        assertEquals(expected, config)
    }
}