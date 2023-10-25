package com.segment.analytics.next.plugins

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import androidx.core.app.ActivityCompat

/**
 * The PassiveLocationPlugin will add location information to `event.context.location` if the host
 * app is granted Fine or Coarse location.
 *
 * This plugin will not cause the app the request permission, the host app must implement that logic.
 */
class PassiveLocationPlugin(val context: Context) : Plugin {
    override lateinit var analytics: Analytics
    override val type: Plugin.Type = Plugin.Type.Enrichment


    override fun execute(event: BaseEvent): BaseEvent? {

        // Update the context property
        event.context = buildJsonObject {

            // Add all existing context properties
            event.context.forEach { (key, value) ->
                put(key, value)
            }

            // If we have Location Permission (Fine or Coarse)
            if (haveAnyLocationPermission()
            ) {


                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                @SuppressLint("MissingPermission")
                // Passive Provider is API level 8
                val passiveLastKnownLocation = locationManager.getLastKnownLocation(
                    LocationManager.PASSIVE_PROVIDER
                )

                // Build top-level event.context.location object.
                put("location", buildJsonObject {
                    put("lat", JsonPrimitive(passiveLastKnownLocation?.latitude))
                    put("lon", JsonPrimitive(passiveLastKnownLocation?.longitude))
                    put("alt", JsonPrimitive(passiveLastKnownLocation?.altitude))
                    put("acc", JsonPrimitive(passiveLastKnownLocation?.accuracy))
                    put("bearing", JsonPrimitive(passiveLastKnownLocation?.bearing))
                    put("provider", JsonPrimitive(passiveLastKnownLocation?.provider))
                    put("speed", JsonPrimitive(passiveLastKnownLocation?.speed))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        put("acc", JsonPrimitive(passiveLastKnownLocation?.verticalAccuracyMeters))
                        put(
                            "BearingAcc",
                            JsonPrimitive(passiveLastKnownLocation?.bearingAccuracyDegrees)
                        )
                    }


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        put(
                            "elapsedRealtimeAgeMillis",
                            JsonPrimitive(passiveLastKnownLocation?.elapsedRealtimeAgeMillis)
                        )
                        put(
                            "elapsedRealtimeMillis",
                            JsonPrimitive(passiveLastKnownLocation?.elapsedRealtimeMillis)
                        )
                        put("isMock", JsonPrimitive(passiveLastKnownLocation?.isMock))
                    }
                })
            } else {
                // If we don't have permissions then just set event.context.location = "n/a"
                put("location", JsonPrimitive("n/a"))
            }
        }

        return event
    }

    /**
     * Returns true if we have either Fine or Coarse Location Permission.
     */
    private fun haveAnyLocationPermission() = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}