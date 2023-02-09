package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.EventType
import com.segment.analytics.kotlin.core.platform.Plugin

/**
 * Analytics plugin used to populate events with basic UserInfo data.
 * Auto-added to analytics client on construction
 */
class UserInfoPlugin : Plugin {

    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics

    override fun execute(event: BaseEvent): BaseEvent {
        val injectedEvent: BaseEvent = event

        if (injectedEvent.type == EventType.Identify) {

            analytics.userInfo.userId =  injectedEvent.userId
            analytics.userInfo.anonymousId =  injectedEvent.anonymousId

        } else if (injectedEvent.type === EventType.Alias) {

            analytics.userInfo.anonymousId =  injectedEvent.anonymousId

        } else {

            injectedEvent.userId = analytics.userInfo.userId.toString()
            injectedEvent.anonymousId = analytics.userInfo.anonymousId
        }

        return injectedEvent
    }

}

