package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Constants
import com.segment.analytics.kotlin.core.HTTPClient
import com.segment.analytics.kotlin.core.compat.Builders
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors


data class MetricsOptions(
    val host: String,
    val path: String = "m",
    val flushAt: Long = 5000,
    val maxQueueSize: Int = 5,
    val sampleRate: Double = 0.1
)


data class Metric(
    val metric: MetricName,
    val value: Int,
    val type: MetricsType = MetricsType.Counter,
    val tags: JsonObject = emptyJsonObject
)

enum class MetricsType() {
    Error,
    Counter
}

enum class MetricName(val value: String) {
    Invoke("invoke"),
    InvokeError("invoke.error"),
    IntegrationInvoke("integration.invoke"),
    IntegrationInvokeError("integration.invoke.error")
}


fun Metric.toJson(): JsonElement {
    val result = Builders.JsonObjectBuilder()

    result.put("metric", "analytics_mobile." + metric.value)
    result.put("value", value)
    result.put("type", type.name)
    result.putJsonObject("tags") { builder ->
        tags.forEach {
            builder.put(it.key, it.value)
        }
    }

    return result.build()
}

fun CopyOnWriteArrayList<Metric>.toMetricsJson(): JsonArray {
    val content = mutableListOf<JsonElement>()
    forEach {
        content.add(it.toJson())
    }
    return JsonArray(content)
}


class Metrics(options: MetricsOptions) {

    companion object {
        val defaultOptions = MetricsOptions( Constants.DEFAULT_API_HOST, "m", 30000, 5, 0.10)
    }

    private val metricsDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Analytics.segmentLog("METRIC: Caught Exception in metrics scope: $throwable")
    }
    private val metricsScope = CoroutineScope(Job() + exceptionHandler + metricsDispatcher)

    private var flushJob: Job? = null

    lateinit var host: String
    lateinit var path: String
    var flushAt: Long = 5000
    var maxQueueSize: Int = 5
    var sampleRate: Double = 0.10

    var queue = CopyOnWriteArrayList<Metric>()

    init {
        configure(options)
        scheduleFlush(this.flushAt)
    }

    private fun configure(options: MetricsOptions) {
        this.host = options.host
        this.path = options.path
        this.flushAt = options.flushAt
        this.maxQueueSize = options.maxQueueSize
        this.sampleRate = options.sampleRate
    }

    private fun scheduleFlush(flushAt: Long) {
        metricsScope.launch {
            flushJob?.let {
                if (it.isActive) {
                    try {
                        withTimeout(2000) {
                            it.cancel("METRIC: Canceling old flushJob to start new flush job")
                            it.join()
                        }
                    } catch (t: Throwable) {
                        Analytics.segmentLog("METRIC: Caught Error while starting flush job: $t")
                    }
                }
            }

            // Start the new flush job
            if (flushAt >= 0) {
                flushJob = launch {
                    println("METRIC: Starting flush job..")
                    while (isActive) {
                        println("METRIC: Checking for queued up metrics...")
                        if (queue.size > 0) {
                            flush()
                        }
                        delay(flushAt)
                    }
                    println("METRIC: Flush job finishing..")
                }
            } else {
                Analytics.segmentLog("METRIC: flushAt value <= 0; Not scheduling flush job")
            }
        }
    }

    suspend fun flush() {
        withContext(metricsScope.coroutineContext) {
            println("METRIC: trying to flush at ${Date()}...")
            println("METRIC: queue size before flush: ${queue.size}")
            upload(queue)
            println("METRIC: queue size after flush: ${queue.size}")
        }
    }

    // HMMM... metrics list could be VERY long...what to do?
    internal suspend fun upload(metrics: CopyOnWriteArrayList<Metric>) {
        withContext(metricsScope.coroutineContext) {

            println("METRIC: uploading ${metrics.size} metric(s) at ${Date()}...")

            val requestProperties = mutableMapOf<String, String>()
            requestProperties.put("Content-Type","text/plain")
//            requestProperties.put("Content-Type","application/json")

            val conn = HTTPClient.upload(host, path, requestProperties)

            val metricJsonArray = metrics.toMetricsJson()
            println("METRIC: metricJsonArray.isEmpty(): ${metricJsonArray.isEmpty()}")

            if (metricJsonArray.isNotEmpty()) {
                val metricsString = "{ \"series\": $metricJsonArray }"

                println("METRIC: uploading metricsString:\n$metricsString")

                try {
                    conn.outputStream?.let {
                        metricsString.byteInputStream().copyTo(it)
                        it.close()

                        println("METRIC: ${conn.connection.requestMethod}")
                        println("METRIC: ${conn.connection.url}")
                        println("METRIC: ${conn.connection.headerFields}")
                        println("METRIC: ${conn.connection.responseMessage}")
                        println("METRIC: response code: ${conn.connection.responseCode}")

                        conn.inputStream?.let { inputStream ->
                             val bin = BufferedInputStream(inputStream)
                             println("METRIC: response: ${bin}")
                        }

                        it.close()
                        // Delete metrics
                        metrics.clear()
                    }
                } catch (t: Throwable) {
                    println("METRIC: Caught Exception in metric upload: $t")
                }
            } else {
                println("METRIC: Skipping empty upload")
            }
        }
    }

    fun add(metric: Metric) {
        metricsScope.launch {
            queue.add(metric)
            println("METRIC: ADD $metric")
            if (metric.metric.value.contains("error")) {
                println("METRIC: metric was error! flush it now!")
                // we need flush now!
                flush()
            } else if (queue.size >= maxQueueSize) {
                println("METRIC: reached max queue size. let's flush!")
                flush()
            }
        }
    }

}

