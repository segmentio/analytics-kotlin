package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.utilities.SegmentInstant
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.net.HttpURLConnection
import java.lang.System
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.Channel

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
    Analytics.reportInternalError(err)
}

/**
 * A class for sending telemetry data to Segment.
 * This system is used to gather usage and error data from the SDK for the purpose of improving the SDK.
 * It can be disabled at any time by setting Telemetry.enable to false.
 * Errors are sent with a write key, which can be disabled by setting Telemetry.sendWriteKeyOnError to false.
 * All data is downsampled and no PII is collected.
 */
object Telemetry: Subscriber {
    private const val METRICS_BASE_TAG = "analytics_mobile"
    // Metric class for Analytics SDK
    const val INVOKE_METRIC = "$METRICS_BASE_TAG.invoke"
    // Metric class for Analytics SDK errors
    const val INVOKE_ERROR_METRIC = "$METRICS_BASE_TAG.invoke.error"
    // Metric class for Analytics SDK plugins
    const val INTEGRATION_METRIC = "$METRICS_BASE_TAG.integration.invoke"
    // Metric class for Analytics SDK plugin errors
    const val INTEGRATION_ERROR_METRIC = "$METRICS_BASE_TAG.integration.invoke.error"

    /**
     * Enables or disables telemetry.
     */
    var enable: Boolean = true
        set(value) {
            field = value
            if(value) {
                // We don't want to start telemetry until two conditions are met:
                // Telemetry.enable is set to true
                // Settings from the server have adjusted the sampleRate
                // start is called in both places
                start()
            }
        }

    var host: String = Constants.DEFAULT_API_HOST
    // 1.0 is 100%, will get set by Segment setting before start()
    // Values are adjusted by the sampleRate on send
    private var sampleRate = AtomicReference<Double>(1.0)
    private var flushTimer: Int = 30 * 1000 // 30s
    var httpClient: HTTPClient = HTTPClient("", MetricsRequestFactory())
    var sendWriteKeyOnError: Boolean = true
    var sendErrorLogData: Boolean = false
    var errorHandler: ((Throwable) -> Unit)? = ::logError
    private var maxQueueSize: Int = 20
    private var errorLogSizeMax: Int = 4000

    private const val MAX_QUEUE_BYTES = 28000
    var maxQueueBytes: Int = MAX_QUEUE_BYTES
        set(value) {
            field = min(value, MAX_QUEUE_BYTES)
        }

