package com.segment.analytics.kotlin.core

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.lang.System

class MetricsRequestFactory : RequestFactory() {
    override fun upload(apiHost: String): HttpURLConnection {
        val connection: HttpURLConnection = openConnection("https://$apiHost/m")
        connection.setRequestProperty("Content-Type", "text/plain")
        connection.doOutput = true
        connection.setChunkedStreamingMode(0)
        return connection
    }
}

@Serializable
data class RemoteMetric(
    val type: String = "Counter",
    val metric: String,
    val value: Int = 1,
    val tags: Map<String, String>
)

fun createRemoteMetric(metric: String, tags: Map<String, String>): RemoteMetric {
    var osversion = System.getProperty("os.version")
    val regex = Regex("android[0-9]+")
    val match = regex.find(osversion)
    if (match != null) {
        val majorVersion = match.value.takeLast(2) // last two characters should be major version
        osversion = "android$majorVersion"
    }
    return RemoteMetric(
        metric = metric,
        tags = tags + mapOf(
            "os" to System.getProperty("os.name") + "-" + osversion,
            "interpreter" to System.getProperty("java.vendor") + "-" + System.getProperty("java.version"),
            "library" to "analytics.kotlin",
            "library_version" to Constants.LIBRARY_VERSION
        )
    )
}

fun logError(err: Throwable) {
    println("Error sending segment performance metrics $err")
}

object Telemetry {
    private var _host: String = Constants.DEFAULT_API_HOST
    private var _sampleRate: Double = 0.1
    private var _flushTimer: Int = /*30*/ 1 * 1000 // 30s
    private var _maxQueueSize: Int = 20
    private var _httpClient: HTTPClient = HTTPClient("", MetricsRequestFactory())

    var host: String
        get() = _host
        set(value) {
            _host = value
        }

    var sampleRate: Double
        get() = _sampleRate
        set(value) {
            _sampleRate = value
        }

    var flushTimer: Int
        get() = _flushTimer
        set(value) {
            _flushTimer = value
        }

    var maxQueueSize: Int
        get() = _maxQueueSize
        set(value) {
            _maxQueueSize = value
        }

    var httpClient: HTTPClient
        get() = _httpClient
        set(value) {
            _httpClient = value
        }

    private val queue = mutableListOf<RemoteMetric>()

    init {
        if (sampleRate > 0) {
            CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    try {
                        flush()
                    } catch (e: Throwable) {
                        logError(e)
                    }
                    delay(flushTimer.toLong())
                }
            }
        }
    }

    fun increment(metric: String, tags: Map<String, String>) {
        if (!metric.startsWith("analytics_mobile.")) return
        if (tags.isEmpty()) return
        if (Math.random() > sampleRate) return
        if (queue.size >= maxQueueSize) return

        val remoteMetric = createRemoteMetric(metric, tags)
        queue.add(remoteMetric)

        if (metric.contains("error")) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    flush()
                } catch (e: Throwable) {
                    logError(e)
                }
            }
        }
    }

    fun flush() {
        if (queue.isEmpty()) return

        try {
            send()
        } catch (error: Throwable) {
            logError(error)
            sampleRate = 0.0
        }
    }

    private fun send() {
        val payload = Json.encodeToString(mapOf("series" to queue))
        queue.clear()

        try {
            val connection = httpClient.upload(host)
            connection.outputStream?.use { outputStream ->
                // Write the JSON string to the outputStream.
                outputStream.write(payload.toByteArray(Charsets.UTF_8))
                outputStream.flush() // Ensure all data is written
            }
            connection.close()
        } catch (e: Exception) {
            println(e)
        }

    }
}