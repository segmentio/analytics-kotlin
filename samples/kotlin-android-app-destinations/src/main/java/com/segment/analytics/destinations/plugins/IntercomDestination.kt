package com.segment.analytics.destinations.plugins

import android.app.Application
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.log
import com.segment.analytics.kotlin.core.utilities.*
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
                val amount = properties.getDouble(REVENUE) ?: properties.getDouble(TOTAL)
                amount?.let {
                    put(AMOUNT, it * 100)
                }

                properties.getString(CURRENCY)?.let {
                    put(CURRENCY, it)
                }
            }

            val event = buildJsonObject {
                if (!price.isNullOrEmpty()) {
                    put(PRICE, price)
                }

                properties.forEach { (key, value) ->
                    if (key !in setOf("products", REVENUE, TOTAL, CURRENCY)
                        && value is JsonPrimitive) {
                        put(key, value)
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

        val intercomOptions = payload.integrations["Intercom"]?.safeJsonObject
        intercomOptions?.getString("userHash")?.let {
            intercom.setUserHash(it)
        }

        if (!payload.traits.isNullOrEmpty() && !intercomOptions.isNullOrEmpty()) {
            setUserAttributes(payload.traits, intercomOptions)
        }
        else {
            setUserAttributes(payload.traits, null)
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

        traits.getString(NAME)?.let { builder.withName(it) }
        traits.getString(EMAIL)?.let { builder.withEmail(it) }
        traits.getString(PHONE)?.let { builder.withPhone(it) }

        intercomOptions?.let {
            builder.withLanguageOverride(it.getString(LANGUAGE_OVERRIDE))
            builder.withSignedUpAt(it.getLong(CREATED_AT))
            builder.withUnsubscribedFromEmails(it.getBoolean(UNSUBSCRIBED_FROM_EMAILS))
        }

        traits[COMPANY]?.safeJsonObject?.let {
            val company = setCompany(it)
            builder.withCompany(company)
        }

        traits.forEach { (key, value) ->
            if (value is JsonPrimitive &&
                key !in setOf(NAME, EMAIL, PHONE, "userId", "anonymousId")) {
                builder.withCustomAttribute(key, value.toContent())
            }
        }

        intercom.updateUser(builder.build())
        analytics.log("Intercom.client().updateUser(userAttributes)")
    }

    private fun setCompany(traits: JsonObject): Company {
        val builder = Company.Builder()
        traits.getString("id")?.let {
            builder.withCompanyId(it)
        } ?: return builder.build()

        traits.getString(NAME)?.let { builder.withName(it) }
        traits.getLong(CREATED_AT)?.let { builder.withCreatedAt(it) }
        traits.getInt(MONTHLY_SPEND)?.let { builder.withMonthlySpend(it) }
        traits.getString(PLAN)?.let { builder.withPlan(it) }

        traits.forEach { (key, value) ->
           if (value is JsonPrimitive &&
                   key !in setOf("id", NAME, CREATED_AT, MONTHLY_SPEND, PLAN)
           ) {
               builder.withCustomAttribute(key, value.toContent())
           }
        }

        return builder.build()
    }
}