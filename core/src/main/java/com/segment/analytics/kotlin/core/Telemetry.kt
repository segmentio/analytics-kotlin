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
data class RemoteMetric(
    val type: String,
    val metric: String,
    var value: Int,
    val tags: Map<String, String>,
    val log: Map<String, String>? = null,
)

fun logError(err: Throwable) {
    println("Error sending segment performance metrics $err")
}

object Telemetry {
    private var _enable: Boolean = true
    private var _host: String = Constants.DEFAULT_API_HOST
    private var _sampleRate: Double = 1.0
    private var _flushTimer: Int = 30 * 1000 // 30s
    private var _maxQueueSize: Int = 20
    private var MAX_QUEUE_BYTES = 28000
    private var _maxQueueBytes: Int = MAX_QUEUE_BYTES
    private var _httpClient: HTTPClient = HTTPClient("", MetricsRequestFactory())
    private var _sendWriteKeyOnError: Boolean = true
    private var _sendErrorLogData: Boolean = false
    private var _errorHandler: ((Throwable) -> Unit)? = null
    private var _rateLimitTimer: Int = 0

    var enable: Boolean
        get() = _enable
        set(value) {
            _enable = value
        }
    var sendWriteKeyOnError: Boolean
        get() = _sendWriteKeyOnError
        set(value) {
            _sendWriteKeyOnError = value
        }

    var sendErrorLogData: Boolean
        get() = _sendErrorLogData
        set(value) {
            _sendErrorLogData = value
        }
    var errorHandler: ((Throwable) -> Unit)?
        get() = _errorHandler
        set(value) {
            _errorHandler = value
        }

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

    var maxQueueBytes: Int
        get() = _maxQueueBytes
        set(value) {
            _maxQueueBytes = min(value, MAX_QUEUE_BYTES)
        }

    var httpClient: HTTPClient
        get() = _httpClient
        set(value) {
            _httpClient = value
        }

    private val queue = mutableListOf<RemoteMetric>()
    private var queueBytes = 0
    private var queueSizeExceeded = false
    private val seenErrors = mutableMapOf<String, Int>()
    private var started = false

    init {
        if (sampleRate > 0) {
            CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    try {
                        if(started) flush()
                    } catch (e: Throwable) {
                        logError(e)
                    }
                    try {
                        delay(flushTimer.toLong())
                    } catch (e: CancellationException) {
                        // last flush before shutdown
                        if(started) flush()
                    }
                }
            }
        }
    }

    fun start() {
        if (started) return
        // Assume sampleRate is now set and everything in the queue hasn't had it applied
        if (Math.random() > sampleRate) {
            resetQueue()
        } else {
            queue.forEach {
                it.value = (it.value / sampleRate).roundToInt()
            }
        }

        started = true
    }

    fun increment(metric: String, tags: Map<String, String>) {
        if (!_enable) return
        if (!metric.startsWith("analytics_mobile.")) return
        if (tags.isEmpty()) return
        if (Math.random() > sampleRate) return
        if (queue.size >= maxQueueSize) return

        addRemoteMetric(metric, tags, value = (1.0 / sampleRate).roundToInt())
    }

    fun error(metric:String, tags: Map<String, String>, log: String) {
        if (!enable) return
        if (!metric.startsWith("analytics_mobile.")) return
        if (tags.isEmpty()) return
        if (queue.size >= maxQueueSize) return

        var filteredTags = tags
        if (!sendWriteKeyOnError) filteredTags = tags.filterKeys { it != "writekey" }
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
        if (queue.isEmpty()) return

        if (_rateLimitTimer > 0 && _rateLimitTimer > (System.currentTimeMillis() / 1000).toInt()) {
            return
        }
        _rateLimitTimer = 0

        try {
            send()
            queueBytes = 0
        } catch (error: Throwable) {
            logError(error)
            sampleRate = 0.0
        }
    }

    private val json = Json

    private fun send() {
        val payload = json.encodeToString(mapOf("series" to queue))
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
            _errorHandler?.let { it(e) }
            if (e.responseCode != 429) {
                val headers = e.responseHeaders
                val rateLimit = headers["X-RateLimit-Reset"]?.firstOrNull()?.toLongOrNull()
                if (rateLimit != null) {
                    _rateLimitTimer = rateLimit.toInt() + (System.currentTimeMillis() / 1000).toInt()
                }
            }
        } catch (e: Exception) {
            _errorHandler?.let { it(e) }
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
            type = "Counter",
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