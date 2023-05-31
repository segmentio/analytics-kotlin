package com.segment.analytics.kotlin.android.utilities

import android.content.Intent
import com.segment.analytics.kotlin.core.Analytics
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeepLinkUtils(val analytics: Analytics) {

    fun trackDeepLinkFrom(referrer: String?, intent: Intent?) {

        if (intent == null || intent.data == null) {
            return
        }

        val properties = buildJsonObject {

            referrer?.let {
                put("referrer", it)
            }

            val uri = intent.data
            uri?.let {
                if (it.isHierarchical) {
                    for (parameter in uri.queryParameterNames) {
                        val value = uri.getQueryParameter(parameter)
                        if (value != null && value.trim().isNotEmpty()) {
                            put(parameter, value)
                        }
                    }
                }
                put("url", uri.toString())
            }
        }
        analytics.track("Deep Link Opened", properties)
    }
}