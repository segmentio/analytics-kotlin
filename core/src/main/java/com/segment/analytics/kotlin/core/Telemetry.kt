package com.segment.analytics.kotlin.core

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.lang.System
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.roundToInt

class MetricsRequestFactory : RequestFactory() {
    override fun upload(apiHost: String): HttpURLConnection {
        val connection: HttpURLConnection = openConnection("https://${apiHost}/m")
        connection.setRequestProperty("Content-Type", "text/plain")
        connection.doOutput = true
        connection.setChunkedStreamingMode(0)
        return connection
    }
}

@Serializable
// Default values will not be sent by Json.encodeToString
data class RemoteMetric(
    val type: String,
    val metric: String,
    var value: Int,
    val tags: Map<String, String>,
    val log: Map<String, String>? = null,
)
private const val METRIC_TYPE = "Counter"

fun logError(err: Throwable) {
    println("Error sending segment performance metrics $err")
}

object Telemetry {
    // Metric class for Analytics SDK
    const val INVOKE = "analytics_mobile.invoke"
    // Metric class for Analytics SDK errors
    const val INVOKE_ERROR = "analytics_mobile.invoke.error"
    // Metric class for Analytics SDK plugins
    const val INTEGRATION = "analytics_mobile.integration.invoke"
    // Metric class for Analytics SDK plugin errors
    const val INTEGRATION_ERROR = "analytics_mobile.integration.invoke.error"

    var enable: Boolean = true
    var host: String = "webhook.site/5d2ce61e-0b30-4fb6-a51c-1b87ed500c46" //Constants.DEFAULT_API_HOST
    // 1.0 is 100%, will get set by Segment setting before start()
    var sampleRate: Double = 1.0
    var flushTimer: Int = 30 * 1000 // 30s
    var httpClient: HTTPClient = HTTPClient("", MetricsRequestFactory())
    var sendWriteKeyOnError: Boolean = true
    var sendErrorLogData: Boolean = false
    var errorHandler: ((Throwable) -> Unit)? = null
    var maxQueueSize: Int = 20

    private const val MAX_QUEUE_BYTES = 28000
    var maxQueueBytes: Int = MAX_QUEUE_BYTES
        set(value) {
            field = min(value, MAX_QUEUE_BYTES)
        }

    private val queue = mutableListOf<RemoteMetric>()
    private var queueBytes = 0
    private var queueSizeExceeded = false
    private val seenErrors = mutableMapOf<String, Int>()
    private var started = false
    private var rateLimitEndTime: Long = 0

    fun start() {
        if (started || sampleRate == 0.0) return
        // Assume sampleRate is now set and everything in the queue hasn't had it applied
        if (Math.random() > sampleRate) {
            resetQueue()
        } else {
            queue.forEach {
                it.value = (it.value / sampleRate).roundToInt()
            }
        }

        started = true

        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                if (!enable) return@launch
                try {
                    flush()
                } catch (e: Throwable) {
                    logError(e)
                }
                try {
                    delay(flushTimer.toLong())
                } catch (e: CancellationException) {
                    // last flush before shutdown
                    flush()
                }
            }
        }
    }

    fun increment(metric: String, tags: Map<String, String>) {
        if (!enable || sampleRate == 0.0) return
        if (!metric.startsWith("analytics_mobile.")) return
        if (tags.isEmpty()) return
        if (Math.random() > sampleRate) return
        if (queue.size >= maxQueueSize) return

        addRemoteMetric(metric, tags, value = (1.0 / sampleRate).roundToInt())
    }

    fun error(metric:String, tags: Map<String, String>, log: String) {
        if (!enable || sampleRate == 0.0) return
        if (!metric.startsWith("analytics_mobile.")) return
        if (tags.isEmpty()) return
        if (queue.size >= maxQueueSize) return

        var filteredTags = tags
        if (!sendWriteKeyOnError) filteredTags = tags.filterKeys { it.lowercase() != "writekey" }
        var logData: String? = null
        if (sendErrorLogData) logData = log

        val errorKey = tags["error"]
        if (errorKey != null) {
            if (seenErrors.containsKey(errorKey)) {
                seenErrors[errorKey] = seenErrors[errorKey]!! + 1
                if (Math.random() > sampleRate) return
                // Send how many we've seen after the first since we know for sure.
                addRemoteMetric(metric, filteredTags, log=logData, value = seenErrors[errorKey]!!)
                seenErrors[errorKey] = 0
            } else {
                addRemoteMetric(metric, filteredTags, log=logData)
                flush()
                seenErrors[errorKey] = 0 // Zero because it's already been sent.
            }
        }
    }

    fun flush() {
        if (!started || !enable || queue.isEmpty()) return

        if (rateLimitEndTime > (System.currentTimeMillis() / 1000).toInt()) {
            return
        }
        rateLimitEndTime = 0

        try {
            send()
            queueBytes = 0
        } catch (error: Throwable) {
            logError(error)
            sampleRate = 0.0
        }
    }

    private fun send() {
        // Json.encodeToString by default does not include default values
        //  We're using this to leave off the 'log' parameter if unset.
        val payload = Json.encodeToString(mapOf("series" to queue))
        resetQueue()

        try {
            val connection = httpClient.upload(host)
            connection.outputStream?.use { outputStream ->
                // Write the JSON string to the outputStream.
                outputStream.write(payload.toByteArray(Charsets.UTF_8))
                outputStream.flush() // Ensure all data is written
            }
            connection.close()
        } catch (e: HTTPException) {
            errorHandler?.let { it(e) }
            if (e.responseCode == 429) {
                val headers = e.responseHeaders
                val rateLimit = headers["Retry-After"]?.firstOrNull()?.toLongOrNull()
                if (rateLimit != null) {
                    rateLimitEndTime = rateLimit + (System.currentTimeMillis() / 1000)
                }
            }
        } catch (e: Exception) {
            errorHandler?.let { it(e) }
        }
    }

    private val additionalTags: Map<String, String> by lazy {
        var osVersion = System.getProperty("os.version")
        val regex = Regex("android[0-9][0-9]")
        val match = regex.find(osVersion)
        if (match != null) {
            osVersion = match.value
        }
        mapOf(
            "os" to System.getProperty("os.name") + "-" + osVersion,
            "interpreter" to System.getProperty("java.vendor") + "-" + System.getProperty("java.version"),
            "library" to "analytics.kotlin",
            "library_version" to Constants.LIBRARY_VERSION
        )
    }

    private fun addRemoteMetric(metric: String, tags: Map<String, String>, value: Int = 1, log: String? = null) {
        val found = queue.find {
            it.metric == metric && it.tags == (tags + additionalTags)
        }
        if (found != null) {
            found.value += value
            return
        }

        val newMetric = RemoteMetric(
            type = METRIC_TYPE,
            metric = metric,
            value = value,
            log = log?.let { mapOf("timestamp" to LocalDateTime.now().toString(), "trace" to it) },
            tags = tags + additionalTags
        )
        val newMetricSize = newMetric.toString().toByteArray().size
        if (queueBytes + newMetricSize <= maxQueueBytes) {
            queue.add(newMetric)
            queueBytes += newMetricSize
        } else {
            queueSizeExceeded = true
        }
    }

    private fun resetQueue() {
        queue.clear()
        queueBytes = 0
        queueSizeExceeded = false
    }
}