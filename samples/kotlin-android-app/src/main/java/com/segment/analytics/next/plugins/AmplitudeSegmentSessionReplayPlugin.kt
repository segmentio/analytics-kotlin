package com.segment.analytics.next.plugins

import android.content.Context
import com.amplitude.android.sessionreplay.SessionReplay
import com.amplitude.android.sessionreplay.config.PrivacyConfig
import com.amplitude.android.sessionreplay.internal.InternalOptions
import com.amplitude.common.Logger
import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.ServerZone
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.utilities.getLong
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.kotlin.core.utilities.safeJsonObject
import com.segment.analytics.kotlin.core.utilities.toJsonElement
import com.segment.analytics.kotlin.core.utilities.updateJsonObject
import kotlinx.serialization.json.JsonObject

class AmplitudeSegmentSessionReplayPlugin(
    amplitudeApiKey: String,
    context: Context,
    deviceId: String = "",
    sessionId: Long = 1L,
    optOut: Boolean = false,
    sampleRate: Number = 0.0,
    logger: Logger? = LogcatLogger(),
    enableRemoteConfig: Boolean = true,
    serverZone: ServerZone = ServerZone.US,
    serverUrl: String? = null,
    bandwidthLimitBytes: Int? = null,
    storageLimitMB: Int? = null,
    internalOptions: InternalOptions = InternalOptions(),
    privacyConfig: PrivacyConfig = PrivacyConfig(),
    library: String = "${SessionReplay.Library}/${SessionReplay.Version}"
): DestinationPlugin() {
    override val key: String = "Amplitude Session Replay"

    private val sessionReplay: SessionReplay

    init {
        sessionReplay = SessionReplay(
            amplitudeApiKey,
            context,
            deviceId,
            sessionId,
            optOut,
            sampleRate,
            logger,
            enableRemoteConfig,
            serverZone,
            serverUrl,
            bandwidthLimitBytes,
            storageLimitMB,
            internalOptions,
            privacyConfig,
            library
        )

        // SessionReplay in kotlin always auto starts
    }

    override fun track(payload: TrackEvent): BaseEvent? {
        return enrich(payload)
    }

    override fun identify(payload: IdentifyEvent): BaseEvent? {
        return enrich(payload)
    }

    private fun enrich(event: BaseEvent): BaseEvent? {
        val amplitudeProperties = event.integrations["Actions Amplitude"]
        val eventProperties = when(event) {
            is TrackEvent -> event.properties
            is ScreenEvent -> event.properties
            else -> null
        }

        sessionReplay.setDeviceId(event.context["device"]?.safeJsonObject?.getString("id") ?: event.anonymousId)
        sessionReplay.setSessionId(
            amplitudeProperties?.safeJsonObject?.getLong("session_id") ?:
            eventProperties?.getLong("session_id") ?:
            1L)

        val additionalEventProperties = sessionReplay.getSessionReplayProperties().toJsonElement()
        if (additionalEventProperties is JsonObject && additionalEventProperties.size > 0) {
            val properties = updateJsonObject(eventProperties ?: emptyJsonObject) {
                it.putAll(additionalEventProperties)
            }

            when(event) {
                is TrackEvent -> event.properties = properties
                is ScreenEvent -> event.properties = properties
                else -> {}
            }
        }

        return event
    }

    override fun flush() {
        sessionReplay.flush()
    }
}