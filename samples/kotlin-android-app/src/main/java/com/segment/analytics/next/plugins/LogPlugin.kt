package com.segment.analytics.next.plugins

import android.util.Log
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.log
import com.segment.analytics.kotlin.core.utilities.updateJsonObject
import kotlinx.serialization.json.JsonObject

class LogPlugin: Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics
    companion object {
        private const val TAG = "LogPlugin"
    }
    override fun execute(event: BaseEvent): BaseEvent? {

        Log.d(TAG, "I'm the LogPlugin: $event")

        analytics.log("Logging from the analytics.log()")
        return event
    }
}