package com.segment.analytics.kotlin.core

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Field
import java.net.HttpURLConnection

class TelemetryTest {
    fun TelemetryQueueSize(): Int {
        val queueField: Field = Telemetry::class.java.getDeclaredField("queue")
        queueField.isAccessible = true
        val queueValue: List<*> = queueField.get(Telemetry) as List<*>
        return queueValue.size
    }
    fun TelemetryQueueBytes(): Int {
        val queueBytesField: Field = Telemetry::class.java.getDeclaredField("queueBytes")
        queueBytesField.isAccessible = true
        return queueBytesField.get(Telemetry) as Int
    }
    var TelemetryStarted: Boolean
        get() {
            val startedField: Field = Telemetry::class.java.getDeclaredField("started")
            startedField.isAccessible = true
            return startedField.get(Telemetry) as Boolean
        }
        set(value) {
            val startedField: Field = Telemetry::class.java.getDeclaredField("started")
            startedField.isAccessible = true
            startedField.set(Telemetry, value)
        }

    val errors: MutableList<String> = mutableListOf()

    fun errorHandler(error: Throwable) {
        errors.add(error.toString() + error.stackTraceToString())
    }

    fun mockTelemetryHTTPClient(telemetryHost: String = Telemetry.host, shouldThrow: Boolean = false) {
        val httpClient: HTTPClient = mockk()
        val httpConnection: HttpURLConnection = mockk(relaxed = true)
        val connection = object : Connection(httpConnection, null, null) {}

        if (shouldThrow) {
            every { httpClient.upload(any()) } throws Exception("Test exception")
        } else {
            every { httpClient.upload(any()) } returns connection
        }

        // Replace the actual HTTPClient instance with the mocked one
        Telemetry.httpClient = httpClient
    }

    @BeforeEach
    fun setup() {
        Telemetry.reset()
        Telemetry.enable = true
        Telemetry.errorHandler = ::errorHandler
        errors.clear()
        Telemetry.sampleRate = 1.0
        MockKAnnotations.init(this)
        mockTelemetryHTTPClient()
    }

    @Test
    fun `Test telemetry start`() {
        Telemetry.sampleRate = 0.0
        Telemetry.start()
        assertEquals(false, TelemetryStarted)

        Telemetry.sampleRate = 1.0
        Telemetry.start()
        assertEquals(true, TelemetryStarted)
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test rolling up duplicate metrics`() {
        Telemetry.start()
        for (i in 1..3) {
            Telemetry.increment(Telemetry.INVOKE,mapOf("test" to "test"))
            Telemetry.error(Telemetry.INVOKE_ERROR,mapOf("test" to "test2"),"log")
        }
        assertEquals(2,TelemetryQueueSize())
    }

    @Test
    fun `Test increment when telemetry is disabled`() {
        Telemetry.enable = false
        Telemetry.start()
        Telemetry.increment(Telemetry.INVOKE, mapOf("test" to "test"))
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test increment with wrong metric`() {
        Telemetry.start()
        Telemetry.increment("wrong_metric", mapOf("test" to "test"))
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test increment with no tags`() {
        Telemetry.start()
        Telemetry.increment(Telemetry.INVOKE, emptyMap())
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test error when telemetry is disabled`() {
        Telemetry.enable = false
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_ERROR, mapOf("test" to "test"), "error")
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test error with no tags`() {
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_ERROR, emptyMap(), "error")
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test flush works even when telemetry is not started`() {
        Telemetry.increment(Telemetry.INVOKE, mapOf("test" to "test"))
        Telemetry.flush()
        assertEquals(0,TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test flush when telemetry is disabled`() {
        Telemetry.start()
        Telemetry.enable = false
        Telemetry.increment(Telemetry.INVOKE, mapOf("test" to "test"))
        assertTrue(TelemetryQueueSize() == 0)
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test flush with empty queue`() {
        Telemetry.start()
        Telemetry.flush()
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test HTTP Exception`() {
        mockTelemetryHTTPClient(shouldThrow = true)
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE, mapOf("error" to "test"),"log")
        assertEquals(0,TelemetryQueueSize())
        assertEquals(1,errors.size)
    }

    @Test
    fun `Test increment and error methods when queue is full`() {
        Telemetry.start()
        for (i in 1..Telemetry.maxQueueSize + 1) {
            Telemetry.increment(Telemetry.INVOKE, mapOf("test" to "test" + i))
            Telemetry.error(Telemetry.INVOKE_ERROR, mapOf("test" to "test" + i), "error")
        }
        assertEquals(Telemetry.maxQueueSize, TelemetryQueueSize())
    }

    @Test
    fun `Test error method with different flag settings`() {
        val longString = CharArray(1000) { 'a' }.joinToString("")
        Telemetry.start()
        Telemetry.sendWriteKeyOnError = false
        Telemetry.sendErrorLogData = false
        Telemetry.error(Telemetry.INVOKE_ERROR, mapOf("writekey" to longString), longString)
        assertTrue(TelemetryQueueSize() < 1000)
    }
}
