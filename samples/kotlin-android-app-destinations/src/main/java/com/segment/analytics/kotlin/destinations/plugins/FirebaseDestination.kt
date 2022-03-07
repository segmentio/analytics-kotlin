package com.segment.analytics.kotlin.destinations.plugins

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.getDouble
import com.segment.analytics.kotlin.core.utilities.getMapList
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.json.JsonObject
import java.util.*

/*
This is an example of the Firebase device-mode destination plugin that can be integrated with
Segment analytics.
Note: This plugin is NOT SUPPORTED by Segment.  It is here merely as an example,
and for your convenience should you find it useful.
# Instructions for adding Firebase:
- In your top-level build.gradle file add these lines
```
buildscript {
    ...
    repositories {
        google()
    }
    dependencies {
        ...
        classpath 'com.google.gms:google-services:4.3.5'
    }
}
```
- In your app-module build.gradle file add the following:
```
...
plugins {
    id 'com.google.gms.google-services'
}
dependencies {
    ...
    implementation platform('com.google.firebase:firebase-bom:28.2.1')
    implementation 'com.google.firebase:firebase-analytics-ktx'
}
```
- Copy this entire FirebaseDestination.kt file into your project's codebase.
- Copy your google-service.json file to your to app-module
- Go to your project's codebase and wherever u initialize the analytics client add these lines
```
val Firebase = FirebaseDestination()
analytics.add(Firebase)
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

class FirebaseDestination(
    private val context: Context
) : DestinationPlugin(), AndroidLifecycle {

    override val key: String = "Firebase"
    internal var firebaseAnalytics: FirebaseAnalytics? = null
    private var activity: Activity? = null

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        if (type == Plugin.UpdateType.Initial) {
            @SuppressLint("MissingPermission") // Suppress need for INTERNET, WAKE_LOCK, NETWORK_STATE perms
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        }
    }

    // Make sure keys do not contain ".", "-", " ", ":" and are replaced with _
    private fun makeKey(key: String): String {
        val charsToFilter = """[. \-:]""".toRegex()
        return key.replace(charsToFilter, "_")
    }

    override fun identify(payload: IdentifyEvent): BaseEvent {
        val userId: String = payload.userId
        val traits: JsonObject = payload.traits
        firebaseAnalytics?.setUserId(userId)

        traits.let {
            for ((traitKey, traitValue) in it) {
                val normalizedKey = makeKey(traitKey)
                val updatedTraitValue = traitValue.toContent().toString()
                firebaseAnalytics?.setUserProperty(normalizedKey, updatedTraitValue)
                analytics.log("firebaseAnalytics.setUserProperty($normalizedKey, $updatedTraitValue)")
            }
        }

        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent? {
        val screenName = payload.name

        val tempActivity = activity
        if (tempActivity != null) {
            val bundle = bundleOf(
                FirebaseAnalytics.Param.SCREEN_NAME to screenName,
                FirebaseAnalytics.Param.SCREEN_CLASS to tempActivity::class.simpleName
            )
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            analytics.log("firebaseAnalytics.logEvent(SCREEN_VIEW, $bundle)")
        }

        return payload
    }

    override fun track(payload: TrackEvent): BaseEvent {
        val event = payload.event
        // Clean the eventName up
        val eventName = EVENT_MAPPER[event] ?: makeKey(event)

        val bundledProperties = formatProperties(payload.properties)

        firebaseAnalytics?.logEvent(eventName, bundledProperties)
        analytics.log("firebaseAnalytics.logEvent($eventName, $bundledProperties)")

        return payload
    }

    // AndroidActivity Methods
    override fun onActivityResumed(activity: Activity?) {
        super.onActivityResumed(activity)

        try {
            val packageManager = activity?.packageManager ?: return

            packageManager.getActivityInfo(activity.componentName, PackageManager.GET_META_DATA)
                .let {
                    it.loadLabel(packageManager).toString().let { activityName ->
                        val bundle = bundleOf(FirebaseAnalytics.Param.SCREEN_NAME to activityName)
                        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
                        analytics.log(
                            "firebaseAnalytics.setCurrentScreen(activity, $activityName, null"
                        )
                    }
                }
        } catch (exception: PackageManager.NameNotFoundException) {
            analytics.log("Activity Not Found: $exception")
        }
    }

    override fun onActivityStarted(activity: Activity?) {
        super.onActivityStarted(activity)
        this.activity = activity
    }

    override fun onActivityStopped(activity: Activity?) {
        super.onActivityStopped(activity)
        this.activity = null
    }


    // Private Helper Methods

    companion object {
        private val PROPERTY_MAPPER: Map<String, String> = mapOf(
            "category" to FirebaseAnalytics.Param.ITEM_CATEGORY,
            "product_id" to FirebaseAnalytics.Param.ITEM_ID,
            "name" to FirebaseAnalytics.Param.ITEM_NAME,
            "price" to FirebaseAnalytics.Param.PRICE,
            "quantity" to FirebaseAnalytics.Param.QUANTITY,
            "query" to FirebaseAnalytics.Param.SEARCH_TERM,
            "shipping" to FirebaseAnalytics.Param.SHIPPING,
            "tax" to FirebaseAnalytics.Param.TAX,
            "total" to FirebaseAnalytics.Param.VALUE,
            "revenue" to FirebaseAnalytics.Param.VALUE,
            "order_id" to FirebaseAnalytics.Param.TRANSACTION_ID,
            "currency" to FirebaseAnalytics.Param.CURRENCY,
            "products" to FirebaseAnalytics.Param.ITEM_LIST
        )

        private val PRODUCT_MAPPER: Map<String, String> = mapOf(
            "category" to FirebaseAnalytics.Param.ITEM_CATEGORY,
            "product_id" to FirebaseAnalytics.Param.ITEM_ID,
            "id" to FirebaseAnalytics.Param.ITEM_ID,
            "name" to FirebaseAnalytics.Param.ITEM_NAME,
            "price" to FirebaseAnalytics.Param.PRICE,
            "quantity" to FirebaseAnalytics.Param.QUANTITY
        )

        private val EVENT_MAPPER: Map<String, String> = mapOf(
            "Product Added" to FirebaseAnalytics.Event.ADD_TO_CART,
            "Checkout Started" to FirebaseAnalytics.Event.BEGIN_CHECKOUT,
            "Order Completed" to FirebaseAnalytics.Event.ECOMMERCE_PURCHASE,
            "Order Refunded" to FirebaseAnalytics.Event.PURCHASE_REFUND,
            "Product Viewed" to FirebaseAnalytics.Event.VIEW_ITEM,
            "Product List Viewed" to FirebaseAnalytics.Event.VIEW_ITEM_LIST,
            "Payment Info Entered" to FirebaseAnalytics.Event.ADD_PAYMENT_INFO,
            "Promotion Viewed" to FirebaseAnalytics.Event.PRESENT_OFFER,
            "Product Added to Wishlist" to FirebaseAnalytics.Event.ADD_TO_WISHLIST,
            "Product Shared" to FirebaseAnalytics.Event.SHARE,
            "Product Clicked" to FirebaseAnalytics.Event.SELECT_CONTENT,
            "Products Searched" to FirebaseAnalytics.Event.SEARCH
        )
    }

    private fun formatProperties(properties: JsonObject): Bundle? {
        val bundle = Bundle()

        val revenue = properties.getDouble("revenue") ?: 0.0
        val total = properties.getDouble("total") ?: 0.0
        val currency = properties.getString("currency") ?: ""
        if ((revenue != 0.0 || total != 0.0) && currency.isNotEmpty()) {
            bundle.putString(FirebaseAnalytics.Param.CURRENCY, "USD")
        }

        for ((property, value) in properties.entries) {
            var finalProperty = makeKey(property)
            if (PROPERTY_MAPPER.containsKey(property)) {
                finalProperty = PROPERTY_MAPPER[property].toString()
            }

            if (finalProperty == FirebaseAnalytics.Param.ITEM_LIST) {
                val products = properties.getMapList("products") ?: continue
                val formattedProducts = formatProducts(products)
                bundle.putParcelableArrayList(finalProperty, formattedProducts)
            } else {
                bundle.putValue(finalProperty, value.toContent())
            }
        }

        // Don't return a valid bundle if there wasn't anything added
        return if (bundle.isEmpty) {
            null
        } else {
            bundle
        }
    }

    private fun formatProducts(products: List<Map<String, Any?>>): ArrayList<Bundle> {
        val mappedProducts = ArrayList<Bundle>()

        for (product in products) {
            val mappedProduct = Bundle()
            for (key in product.keys) {
                val value = product[key]
                val finalKey = PRODUCT_MAPPER[key] ?: makeKey(key)
                mappedProduct.putValue(finalKey, value)
            }
            mappedProducts.add(mappedProduct)
        }

        return mappedProducts
    }

    // Adds the appropriate value & type to a supplied bundle
    private fun Bundle.putValue(key: String, value: Any?) {
        when (value) {
            is Boolean -> {
                putBoolean(key, value)
            }
            is Int -> {
                putInt(key, value)
            }
            is Double -> {
                putDouble(key, value)
            }
            is Long -> {
                putLong(key, value)
            }
            is String -> {
                putString(key, value)
            }
            else -> {
                val stringValue = value.toString()
                putString(key, stringValue)
            }
        }
    }

}