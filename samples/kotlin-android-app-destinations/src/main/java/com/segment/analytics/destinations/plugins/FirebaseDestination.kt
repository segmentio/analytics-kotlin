package com.segment.analytics.destinations.plugins

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.Event
import com.google.firebase.analytics.FirebaseAnalytics.Param
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.Properties
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.plugins.log
import com.segment.analytics.kotlin.core.utilities.getDouble
import com.segment.analytics.kotlin.core.utilities.getMapSet
import com.segment.analytics.kotlin.core.utilities.getString
import kotlinx.serialization.json.JsonPrimitive

class FirebaseDestination(
    private val context: Context
) : DestinationPlugin(), AndroidLifecycle {

    override val key: String = "Firebase"
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var activity: Activity? = null

    companion object {
        private val EVENT_MAPPER: Map<String, String> = mapOf(
            "Product Added" to Event.ADD_TO_CART,
            "Checkout Started" to Event.BEGIN_CHECKOUT,
            "Order Completed" to Event.ECOMMERCE_PURCHASE,
            "Order Refunded" to Event.PURCHASE_REFUND,
            "Product Viewed" to Event.VIEW_ITEM,
            "Product List Viewed" to Event.VIEW_ITEM_LIST,
            "Payment Info Entered" to Event.ADD_PAYMENT_INFO,
            "Promotion Viewed" to Event.PRESENT_OFFER,
            "Product Added to Wishlist" to Event.ADD_TO_WISHLIST,
            "Product Shared" to Event.SHARE,
            "Product Clicked" to Event.SELECT_CONTENT,
            "Products Searched" to Event.SEARCH
        )

        private val PROPERTY_MAPPER: Map<String, String> = mapOf(
            "category" to Param.ITEM_CATEGORY,
            "product_id" to Param.ITEM_ID,
            "name" to Param.ITEM_NAME,
            "price" to Param.PRICE,
            "quantity" to Param.QUANTITY,
            "query" to Param.SEARCH_TERM,
            "shipping" to Param.SHIPPING,
            "tax" to Param.TAX,
            "total" to Param.VALUE,
            "revenue" to Param.VALUE,
            "order_id" to Param.TRANSACTION_ID,
            "currency" to Param.CURRENCY,
            "products" to Param.ITEM_LIST
        )

        private val PRODUCT_MAPPER: Map<String, String> = mapOf(
            "category" to Param.ITEM_CATEGORY,
            "product_id" to Param.ITEM_ID,
            "id" to Param.ITEM_ID,
            "name" to Param.ITEM_NAME,
            "price" to Param.PRICE,
            "quantity" to Param.QUANTITY
        )
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)

        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    override fun identify(payload: IdentifyEvent): BaseEvent? {
        var returnPayload = super.identify(payload)

        firebaseAnalytics.setUserId(payload.userId)

        payload.traits.let {
            for ((traitKey, traitValue) in it) {
                val updatedTrait = makeKey(traitValue.toString())
                firebaseAnalytics.setUserProperty(traitKey, updatedTrait)
            }
        }

        return returnPayload
    }

    override fun track(payload: TrackEvent): BaseEvent? {
        var returnPayload = super.track(payload)

        // Clean the eventName up
        var eventName = payload.event
        if (EVENT_MAPPER.containsKey(eventName)) {
            eventName = EVENT_MAPPER[eventName].toString()
        } else {
            eventName = makeKey(eventName)
        }

        val bundledProperties = formatProperties(payload.properties)

        firebaseAnalytics.logEvent(eventName, bundledProperties)
        analytics.log("firebaseAnalytics.logEvent($eventName, $bundledProperties)")

        return returnPayload
    }

    override fun screen(payload: ScreenEvent): BaseEvent? {
        var returnPayload = super.screen(payload)

        val tempActivity = activity
        if (tempActivity != null) {
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, payload.name)
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        }

        return returnPayload
    }


    // AndroidActivity Methods
    override fun onActivityResumed(activity: Activity?) {
        super.onActivityResumed(activity)

        try {
            val packageManager = activity?.packageManager ?: return

            packageManager.getActivityInfo(activity.componentName, PackageManager.GET_META_DATA)
                .let {
                    it.loadLabel(packageManager).toString().let { activityName ->
                        val bundle = Bundle()
                        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, activityName)
                        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
                        analytics.log(
                            "firebaseAnalytics.setCurrentScreen(activity, $activityName, null"
                        )
                    }
                }
        } catch (exception: PackageManager.NameNotFoundException) {
            analytics.log("Activity Not Found: " + exception.toString())
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

    // Format properties into a format needed by firebase
    private fun formatProperties(properties: Properties): Bundle? {

        var bundle: Bundle? = Bundle()

        val revenue = properties.getDouble("revenue") ?: 0.0
        val total = properties.getDouble("total") ?: 0.0
        val currency = properties.getString("currency") ?: ""
        if ((revenue != 0.0 || total != 0.0) && currency.isNotEmpty()) {
            bundle?.putString(Param.CURRENCY, "USD")
        }

        for ((property, value) in properties.entries) {
            var finalProperty = makeKey(property)
            if (PROPERTY_MAPPER.containsKey(property)) {
                finalProperty = PROPERTY_MAPPER.get(property).toString()
            }

            if (finalProperty == Param.ITEM_LIST) {
                val products = properties.getMapSet("products") ?: continue
                val formattedProducts = formatProducts(products)
                bundle?.putParcelableArrayList(finalProperty, formattedProducts)
            } else if (bundle != null) {
                putValue(bundle, finalProperty, value)
            }
        }

        // Don't return a valid bundle if there wasn't anything added
        if (bundle?.isEmpty == true) {
            bundle = null
        }

        return bundle
    }

    private fun formatProducts(products: Set<Map<String, Any>>): ArrayList<Bundle>? {

        val mappedProducts: ArrayList<Bundle> = ArrayList()

        for (product in products) {
            val mappedProduct = Bundle()
            for (key in product.keys) {
                var value = product[key] as JsonPrimitive
                val finalKey = if (PRODUCT_MAPPER.containsKey(key)) {
                    PRODUCT_MAPPER[key] ?: makeKey(key)
                } else {
                    makeKey(key)
                }
                putValue(mappedProduct, finalKey, value.content)
            }
            mappedProducts.add(mappedProduct)
        }

        return mappedProducts
    }

    // Make sure keys do not contain ".", "-", " ", ":" and are replaced with _
    private fun makeKey(key: String): String {
        val charsToFilter = """[\. \-:]""".toRegex()
        return key.replace(charsToFilter, "_")
    }

    // Adds the appropriate value & type to a supplied bundle
    private fun putValue(bundle: Bundle, key: String, value: Any) {

        when (value) {
            is Int -> {
                bundle.putInt(key, value)
            }
            is Double -> {
                bundle.putDouble(key, value)
            }
            is Long -> {
                bundle.putLong(key, value)
            }
            else -> {
                val stringValue = value.toString()
                bundle.putString(key, stringValue)
            }
        }
    }
}
