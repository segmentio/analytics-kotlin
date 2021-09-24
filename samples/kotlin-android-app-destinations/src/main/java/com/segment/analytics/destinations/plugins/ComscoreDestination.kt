package com.segment.analytics.destinations.plugins

import android.content.Context
import com.comscore.Analytics
import com.comscore.PartnerConfiguration
import com.comscore.PublisherConfiguration
import com.comscore.UsagePropertiesAutoUpdateMode
import com.comscore.streaming.AdvertisementMetadata
import com.comscore.streaming.ContentMetadata
import com.comscore.streaming.StreamingAnalytics
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.log
import com.segment.analytics.kotlin.core.utilities.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import com.segment.analytics.kotlin.core.Analytics as SegmentAnalytics
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/*
This is an example of the Comscore device-mode destination plugin that can be integrated with
Segment analytics.
Note: This plugin is NOT SUPPORTED by Segment.  It is here merely as an example,
and for your convenience should you find it useful.
To use it in your codebase, we suggest copying this file over and include the following
dependencies in your `build.gradle` file:
```
dependencies {
    ...
    api 'com.comscore:android-analytics:6.5.0'
}
```
Note: due to the inclusion of comscore partner integration your minSdk cannot be smaller than 21
MIT License
Copyright (c) 2021 Segment
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

@Serializable
data class ComscoreSettings(
    // Customer Id
    var c2: String,
    // Application Name for this instance
    var appName: String?,
    // Publisher Secret
    var publisherSecret: String,
    // enable autoUpdate of usage-properties
    var autoUpdate: Boolean = DEFAULT_AUTOUPDATE,
    // Interval for auto-update
    var autoUpdateInterval: Int = DEFAULT_INTERVAL,
    // enable usage tracking when the application is in foreground.
    var foregroundOnly: Boolean = DEFAULT_FOREGROUND,
    // enable HTTPS to be used for Comscore install
    var useHTTPS: Boolean = DEFAULT_HTTPS
) {
    companion object {
        const val DEFAULT_INTERVAL = 60
        const val DEFAULT_HTTPS = true
        const val DEFAULT_AUTOUPDATE = false
        const val DEFAULT_FOREGROUND = true
    }

    fun toPublisherConfiguration(): PublisherConfiguration =
        PublisherConfiguration.Builder()
            .publisherId(c2)
            .secureTransmission(useHTTPS)
            .build()

    fun analyticsConfig() = Analytics.getConfiguration()?.run {
        setUsagePropertiesAutoUpdateInterval(autoUpdateInterval)
        appName?.let { setApplicationName(it) }
        when {
            autoUpdate -> {
                setUsagePropertiesAutoUpdateMode(UsagePropertiesAutoUpdateMode.FOREGROUND_AND_BACKGROUND)
            }
            foregroundOnly -> {
                setUsagePropertiesAutoUpdateMode(UsagePropertiesAutoUpdateMode.FOREGROUND_ONLY)
            }
            else -> {
                setUsagePropertiesAutoUpdateMode(UsagePropertiesAutoUpdateMode.DISABLED)
            }
        }
    }
}

/**
 * This plugin copies any properties living under "videoMetricDictionaryClassification"
 * to an internal map for `track` events, which can be retrieved later.
 */
class ComscoreOptionsPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: SegmentAnalytics

    // Map of messageId to stored comscoreOptions
    val videoMetricClassificationMap = ConcurrentHashMap<String, Map<String, String>>()

    override fun track(payload: TrackEvent): BaseEvent {
        val comscoreOptions = payload.properties[TARGET_KEY]?.safeJsonObject
        if (comscoreOptions.isNullOrEmpty()) {
            return payload
        }
        payload.properties = JsonObject(payload.properties.filter { (k, _) ->
            k != TARGET_KEY
        })
        videoMetricClassificationMap[payload.messageId] = comscoreOptions.asStringMap()
        return payload
    }

    companion object {
        const val TARGET_KEY = "videoMetricDictionaryClassification"
    }
}

