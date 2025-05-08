package com.segment.analytics.next.plugins

import android.content.Context
import com.amplitude.android.sessionreplay.SessionReplay
import com.amplitude.android.sessionreplay.config.PrivacyConfig
import com.amplitude.android.sessionreplay.internal.InternalOptions
import com.amplitude.common.Logger
import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.ServerZone
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.Plugin
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
    sessionId: Long = -1L,
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
): Plugin {

    override val type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

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

    override fun execute(event: BaseEvent): BaseEvent? {
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
            -1)

        val additionalEventProperties = sessionReplay.getSessionReplayProperties().toJsonElement()
        if (additionalEventProperties is JsonObject) {
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
}