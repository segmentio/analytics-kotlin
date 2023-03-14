package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.HTTPClient
import com.segment.analytics.kotlin.core.compat.Builders
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.InputStream
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors


/*
export interface MetricsOptions {
  host: string;
  flushTimer: number;
  maxQueueSize: number;
  sampleRate: number;
}
 */

data class MetricsOptions(
    val host: String,
    val path: String = "m",
    val flushAt: Long = 5000,
    val maxQueueSize: Int = 5,
    val sampleRate: Double = 0.1
)




data class Metric(
    val metric: String,
    val value: String,
    val type: MetricsType = MetricsType.Counter,
    val tags: JsonObject = emptyJsonObject
)

enum class MetricsType() {
    Error,
    Counter
}


fun Metric.toJson(): JsonElement {
    val result = Builders.JsonObjectBuilder()

    result.put("metric", metric)
    result.put("value", value)
    result.put("type", type.name)
    result.putJsonObject("tags") { tags }

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
        val defaultOptions = MetricsOptions("api.segment.com", "m", 5000, 5, 0.10)
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

    fun flush() {
        metricsScope.launch {
            if (queue.size >= maxQueueSize) {
                println("METRIC: flushing at: ${Date()}...")
                upload(queue)
            } else {
                println("METRIC: Nothing to flush at ${Date()}...")
            }
        }
    }

    // HMMM... metrics list could be VERY long...what to do?
    internal suspend fun upload(metrics: List<Metric>) {
        withContext(metricsScope.coroutineContext) {


            val requestProperties = mapOf<String, String>()

            // TODO: Refactor need for writekey??
            val conn = HTTPClient("foo").upload(host, path, requestProperties)

            val metricsString = metrics.toMetricsJson().toString()

            println("METRIC: uploading metricsString:\n$metricsString")

            try {
                conn.outputStream?.let {
                    metricsString.byteInputStream().copyTo(it)
                    it.close()

                    // Delete metrics
                    metrics.dropLast(metrics.size)
                }
            } catch (t: Throwable) {
                println("METRIC: Caught Exception in metric upload: $t")
            }
        }
    }

    fun add(metric: Metric) {
        metricsScope.launch {
            queue.add(metric)

            if (metric.type == MetricsType.Error) {
                println("METRIC: metric was error! flush it now!")
                // we need flush now!
                flush()
            }
        }
    }

}