class ComscoreDestination(
    private var comScoreAnalytics: ComscoreAnalytics = ComscoreAnalytics.DefaultComscoreAnalytics()
) : DestinationPlugin() {
    override val key: String = "comScore"

    internal var streamingAnalytics: StreamingAnalytics? = null
    internal val configurationLabels = hashMapOf<String, String>()
    internal var settings: ComscoreSettings? = null
    internal var comscoreOptionsPlugin: ComscoreOptionsPlugin? = null

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        if (settings.isDestinationEnabled(key)) {
            analytics.log("Comscore Destination is enabled")
            this.settings = settings.destinationSettings(key, ComscoreSettings.serializer())
            if (type == Plugin.UpdateType.Initial) {
                val optionsPlugin = ComscoreOptionsPlugin()
                analytics.add(optionsPlugin)
                comscoreOptionsPlugin = optionsPlugin
                this.settings?.let {
                    comScoreAnalytics.start(
                        analytics.configuration.application as Context,
                        PARTNER_ID,
                        it.toPublisherConfiguration()
                    )
                    it.analyticsConfig()
                    analytics.log("Comscore Destination loaded")
                }
            }
        }
    }

    override fun flush() {
        comScoreAnalytics.flush()
    }

    override fun identify(payload: IdentifyEvent): BaseEvent {
        val userId: String = payload.userId
        val anonymousId: String = payload.anonymousId
        payload.traits = buildJsonObject {
            putAll(payload.traits)
            put("userId", userId)
            put("anonymousId", anonymousId)
        }
        comScoreAnalytics.setPersistentLabels(payload.traits.asStringMap())
        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent {
        val name: String = payload.name
        val category: String = payload.category
        payload.properties = buildJsonObject {
            putAll(payload.properties)
            put("name", name)
            put("category", category)
        }
        comScoreAnalytics.notifyViewEvent(payload.properties.asStringMap())
        return payload
    }

    override fun track(payload: TrackEvent): BaseEvent {
        val event = payload.event
        val properties = payload.properties

        var comscoreOptions =
            comscoreOptionsPlugin?.videoMetricClassificationMap?.get(payload.messageId)
        if (comscoreOptions.isNullOrEmpty()) {
            comscoreOptions = emptyMap()
        }

        when (event) {
            "Video Playback Started",
            "Video Playback Paused",
            "Video Playback Interrupted",
            "Video Playback Buffer Started",
            "Video Playback Buffer Completed",
            "Video Playback Seek Started",
            "Video Playback Seek Completed",
            "Video Playback Resumed" -> {
                trackVideoPlayback(
                    payload, properties, comscoreOptions
                )
            }
            "Video Content Started",
            "Video Content Playing",
            "Video Content Completed" -> {
                trackVideoContent(
                    payload, properties, comscoreOptions
                )
            }
            "Video Ad Started",
            "Video Ad Playing",
            "Video Ad Completed" -> {
                trackVideoAd(
                    payload,
                    properties,
                    comscoreOptions
                )
            }
            else -> {
                val props = buildJsonObject {
                    putAll(properties)
                    put("name", event)
                }
                comScoreAnalytics.notifyHiddenEvent(props.asStringMap())
            }
        }

        // Clear the map now that we are done with it
        comscoreOptionsPlugin?.videoMetricClassificationMap?.remove(payload.messageId)

        return payload
    }

    private fun trackVideoPlayback(
        track: TrackEvent,
        properties: JsonObject,
        comscoreOptions: Map<String, String>
    ) {
        val name: String = track.event
        var playbackPosition: Long = properties.getLong("playbackPosition") ?: 0L
        if (playbackPosition == 0L) {
            playbackPosition = properties.getLong("position") ?: 0L
        }
        var adType = properties.getString("adType")
        if (adType.isNullOrBlank()) {
            adType = properties.getString("ad_type")
            if (adType.isNullOrBlank()) {
                adType = properties.getString("type")
            }
        }
        configurationLabels.clear()
        val mappedPlaybackProperties =
            mapPlaybackProperties(properties, comscoreOptions, PLAYBACK_MAPPER)
        if (name == "Video Playback Started") {
            comScoreAnalytics.createStreamingAnalytics().let {
                streamingAnalytics = it
                it.createPlaybackSession()
                it.configuration.addLabels(mappedPlaybackProperties)

                // adding ad_type to configurationLabels assuming pre-roll ad plays before video content
                if (adType != null) {
                    configurationLabels["ns_st_ad"] = adType
                }

                // The label ns_st_ci must be set through a setAsset call
                val mappedContentProperties = mapSpecialKeys(properties, CONTENT_ID_MAPPER)
                it.setMetadata(getContentMetadata(mappedContentProperties))
                mappedContentProperties["ns_st_ci"]?.let { asset_id ->
                    configurationLabels["ns_st_ci"] = asset_id
                }
            }
            return
        }
        streamingAnalytics.let {
            if (it == null) {
                analytics.log("streamingAnalytics instance not initialized correctly. Please call Video Playback Started to initialize.")
            } else {
                it.configuration.addLabels(mappedPlaybackProperties)
                when (name) {
                    "Video Playback Paused", "Video Playback Interrupted" -> {
                        it.notifyPause()
                        analytics.log("streamingAnalytics.notifyPause($playbackPosition)")
                    }
                    "Video Playback Buffer Started" -> {
                        it.startFromPosition(playbackPosition)
                        it.notifyBufferStart()
                        analytics.log("streamingAnalytics.notifyBufferStart($playbackPosition)")
                    }
                    "Video Playback Buffer Completed" -> {
                        it.startFromPosition(playbackPosition)
                        it.notifyBufferStop()
                        analytics.log("streamingAnalytics.notifyBufferStop($playbackPosition)")
                    }
                    "Video Playback Seek Started" -> {
                        it.notifySeekStart()
                        analytics.log("streamingAnalytics.notifySeekStart($playbackPosition)")
                    }
                    "Video Playback Seek Completed" -> {
                        it.startFromPosition(playbackPosition)
                        it.notifyPlay()
                        analytics.log("streamingAnalytics.notifyPlay($playbackPosition)")
                    }
                    "Video Playback Resumed" -> {
                        it.startFromPosition(playbackPosition)
                        it.notifyPlay()
                        analytics.log("streamingAnalytics.notifyPlay($playbackPosition)")
                    }
                }
            }
        }
    }

    private fun trackVideoContent(
        track: TrackEvent,
        properties: Properties,
        comscoreOptions: Map<String, String>
    ) {
        val name: String = track.event
        var playbackPosition = properties.getLong("playbackPosition") ?: 0L
        if (playbackPosition == 0L) {
            playbackPosition = properties.getLong("position") ?: 0L
        }
        val mappedContentProperties =
            mapContentProperties(properties, comscoreOptions, CONTENT_MAPPER)
        streamingAnalytics.let {
            if (it == null) {
                analytics.log("streamingAnalytics instance not initialized correctly. Please call Video Playback Started to initialize.")
            } else {
                when (name) {
                    "Video Content Started" -> {
                        it.setMetadata(getContentMetadata(mappedContentProperties))
                        analytics.log("streamingAnalytics.setMetadata($mappedContentProperties)")
                        it.startFromPosition(playbackPosition)
                        it.notifyPlay()
                        analytics.log("streamingAnalytics.notifyPlay($playbackPosition)")
                    }
                    "Video Content Playing" -> {
                        // The presence of ns_st_ad on the StreamingAnalytics's asset means that we just exited an ad break, so
                        // we need to call setAsset with the content metadata.  If ns_st_ad is not present, that means the last
                        // observed event was related to content, in which case a setAsset call should not be made (because asset
                        // did not change).
                        if (configurationLabels.containsKey("ns_st_ad")) {
                            it.setMetadata(
                                getContentMetadata(
                                    mappedContentProperties
                                )
                            )
                            analytics.log("streamingAnalytics.setMetadata($mappedContentProperties)")
                        }
                        it.startFromPosition(playbackPosition)
                        it.notifyPlay()
                        analytics.log("streamingAnalytics.notifyPlay($playbackPosition)")
                    }
                    "Video Content Completed" -> {
                        it.notifyEnd()
                        analytics.log("streamingAnalytics.notifyEnd($playbackPosition)")
                    }
                }
            }
        }
    }

    private fun trackVideoAd(
        track: TrackEvent,
        properties: Properties,
        comscoreOptions: Map<String, String>
    ) {
        val name: String = track.event
        var playbackPosition = properties.getLong("playbackPosition") ?: 0L
        if (playbackPosition == 0L) {
            playbackPosition = properties.getLong("position") ?: 0L
        }
        var adType = properties.getString("adType")
        if (adType == null || adType.trim().isEmpty()) {
            adType = properties.getString("ad_type")
            if (adType == null || adType.trim().isEmpty()) {
                adType = properties.getString("type")
            }
        }
        if (adType != null) {
            configurationLabels["ns_st_ad"] = adType
        }
        val mappedAdProperties =
            mapAdProperties(properties, comscoreOptions, AD_MAPPER).toMutableMap()
        streamingAnalytics.let {
            if (it == null) {
                analytics.log("streamingAnalytics instance not initialized correctly. Please call Video Playback Started to initialize.")
            } else {
                when (name) {
                    "Video Ad Started" -> {
                        // The ID for content is not available on Ad Start events, however it will be available on the current
                        // StreamingAnalytics's asset. This is because ns_st_ci will have already been set on Content Started
                        // calls (if this is a mid or post-roll), or on Video Playback Started (if this is a pre-roll).
                        val contentId = configurationLabels["ns_st_ci"]
                        if (!contentId.isNullOrEmpty()) {
                            mappedAdProperties["ns_st_ci"] = contentId
                        }
                        it.setMetadata(getAdvertisementMetadata(mappedAdProperties))
                        analytics.log("streamingAnalytics.setMetadata($mappedAdProperties)")
                        it.startFromPosition(playbackPosition)
                        it.notifyPlay()
                        analytics.log("streamingAnalytics.notifyPlay($playbackPosition)")
                    }
                    "Video Ad Playing" -> {
                        it.startFromPosition(playbackPosition)
                        it.notifyPlay()
                        analytics.log("streamingAnalytics.notifyPlay($playbackPosition)")
                    }
                    "Video Ad Completed" -> {
                        it.notifyEnd()
                        analytics.log("streamingAnalytics.notifyEnd($playbackPosition)")
                    }
                }
            }
        }
    }

    companion object {
        const val PARTNER_ID = "24186693"
        val AD_MAPPER = mapOf(
            "assetId" to "ns_st_ami",
            "asset_id" to "ns_st_ami",
            "title" to "ns_st_amt",
            "publisher" to "ns_st_pu"
        )
        val CONTENT_MAPPER = mapOf(
            "title" to "ns_st_ep",
            "season" to "ns_st_sn",
            "episode" to "ns_st_en",
            "genre" to "ns_st_ge",
            "program" to "ns_st_pr",
            "channel" to "ns_st_st",
            "publisher" to "ns_st_pu",
            "fullEpisode" to "ns_st_ce",
            "full_episode" to "ns_st_ce",
            "podId" to "ns_st_pn",
            "pod_id" to "ns_st_pn"
        )
        val CONTENT_ID_MAPPER = mapOf(
            "assetId" to "ns_st_ci",
            "asset_id" to "ns_st_ci"
        )
        val PLAYBACK_MAPPER = mapOf(
            "videoPlayer" to "ns_st_mp",
            "video_player" to "ns_st_mp",
            "sound" to "ns_st_vo"
        )
    }

    private fun mapContentProperties(
        properties: Properties,
        comscoreOptions: Map<String, String>,
        mapper: Map<String, String>
    ): Map<String, String> {
        val asset = mapSpecialKeys(properties, mapper).toMutableMap()
        var contentAssetId = properties.getString("assetId")
        if (contentAssetId.isNullOrBlank()) {
            contentAssetId = properties.getString("asset_id")
            if (contentAssetId.isNullOrBlank()) {
                contentAssetId = "0"
            }
        }
        asset["ns_st_ci"] = contentAssetId.toString()
        if (properties.containsKey("totalLength") || properties.containsKey("total_length")) {
            // Comscore expects milliseconds.
            var length = (properties.getInt("totalLength") ?: 0) * 1000
            if (length == 0) {
                length = (properties.getInt("total_length") ?: 0) * 1000
            }
            asset["ns_st_cl"] = length.toString()
        }
        if (comscoreOptions.containsKey("contentClassificationType")) {
            val contentClassificationType = comscoreOptions["contentClassificationType"].toString()
            asset["ns_st_ct"] = contentClassificationType
        } else {
            asset["ns_st_ct"] = "vc00"
        }
        if (comscoreOptions.containsKey("digitalAirdate")) {
            val digitalAirdate = comscoreOptions["digitalAirdate"].toString()
            asset["ns_st_ddt"] = digitalAirdate
        }
        if (comscoreOptions.containsKey("tvAirdate")) {
            val tvAirdate = comscoreOptions["tvAirdate"].toString()
            asset["ns_st_tdt"] = tvAirdate
        }
        setNullIfNotProvided(asset, comscoreOptions, properties, "c3")
        setNullIfNotProvided(asset, comscoreOptions, properties, "c4")
        setNullIfNotProvided(asset, comscoreOptions, properties, "c6")
        return asset
    }

    private fun mapAdProperties(
        properties: Properties,
        comscoreOptions: Map<String, String>,
        mapper: Map<String, String>
    ): Map<String, String> {
        val asset = mapSpecialKeys(properties, mapper).toMutableMap()
        if (properties.containsKey("totalLength") || properties.containsKey("total_length")) {
            // Comscore expects milliseconds.
            var length = (properties.getInt("totalLength") ?: 0) * 1000
            if (length == 0) {
                length = (properties.getInt("total_length") ?: 0) * 1000
            }
            asset["ns_st_cl"] = length.toString()
        }
        if (comscoreOptions.containsKey("adClassificationType")) {
            val adClassificationType = comscoreOptions["adClassificationType"].toString()
            asset["ns_st_ct"] = adClassificationType
        } else {
            asset["ns_st_ct"] = "va00"
        }
        when (val adType = properties.getString("type")) {
            "pre-roll",
            "mid-roll",
            "post-roll" -> {
                asset["ns_st_ad"] = adType
            }
            else -> {
                asset["ns_st_ad"] = "1"
            }
        }
        setNullIfNotProvided(asset, comscoreOptions, properties, "c3")
        setNullIfNotProvided(asset, comscoreOptions, properties, "c4")
        setNullIfNotProvided(asset, comscoreOptions, properties, "c6")
        return asset
    }

    private fun mapPlaybackProperties(
        properties: Properties,
        comscoreOptions: Map<String, String>,
        mapper: Map<String, String>
    ): Map<String, String> {
        val asset = mapSpecialKeys(properties, mapper).toMutableMap()
        var fullScreen = properties.getBoolean("fullScreen") ?: false
        if (!fullScreen) {
            fullScreen = properties.getBoolean("full_screen") ?: false
        }
        asset["ns_st_ws"] = if (fullScreen) "full" else "norm"
        val bitrate = (properties.getInt("bitrate") ?: 0) * 1000 // Comscore expects bps.
        asset["ns_st_br"] = bitrate.toString()
        setNullIfNotProvided(asset, comscoreOptions, properties, "c3")
        setNullIfNotProvided(asset, comscoreOptions, properties, "c4")
        setNullIfNotProvided(asset, comscoreOptions, properties, "c6")
        return asset
    }

}

