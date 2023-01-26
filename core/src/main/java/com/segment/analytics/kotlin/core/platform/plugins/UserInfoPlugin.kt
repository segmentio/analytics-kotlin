package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin

/**
 * Analytics plugin used to populate events with basic UserInfo data.
 * Auto-added to analytics client on construction
 */
class UserInfoPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before

    lateinit var userInfo: UserInfo

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        // empty body default
    }

    override fun execute(event: BaseEvent): BaseEvent {
        val injectedEvent: BaseEvent = event

        if (injectedEvent.type == EventType.Identify) {

            userInfo.userId =  injectedEvent.userId
            userInfo.anonymousId =  injectedEvent.anonymousId

            return injectedEvent as IdentifyEvent
        } else if (injectedEvent.type === EventType.Alias) {

            userInfo.anonymousId =  injectedEvent.anonymousId

            return injectedEvent as AliasEvent
        }

        injectedEvent.userId = userInfo.userId.toString()
        injectedEvent.anonymousId = userInfo.anonymousId

        return injectedEvent
    }

}

