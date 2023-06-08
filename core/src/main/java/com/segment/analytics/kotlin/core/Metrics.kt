package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.compat.Builders
import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.*
import java.io.BufferedInputStream
import java.util.*
import java.util.concurrent.BlockingDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger


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

private val metricNamePrefix = "analytics_mobile."

enum class MetricName(val value: String) {
    Invoke("invoke"),
    InvokeError("invoke.error"),
    IntegrationInvoke("integration.invoke"),
    IntegrationInvokeError("integration.invoke.error")
}

fun Metric.toJson(): JsonElement {
    val result = Builders.JsonObjectBuilder()

    result.put("metric", metricNamePrefix + metric.value)
    result.put("value", value)
    result.put("type", type.name)
    result.putJsonObject("tags") { builder ->
        tags.forEach {
            builder.put(it.key, it.value)
        }
    }

    return result.build()
}

fun List<Metric>.toMetricsJson(): JsonArray {
    val content = mutableListOf<JsonElement>()
    forEach {
        content.add(it.toJson())
    }
    return JsonArray(content)
}


class Metrics(options: MetricsOptions) {

    companion object {
        val defaultOptions = MetricsOptions(Constants.DEFAULT_API_HOST, "m", 30000, 5, 0.10)
    }

    private var flushJob: Job? = null
    private val metricsDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Analytics.segmentLog("METRIC: Caught Exception in metrics scope: $throwable")
    }
    private val metricsScope = CoroutineScope(Job() + exceptionHandler + metricsDispatcher)

    lateinit var host: String
    lateinit var path: String
    var flushAt: Long = 5000
    var maxQueueSize: Int = 5
    var sampleRate: Double = 0.10

    val uploadChannel = Channel<Metric>()
    var queueSize = AtomicInteger(0)

    init {
        configure(options)
        scheduleFlush(this.flushAt)
        collect(uploadChannel)
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

                        flush()

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
            upload(metricList, queueSize)
        }
    }

    private val metricList = CopyOnWriteArrayList<Metric>()
    internal fun collect(metricsChannel: Channel<Metric>) {
        metricsScope.launch {
            metricsChannel.consumeEach {
                metricList.add(it)

                val currentQueueSize = queueSize.incrementAndGet()
                if (currentQueueSize >= maxQueueSize) {
                    upload(metricList, queueSize)
                } else if (it.metric.value.contains("error")) {
                    upload(metricList, queueSize)
                }
            }
        }
    }


    // HMMM... metrics list could be VERY long...what to do?
    internal fun upload(metricList: CopyOnWriteArrayList<Metric>, queueSize: AtomicInteger) = synchronized(metricList) {

        println("METRIC: trying upload...")
        println("METRIC: metricList.size: ${metricList.size}")

        if (metricList.size > 0) {
            println("METRIC: uploading ${metricList.size} metric(s) at ${Date()}...")

            val requestProperties = mutableMapOf<String, String>()
            requestProperties.put("Content-Type", "text/plain")

            val conn = HTTPClient.upload(host, path, requestProperties)

            val metricJsonArray = metricList.toMetricsJson()
            println("METRIC: metricJsonArray.isEmpty(): ${metricJsonArray.isEmpty()}")


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


                    metricList.clear()
                    queueSize.set(0)
                }
            } catch (t: Throwable) {
                println("METRIC: Caught Exception in metric upload: $t")
            }
        } else {
            println("METRIC: Skipping empty upload")
        }
    }

    fun add(metric: Metric) {
        println("METRIC: trying to add($metric)")
        metricsScope.launch {
            uploadChannel.send(metric)
        }
    }

}

