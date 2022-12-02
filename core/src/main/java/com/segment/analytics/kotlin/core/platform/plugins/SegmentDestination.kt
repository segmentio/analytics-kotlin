package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.EventPipeline
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.VersionedPlugin
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.platform.policies.FlushPolicy
import com.segment.analytics.kotlin.core.platform.policies.FrequencyFlushPolicy
import kotlinx.serialization.Serializable

@Serializable
data class SegmentSettings(
    var apiKey: String,
    var apiHost: String? = null
)

/**
 * Segment Analytics plugin that is used to send events to Segment's tracking api, in the choice of region.
 * How it works
 * - Plugin receives `apiHost` settings
 * - We store events into a file with the batch api format (@link {https://segment.com/docs/connections/sources/catalog/libraries/server/http-api/#batch})
 * - We upload events on a dedicated thread using the batch api
 */
class SegmentDestination: DestinationPlugin(), VersionedPlugin {

    private lateinit var pipeline: EventPipeline
    var flushPolicies: List<FlushPolicy> = emptyList()
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


    private fun enqueue(payload: BaseEvent) {
        pipeline.put(payload)
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)

        // convert flushAt and flushIntervals into FlushPolicies
        flushPolicies = if (analytics.configuration.flushPolicies.isEmpty()) {
            listOf(
                CountBasedFlushPolicy(analytics.configuration.flushAt),
                FrequencyFlushPolicy(analytics.configuration.flushInterval * 1000L)
            )
        } else {
            analytics.configuration.flushPolicies
        }

        // Add DestinationMetadata enrichment plugin
        add(DestinationMetadataPlugin())

        with(analytics) {
            pipeline = EventPipeline(
                analytics,
                key,
                configuration.writeKey,
                flushPolicies,
                configuration.apiHost
            )
            pipeline.start()
        }
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        if (settings.hasIntegrationSettings(this)) {
            // only populate the apiHost value if it exists
            settings.destinationSettings<SegmentSettings>(key)?.apiHost?.let {
                pipeline.apiHost = it
            }
        }
    }

    override fun flush() {
        pipeline.flush()
    }

    override fun version(): String {
        return Constants.LIBRARY_VERSION
    }
}