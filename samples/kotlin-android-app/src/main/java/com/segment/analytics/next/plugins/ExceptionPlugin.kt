package com.segment.analytics.next.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.updateJsonObject
import kotlinx.serialization.json.JsonObject

class ExceptionPlugin: Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    override fun execute(event: BaseEvent): BaseEvent? {
        throw Error("I'm a bad plugin! Boom! HAHAHAHAHA!!!")
        return event
    }
}