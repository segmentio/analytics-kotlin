package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.plugins.logger.LogFilterKind
import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

// Platform abstraction for managing plugins' execution (of a specific type)
// All operations are thread safe via the CopyOnWriteArrayList. Which allows multiple
// threads to read the list but when a modification is made the modifier is given a new copy of
// list and that becomes the new version of the list.
// More info: https://developer.android.com/reference/kotlin/java/util/concurrent/CopyOnWriteArrayList
internal class Mediator(internal var plugins: CopyOnWriteArrayList<Plugin> = CopyOnWriteArrayList()) {

    fun add(plugin: Plugin) {
        plugins.add(plugin)
    }

    fun remove(plugin: Plugin) {
        // Note: We want to use this form of removeAll() function that takes a collection and
        // and NOT the removeAll {} that takes a code block as that will get wrapped by MutableList
        // and use a stateful iterator that will break when run from multiple threads.
        plugins.removeAll(setOf(plugin))
    }

    fun execute(event: BaseEvent): BaseEvent? {
        var result: BaseEvent? = event

        plugins.forEach { plugin ->
            result?.let {
                try {
                    when (plugin) {
                        is DestinationPlugin -> {
                            plugin.execute(it)
                        }
                        else -> {
                            result = plugin.execute(it)
                        }
                    }
                } catch (t: Throwable) {
                    Analytics.segmentLog("Caught Exception in plugin: $t", kind = LogFilterKind.ERROR)
                    Analytics.segmentLog("Skipping plugin due to Exception: $plugin", kind = LogFilterKind.WARNING)
                }
            }
        }

        return result
    }

    fun applyClosure(closure: (Plugin) -> Unit) {
        plugins.forEach {
            closure(it)
        }
    }

    fun <T : Plugin> find(pluginClass: KClass<T>): T? {
        plugins.forEach {
            if (pluginClass.isInstance(it)) {
                return it as T
            }
        }
        return null
    }

    fun <T : Plugin> findAll(pluginClass: KClass<T>): List<T> {
        return plugins.filter { pluginClass.isInstance(it) } as List<T>
    }
}
