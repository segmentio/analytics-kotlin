package com.segment.analytics.next.plugins

import android.Manifest
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

class PassiveLocationPlugin(val context: Context): Plugin {
    override lateinit var analytics: Analytics
    override val type: Plugin.Type = Plugin.Type.Enrichment

    override fun execute(event: BaseEvent): BaseEvent? {

        event.context = buildJsonObject {

            event.context.forEach { (key, value) ->
                put(key, value)
            }

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                // Passive Provider is API level 8
                val passiveLastKnownLocation = locationManager.getLastKnownLocation(
                    LocationManager.PASSIVE_PROVIDER
                )

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
                put("location", JsonPrimitive("n/a"))
            }

        }


        return event
    }
}