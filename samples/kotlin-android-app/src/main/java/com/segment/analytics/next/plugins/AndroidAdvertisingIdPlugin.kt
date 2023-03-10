package com.segment.analytics.next.plugins

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.putAll
import com.segment.analytics.kotlin.core.utilities.safeJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Analytics Plugin to retrieve and add advertisingId to all events
 *
 * For google play services ad tracking, please include `implementation 'com.google.android.gms:play-services-ads:+'` in dependencies list
 */
class AndroidAdvertisingIdPlugin(private val androidContext: Context) : Plugin {

    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    companion object {
        const val DEVICE_ADVERTISING_ID_KEY = "advertisingId"
        const val DEVICE_AD_TRACKING_ENABLED_KEY = "adTrackingEnabled"

        // Check to see if this plugin should be added
        fun isAdvertisingLibraryAvailable(): Boolean {
            return try {
                Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
                true
            } catch (ignored: ClassNotFoundException) {
                false
            }
        }
    }

    private var advertisingId = ""
    private var adTrackingEnabled = false

    private fun getGooglePlayServicesAdvertisingID(context: Context): Result<String, Exception> {
        val advertisingInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
        val isLimitAdTrackingEnabled = advertisingInfo.isLimitAdTrackingEnabled
        if (isLimitAdTrackingEnabled) {
            analytics.log(
                "Not collecting advertising ID because isLimitAdTrackingEnabled (Google Play Services) is true.",
                kind = LogKind.WARNING
            )
            return Result.Err(Exception("LimitAdTrackingEnabled (Google Play Services) is true"))
        }
        val advertisingId = advertisingInfo.id
        return Result.Ok(advertisingId)
    }

    private fun getAmazonFireAdvertisingID(context: Context): Result<String, Exception> {
        val contentResolver = context.contentResolver

        // Ref: http://prateeks.link/2uGs6bf
        // limit_ad_tracking != 0 indicates user wants to limit ad tracking.
        val limitAdTracking = android.provider.Settings.Secure.getInt(contentResolver, "limit_ad_tracking") != 0
        if (limitAdTracking) {
            analytics.log(
                "Not collecting advertising ID because limit_ad_tracking (Amazon Fire OS) is true.",
                kind = LogKind.WARNING
            )
            return Result.Err(Exception("limit_ad_tracking (Amazon Fire OS) is true."))
        }
        val advertisingId = android.provider.Settings.Secure.getString(contentResolver, "advertising_id")
        return Result.Ok(advertisingId)
    }

    private fun updateAdvertisingId() {
        try {
            when (val result = getGooglePlayServicesAdvertisingID(androidContext)) {
                is Result.Ok -> {
                    adTrackingEnabled = true
                    advertisingId = result.value
                }
                is Result.Err -> {
                    adTrackingEnabled = false
                    advertisingId = ""
                    throw result.error
                }
            }
            analytics.log("Collected advertising Id from Google Play Services")
            return
        } catch (e: Exception) {
            Analytics.segmentLog(
                message = "${e.message}: Unable to collect advertising ID from Google Play Services.",
                kind = LogKind.ERROR
            )
        }
        try {
            when (val result = getAmazonFireAdvertisingID(androidContext)) {
                is Result.Ok -> {
                    adTrackingEnabled = true
                    advertisingId = result.value
                }
                is Result.Err -> {
                    adTrackingEnabled = false
                    advertisingId = ""
                    throw result.error
                }
            }
            analytics.log("Collected advertising Id from Amazon Fire OS")
            return
        } catch (e: Exception) {
            Analytics.segmentLog(
                "${e.message}: Unable to collect advertising ID from Amazon Fire OS.",
                kind = LogKind.WARNING
            )
        }
        analytics.log(
            "Unable to collect advertising ID from Amazon Fire OS and Google Play Services.",
            kind = LogKind.WARNING
        )
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        // Have to fetch advertisingId on non-main thread
        GlobalScope.launch(Dispatchers.IO) {
            updateAdvertisingId()
        }
    }

    internal fun attachAdvertisingId(payload: BaseEvent): BaseEvent {
        val newContext = buildJsonObject {
            // copy existing context
            putAll(payload.context)

            val newDevice = buildJsonObject {
                payload.context["device"]?.safeJsonObject?.let {
                    putAll(it)
                }
                if (adTrackingEnabled && advertisingId.isNotBlank()) {
                    put(DEVICE_ADVERTISING_ID_KEY, advertisingId)
                }
                put(DEVICE_AD_TRACKING_ENABLED_KEY, adTrackingEnabled)
            }

            // putDevice
            put("device", newDevice)
        }
        payload.context = newContext
        return payload
    }

    override fun execute(event: BaseEvent): BaseEvent {
        return attachAdvertisingId(event)
    }

    private sealed class Result<out T, out E> {
        class Ok<out T>(val value: T) : Result<T, Nothing>()
        class Err<out E>(val error: E) : Result<Nothing, E>()
    }
}
