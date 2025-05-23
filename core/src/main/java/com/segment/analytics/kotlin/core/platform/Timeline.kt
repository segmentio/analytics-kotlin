package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.Telemetry
import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog
import com.segment.analytics.kotlin.core.reportErrorWithMetrics
import com.segment.analytics.kotlin.core.reportInternalError
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

// Platform abstraction for managing all plugins and their execution
// Currently the execution follows
//      Before -> Enrichment -> Destination -> After
internal class Timeline {
    internal val plugins: Map<Plugin.Type, Mediator> = mapOf(
        Plugin.Type.Before to Mediator(),
        Plugin.Type.Enrichment to Mediator(),
        Plugin.Type.Destination to Mediator(),
        Plugin.Type.After to Mediator(),
        Plugin.Type.Utility to Mediator()
    )
    lateinit var analytics: Analytics

    // initiate the event's lifecycle
    fun process(incomingEvent: BaseEvent): BaseEvent? {
        val beforeResult = applyPlugins(Plugin.Type.Before, incomingEvent)
        var enrichmentResult = applyPlugins(Plugin.Type.Enrichment, beforeResult)
        enrichmentResult?.enrichment?.let {
            enrichmentResult = it(enrichmentResult)
        }

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
        try {
            plugin.setup(analytics)
        } catch (t: Throwable) {
            reportErrorWithMetrics(analytics, t,
                "Caught Exception while setting up plugin $plugin",
                Telemetry.INTEGRATION_ERROR_METRIC, t.stackTraceToString()) {
                it["error"] = t.toString()
                if (plugin is DestinationPlugin && plugin.key != "") {
                    it["plugin"] = "${plugin.type}-${plugin.key}"
                } else {
                    it["plugin"] = "${plugin.type}-${plugin.javaClass}"
                }
                it["writekey"] = analytics.configuration.writeKey
                it["message"] = "Exception executing plugin"
            }
        }
        plugins[plugin.type]?.add(plugin)
        with(analytics) {
            analyticsScope.launch(analyticsDispatcher) {
                val systemState = store.currentState(System::class)
                val systemSettings = systemState?.settings
                systemSettings?.let {
                    if (systemState.initializedPlugins.isNotEmpty()) {
                        // if we have settings then update plugin with it
                        // otherwise it will be updated when settings becomes available
                        plugin.update(it, Plugin.UpdateType.Initial)
                        store.dispatch(
                            System.AddInitializedPlugins(setOf(plugin.hashCode())),
                            System::class
                        )
                    }
                }
            }
        }

        Telemetry.increment(Telemetry.INTEGRATION_METRIC) {
            it["message"] = "added"
            if (plugin is DestinationPlugin && plugin.key != "") {
                it["plugin"] = "${plugin.type}-${plugin.key}"
            } else {
                it["plugin"] = "${plugin.type}-${plugin.javaClass}"
            }
        }
    }

    // Remove a registered plugin
    fun remove(plugin: Plugin) {
        // remove all plugins with this name in every category
        plugins.forEach { (_, list) ->
            list.remove(plugin)
            Telemetry.increment(Telemetry.INTEGRATION_METRIC) {
                it["message"] = "removed"
                if (plugin is DestinationPlugin && plugin.key != "") {
                    it["plugin"] = "${plugin.type}-${plugin.key}"
                } else {
                    it["plugin"] = "${plugin.type}-${plugin.javaClass}"
                }
            }
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

    // Find a destination plugin by its name
    fun find(destination: String) =
        plugins[Plugin.Type.Destination]?.plugins?.find {
            it is DestinationPlugin && it.key == destination
        } as? DestinationPlugin


    fun <T: Plugin> findAll(pluginClass: KClass<T>): List<T> {
        val result = mutableListOf<T>()
        plugins.forEach { (_, list) ->
            val found = list.findAll(pluginClass)
            result.addAll(found)
        }
        return result
    }
}