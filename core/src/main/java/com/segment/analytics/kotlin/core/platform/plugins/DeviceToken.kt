package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.putInContextUnderKey

/**
 * Analytics plugin to add device token to events
 */
class DeviceToken(var token: String) : Plugin {
    override var type = Plugin.Type.Before
    override lateinit var analytics: Analytics

    override suspend fun execute(event: BaseEvent): BaseEvent {
        event.putInContextUnderKey("device", "token", token)
        return event
    }
}

/**
 * Set a device token in your payload's context
 * @param token [String] Device Token to add to payloads
 */
fun Analytics.setDeviceToken(token: String) {
    var tokenPlugin = find(DeviceToken::class)
    if (tokenPlugin != null) {
        tokenPlugin.token = token
    } else {
        tokenPlugin = DeviceToken(token)
        add(tokenPlugin)
    }
}
