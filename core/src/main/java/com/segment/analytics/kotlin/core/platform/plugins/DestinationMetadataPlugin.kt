package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.DestinationMetadata
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.safeJsonArray
import com.segment.analytics.kotlin.core.utilities.safeJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

/**
 * DestinationMetadataPlugin adds `_metadata` information to payloads that Segment uses to
 * determine delivery of events to cloud/device-mode destinations
 */
class DestinationMetadataPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics
    private var analyticsSettings: Settings = Settings()

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        analyticsSettings = settings
    }

    override fun execute(event: BaseEvent): BaseEvent {
        // Using this over `findAll` for that teensy performance benefit
        val enabledDestinations = analytics.timeline.plugins[Plugin.Type.Destination]?.plugins
            ?.map { it as DestinationPlugin }
            ?.filter { it.enabled && it !is SegmentDestination }
        val metadata = DestinationMetadata().apply {
            // Mark all loaded destinations as bundled
            this.bundled = buildJsonArray {
                enabledDestinations?.forEach {
                    add(it.key)
                }
            }
            // All unbundledIntegrations not in `bundled` are put in `unbundled`
            this.unbundled = buildJsonArray {
                analyticsSettings.integrations["Segment.io"]?.safeJsonObject
                    ?.get("unbundledIntegrations")?.safeJsonArray?.let { unbundledList ->
                        unbundledList.forEach {
                            if (!bundled.contains(it)) {
                                add(it)
                            }
                        }
                    }
            }
            // `bundledIds` for mobile is empty
            this.bundledIds = JsonArray(emptyList())
        }

        val payload = event.copy<BaseEvent>().apply {
            this._metadata = metadata
        }

        return payload
    }
}