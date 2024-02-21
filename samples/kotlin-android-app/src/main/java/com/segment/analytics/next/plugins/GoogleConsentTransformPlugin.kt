package com.segment.analytics.next.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.getString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The Google Consent Transformation plugin is plugin that is used to convert existing consent
 * properties in your events to the name/value that Google needs.
 *
 * This plugin is only needed in cases where your Consent Management Platform (CMP) is not already
 * add the consent information to your events.
 *
 *
 * Learn more here:
 *
 * https://developers.google.com/tag-platform/security/guides/app-consent?consentmode=basic&platform=android
 * https://support.google.com/tagmanager/answer/13695607
 * https://support.google.com/tagmanager/answer/13802165?sjid=2747385890937182130-NA
 */
class GoogleConsentTransformPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    companion object {
        private const val TAG = "GCTP"
    }

    override fun execute(event: BaseEvent): BaseEvent? {

        /**
         * In this example the app is already capturing consent in a `consent` property with the
         * following structure:
         *
         * {
         *   "storage": 1,
         *   "data": 0,
         *   "personalization": 0,
         *   "analytics": 1,
         *   "storageFunctional": 1,
         *   "storagePersonalized": 0,
         *   "storageSecurity": 1
         * }
         *
         * Here we have the names of the consent or store types and a value 0/1. Where 0 is not
         * granted and 1 is granted.
         *
         * This structure is a contrived example for this sample plugin. You must adapt your code
         * to whatever structure you have for your consent data.
         *
         * If you don't already have a way to capture consent and add it to your events please
         * see:
         *
         * https://github.com/segment-integrations/analytics-kotlin-consent
         *
         */
        val consentJson = event.context?.get("consent") as? JsonObject ?: emptyJsonObject

        // Grab all the consent values as 'granted' or 'denied'
        val adStorage = if (consentJson.getString("storage") == "1") "granted" else "denied"
        val adUserData = if (consentJson.getString("data") == "1") "granted" else "denied"
        val adPersonalization =
            if (consentJson.getString("personalization") == "1") "granted" else "denied"
        val analyticsStorage =
            if (consentJson.getString("analytics") == "1") "granted" else "denied"
        val functionalityStorage =
            if (consentJson.getString("storageFunctional") == "1") "granted" else "denied"
        val personalizationStorage =
            if (consentJson.getString("storagePersonalized") == "1") "granted" else "denied"
        val securityStorage =
            if (consentJson.getString("storageSecurity") == "1") "granted" else "denied"

        val map = event.context.jsonObject.toMutableMap()

        // map consent values to Google Consent approved keys.
        map.put("ad_storage", JsonPrimitive(adStorage))
        map.put("ad_user_data", JsonPrimitive(adUserData))
        map.put("ad_personalization", JsonPrimitive(adPersonalization))
        map.put("analytics_storage", JsonPrimitive(analyticsStorage))
        map.put("functionality_storage", JsonPrimitive(functionalityStorage))
        map.put("personalization_storage", JsonPrimitive(personalizationStorage))
        map.put("security_storage", JsonPrimitive(securityStorage))

        // Replace event.context properties with new properties
        event.context = buildJsonObject {
            map.forEach { (key, value) ->
                put(key, value)
            }
        }

        return event
    }
}