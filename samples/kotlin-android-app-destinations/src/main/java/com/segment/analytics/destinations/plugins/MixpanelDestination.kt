package com.segment.analytics.destinations.plugins

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.segment.analytics.*
import com.segment.analytics.platform.DestinationPlugin
import com.segment.analytics.platform.plugins.LogType
import com.segment.analytics.platform.plugins.android.AndroidLifecycle
import com.segment.analytics.platform.plugins.log
import com.segment.analytics.utilities.*
import kotlinx.serialization.json.*
import java.util.*

class MixpanelDestination(
    private val context: Context,
    private var token: String
) : DestinationPlugin(), AndroidLifecycle {
    override val name: String = "Mixpanel"
    private var mixpanel: MixpanelAPI = MixpanelAPI.getInstance(context, token)

    companion object {
        private val TRAITS_MAPPER: Map<String, String> = mapOf(
            "email" to "\$email",
            "phone" to "\$phone",
            "firstName" to "\$first_name",
            "lastName" to "\$last_name",
            "name" to "\$name",
            "username" to "\$username",
            "createdAt" to "\$created",
        )
    }

    // Configuration
    private var isPeopleEnabled = false
    private var setAllTraitsByDefault = false
    private var consolidatedPageCalls = false
    private var trackAllPages = false
    private var trackCategorizedPages = false
    private var trackNamedPages = false
    private var peoplePropertiesFilter: Set<String> = setOf("peopleProperties")
    private var superPropertiesFilter: Set<String> = setOf("superProperties")
    private var increments: Set<String> = setOf("superProperties")

    override fun track(payload: TrackEvent): BaseEvent {
        val eventName = payload.event

        trackEvent(eventName, payload.properties)

        if (isPeopleEnabled && increments.contains(eventName)) {
            mixpanel.people.increment(eventName, 1.0)
            mixpanel.people.set("Last $eventName", Date())
        }

        return payload
    }

    override fun identify(payload: IdentifyEvent): BaseEvent {
        val userId = payload.userId
        mixpanel.identify(userId)
        analytics.log("mixpanel.identify($userId)", type = LogType.INFO, event = payload)

        // Filter based on configuration and transform keys using PROPERTY_MAPPER
        val peopleProperties = if (setAllTraitsByDefault || peoplePropertiesFilter.isEmpty()) {
            payload.traits
        } else {
            payload.traits.filter { (k, _) -> peoplePropertiesFilter.contains(k) }
        }.mapKeys { TRAITS_MAPPER[it.key] ?: it.key }

        // Filter based on configuration and transform keys using PROPERTY_MAPPER
        val superProperties = if (setAllTraitsByDefault || superPropertiesFilter.isEmpty()) {
            payload.traits
        } else {
            payload.traits.filter { (k, _) -> superPropertiesFilter.contains(k) }
        }.mapKeys { TRAITS_MAPPER[it.key] ?: it.key }

        mixpanel.registerSuperProperties(superProperties.toJSONObject())
        analytics.log(
            "mixpanel.registerSuperProperties($superProperties)",
            type = LogType.INFO,
            event = payload
        )

        if (isPeopleEnabled) {
            mixpanel.people.identify(userId)
            analytics.log(
                "mixpanel.people.identify($userId)",
                type = LogType.INFO,
                event = payload
            )
            mixpanel.people.set(peopleProperties.toJSONObject())
            analytics.log(
                "mixpanel.getPeople().set($peopleProperties)",
                type = LogType.INFO,
                event = payload
            )
        }

        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent {
        if (consolidatedPageCalls) {
            val props = buildJsonObject {
                putAll(payload.properties)
                put("name", payload.name)
            }
            trackEvent("Loaded a Screen", props)
        } else if (trackAllPages) {
            trackEvent("Viewed ${payload.name} Screen", payload.properties)
        } else if (trackCategorizedPages && payload.category.isNotEmpty()) {
            trackEvent("Viewed ${payload.category} Screen", payload.properties)
        } else if (trackNamedPages && payload.name.isNotEmpty()) {
            trackEvent("Viewed ${payload.name} Screen", payload.properties)
        }
        return payload
    }

    override fun group(payload: GroupEvent): BaseEvent {
        val payloadGroupName = payload.traits["name"]?.jsonPrimitive?.let {
            if (it.isString) {
                it.content
            } else {
                null
            }
        }
        val groupName = if (payloadGroupName.isNullOrEmpty()) {
            "[Segment] Group"
        } else {
            payloadGroupName
        }
        val groupId = payload.groupId

        // Set Group Traits
        if (payload.traits.isNotEmpty()) {
            mixpanel.getGroup(groupName, groupId).setOnce(payload.traits.toJSONObject())
        }

        mixpanel.setGroup(groupName, groupId)
        analytics.log(
            "mixpanel.setGroup($groupName, $groupId)",
            type = LogType.INFO,
            event = payload
        )

        return payload
    }

    override fun alias(payload: AliasEvent): BaseEvent {
        val previousId = if (payload.previousId == payload.anonymousId) {
            // Instead of using our own anonymousId, we use Mixpanel's own generated Id.
            mixpanel.distinctId
        } else {
            payload.previousId
        }
        val userId = payload.userId
        if (userId.isNotBlank()) {
            mixpanel.alias(userId, previousId)
            analytics.log("mixpanel.alias($userId, $previousId)")
        }
        return payload
    }

    override fun update(settings: Settings) {
        super.update(settings)
        val mixpanelSettings = settings.integrations[name]
        mixpanelSettings?.jsonObject?.let {
            consolidatedPageCalls = it.getBoolean("consolidatedPageCalls") ?: true
            trackAllPages = it.getBoolean("trackAllPages") ?: true
            trackCategorizedPages = it.getBoolean("trackCategorizedPages") ?: true
            trackNamedPages = it.getBoolean("trackNamedPages") ?: true
            isPeopleEnabled = it.getBoolean("people") ?: true
            consolidatedPageCalls = it.getBoolean("consolidatedPageCalls") ?: true
            setAllTraitsByDefault = it.getBoolean("setAllTraitsByDefault") ?: true

            token = it.getString("token") ?: ""
            increments = it.getStringSet("increments") ?: emptySet()
            peoplePropertiesFilter = it.getStringSet("peopleProperties") ?: emptySet()
            superPropertiesFilter = it.getStringSet("superProperties") ?: emptySet()
        }
        mixpanel = MixpanelAPI.getInstance(context, token)
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        // This is needed to trigger a call to #checkIntentForInboundAppLink.
        // From Mixpanel's source, this won't trigger a creation of another instance. It caches
        // instances by the application context and token, both of which remain the same.
        MixpanelAPI.getInstance(activity, token);
    }

    private fun trackEvent(name: String, properties: JsonObject) {
        val props = properties.toJSONObject()
        mixpanel.track(name, props)
        analytics.log("mixpanel.track($name, $properties)")

        val revenue = properties["revenue"]?.jsonPrimitive?.double
        if (isPeopleEnabled && revenue != null && revenue != 0.0) {
            mixpanel.people.trackCharge(revenue, props)
            analytics.log("mixpanel.people.trackCharge($name, $props)")
        }
    }
}