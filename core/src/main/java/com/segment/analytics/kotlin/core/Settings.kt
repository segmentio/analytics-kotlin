package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.log
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.kotlin.core.utilities.safeJsonObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.io.BufferedReader
import java.net.URL

@Serializable
data class Settings(
    var integrations: JsonObject = emptyJsonObject,
    var plan: JsonObject = emptyJsonObject,
    var edgeFunction: JsonObject = emptyJsonObject,
    var middlewareSettings: JsonObject = emptyJsonObject,
    var metrics: JsonObject = emptyJsonObject,
    var consentSettings: JsonObject = emptyJsonObject
) {
    inline fun <reified T : Any> destinationSettings(
        name: String,
        strategy: DeserializationStrategy<T> = Json.serializersModule.serializer(),
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

internal suspend fun Analytics.update(settings: Settings) {
    val systemState = store.currentState(System::class) ?: return
    val set = mutableSetOf<Int>()
    timeline.applyClosure { plugin ->
        // tell all top level plugins to update.
        // For destination plugins they auto-handle propagation to sub-plugins
        val type: Plugin.UpdateType =
            if (systemState.initializedPlugins.contains(plugin.hashCode())) {
                Plugin.UpdateType.Refresh
            } else {
                set.add(plugin.hashCode())
                Plugin.UpdateType.Initial
            }
        plugin.update(settings, type)
    }
    store.dispatch(System.AddInitializedPlugins(set), System::class)
}

/**
 * Manually enable a destination plugin.  This is useful when a given DestinationPlugin doesn't have any Segment tie-ins at all.
 * This will allow the destination to be processed in the same way within this library.
 */
fun Analytics.manuallyEnableDestination(plugin: DestinationPlugin) {
    analyticsScope.launch(analyticsDispatcher) {
        store.dispatch(
            System.AddDestinationToSettingsAction(destinationKey = plugin.key),
            System::class
        )
        // Differs from swift, bcos kotlin can store `enabled` state. ref: https://git.io/J1bhJ
        // finding it in timeline rather than using the ref that is provided to cover our bases
        find(plugin::class)?.enabled = true
    }
}


/**
 * Make analytics client call into Segment's settings API, to refresh certain configurations.
 */
suspend fun Analytics.checkSettings() {
    val writeKey = configuration.writeKey
    val cdnHost = configuration.cdnHost

    pauseEventProcessing()

    val settingsObj = withContext(networkIODispatcher) {
        log("Fetching settings on ${Thread.currentThread().name}")
        return@withContext fetchSettings(writeKey, cdnHost)
    }

    settingsObj?.let {
        log("Dispatching update settings on ${Thread.currentThread().name}")
        store.dispatch(System.UpdateSettingsAction(settingsObj), System::class)
    }

    store.currentState(System::class)?.let { system ->
        system.settings?.let { settings ->
            log("Propagating settings on ${Thread.currentThread().name}")
            update(settings)
        }
    }

    // we're good to go back to a running state.
    resumeEventProcessing()
}

internal fun Analytics.fetchSettings(
    writeKey: String,
    cdnHost: String
): Settings? = try {
    val connection = HTTPClient(writeKey, this.configuration.requestFactory).settings(cdnHost)
    val settingsString =
        connection.inputStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
    log("Fetched Settings: $settingsString")
    LenientJson.decodeFromString(settingsString)
} catch (ex: Exception) {
    reportErrorWithMetrics(
        this,
        AnalyticsError.SettingsFail(
            AnalyticsError.NetworkUnknown(
                URL("https://$cdnHost/projects/$writeKey/settings"),
                ex
            )
        ),
        "Failed to fetch settings",
        Telemetry.INVOKE_ERROR_METRIC,
        ex.stackTraceToString()
    ) {
        it["error"] = ex.toString()
        it["writekey"] = writeKey
        it["message"] = "Error retrieving settings"
    }
    null
}