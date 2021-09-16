package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.TrackEvent

// Most simple interface for an plugin
interface Plugin {
    enum class Type {
        Before, // Executed before event processing begins.
        Enrichment, // Executed as the first level of event processing.
        Destination, // Executed as events begin to pass off to destinations.
        After, // Executed after all event processing is completed.  This can be used to perform cleanup operations, etc.
        Utility // Executed only when called manually, such as Logging.
    }

    enum class UpdateType {
        Initial,
        Refresh
    }

    val type: Type
    var analytics: Analytics // ideally will be auto-assigned by setup(), and can be declared as lateinit

    // A simple setup function thats executed when plugin is attached to analytics
    // If overridden, ensure that super.setup() is invoked
    fun setup(analytics: Analytics) {
        this.analytics = analytics
    }

    fun execute(event: BaseEvent): BaseEvent? {
        // empty body default
        return event
    }

    fun update(settings: Settings, type: UpdateType) {
        // empty body default
    }
}

// Advanced plugin that can act on specific event payloads
interface EventPlugin : Plugin {
    fun track(payload: TrackEvent): BaseEvent? {
        return payload
    }

    fun identify(payload: IdentifyEvent): BaseEvent? {
        return payload
    }

    fun screen(payload: ScreenEvent): BaseEvent? {
        return payload
    }

    fun group(payload: GroupEvent): BaseEvent? {
        return payload
    }

    fun alias(payload: AliasEvent): BaseEvent? {
        return payload
    }
}

// Basic interface for device-mode destinations. Allows overriding track, identify, screen, group, alias, flush and reset
abstract class DestinationPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Destination
    private val timeline: Timeline = Timeline()
    override lateinit var analytics: Analytics
    internal var enabled = true
    abstract val key: String

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        timeline.analytics = analytics
    }

    fun add(plugin: Plugin) {
        plugin.analytics = this.analytics
        timeline.add(plugin)
    }

    fun remove(plugin: Plugin) {
        timeline.remove(plugin)
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        // Apply settings update to its own plugins
        timeline.applyClosure {
            it.update(settings, type)
        }
    }

    // Special function for DestinationPlugin that manages its own timeline execution
    fun process(event: BaseEvent?): BaseEvent? {
        // Skip this destination if it is disabled via settings
        if (!enabled) {
            return null
        }
        val beforeResult = timeline.applyPlugins(Plugin.Type.Before, event)
        val enrichmentResult = timeline.applyPlugins(Plugin.Type.Enrichment, beforeResult)

        val destinationResult = enrichmentResult?.let {
            when (it) {
                is IdentifyEvent -> {
                    identify(it)
                }
                is TrackEvent -> {
                    track(it)
                }
                is GroupEvent -> {
                    group(it)
                }
                is ScreenEvent -> {
                    screen(it)
                }
                is AliasEvent -> {
                    alias(it)
                }
            }
        }

        val afterResult = timeline.applyPlugins(Plugin.Type.After, destinationResult)

        return afterResult
    }

    final override fun execute(event: BaseEvent): BaseEvent? {
        return null
    }

    open fun flush() {}

    open fun reset() {}
}