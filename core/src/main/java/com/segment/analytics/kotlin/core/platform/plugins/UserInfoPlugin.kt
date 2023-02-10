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
    var savedUserId: String? = null
    var savedAnonymousId: String? = null

    override fun execute(event: BaseEvent): BaseEvent {

        if (event.type == EventType.Identify) {

            savedUserId =  event.userId
            savedAnonymousId =  event.anonymousId

        } else if (event.type === EventType.Alias) {

            savedAnonymousId =  event.anonymousId

        } else {
            savedUserId?.let {
                event.userId = savedUserId.toString()
            }
            savedAnonymousId?.let {
                event.anonymousId = savedAnonymousId.toString()
            }
        }

        return event
    }

}

