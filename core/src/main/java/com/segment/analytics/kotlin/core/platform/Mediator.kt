package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

// Platform abstraction for managing plugins' execution (of a specific type)
// All operations are thread safe via the `synchronized` function
internal class Mediator(internal val plugins: MutableList<Plugin>) {

    fun add(plugin: Plugin) = synchronized(plugins) {
        plugins.add(plugin)
    }

    fun remove(plugin: Plugin) = synchronized(plugins) {
        plugins.removeAll { it === plugin } // remove only if reference is the same
    }

    fun execute(event: BaseEvent): BaseEvent? {
        var result: BaseEvent? = event

        plugins.forEach { plugin ->
            result?.let {
                when (plugin) {
                    is DestinationPlugin -> {
                        plugin.execute(it)
                    }
                    else -> {
                        result = plugin.execute(it)
                    }
                }
            }
        }

        return result
    }

    fun applyClosure(closure: (Plugin) -> Unit) = synchronized(plugins) {
        plugins.forEach {
            closure(it)
        }
    }

    fun <T: Plugin> find(pluginClass: KClass<T>): T? = synchronized(plugins) {
        plugins.forEach {
            if (pluginClass.isInstance(it)) {
                return it as T
            }
        }
        return null
    }

    fun <T: Plugin> findAll(pluginClass: KClass<T>): List<T> = synchronized(plugins) {
        return plugins.filter { pluginClass.isInstance(it) } as List<T>
    }
}
