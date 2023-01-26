package com.segment.analytics.kotlin.android.utils

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.*

class TestRunPlugin(var closure: (BaseEvent?) -> Unit): EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics
    var ran = false

    override fun reset() {
        ran = false
    }

    fun updateState(ran: Boolean) {
        this.ran = ran
    }

    override fun execute(event: BaseEvent): BaseEvent {
        super.execute(event)
        updateState(true)
        return event
    }

    override fun track(payload: TrackEvent): BaseEvent {
        closure(payload)
        updateState(true)
        return payload
    }

    override fun identify(payload: IdentifyEvent): BaseEvent {
        closure(payload)
        updateState(true)
        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent {
        closure(payload)
        updateState(true)
        return payload
    }

    override fun group(payload: GroupEvent): BaseEvent {
        closure(payload)
        updateState(true)
        return payload
    }

    override fun alias(payload: AliasEvent): BaseEvent {
        closure(payload)
        updateState(true)
        return payload
    }
}

class StubPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics
}