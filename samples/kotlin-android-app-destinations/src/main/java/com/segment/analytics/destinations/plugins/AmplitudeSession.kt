package com.segment.analytics.destinations.plugins

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.plugins.LogType
import com.segment.analytics.kotlin.core.platform.plugins.log
import com.segment.analytics.kotlin.core.utilities.putIntegrations
import java.util.*
import kotlin.concurrent.schedule

// A Destination plugin that adds session tracking to Amplitude cloud mode.
class AmplitudeSession() : DestinationPlugin() {

    override val name: String = "AmplitudeSession"
    var sessionID: Long = -1

    private var timer: TimerTask? = null
    private val fireTime: Long = 300000

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
    }

    // Add the session_id to the Amplitude payload for cloud mode to handle.
    private inline fun <reified T: BaseEvent?> insertSession(payload: T?): BaseEvent? {
        var returnPayload = payload
        payload?.let {
            analytics.log(message = "Running ${payload.type} payload through $name", event = payload, type = LogType.INFO)
            returnPayload = payload.putIntegrations("Amplitude", mapOf("session_id" to sessionID)) as T?
        }
        return returnPayload
    }

    override fun track(payload: TrackEvent): BaseEvent? {
        if (payload.event == "Application Backgrounded") {
            onBackground()
        } else if (payload.event == "Application Opened") {
            onForeground()
        }
        insertSession(payload)
        return payload
    }

    override fun identify(payload: IdentifyEvent): BaseEvent? {
        insertSession(payload)
        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent? {
        insertSession(payload)
        return payload
    }

    override fun group(payload: GroupEvent): BaseEvent? {
        insertSession(payload)
        return payload
    }

    override fun alias(payload: AliasEvent): BaseEvent? {
        insertSession(payload)
        return payload
    }

    fun onBackground() {
        stopTimer()
    }

    fun onForeground() {
        startTimer()
    }

    fun startTimer() {

        // Set the session id
        sessionID = Calendar.getInstance().timeInMillis

        timer = Timer().schedule(fireTime) {
            stopTimer()
            startTimer()
        }
    }

    fun stopTimer() {
        timer?.cancel()
        sessionID = -1
    }
}