/**
 * This is a wrapper to all Comscore components. It helps with testing since Comscore relays heavily
 * on JNI and static classes, and all needs to be mocked in any case.
 */
interface ComscoreAnalytics {
    /**
     * Creates a new streaming analytics session.
     *
     * @return The new session.
     */
    fun createStreamingAnalytics(): StreamingAnalytics

    /**
     * Starts collecting analytics with the provided client configuration
     *
     * @param context Application context
     * @param partnerId Partner ID
     * @param publisher Publisher configuration
     */
    fun start(context: Context, partnerId: String, publisher: PublisherConfiguration)

    /**
     * Sets global labels
     *
     * @param labels Labels.
     */
    fun setPersistentLabels(labels: Map<String, String>)

    /**
     * Sends an view event with the provided properties.
     *
     * @param properties Event properties.
     */
    fun notifyViewEvent(properties: Map<String, String>)

    /**
     * Sends an unmapped event with the provided properties.
     *
     * @param properties Event properties.
     */
    fun notifyHiddenEvent(properties: Map<String, String>)

    /**
     * Flushes the offline cache
     */
    fun flush()

    /**
     * Default implementation of ComscoreAnalytics. It uses the methods and classes provided by the
     * Comscore SDK.
     */
    class DefaultComscoreAnalytics : ComscoreAnalytics {
        override fun createStreamingAnalytics(): StreamingAnalytics {
            return StreamingAnalytics()
        }

