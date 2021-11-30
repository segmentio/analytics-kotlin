package com.segment.analytics.destinations.plugins

import android.app.Application
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.*
import io.intercom.android.sdk.Company
import io.intercom.android.sdk.Intercom
import io.intercom.android.sdk.UserAttributes
import io.intercom.android.sdk.identity.Registration
import kotlinx.serialization.json.*

/*
This is an example of the Intercom device-mode destination plugin that can be integrated with
Segment analytics.
Note: This plugin is NOT SUPPORTED by Segment.  It is here merely as an example,
and for your convenience should you find it useful.
# Instructions for adding Intercom:
- In your app-module build.gradle file add the following:
```
...
dependencies {
    ...
    // Intercom
    implementation 'io.intercom.android:intercom-sdk-base:10.1.1'
    implementation 'io.intercom.android:intercom-sdk-fcm:10.1.1'
}
```
- Copy this entire IntercomDestination.kt file into your project's codebase.
- Go to your project's codebase and wherever u initialize the analytics client add these lines
```
val intercom = IntercomDestination()
analytics.add(intercom)
```

Note: due to the inclusion of Intercom partner integration your minSdk cannot be smaller than 21

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

class IntercomDestination(
    private val application: Application
): DestinationPlugin(), AndroidLifecycle {

    override val key: String = "Intercom"
    private var mobileApiKey: String = ""
    private var appId: String = ""
    lateinit var intercom: Intercom
        private set

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

            val eventProperties = buildJsonObject {
                if (!price.isNullOrEmpty()) {
                    put(PRICE, price)
                }

                properties.forEach { (key, value) ->
                    // here we are only interested in primitive values and not maps or collections
                    if (key !in setOf("products", REVENUE, TOTAL, CURRENCY)
                        && value is JsonPrimitive) {
                        put(key, value)
                    }
                }
            }

            intercom.logEvent(eventName, eventProperties)
            analytics.log("Intercom.client().logEvent($eventName, $eventProperties)")
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
            // here we are only interested in primitive values and not maps or collections
            if (value is JsonPrimitive &&
                key !in setOf(NAME, EMAIL, PHONE, "userId", "anonymousId")) {
                builder.withCustomAttribute(key, value.toContent())
            }
        }

        intercom.updateUser(builder.build())
        analytics.log("Intercom.client().updateUser(userAttributes)")
    }

    private fun setCompany(company: JsonObject): Company {
        val builder = Company.Builder()

        val id = company.getString("id")
        if (id == null) {
            return builder.build()
        }
        else {
            builder.withCompanyId(id)
        }

        company.getString(NAME)?.let { builder.withName(it) }
        company.getLong(CREATED_AT)?.let { builder.withCreatedAt(it) }
        company.getInt(MONTHLY_SPEND)?.let { builder.withMonthlySpend(it) }
        company.getString(PLAN)?.let { builder.withPlan(it) }

        company.forEach { (key, value) ->
           // here we are only interested in primitive values and not maps or collections
           if (value is JsonPrimitive &&
                   key !in setOf("id", NAME, CREATED_AT, MONTHLY_SPEND, PLAN)
           ) {
               builder.withCustomAttribute(key, value.toContent())
           }
        }

        return builder.build()
    }

    companion object {

        // Intercom common specced attributes
        private const val NAME = "name"
        private const val CREATED_AT = "createdAt"
        private const val COMPANY = "company"
        private const val PRICE = "price"
        private const val AMOUNT = "amount"
        private const val CURRENCY = "currency"

        // Intercom specced user attributes
        private const val EMAIL = "email"
        private const val PHONE = "phone"
        private const val LANGUAGE_OVERRIDE = "languageOverride"
        private const val UNSUBSCRIBED_FROM_EMAILS = "unsubscribedFromEmails"

        // Intercom specced group attributes
        private const val MONTHLY_SPEND = "monthlySpend"
        private const val PLAN = "plan"

        // Segment specced properties
        private const val REVENUE = "revenue"
        private const val TOTAL = "total"
    }
}