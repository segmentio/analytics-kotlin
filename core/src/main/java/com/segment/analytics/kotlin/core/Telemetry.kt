package com.segment.analytics.kotlin.core

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import java.net.HttpURLConnection

class MetricsRequestFactory : RequestFactory() {
    override fun upload(apiHost: String): HttpURLConnection {
        val connection: HttpURLConnection = openConnection("https://webhook.site/6fd5d19e-fb17-46e5-940d-27628dd8991a")
        connection.setRequestProperty("Content-Type", "text/plain")
        connection.doOutput = true
        connection.setChunkedStreamingMode(0)
        return connection
    }
}

const val SEGMENT_API_HOST = "api.segment.io"

data class MetricsOptions(
    val host: String? = null,
    val sampleRate: Double? = null,
    val flushTimer: Int? = null,
    val maxQueueSize: Int? = null
)

@Serializable
data class RemoteMetric(
    val type: String = "Counter",
    val metric: String,
    val value: Int = 1,
    val tags: Map<String, String>
)

fun createRemoteMetric(metric: String, tags: List<String>): RemoteMetric {
    val formattedTags = tags.map { it.split(":") }.associate { it[0] to it[1] }

    return RemoteMetric(
        metric = metric,
        tags = formattedTags + mapOf(
            "library" to "analytics.kotlin",
            "library_version" to Constants.LIBRARY_VERSION
        )
    )
}

fun logError(err: Throwable) {
    println("Error sending segment performance metrics $err")
}

class Telemetry(options: MetricsOptions? = null) {
    private val client = HTTPClient("", MetricsRequestFactory())
    private var host: String = options?.host ?: SEGMENT_API_HOST
    private var sampleRate: Double = options?.sampleRate ?: 1.0
    private val flushTimer: Int = options?.flushTimer ?: (/*30*/ 1 * 1000) // 30s
    private val maxQueueSize: Int = options?.maxQueueSize ?: 20

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

    fun increment(metric: String, tags: List<String>) {
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
            val connection = client.upload(host)
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