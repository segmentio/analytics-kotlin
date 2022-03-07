package com.segment.analytics.kotlin.destinations.plugins

import android.app.Activity
import android.content.Context
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.Serializable
import com.appsflyer.AppsFlyerLib
import com.appsflyer.AppsFlyerConversionListener
import android.content.SharedPreferences
import android.os.Bundle
import com.appsflyer.AFInAppEventParameterName
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.appsflyer.deeplink.DeepLinkListener
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.kotlin.core.utilities.mapTransform
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.json.*

/*
This is an example of the AppsFlyer device-mode destination plugin that can be integrated with
Segment analytics.
Note: This plugin is NOT SUPPORTED by Segment.  It is here merely as an example,
and for your convenience should you find it useful.
To use it in your codebase, we suggest copying this file over and include the following
dependencies in your `build.gradle` file:
```
dependencies {
    ...
    implementation 'com.appsflyer:af-android-sdk:6.3.2'
    implementation 'com.android.installreferrer:installreferrer:2.2'
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
data class AppsFlyerSettings(
    var appsFlyerDevKey: String,
    var trackAttributionData: Boolean = false
)

class AppsFlyerDestination(
    private val applicationContext: Context,
    private var isDebug: Boolean = false
) : DestinationPlugin(), AndroidLifecycle {

    internal var settings: AppsFlyerSettings? = null
    internal var appsflyer: AppsFlyerLib? = null

    internal var customerUserId: String = ""
    internal var currencyCode: String = ""
    var conversionListener: ExternalAppsFlyerConversionListener? = null
    var deepLinkListener: ExternalDeepLinkListener? = null

    override val key: String = "AppsFlyer"

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        if (settings.hasIntegrationSettings(this)) {
            analytics.log("Appsflyer Destination is enabled")
            this.settings = settings.destinationSettings(key)
            if (type == Plugin.UpdateType.Initial) {
                appsflyer = AppsFlyerLib.getInstance()
                analytics.log("Appsflyer Destination loaded")
                var listener: AppsFlyerConversionListener? = null
                this.settings?.let {
                    if (it.trackAttributionData) {
                        listener = ConversionListener()
                    }
                    appsflyer?.setDebugLog(isDebug)
                    appsflyer?.init(it.appsFlyerDevKey, listener, applicationContext)
                }
            }
            deepLinkListener?.let {
                appsflyer?.subscribeForDeepLink(it)
            }
        }
    }

    override fun identify(payload: IdentifyEvent): BaseEvent? {
        val userId: String = payload.userId
        val traits: JsonObject = payload.traits

        customerUserId = userId
        currencyCode = traits.getString("currencyCode") ?: ""

        updateEndUserAttributes()

        return payload
    }

    override fun track(payload: TrackEvent): BaseEvent? {
        val event: String = payload.event
        val properties: Properties = payload.properties

        val afProperties = properties.mapTransform(propertiesMapper).mapValues { (_, v) -> v.toContent() }

        appsflyer?.logEvent(applicationContext, event, afProperties)
        analytics.log("appsflyer.logEvent(context, $event, $properties)")
        return payload
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        super.onActivityCreated(activity, savedInstanceState)
        if (activity != null) {
            AppsFlyerLib.getInstance().start(activity)
            analytics.log("AppsFlyerLib.getInstance().start($activity)")
        }
        updateEndUserAttributes()
    }

    private fun updateEndUserAttributes() {
        appsflyer?.setCustomerUserId(customerUserId)
        analytics.log("appsflyer.setCustomerUserId($customerUserId)")
        appsflyer?.setCurrencyCode(currencyCode)
        analytics.log("appsflyer.setCurrencyCode($currencyCode)")
        appsflyer?.setDebugLog(isDebug)
        analytics.log("appsflyer.setDebugLog($isDebug)")
    }


    companion object {
        const val AF_SEGMENT_SHARED_PREF = "appsflyer-segment-data"
        const val CONV_KEY = "AF_onConversion_Data"
    }

    private val propertiesMapper = mapOf(
        "revenue" to AFInAppEventParameterName.REVENUE,
        "price" to AFInAppEventParameterName.PRICE,
        "currency" to AFInAppEventParameterName.CURRENCY
    )

    interface ExternalAppsFlyerConversionListener : AppsFlyerConversionListener
    interface ExternalDeepLinkListener : DeepLinkListener

    inner class ConversionListener : AppsFlyerConversionListener {
        override fun onConversionDataSuccess(conversionData: Map<String, Any>) {
            if (!getFlag(CONV_KEY)) {
                trackInstallAttributed(conversionData)
                setFlag(CONV_KEY, true)
            }
            conversionListener?.onConversionDataSuccess(conversionData)
        }

        override fun onConversionDataFail(errorMessage: String) {
            conversionListener?.onConversionDataFail(errorMessage)
        }

        override fun onAppOpenAttribution(attributionData: Map<String, String>) {
            conversionListener?.onAppOpenAttribution(attributionData)
        }

        override fun onAttributionFailure(errorMessage: String) {
            conversionListener?.onAttributionFailure(errorMessage)
        }

        private fun convertToPrimitive(value: Any?): JsonElement {
            return when (value) {
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                is Map<*, *> -> buildJsonObject {
                    value.forEach { (k, v) ->
                        put(k.toString(), convertToPrimitive(v))
                    }
                }
                is List<*> -> buildJsonArray {
                    value.forEach { v ->
                        add(convertToPrimitive(v))
                    }
                }
                is Array<*> -> buildJsonArray {
                    value.forEach { v ->
                        add(convertToPrimitive(v))
                    }
                }
                else -> JsonPrimitive(value.toString())
            }
        }

        private fun trackInstallAttributed(attributionData: Map<String, Any>) {
            // See https://segment.com/docs/spec/mobile/#install-attributed.
            val properties = buildJsonObject {
                put("provider", key)
                attributionData.forEach { (k, v) ->
                    if (k !in setOf("media_source", "adgroup")) {
                        put(k, convertToPrimitive(v))
                    }
                }
                put("campaign", buildJsonObject {
                    put("source", convertToPrimitive(attributionData["media_source"]))
                    put("name", convertToPrimitive(attributionData["campaign"]))
                    put("ad_group", convertToPrimitive(attributionData["adgroup"]))
                })
            }

            // If you are working with networks that don't allow passing user level data to 3rd parties,
            // you will need to apply code to filter out these networks before calling
            // `analytics.track("Install Attributed", properties);`
            analytics.track("Install Attributed", properties)
        }

        private fun getFlag(key: String): Boolean {
            val sharedPreferences: SharedPreferences =
                applicationContext.getSharedPreferences(AF_SEGMENT_SHARED_PREF, 0)
            return sharedPreferences.getBoolean(key, false)
        }

        private fun setFlag(key: String, value: Boolean) {
            val sharedPreferences: SharedPreferences =
                applicationContext.getSharedPreferences(AF_SEGMENT_SHARED_PREF, 0)
            val editor = sharedPreferences.edit()
            editor.putBoolean(key, value).apply()
        }
    }

}