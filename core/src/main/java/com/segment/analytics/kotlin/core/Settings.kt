package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.kotlin.core.utilities.safeJsonObject
import kotlinx.coroutines.launch
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

    fun hasIntegrationSettings(plugin: DestinationPlugin): Boolean {
        return hasIntegrationSettings(plugin.key)
    }

    fun hasIntegrationSettings(key: String): Boolean {
        return integrations.containsKey(key)
    }
}

internal fun Analytics.update(settings: Settings, type: Plugin.UpdateType) {
    timeline.applyClosure { plugin ->
        if (plugin is DestinationPlugin) {
            plugin.enabled = settings.hasIntegrationSettings(plugin)
        }
        // tell all top level plugins to update.
        // For destination plugins they auto-handle propagation to sub-plugins
        plugin.update(settings, type)
    }
}

fun Analytics.manuallyEnableDestination(plugin: DestinationPlugin) {
    analyticsScope.launch(analyticsDispatcher) {
        store.dispatch(
            System.AddDestinationToSettingsAction(destinationKey = plugin.key),
            System::class
        )
        val system = store.currentState(System::class)
        system?.settings?.let { settings ->
            findAll(DestinationPlugin::class).forEach {
                plugin.enabled = settings.hasIntegrationSettings(it.key)
            }
        }
    }
}


/**
 * Make analytics client call into Segment's settings API, to refresh certain configurations.
 */
suspend fun Analytics.checkSettings() {
    val writeKey = configuration.writeKey
    val cdnHost = configuration.cdnHost

    // check current system state to determine whether it's initial or refresh
    val systemState = store.currentState(System::class) ?: return
    val updateType = if (systemState.initialSettingsDispatched) {
        Plugin.UpdateType.Refresh
    } else {
        Plugin.UpdateType.Initial
    }

    store.dispatch(System.ToggleRunningAction(running = false), System::class)

    withContext(networkIODispatcher) {
        log("Fetching settings on ${Thread.currentThread().name}")
        val settingsObj: Settings? = try {
            val connection = HTTPClient(writeKey).settings(cdnHost)
            val settingsString =
                connection.inputStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            log("Fetched Settings: $settingsString")
            LenientJson.decodeFromString(settingsString)
        } catch (ex: Exception) {
            Analytics.segmentLog(
                "${ex.message}: failed to fetch settings",
                kind = LogFilterKind.ERROR
            )
            null
        }

        withContext(analyticsDispatcher) {
            settingsObj?.let {
                log("Dispatching update settings on ${Thread.currentThread().name}")
                store.dispatch(System.UpdateSettingsAction(settingsObj), System::class)
                update(settingsObj, updateType)
                store.dispatch(System.ToggleSettingsDispatch(dispatched = true), System::class)
            }

            // we're good to go back to a running state.
            store.dispatch(System.ToggleRunningAction(running = true), System::class)
        }
    }
}