    private val queue = ConcurrentLinkedQueue<RemoteMetric>()
    private var queueBytes = AtomicInteger(0)
    private var started = AtomicBoolean(false)
    private var rateLimitEndTime: Long = 0
    private var flushFirstError = AtomicBoolean(true)
    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        errorHandler?.let {
            it( Exception(
                "Caught Exception in Telemetry Scope: ${t.message}",
                t
            ))
        }
    }
    private var telemetryScope: CoroutineScope = CoroutineScope(SupervisorJob() + exceptionHandler)
    private var telemetryDispatcher: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var telemetryJob: Job? = null

    private val flushChannel = Channel<Unit>(Channel.UNLIMITED)

    // Start a coroutine to process flush requests
    init {
        telemetryScope.launch(telemetryDispatcher) {
            for (event in flushChannel) {
                performFlush()
            }
        }
    }

    /**
     * Starts the telemetry if it is enabled and not already started, and the sample rate is greater than 0.
     * Called automatically when Telemetry.enable is set to true and when configuration data is received from Segment.
     */
    fun start() {
        if (!enable || started.get() || sampleRate.get() == 0.0) return
        started.set(true)

        // Everything queued was sampled at default 100%, downsample adjustment and send will adjust values
        if (Math.random() > sampleRate.get()) {
            resetQueue()
        }

        telemetryJob = telemetryScope.launch(telemetryDispatcher) {
            while (isActive) {
                if (!enable) {
                    started.set(false)
                    return@launch
                }
                try {
                    flush()
                } catch (e: Throwable) {
                    errorHandler?.let { it(e) }
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

    /**
     * Resets the telemetry by canceling the telemetry job, clearing the error queue, and resetting the state.
     */
    fun reset() {
        telemetryJob?.cancel()
        resetQueue()
        started.set(false)
        rateLimitEndTime = 0
    }

    /**
     * Increments a metric with the specified tags.
     *
     * @param metric The name of the metric to increment.
     * @param buildTags A lambda function to build the tags for the metric.
     */
    fun increment(metric: String, buildTags: (MutableMap<String, String>) -> Unit) {
        val tags = mutableMapOf<String, String>()
        buildTags(tags)

        if (!enable || sampleRate.get() == 0.0) return
        if (!metric.startsWith(METRICS_BASE_TAG)) return
        if (tags.isEmpty()) return
        if (Math.random() > sampleRate.get()) return

        addRemoteMetric(metric, tags)
    }

    /**
     * Logs an error metric with the specified tags and log data.
     *
     * @param metric The name of the error metric.
     * @param log The log data associated with the error.
     * @param buildTags A lambda function to build the tags for the error metric.
     */
    fun error(metric:String, log: String, buildTags: (MutableMap<String, String>) -> Unit) {
        val tags = mutableMapOf<String, String>()
        buildTags(tags)

        if (!enable || sampleRate.get() == 0.0) return
        if (!metric.startsWith(METRICS_BASE_TAG)) return
        if (tags.isEmpty()) return
        if (Math.random() > sampleRate.get()) return

        var filteredTags = if(sendWriteKeyOnError) {
            tags.toMap()
        } else {
            tags.filterKeys { it.lowercase() != "writekey" }
        }
        var logData: String? = null
        if (sendErrorLogData) {
            logData = if (log.length > errorLogSizeMax) {
                log.substring(0, errorLogSizeMax)
            } else {
                log
            }
        }

        addRemoteMetric(metric, filteredTags, log=logData)

        if(flushFirstError.get()) {
            flushFirstError.set(false)
            flush()
        }
    }

    fun flush() {
        if (!enable) return
        flushChannel.trySend(Unit)
    }

    private fun performFlush() {
        if (!enable || queue.isEmpty()) return
        if (rateLimitEndTime > (System.currentTimeMillis() / 1000).toInt()) {
            return
        }
        rateLimitEndTime = 0
        flushFirstError.set(false)
        try {
            send()
        } catch (error: Throwable) {
            errorHandler?.invoke(error)
            sampleRate.set(0.0)
        }
    }

    private fun send() {
        if (sampleRate.get() == 0.0) return
        val sendQueue = mutableListOf<RemoteMetric>()
        // Reset queue data size counter since all current queue items will be removed
        queueBytes.set(0)
        var queueCount = queue.size
        while(queueCount > 0 && !queue.isEmpty()) {
            --queueCount
            val m = queue.poll()
            if(m != null) {
                m.value = (m.value / sampleRate.get()).roundToInt()
                sendQueue.add(m)
            }
        }
        assert(queue.size == 0)
        try {
            // Json.encodeToString by default does not include default values
            //  We're using this to leave off the 'log' parameter if unset.
            val payload = Json.encodeToString(mapOf("series" to sendQueue))

            val connection = httpClient.upload(host)
            connection.outputStream?.use { outputStream ->
                // Write the JSON string to the outputStream.
                outputStream.write(payload.toByteArray(Charsets.UTF_8))
                outputStream.flush() // Ensure all data is written
            }
            connection.inputStream?.close()
            connection.outputStream?.close()
            connection.close()
        } catch (e: HTTPException) {
            errorHandler?.invoke(e)
            if (e.responseCode == 429) {
                val headers = e.responseHeaders
                val rateLimit = headers["Retry-After"]?.firstOrNull()?.toLongOrNull()
                if (rateLimit != null) {
                    rateLimitEndTime = rateLimit + (System.currentTimeMillis() / 1000)
                }
            }
        } catch (e: Exception) {
            errorHandler?.invoke(e)
        }
    }

    private val additionalTags: Map<String, String> by lazy {
        var osVersion = System.getProperty("os.version")
        val androidRegex = Regex("android[0-9][0-9]")
        val androidMatch = androidRegex.find(osVersion)
        if (androidMatch != null) {
            osVersion = androidMatch.value
        } else {
            // other OSes seem to have well formed version numbers, just grab first for major version
            val otherRegex = Regex("[0-9]+")
            val otherMatch = otherRegex.find(osVersion)
            if (otherMatch != null) {
                osVersion = otherMatch.value
            }
        }
        mapOf(
            "os" to System.getProperty("os.name") + "-" + osVersion,
            "interpreter" to System.getProperty("java.vendor") + "-" + System.getProperty("java.version"),
            "library" to "analytics.kotlin",
            "library_version" to Constants.LIBRARY_VERSION
        )
    }

    private fun addRemoteMetric(metric: String, tags: Map<String, String>, value: Int = 1, log: String? = null) {
        val fullTags = tags + additionalTags
        val found = queue.find {
            it.metric == metric && it.tags == fullTags
        }
        if (found != null) {
            found.value += value
            return
        }
        if (queue.size >= maxQueueSize) {
            return
        }

        val newMetric = RemoteMetric(
            type = METRIC_TYPE,
            metric = metric,
            value = value,
            log = log?.let { mapOf("timestamp" to SegmentInstant.now(), "trace" to it) },
            tags = fullTags
        )
        val newMetricSize = newMetric.toString().toByteArray().size
        // Avoid synchronization issue by adding the size before checking.
        if (queueBytes.addAndGet(newMetricSize) <= maxQueueBytes) {
            queue.add(newMetric)
        } else {
            if(queueBytes.addAndGet(-newMetricSize) < 0) {
                queueBytes.set(0)
            }
        }
    }

    internal suspend fun subscribe(store: Store) {
        store.subscribe(
            this,
            com.segment.analytics.kotlin.core.System::class,
            initialState = true,
            handler = ::systemUpdate,
            queue = telemetryDispatcher
        )
    }
    private suspend fun systemUpdate(system: com.segment.analytics.kotlin.core.System) {
        system.settings?.let { settings ->
            settings.metrics["sampleRate"]?.jsonPrimitive?.double?.let {
                sampleRate.set(it)
                // We don't want to start telemetry until two conditions are met:
                // Telemetry.enable is set to true
                // Settings from the server have adjusted the sampleRate
                // start is called in both places
                start()
            }
        }
    }

    private fun resetQueue() {
        queue.clear()
        queueBytes.set(0)
    }
}