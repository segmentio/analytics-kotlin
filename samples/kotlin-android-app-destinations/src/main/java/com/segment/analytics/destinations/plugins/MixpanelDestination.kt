package com.segment.analytics.destinations.plugins

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.segment.analytics.kotlin.android.utilities.toJSONObject
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.*

/*
This is an example of the Mixpanel device-mode destination plugin that can be integrated with
Segment analytics.
Note: This plugin is NOT SUPPORTED by Segment.  It is here merely as an example,
and for your convenience should you find it useful.
To use it in your codebase, we suggest copying this file over and include the following
dependencies in your `build.gradle` file:
```
dependencies {
    ...
    api 'com.mixpanel.android:mixpanel-android:5.8.7'
}
```
MIT License
Copyright (c) 2021 Segment
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

@Serializable
data class MixpanelSettings(
    var token: String,
    @SerialName("people")
    var isPeopleEnabled: Boolean = false,
    var setAllTraitsByDefault: Boolean = true,
    var consolidatedPageCalls: Boolean = true,
    var trackAllPages: Boolean = false,
    var trackCategorizedPages: Boolean = false,
    var trackNamedPages: Boolean = false,
    @SerialName("peopleProperties")
    var peoplePropertiesFilter: Set<String> = setOf(),
    @SerialName("superProperties")
    var superPropertiesFilter: Set<String> = setOf(),
    @SerialName("eventIncrements")
    var increments: Set<String> = setOf()
)

class MixpanelDestination(
    private val context: Context
) : DestinationPlugin(), AndroidLifecycle {

    internal var settings: MixpanelSettings? = null
    internal var mixpanel: MixpanelAPI? = null

    override val key: String = "Mixpanel"

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        if (settings.isDestinationEnabled(key)) {
            analytics.log("Mixpanel Destination is enabled")
            this.settings = settings.destinationSettings(key)
            if (type == Plugin.UpdateType.Initial) {
                mixpanel = MixpanelAPI.getInstance(context, this.settings?.token)
                analytics.log("Mixpanel Destination loaded")
            }
        } else {
            analytics.log("Mixpanel destination is disabled via settings")
        }
    }

    override fun track(payload: TrackEvent): BaseEvent? {
        val settings = settings ?: return payload
        // Example of transforming event property keys
        val eventName = payload.event
        val properties = payload.properties

        trackEvent(eventName, properties)

        with(settings) {
            if (isPeopleEnabled && increments.contains(eventName)) {
                mixpanel?.people?.increment(eventName, 1.0)
                mixpanel?.people?.set("Last $eventName", Date())
            }
        }

        return payload
    }

    override fun identify(payload: IdentifyEvent): BaseEvent? {
        val settings = settings ?: return payload
        val userId: String = payload.userId
        val traits: JsonObject = payload.traits

        if (userId.isNotEmpty()) {
            mixpanel?.identify(userId)
            analytics.log("mixpanel.identify($userId)")
        }

        with(settings) {
            // Filter based on configuration
            val peopleProperties = if (setAllTraitsByDefault) {
                traits
            } else {
                traits.filter { (k, _) -> peoplePropertiesFilter.contains(k) }
            }.map(TRAIT_MAPPER)

            // Filter based on configuration
            val superProperties = if (setAllTraitsByDefault) {
                traits
            } else {
                traits.filter { (k, _) -> superPropertiesFilter.contains(k) }
            }.map(TRAIT_MAPPER)

            if (superProperties.isNotEmpty()) {
                mixpanel?.registerSuperProperties(superProperties.toJSONObject())
                analytics.log("mixpanel.registerSuperProperties($superProperties)")
            }

            if (isPeopleEnabled) {
                mixpanel?.people?.identify(userId)
                analytics.log("mixpanel.people.identify($userId)")
                if (peopleProperties.isNotEmpty()) {
                    mixpanel?.people?.set(peopleProperties.toJSONObject())
                    analytics.log("mixpanel.getPeople().set($peopleProperties)")
                }
            }
        }

        return payload
    }

    override fun group(payload: GroupEvent): BaseEvent? {
        val groupId = payload.groupId
        val traits = payload.traits

        val payloadGroupName = traits.getString("name")
        val groupName = if (payloadGroupName.isNullOrEmpty()) {
            "[Segment] Group"
        } else {
            payloadGroupName
        }

        // Set Group Traits
        if (traits.isNotEmpty()) {
            mixpanel?.getGroup(groupName, groupId)?.setOnce(traits.toJSONObject())
        }

        mixpanel?.setGroup(groupName, groupId)
        analytics.log("mixpanel.setGroup($groupName, $groupId)")

        return payload
    }

    override fun alias(payload: AliasEvent): BaseEvent? {
        val userId = payload.userId
        val previousId = if (payload.previousId == payload.anonymousId) {
            // Instead of using our own anonymousId, we use Mixpanel's own generated Id.
            mixpanel?.distinctId
        } else {
            payload.previousId
        }
        if (userId.isNotBlank()) {
            mixpanel?.alias(userId, previousId)
            analytics.log("mixpanel.alias($userId, $previousId)")
        }
        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent? {
        val settings = settings ?: return payload
        val screenName = payload.name
        val properties = payload.properties
        val screenCategory = payload.category

        with(settings) {
            if (consolidatedPageCalls) {
                val props = buildJsonObject {
                    putAll(properties)
                    put("name", screenName)
                }
                trackEvent("Loaded a Screen", props)
            } else if (trackAllPages) {
                trackEvent("Viewed $screenName Screen", properties)
            } else if (trackCategorizedPages && screenCategory.isNotEmpty()) {
                trackEvent("Viewed $screenCategory Screen", properties)
            } else if (trackNamedPages && screenName.isNotEmpty()) {
                trackEvent("Viewed $screenName Screen", properties)
            }
        }

        return payload
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        val settings = settings ?: return
        // This is needed to trigger a call to #checkIntentForInboundAppLink.
        // From Mixpanel's source, this won't trigger a creation of another instance. It caches
        // instances by the application context and token, both of which remain the same.
        MixpanelAPI.getInstance(activity, settings.token);
    }

    companion object {
        // Rules for transforming a track event name
        private val TRAIT_MAPPER = mapOf(
            "email" to "\$email",
            "phone" to "\$phone",
            "firstName" to "\$first_name",
            "lastName" to "\$last_name",
            "name" to "\$name",
            "username" to "\$username",
            "createdAt" to "\$created"
        )
    }

    private fun trackEvent(name: String, properties: JsonObject) {
        val props = properties.toJSONObject()
        mixpanel?.track(name, props)
        analytics.log("mixpanel.track($name, $properties)")

        val revenue = properties.getDouble("revenue")

        with(settings!!) {
            if (isPeopleEnabled && revenue != null && revenue != 0.0) {
                mixpanel?.people?.trackCharge(revenue, props)
                analytics.log("mixpanel.people.trackCharge($name, $props)")
            }
        }
    }

    private fun Map<String, JsonElement>.map(
        keyMapper: Map<String, String>,
    ): Map<String, JsonElement> = JsonObject(this).mapTransform(keyMapper, null)

}