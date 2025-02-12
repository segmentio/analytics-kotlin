package com.segment.analytics.kotlin.core

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class TelemetryTest {
    fun TelemetryResetFlushFirstError() {
        val field: Field = Telemetry::class.java.getDeclaredField("flushFirstError")
        field.isAccessible = true
        field.set(true, true)
    }
    fun TelemetryQueueSize(): Int {
        val queueField: Field = Telemetry::class.java.getDeclaredField("queue")
        queueField.isAccessible = true
        val queueValue: ConcurrentLinkedQueue<*> = queueField.get(Telemetry) as ConcurrentLinkedQueue<*>
        return queueValue.size
    }
    fun TelemetryQueueBytes(): Int {
        val queueBytesField: Field = Telemetry::class.java.getDeclaredField("queueBytes")
        queueBytesField.isAccessible = true
        return queueBytesField.get(Telemetry) as Int
    }
    fun TelemetryQueuePeek(): RemoteMetric {
        val queueField: Field = Telemetry::class.java.getDeclaredField("queue")
        queueField.isAccessible = true
        val queueValue: ConcurrentLinkedQueue<*> = queueField.get(Telemetry) as ConcurrentLinkedQueue<*>
        return queueValue.peek() as RemoteMetric
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
        Telemetry.errorHandler = ::errorHandler
        errors.clear()
        Telemetry.sampleRate = 1.0
        MockKAnnotations.init(this)
        mockTelemetryHTTPClient()
        // Telemetry.enable = true <- this will call start(), so don't do it here
    }

    @Test
    fun `Test telemetry start`() {
        Telemetry.sampleRate = 0.0
        Telemetry.enable = true
        Telemetry.start()
        assertEquals(false, TelemetryStarted)

        Telemetry.sampleRate = 1.0
        Telemetry.start()
        assertEquals(true, TelemetryStarted)
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test rolling up duplicate metrics`() {
        Telemetry.enable = true
        Telemetry.start()
        for (i in 1..3) {
            Telemetry.increment(Telemetry.INVOKE_METRIC) { it["test"] = "test" }
            Telemetry.error(Telemetry.INVOKE_ERROR_METRIC,"log") { it["test"] = "test2" }
        }
        assertEquals(2,TelemetryQueueSize())
    }

    @Test
    fun `Test increment when telemetry is disabled`() {
        Telemetry.enable = false
        Telemetry.start()
        Telemetry.increment(Telemetry.INVOKE_METRIC) { it["test"] = "test" }
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test increment with wrong metric`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.increment("wrong_metric") { it["test"] = "test" }
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test increment with no tags`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.increment(Telemetry.INVOKE_METRIC) { it.clear() }
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test error when telemetry is disabled`() {
        Telemetry.enable = false
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_ERROR_METRIC, "error") { it["test"] = "test" }
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test error with no tags`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_ERROR_METRIC, "error") { it.clear() }
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test flush works even when telemetry is not started`() {
        Telemetry.increment(Telemetry.INVOKE_METRIC) { it["test"] = "test" }
        Telemetry.flush()
        assertEquals(0,TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test flush when telemetry is disabled`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.enable = false
        Telemetry.increment(Telemetry.INVOKE_METRIC) { it["test"] = "test" }
        assertTrue(TelemetryQueueSize() == 0)
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test flush with empty queue`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.flush()
        assertEquals(0, TelemetryQueueSize())
        assertEquals(0,errors.size)
    }

    @Test
    fun `Test HTTP Exception`() {
        mockTelemetryHTTPClient(shouldThrow = true)
        TelemetryResetFlushFirstError()
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_METRIC,"log") { it["error"] = "test" }
        assertEquals(0,TelemetryQueueSize())
        assertEquals(1,errors.size)
    }

    @Test
    fun `Test increment and error methods when queue is full`() {
        Telemetry.enable = true
        Telemetry.start()
        for (i in 1..Telemetry.maxQueueSize + 1) {
            Telemetry.increment(Telemetry.INVOKE_METRIC) { it["test"] = "test" + i }
            Telemetry.error(Telemetry.INVOKE_ERROR_METRIC, "error") { it["error"] = "test" + i }
        }
        assertEquals(Telemetry.maxQueueSize, TelemetryQueueSize())
    }

    @Test
    fun `Test error method with different flag settings`() {
        val longString = CharArray(1000) { 'a' }.joinToString("")
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.sendWriteKeyOnError = false
        Telemetry.sendErrorLogData = false
        Telemetry.error(Telemetry.INVOKE_ERROR_METRIC, longString) { it["writekey"] = longString }
        assertTrue(TelemetryQueueBytes() < 1000)
    }

    @Test
    fun testConcurrentErrorReportingWithQueuePressure() {
        val operationCount = 200
        val latch = CountDownLatch(operationCount)
        val executor = Executors.newFixedThreadPool(3)

        try {
            // Launch operations across multiple threads
            repeat(operationCount) { i ->
                executor.submit {
                    try {
                        Telemetry.error(
                            metric = Telemetry.INVOKE_ERROR_METRIC,
                            log = "High pressure test $i"
                        ) {
                            it["error"] = "pressure_test_key"
                            it["iteration"] = "$i"
                        }

                        // Add random delays to increase race condition probability
                        if (i % 5 == 0) {
                            Thread.sleep(Random.nextLong(1, 3))
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // Wait for all operations to complete
            latch.await(15, TimeUnit.SECONDS)

        } finally {
            executor.shutdown()
        }
        assertTrue(TelemetryQueueSize() == Telemetry.maxQueueSize)
    }

    @Test
    fun `Test error tags are cleaned`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_ERROR_METRIC, "error log") {
            it["error"] = "foo_192.168.0.1:8080"
        }
        assertEquals(1, TelemetryQueueSize())
        assertEquals("foo__IP", TelemetryQueuePeek().tags["error"])
    }

    @Test
    fun `Test error tags are cleaned for IPv6`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_ERROR_METRIC, "error log") {
            it["error"] = "foo_2001:0db8:85a3:0000:0000:8a2e:0370:7334"
        }
        assertEquals(1, TelemetryQueueSize())
        assertEquals("foo__IP", TelemetryQueuePeek().tags["error"])
    }

    @Test
    fun `Test error tags are cleaned for hex values`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_ERROR_METRIC, "error log") {
            it["error"] = "foo_0x1234567890abcdef_bar"
        }
        assertEquals(1, TelemetryQueueSize())
        assertEquals("foo_0x00_bar", TelemetryQueuePeek().tags["error"])
    }

    @Test
    fun `Test error tags are cleaned for sneaky hex values`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_ERROR_METRIC, "error log") {
            it["error"] = "address_deadbeef_face"
        }
        assertEquals(1, TelemetryQueueSize())
        assertEquals("address_0x00_face", TelemetryQueuePeek().tags["error"])
    }

    @Test
    fun `Test error tags are cleaned for mangled library names`() {
        Telemetry.enable = true
        Telemetry.start()
        Telemetry.error(Telemetry.INVOKE_ERROR_METRIC, "error log") {
            it["error"] = "a5.b:_some_error_etc"
        }
        assertEquals(1, TelemetryQueueSize())
        assertEquals("_some_error_etc", TelemetryQueuePeek().tags["error"])
    }
}