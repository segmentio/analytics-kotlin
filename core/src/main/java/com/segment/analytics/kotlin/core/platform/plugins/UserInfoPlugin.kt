package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.json.JsonObject
import sovran.kotlin.Store


/**
 * Analytics plugin used to populate events with basic UserInfo data.
 * Auto-added to analytics client on construction
 */
class UserInfoPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics
    private lateinit var newUser: JsonObject

    lateinit var store: Store
    lateinit var userId : String
    lateinit var anonymousId : String

    override fun execute(event: BaseEvent): BaseEvent {
        store = analytics.store
        userId = newUser.get("userId").toString()
        anonymousId = newUser.get("anonymousId").toString()

        var injectedEvent: BaseEvent = event

        if (event.type == EventType.Identify) {

            return injectedEvent as IdentifyEvent

        } else if (event.type === EventType.Alias) {

            return injectedEvent as AliasEvent
        }

        injectedEvent.anonymousId = anonymousId
        injectedEvent.userId = userId
        return injectedEvent
    }

}

