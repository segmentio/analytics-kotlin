package com.segment.analytics.next.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.updateJsonObject
import kotlinx.serialization.json.JsonObject

class InjectTraitsPlugin: Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    /*
    NOTE: This object acts as a cache for the traits object; We update it as we get
    identify events. Given its role as a cache, however, it should live as a global
    static object. Moving it to your Application class is a good solution.
     */
    var cachedTraits: Traits = emptyJsonObject

    override fun execute(event: BaseEvent): BaseEvent? {

        if (event.type == EventType.Identify) {

            // Grab trait related info from the identify event
            // and update the cache
            val jsonTraits= event.context.get("traits") as? JsonObject ?: emptyJsonObject

            cachedTraits = updateJsonObject(cachedTraits) {
                it.putAll(jsonTraits)
            }
        } else {
            // All other events get the traits added to them.
            event.context = updateJsonObject(event.context) {
                it["traits"] = cachedTraits
            }
        }

        return event
    }
}