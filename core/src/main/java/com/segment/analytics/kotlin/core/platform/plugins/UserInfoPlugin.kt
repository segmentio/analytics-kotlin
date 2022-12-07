package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.EventType
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Analytics plugin used to populate events with basic UserInfo data.
 * Auto-added to analytics client on construction
 */
class UserInfoPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics


    private fun applyUserInfoData(event: BaseEvent) {
        val newUserInfo = buildJsonObject {
            // copy existing context
            put("anonymousId", event.anonymousId)
            put("userId", event.userId)
        }

        if (event.type == EventType.Identify) {

        } else if (event.type == EventType.Alias) {

        } else {
            event.anonymousId =  newUserInfo.get("anonymousId").toString()
            event.userId =  newUserInfo.get("userId").toString()
        }
    }

    override fun execute(event: BaseEvent): BaseEvent {
        applyUserInfoData(event)
        return event
    }
}
