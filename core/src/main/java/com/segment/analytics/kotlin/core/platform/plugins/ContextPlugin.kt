package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.putAll
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

/**
 * Analytics plugin used to populate events with basic context data.
 * Auto-added to analytics client on construction
 */
class ContextPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics

    private lateinit var library: JsonObject
    private val instanceId = UUID.randomUUID().toString()

    companion object {
        // Library
        const val LIBRARY_KEY = "library"
        const val LIBRARY_NAME_KEY = "name"
        const val LIBRARY_VERSION_KEY = "version"
        const val INSTANCE_ID_KEY = "instanceId"
        const val REFERRER_KEY = "referrer"
        const val REFERRER_URL_KEY = "url"
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        library = buildJsonObject {
            put(LIBRARY_NAME_KEY, "analytics-kotlin")
            put(LIBRARY_VERSION_KEY, LIBRARY_VERSION)
        }
    }

    private fun applyContextData(event: BaseEvent) {
        val newContext = buildJsonObject {
            // referrer from the last deep link; added first so a caller-supplied context wins
            analytics.userInfo.referrer?.let { referrer ->
                put(REFERRER_KEY, buildJsonObject { put(REFERRER_URL_KEY, referrer) })
            }

            // copy existing context
            putAll(event.context)

            // putLibrary
            put(LIBRARY_KEY, library)
            put(INSTANCE_ID_KEY, instanceId)
        }
        event.context = newContext
    }

    override fun execute(event: BaseEvent): BaseEvent {
        applyContextData(event)
        return event
    }
}