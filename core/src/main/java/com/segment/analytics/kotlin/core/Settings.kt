package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.kotlin.core.utilities.safeJsonObject
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.io.BufferedReader

@Serializable
data class Settings(
    var integrations: JsonObject = emptyJsonObject,
    var plan: JsonObject = emptyJsonObject,
    var edgeFunction: JsonObject = emptyJsonObject,
) {
    inline fun <reified T : Any> destinationSettings(
        name: String,
        strategy: DeserializationStrategy<T> = Json.serializersModule.serializer()
    ): T? {
        val integrationData = integrations[name]?.safeJsonObject ?: return null
        val typedSettings = LenientJson.decodeFromJsonElement(strategy, integrationData)
        return typedSettings
    }

    fun isDestinationEnabled(name: String): Boolean {
        return integrations.containsKey(name)
    }
}

internal fun Analytics.update(settings: Settings, type: Plugin.UpdateType) {
    timeline.applyClosure { plugin ->
        if (plugin is DestinationPlugin) {
            plugin.enabled = settings.isDestinationEnabled(plugin.key)
        }
        // tell all top level plugins to update.
        // For destination plugins they auto-handle propagation to sub-plugins
        plugin.update(settings, type)
    }
}

/**
 * Make analytics client call into Segment's settings API, to refresh certain configurations.
 */
suspend fun Analytics.checkSettings() {
    val writeKey = configuration.writeKey
    val cdnHost = configuration.cdnHost

    // check current system state to determine whether it's initial or refresh
    val systemState = store.currentState(System::class)
    val hasSettings = systemState?.settings?.integrations != null &&
            systemState.settings?.plan != null
    val updateType = if (hasSettings) Plugin.UpdateType.Refresh else Plugin.UpdateType.Initial

    // stop things; queue in case our settings have changed.
    store.dispatch(System.ToggleRunningAction(running = false), System::class)

    withContext(networkIODispatcher) {
        log("Fetching settings on ${Thread.currentThread().name}")
        val settingsObj: Settings? = try {
            val connection = HTTPClient(writeKey).settings(cdnHost)
            val settingsString =
                connection.inputStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            log( "Fetched Settings: $settingsString")
            LenientJson.decodeFromString(settingsString)
        } catch (ex: Exception) {
            Analytics.segmentLog("${ex.message}: failed to fetch settings", kind = LogFilterKind.ERROR)
            null
        }

        withContext(analyticsDispatcher) {
            settingsObj?.let {
                log("Dispatching update settings on ${Thread.currentThread().name}")
                store.dispatch(System.UpdateSettingsAction(settingsObj), System::class)
                update(settingsObj, updateType)
            }

            // we're good to go back to a running state.
            store.dispatch(System.ToggleRunningAction(running = true), System::class)
        }
    }
}