package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.Constants.DEFAULT_API_HOST
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.EventPipeline
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.putAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
data class SegmentSettings(
    var apiKey: String,
    var apiHost: String = DEFAULT_API_HOST,
)

/**
 * Segment Analytics plugin that is used to send events to Segment's tracking api, in the choice of region.
 * How it works
 * - Plugin receives `apiHost` settings
 * - We store events into a file with the batch api format (@link {https://segment.com/docs/connections/sources/catalog/libraries/server/http-api/#batch})
 * - We upload events on a dedicated thread using the batch api
 */
class SegmentDestination : DestinationPlugin() {

    private lateinit var pipeline: EventPipeline

    override val key: String = "Segment.io"

    override fun track(payload: TrackEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    override fun identify(payload: IdentifyEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    override fun group(payload: GroupEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    override fun alias(payload: AliasEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    private inline fun <reified T : BaseEvent> enqueue(payload: T) {
        val finalPayload = configureCloudDestination(payload)
        // needs to be inline reified for encoding using Json
        val jsonVal = EncodeDefaultsJson.encodeToJsonElement(finalPayload)
            .jsonObject.filterNot { (k, v) ->
                // filter out empty userId and traits values
                (k == "userId" && v.jsonPrimitive.content.isBlank()) || (k == "traits" && v == emptyJsonObject)
            }

        val stringVal = Json.encodeToString(jsonVal)
        analytics.log("$key running $stringVal")

        pipeline.put(stringVal)
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)

        with(analytics) {
            pipeline = EventPipeline(
                analytics,
                key,
                configuration.writeKey,
                configuration.flushAt,
                configuration.flushInterval * 1000L,
                configuration.apiHost
            )
            pipeline.start()
        }
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        if (settings.hasIntegrationSettings(this)) {
            settings.destinationSettings<SegmentSettings>(key)?.let {
                pipeline.apiHost = it.apiHost
            }
        }
    }

    override fun flush() {
        pipeline.flush()
    }

    internal fun <T: BaseEvent> configureCloudDestination(event: T): T {
        val enabledDestinations = analytics.findAll(DestinationPlugin::class).filter { it.enabled }
        val merged = buildJsonObject {
            // Mark all loaded destinations as false
            enabledDestinations.forEach {
                put(it.key, false)
            }
            // apply customer values; the customer is always right!
            putAll(event.integrations)
        }
        event.integrations = merged
        return event
    }
}