        override fun start(
            context: Context,
            partnerId: String,
            publisher: PublisherConfiguration
        ) {
            val partner = PartnerConfiguration.Builder().partnerId(partnerId).build()
            Analytics.getConfiguration().addClient(partner)
            Analytics.getConfiguration().addClient(publisher)
            Analytics.start(context)
        }

        override fun setPersistentLabels(labels: Map<String, String>) {
            Analytics.getConfiguration().addPersistentLabels(labels)
        }

        override fun notifyViewEvent(properties: Map<String, String>) {
            Analytics.notifyViewEvent(properties)
        }

        override fun notifyHiddenEvent(properties: Map<String, String>) {
            Analytics.notifyHiddenEvent(properties)
        }

        override fun flush() {
            Analytics.flushOfflineCache()
        }
    }
}

// Map special keys, preserve only the special keys and convert to string -> string map
private fun mapSpecialKeys(
    properties: Properties,
    mapper: Map<String, String>
): Map<String, String> {
    return properties
        .mapTransform(mapper)
        .asStringMap()
        .filter { (key, _) -> key in mapper.values }
}

/**
 * Store a value for {@param key} in {@param asset} by checking {@param ComscoreOptions} first and
 * falling back to {@param properties}. Uses `"*null"` it not found in either.
 */
private fun setNullIfNotProvided(
    asset: MutableMap<String, String>,
    comscoreOptions: Map<String, String>,
    properties: Properties,
    key: String
) {
    comscoreOptions[key]?.let {
        asset[key] = it
        return
    }
    properties[key]?.let {
        asset[key] = it.toContent().toString()
        return
    }
    asset[key] = "*null"
}

fun getContentMetadata(mappedContentProperties: Map<String, String>): ContentMetadata {
    return ContentMetadata.Builder().customLabels(mappedContentProperties).build()
}

fun getAdvertisementMetadata(mappedAdProperties: Map<String, String>): AdvertisementMetadata {
    return AdvertisementMetadata.Builder().customLabels(mappedAdProperties).build()
}

private fun JsonObject.asStringMap(): Map<String, String> = this.mapValues { (_, value) ->
    value.toContent().toString()
}