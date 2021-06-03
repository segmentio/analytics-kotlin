package com.segment.analytics.destinations.plugins

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.segment.analytics.*
import com.segment.analytics.AndroidLogger.log
import com.segment.analytics.platform.DestinationPlugin
import com.segment.analytics.platform.plugins.LogType
import com.segment.analytics.platform.plugins.log
import com.segment.analytics.utilities.putIntegrations
import java.time.Instant
import java.util.*
import kotlin.concurrent.schedule

// A Destination plugin that adds session tracking to Amplitude cloud mode.
class AmplitudeSession() : DestinationPlugin(), LifecycleObserver {

    override val name: String = "AmplitudeSession"
    var sessionID: Long = -1

    private var timer: TimerTask? = null
    private val fireTime: Long = 300000

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Sends a JSON payload to the specified webhookUrl, with the Content-Type=application/json
     * header set
     */
    private inline fun <reified T: BaseEvent?> insertSession(payload: T?): BaseEvent? {
        var returnPayload = payload
        payload?.let {
            analytics.log(message = "Running ${payload.type} payload through $name", event = payload, type = LogType.INFO)
            returnPayload = payload.putIntegrations("Amplitude", sessionID) as T?
        }
        return returnPayload
    }

    override fun track(payload: TrackEvent): BaseEvent? {
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

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onBackground() {
        log(LogType.INFO, "background", null)
        stopTimer()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onForeground() {
        log(LogType.INFO, "foreground", null)
        startTimer()
    }

    fun startTimer() {

        // Set the session id
        sessionID = Calendar.getInstance().timeInMillis

        timer = Timer().schedule(fireTime) {
            log(LogType.INFO, "Timer Fired", null)
            log(LogType.INFO, "Session: $sessionID", null)
            stopTimer()
            startTimer()
        }

    }

    fun stopTimer() {
        timer?.cancel()
        sessionID = -1
    }
}