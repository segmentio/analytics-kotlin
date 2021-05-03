package com.segment.analytics

import com.segment.analytics.platform.plugins.LogType
import com.segment.analytics.platform.plugins.log
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.BufferedReader
import java.util.zip.GZIPInputStream

@Serializable
data class Settings(
    var integrations: JsonObject = emptyJsonObject,
    var plan: JsonObject = emptyJsonObject,
    var edgeFunction: JsonObject = emptyJsonObject,
)

/**
 * Make analytics client call into Segment's settings API, to refresh certain configurations.
 */
fun Analytics.checkSettings() {
    val writeKey = configuration.writeKey
    val defaultSettings = configuration.defaultSettings
    analyticsScope.launch(ioDispatcher) {
        log("Fetching settings on ${Thread.currentThread().name}")
        val settingsObj: Settings = try {
            val connection = HTTPClient().settings(writeKey)
            val settingsString =
                connection.inputStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            log("Fetched Settings: $settingsString")
            Json { ignoreUnknownKeys = true }.decodeFromString(settingsString)
        } catch (ex: Exception) {
            log(message = "${ex.message}: failed to fetch settings", type = LogType.ERROR)
            defaultSettings
        }
        log("Dispatching update settings on ${Thread.currentThread().name}")
        store.dispatch(System.UpdateSettingsAction(settingsObj), System::class)
    }
}