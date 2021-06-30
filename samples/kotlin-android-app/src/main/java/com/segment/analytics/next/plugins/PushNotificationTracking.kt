package com.segment.analytics.next.plugins

import android.app.Activity
import android.os.Bundle
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PushNotificationTracking: Plugin, AndroidLifecycle {
    override val type: Plugin.Type = Plugin.Type.Utility
    override val name: String = "PushNotificationTracking"
    override lateinit var analytics: Analytics

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        val bundle = if (savedInstanceState == null) {
            activity?.intent?.extras
        } else {
            Bundle().apply {
                putAll(savedInstanceState)
                putAll(activity?.intent?.extras)
            }
        }
        checkPushNotification(bundle)
    }

    private fun checkPushNotification(bundle: Bundle?) {
        if (bundle != null) {
            if (bundle.containsKey("push_notification")) {
                analytics.track("Push Notification Tapped", buildJsonObject {
                    put("action", "Open")
                    val campaign = buildJsonObject {
                        put("medium", "Push")
                        put("source", "FCM")
                        put("name", bundle.getString("title"))
                        put("content", bundle.getString("content"))
                    }
                    put("campaign", campaign)
                })
            }
        }
    }
}