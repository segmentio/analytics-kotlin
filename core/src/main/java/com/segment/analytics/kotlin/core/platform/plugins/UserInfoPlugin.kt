package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin

/**
 * Analytics plugin used to populate events with basic UserInfo data.
 * Auto-added to analytics client on construction
 */
class UserInfoPlugin : Plugin {

    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics

    override fun execute(event: BaseEvent): BaseEvent {

        if (event.type == EventType.Identify) {

            analytics.userInfo.userId = event.userId
            analytics.userInfo.anonymousId = event.anonymousId
            analytics.userInfo.traits = (event as IdentifyEvent).traits

        } else if (event.type === EventType.Alias) {

            analytics.userInfo.anonymousId = event.anonymousId
        } else {

            analytics.userInfo.userId?.let {
                event.userId = analytics.userInfo.userId.toString()
            }
            analytics.userInfo.anonymousId?.let {
                event.anonymousId = analytics.userInfo.anonymousId.toString()
            }
        }

        return event
    }

}

