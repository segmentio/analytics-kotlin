package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.DateSerializer
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.putInContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import java.time.Instant

enum class MetricType(val type: Int) {
    Counter(0), // Not Verbose
    Gauge(1)    // Semi-verbose
}

@Serializable
data class Metric(
    var eventName: String = "",
    var metricName: String = "",
    var value: Double = 0.0,
    var tags: List<String> = emptyList(),
    var type: MetricType = MetricType.Counter,
    @Serializable(with = DateSerializer::class) var timestamp: Instant = Instant.now()
)

fun BaseEvent.addMetric(
    type: MetricType,
    name: String,
    value: Double,
    tags: List<String> = emptyList(),
    timestamp: Instant = Instant.now(),
) {
    val metric = Metric(
        eventName = this.type.name,
        metricName = name,
        value = value,
        tags = tags,
        type = type,
        timestamp = timestamp
    )

    val metrics = buildJsonArray {
        context["metrics"]?.jsonArray?.forEach {
            add(it)
        }
        add(EncodeDefaultsJson.encodeToJsonElement(Metric.serializer(), metric))
    }

    putInContext("metrics", metrics)
}