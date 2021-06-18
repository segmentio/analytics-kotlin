package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.plugins.LogType
import com.segment.analytics.kotlin.core.platform.plugins.log
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.BufferedReader

@Serializable
data class Settings(
    var integrations: JsonObject = emptyJsonObject,
    var plan: JsonObject = emptyJsonObject,
    var edgeFunction: JsonObject = emptyJsonObject,
)

internal fun Analytics.update(settings: Settings) {
    timeline.applyClosure { plugin ->
        // tell all top level plugins to update.
        // For destination plugins they auto-handle propagation to sub-plugins
        plugin.update(settings)
    }
}

/**
 * Make analytics client call into Segment's settings API, to refresh certain configurations.
 */
fun Analytics.checkSettings() {
    val writeKey = configuration.writeKey

    // stop things; queue in case our settings have changed.
    store.dispatch(System.ToggleRunningAction(running = false), System::class)

    analyticsScope.launch(ioDispatcher) {
        log("Fetching settings on ${Thread.currentThread().name}")
        val settingsObj: Settings? = try {
            val connection = HTTPClient().settings(writeKey)
            val settingsString =
                connection.inputStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            log("Fetched Settings: $settingsString")
            Json { ignoreUnknownKeys = true }.decodeFromString(settingsString)
        } catch (ex: Exception) {
            log(message = "${ex.message}: failed to fetch settings", type = LogType.ERROR)
            null
        }
        settingsObj?.let {
            log("Dispatching update settings on ${Thread.currentThread().name}")
            store.dispatch(System.UpdateSettingsAction(settingsObj), System::class)
            update(settingsObj)
        }

        // we're good to go back to a running state.
        store.dispatch(System.ToggleRunningAction(running = true), System::class)
    }
}