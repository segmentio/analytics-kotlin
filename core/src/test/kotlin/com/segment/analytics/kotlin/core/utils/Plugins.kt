package com.segment.analytics.kotlin.core.utils

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.*

/**
 * An analytics plugin that can be used to test features
 * Current capabilities
 * - is a `Before` plugin so is guaranteed to be run
 * - can add a closure that will be run if any hook is executed
 * - has a boolean state `ran` that can be used to verify if a particular hook was executed
 * - has a `reset()` function to reset state between tests
 */
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

/**
 * An analytics plugin that is a simple pass-through plugin. Ideally to be used to verify
 * if particular hooks are run via mockk's `verify`
 */
open class StubPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics
}