package com.segment.analytics.kotlin.core.compat

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.json.JsonObject
import java.util.function.Consumer

class JavaAnalytics(configuration: JavaConfiguration) {

    internal val analytics = Analytics(configuration.configuration)

    val store = analytics.store

    val storage = analytics.storage

    val analyticsScope = analytics.analyticsScope

    val ioDispatcher = analytics.ioDispatcher

    @JvmOverloads
    fun track(name: String, properties: JsonObject = emptyJsonObject) = analytics.track(name, properties)

    @JvmOverloads
    fun identify(userId: String, traits: JsonObject = emptyJsonObject) = analytics.identify(userId, traits)

    @JvmOverloads
    fun screen(
        title: String,
        properties: JsonObject = emptyJsonObject,
        category: String = ""
    ) = analytics.screen(title, properties, category)

    @JvmOverloads
    fun group(groupId: String, traits: JsonObject = emptyJsonObject) = analytics.group(groupId, traits)

    fun process(event: BaseEvent) = analytics.process(event)

    fun add(plugin: Plugin) = analytics.add(plugin)

    fun <T: Plugin> find(plugin: Class<T>) = analytics.find(plugin.kotlin)

    fun remove(plugin: Plugin) = analytics.remove(plugin)

    fun applyClosureToPlugins(closure: (Plugin) -> Unit) = analytics.applyClosureToPlugins(closure)

    @Suppress("NewApi")
    fun applyClosureToPlugins(closure: Consumer<in Plugin>) = analytics.applyClosureToPlugins(closure::accept)

    fun flush() = analytics.flush()

    fun userId() = analytics.userId()

    fun traits() = analytics.traits()

}