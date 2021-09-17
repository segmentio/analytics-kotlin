package com.segment.analytics.destinations.plugins

import android.app.Application
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.log
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.kotlin.core.utilities.putAll
import com.segment.analytics.kotlin.core.utilities.safeJsonObject
import io.intercom.android.sdk.Company
import io.intercom.android.sdk.Intercom
import io.intercom.android.sdk.UserAttributes
import io.intercom.android.sdk.identity.Registration
import kotlinx.serialization.json.*

class IntercomDestination(
    private val application: Application
): DestinationPlugin(), AndroidLifecycle {

    override val key: String = "Intercom"
    private var mobileApiKey: String = ""
    private var appId: String = ""
    lateinit var intercom: Intercom
        private set

    // Intercom common specced attributes
    private val NAME = "name"
    private val CREATED_AT = "createdAt"
    private val COMPANY = "company"
    private val PRICE = "price"
    private val AMOUNT = "amount"
    private val CURRENCY = "currency"

    // Intercom specced user attributes
    private val EMAIL = "email"
    private val PHONE = "phone"
    private val LANGUAGE_OVERRIDE = "languageOverride"
    private val UNSUBSCRIBED_FROM_EMAILS = "unsubscribedFromEmails"

    // Intercom specced group attributes
    private val MONTHLY_SPEND = "monthlySpend"
    private val PLAN = "plan"

    // Segment specced properties
    private val REVENUE = "revenue"
    private val TOTAL = "total"

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        // if we've already set up this singleton SDK, can't do it again, so skip.
        if (type != Plugin.UpdateType.Initial) return
        super.update(settings, type)

        settings.integrations[key]?.jsonObject?.let {
            mobileApiKey = it.getString("mobileApiKey") ?: ""
            appId = it.getString("appId") ?: ""
        }

        Intercom.initialize(application, mobileApiKey, appId)
        this.intercom = Intercom.client()
    }

    override fun track(payload: TrackEvent): BaseEvent? {
        val result = super.track(payload)

        val eventName = payload.event
        val properties = payload.properties

        if (!properties.isNullOrEmpty()) {
            val price = buildJsonObject{
                val amount = properties[REVENUE] ?: properties[TOTAL]
                amount?.let {
                    if (it is Number) {
                        put(AMOUNT, it.toDouble() * 100)
                    }
                }

                properties[CURRENCY]?.let {
                    put(CURRENCY, it.toString())
                }
            }

            val event = buildJsonObject {
                if (!price.isNullOrEmpty()) {
                    put(PRICE, price)
                }

                properties.forEach {
                    if (it.key !in arrayOf("products", REVENUE, TOTAL, CURRENCY)
                        && it.value is JsonPrimitive) {
                        put(it.key, it.value)
                    }
                }
            }

            intercom.logEvent(eventName, event)
            analytics.log("Intercom.client().logEvent($eventName, $event)")
        }
        else {
            intercom.logEvent(eventName)
            analytics.log("Intercom.client().logEvent($eventName)")
        }

        return result
    }

    override fun identify(payload: IdentifyEvent): BaseEvent? {
        val result =  super.identify(payload)

        val userId = payload.userId
        if (userId.isEmpty()) {
            intercom.registerUnidentifiedUser()
            analytics.log("Intercom.client().registerUnidentifiedUser()")
        }
        else {
            val registration = Registration.create().withUserId(userId)
            intercom.registerIdentifiedUser(registration)
            analytics.log("Intercom.client().registerIdentifiedUser(registration)")
        }

        payload.integrations["Intercom"]?.safeJsonObject?.let { intercomOptions ->
            intercomOptions["userHash"]?.let {
                val str = it.toString()
                if (str.isNotEmpty()) {
                    intercom.setUserHash(str)
                }
            }

            if (!payload.traits.isNullOrEmpty() && intercomOptions.isNotEmpty()) {
                setUserAttributes(payload.traits, intercomOptions)
            }
            else {
                setUserAttributes(payload.traits, null)
            }
        }

        return result
    }

    override fun group(payload: GroupEvent): BaseEvent? {
        val result = super.group(payload)

        if (payload.groupId.isNotEmpty()) {
            val traits = buildJsonObject {
                putAll(payload.traits)
                put("id", payload.groupId)
            }
            val company = setCompany(traits)
            val userAttributes = UserAttributes.Builder()
                .withCompany(company)
                .build()
            intercom.updateUser(userAttributes)
        }

        return result
    }

    override fun reset() {
        super.reset()
        intercom.logout()
        analytics.log("Intercom.client().reset()")
    }

    private fun setUserAttributes(traits: Traits, intercomOptions: JsonObject?) {
        val builder = UserAttributes.Builder()
            .withName(traits[NAME].toString())
            .withEmail(traits[EMAIL].toString())
            .withPhone(traits[PHONE].toString())

        intercomOptions?.let {
            builder.withLanguageOverride(it[LANGUAGE_OVERRIDE].toString())

            it[CREATED_AT]?.let { createdAt ->
                if (createdAt is Number) {
                    builder.withSignedUpAt(createdAt.toLong())
                }
            }

            it[UNSUBSCRIBED_FROM_EMAILS]?.let { unsubscribed ->
                if (unsubscribed is JsonPrimitive) {
                    builder.withUnsubscribedFromEmails(unsubscribed.jsonPrimitive.booleanOrNull)
                }
            }
        }

        traits[COMPANY]?.let {
            if (it is JsonObject) {
                val company = setCompany(it)
                builder.withCompany(company)
            }
        }

        traits.forEach {
            if (it.value is JsonPrimitive &&
                it.key !in arrayOf(NAME, EMAIL, PHONE, "userId", "anonymousId")) {
                builder.withCustomAttribute(it.key, it.value)
            }
        }

        intercom.updateUser(builder.build())
        analytics.log("Intercom.client().updateUser(userAttributes)")
    }

    private fun setCompany(traits: JsonObject): Company {
        val builder = Company.Builder()
        traits["id"]?.let {
            builder.withCompanyId(it.toString())
        } ?: return builder.build()

        traits[NAME]?.let {
            builder.withName(it.toString())
        }

        traits[CREATED_AT]?.let {
            if (it is JsonPrimitive) {
                builder.withCreatedAt(it.jsonPrimitive.longOrNull)
            }
        }

        traits[MONTHLY_SPEND]?.let {
            if (it is Number) {
                builder.withMonthlySpend(it.toInt())
            }
        }

        traits[PLAN]?.let {
            builder.withPlan(it.toString())
        }

        traits.forEach {
           if (it.value is JsonPrimitive &&
                   it.key !in arrayOf("id", NAME, CREATED_AT, MONTHLY_SPEND, PLAN)) {
               builder.withCustomAttribute(it.key, it.value)
           }
        }

        return builder.build()
    }
}