package com.segment.analytics.kotlin.core.platform.plugins


import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.EventType
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.Storage

/**
 * Analytics plugin used to populate events with basic UserInfo data.
 * Auto-added to analytics client on construction
 */
class UserInfoPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before

    lateinit var storage: Storage

    override suspend fun execute(event: BaseEvent): BaseEvent {
        var injectedEvent: BaseEvent = event

        if (injectedEvent.type == EventType.Identify) {

            storage.write(Storage.Constants.UserId, injectedEvent.userId.toString())
            storage.write(Storage.Constants.AnonymousId, injectedEvent.anonymousId.toString())

            return injectedEvent as IdentifyEvent
        } else if (injectedEvent.type === EventType.Alias) {
            storage.write(Storage.Constants.AnonymousId, injectedEvent.anonymousId.toString())

            return injectedEvent as AliasEvent
        }

        injectedEvent.userId = storage.read(Storage.Constants.UserId).toString()
        injectedEvent.anonymousId = storage.read(Storage.Constants.AnonymousId).toString()

        return injectedEvent
    }

}

