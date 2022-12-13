package com.segment.analytics.next.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.EventType
import com.segment.analytics.kotlin.core.platform.Plugin

class InjectTraitsPlugin: Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    override fun execute(event: BaseEvent): BaseEvent? {

        if (event.type == EventType.Identify) {
            return event
        }

        event.context.plus(Pair("traits", analytics.traits()));

        return event
    }
}