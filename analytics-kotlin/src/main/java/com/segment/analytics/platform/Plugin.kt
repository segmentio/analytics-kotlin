package com.segment.analytics.platform

import com.segment.analytics.*


// Most simple interface for an plugin
interface Plugin {
    enum class Type {
        Before, // Executed before event processing begins.
        Enrichment, // Executed as the first level of event processing.
        Destination, // Executed as events begin to pass off to destinations.
        After, // Executed after all event processing is completed.  This can be used to perform cleanup operations, etc.
        Utility // Executed only when called manually, such as Logging.
    }

    val type: Type
    val name: String
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

    fun update(settings: Settings) {
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

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        timeline.analytics = analytics
    }

    fun add(plugin: Plugin) {
        plugin.analytics = this.analytics
        timeline.add(plugin)
    }

    fun remove(pluginName: String) {
        timeline.remove(pluginName)
    }

    override fun update(settings: Settings) {
        // Apply settings update to its own plugins
        timeline.applyClosure {
            it.update(settings)
        }
    }

    // Special function for DestinationPlugin that manages its own timeline execution
    fun process(event: BaseEvent?): BaseEvent? {
        val beforeResult = timeline.applyPlugins(Plugin.Type.Before, event)
        val enrichmentResult = timeline.applyPlugins(Plugin.Type.Enrichment, beforeResult)

        enrichmentResult?.let {
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

        val afterResult = timeline.applyPlugins(Plugin.Type.After, enrichmentResult)

        return afterResult
    }

    final override fun execute(event: BaseEvent): BaseEvent? { return null }

    open fun flush() {}

    open fun reset() {}
}