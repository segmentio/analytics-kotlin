package com.segment.analytics.kotlin.core.compat

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.json.JsonObject
import java.util.function.Consumer

class JavaAnalytics(configuration: Configuration) {

    internal val analytics = Analytics(configuration)

    val store = analytics.store

    val storage = analytics.storage

    val analyticsScope = analytics.analyticsScope

    val ioDispatcher = analytics.ioDispatcher

    @JvmOverloads
    fun track(name: String, properties: JsonObject = emptyJsonObject) = analytics.track(name, properties)

    fun track(name: String, serializable: JsonSerializable) = analytics.track(name, serializable.serialize())

    @JvmOverloads
    fun identify(userId: String, traits: JsonObject = emptyJsonObject) = analytics.identify(userId, traits)

    fun identify(userId: String, serializable: JsonSerializable) = analytics.identify(userId, serializable.serialize())

    @JvmOverloads
    fun screen(
        title: String,
        properties: JsonObject = emptyJsonObject,
        category: String = ""
    ) = analytics.screen(title, properties, category)

    fun screen(
        title: String,
        serializable: JsonSerializable,
        category: String = ""
    ) = analytics.screen(title, serializable.serialize(), category)

    @JvmOverloads
    fun group(groupId: String, traits: JsonObject = emptyJsonObject) = analytics.group(groupId, traits)

    fun group(groupId: String, serializable: JsonSerializable) = analytics.group(groupId, serializable.serialize())

    fun process(event: BaseEvent) = analytics.process(event)

    fun add(plugin: Plugin) = apply { analytics.add(plugin) }

    fun <T: Plugin> find(plugin: Class<T>) = analytics.find(plugin.kotlin)

    fun remove(plugin: Plugin) = apply { analytics.remove(plugin) }

    fun applyClosureToPlugins(closure: (Plugin) -> Unit) = analytics.applyClosureToPlugins(closure)

    @Suppress("NewApi")
    fun applyClosureToPlugins(closure: Consumer<in Plugin>) = analytics.applyClosureToPlugins(closure::accept)

    fun flush() = analytics.flush()

    fun userId() = analytics.userId()

    fun traits() = analytics.traits()

}