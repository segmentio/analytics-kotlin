package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.System
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

// Platform abstraction for managing all plugins and their execution
// Currently the execution follows
//      Before -> Enrichment -> Destination -> After
internal class Timeline {
    internal val plugins: Map<Plugin.Type, Mediator> = mapOf(
        Plugin.Type.Before to Mediator(mutableListOf()),
        Plugin.Type.Enrichment to Mediator(mutableListOf()),
        Plugin.Type.Destination to Mediator(mutableListOf()),
        Plugin.Type.After to Mediator(mutableListOf()),
        Plugin.Type.Utility to Mediator(mutableListOf())
    )
    lateinit var analytics: Analytics

    // initiate the event's lifecycle
    fun process(incomingEvent: BaseEvent): BaseEvent? {
        val beforeResult = applyPlugins(Plugin.Type.Before, incomingEvent)
        val enrichmentResult = applyPlugins(Plugin.Type.Enrichment, beforeResult)

        // once the event enters a destination, we don't want
        // to know about changes that happen there
        applyPlugins(Plugin.Type.Destination, enrichmentResult)

        val afterResult = applyPlugins(Plugin.Type.After, enrichmentResult)

        return afterResult
    }

    // Applies a closure on all registered plugins
    fun applyClosure(closure: (Plugin) -> Unit) {
        plugins.forEach { (_, mediator) ->
            mediator.applyClosure(closure)
        }
    }

    // Runs all registered plugins of a particular type on given payload
    fun applyPlugins(type: Plugin.Type, event: BaseEvent?): BaseEvent? {
        var result: BaseEvent? = event
        val mediator = plugins[type]
        result = applyPlugins(mediator, result)
        return result
    }

    // Run a mediator on given payload
    fun applyPlugins(mediator: Mediator?, event: BaseEvent?): BaseEvent? {
        var result: BaseEvent? = event
        result?.let { e ->
            result = mediator?.execute(e)
        }
        return result
    }

    // Register a new plugin
    fun add(plugin: Plugin) {
        plugin.setup(analytics)
        plugins[plugin.type]?.add(plugin)
        with(analytics) {
            analyticsScope.launch(Dispatchers.Analytics) {
                store.currentState(System::class)?.settings?.let {
                    // if we have settings then update plugin with it
                    plugin.update(it, Plugin.UpdateType.Initial)
                }
            }
        }
    }

    // Remove a registered plugin
    fun remove(plugin: Plugin) {
        // remove all plugins with this name in every category
        plugins.forEach { (_, list) ->
            list.remove(plugin)
        }
    }

    // Find a registered plugin
    fun <T: Plugin> find(pluginClass: KClass<T>): T? {
        plugins.forEach { (_, list) ->
            val found = list.find(pluginClass)
            if (found != null) {
                return found
            }
        }
        return null
    }
}