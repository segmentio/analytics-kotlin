package com.segment.analytics.kotlin.android.utilities

import android.content.Intent
import android.net.Uri
import com.segment.analytics.kotlin.core.Analytics
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeepLinkUtils(val analytics: Analytics) {

    fun trackDeepLinkFrom(referrer: String?, intent: Intent?) {

        if (intent == null || intent.data == null) {
            return
        }

        val properties = extractLinkProperties(referrer, intent.data)
        analytics.track("Deep Link Opened", properties)
    }

    /**
     * Builds a JsonObject with the parameters of a given Uri.
     *
     * Note: The Uri must be hierarchical (myUri.isHierarchical == true) for parameters to be
     * extracted.
     *
     * Example hierarchical Uri: http://example.com/
     * Example non-hierarchical Uri: mailto:me@email.com
     *
     * Note: we return the given Uri as a property named: "url" since this is what is expected
     * upstream.
     */
    fun extractLinkProperties(
        referrer: String?,
        uri: Uri?
    ): JsonObject {
        val properties = buildJsonObject {
            referrer?.let {
                put("referrer", it)
            }

            if (uri != null) {
                if (uri.isHierarchical) {
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

        return properties
    }
}