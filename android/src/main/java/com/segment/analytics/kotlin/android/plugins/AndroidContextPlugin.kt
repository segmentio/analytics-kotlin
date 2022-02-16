package com.segment.analytics.kotlin.android.plugins

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Build
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.putAll
import com.segment.analytics.kotlin.core.utilities.putUndefinedIfNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.lang.System as JavaSystem
import android.media.MediaDrm
import com.segment.analytics.kotlin.core.utilities.*
import kotlinx.coroutines.*
import java.lang.Exception
import java.security.MessageDigest


// Plugin that applies context related changes. Auto-added to system on build
class AndroidContextPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics
    private lateinit var context: Context
    private lateinit var storage: Storage

    // Context fields that are collected
    private lateinit var app: JsonObject
    private lateinit var os: JsonObject
    private lateinit var device: JsonObject
    private lateinit var screen: JsonObject

    companion object {
        const val LOCALE_KEY = "locale"
        const val USER_AGENT_KEY = "userAgent"
        const val TIMEZONE_KEY = "timezone"

        // App
        const val APP_KEY = "app"
        const val APP_NAME_KEY = "name"
        const val APP_VERSION_KEY = "version"
        const val APP_NAMESPACE_KEY = "namespace"
        const val APP_BUILD_KEY = "build"

        // Device
        const val DEVICE_KEY = "device"
        const val DEVICE_ID_KEY = "id"
        const val DEVICE_MANUFACTURER_KEY = "manufacturer"
        const val DEVICE_MODEL_KEY = "model"
        const val DEVICE_NAME_KEY = "name"
        const val DEVICE_TYPE_KEY = "type"

        // Network
        const val NETWORK_KEY = "network"
        const val NETWORK_BLUETOOTH_KEY = "bluetooth"
        const val NETWORK_CELLULAR_KEY = "cellular"
        const val NETWORK_WIFI_KEY = "wifi"

        // OS
        const val OS_KEY = "os"
        const val OS_NAME_KEY = "name"
        const val OS_VERSION_KEY = "version"

        // Screen
        const val SCREEN_KEY = "screen"
        const val SCREEN_DENSITY_KEY = "density"
        const val SCREEN_HEIGHT_KEY = "height"
        const val SCREEN_WIDTH_KEY = "width"
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        (analytics.configuration.application as Context).let {
            context = it
        }
        storage = analytics.storage
        val collectDeviceId = analytics.configuration.collectDeviceId

        os = buildJsonObject {
            put(OS_NAME_KEY, "Android")
            put(OS_VERSION_KEY, Build.VERSION.RELEASE)
        }

        screen = buildJsonObject {
            val displayMetrics = context.resources.displayMetrics

            put(SCREEN_DENSITY_KEY, displayMetrics.density)
            put(SCREEN_HEIGHT_KEY, displayMetrics.heightPixels)
            put(SCREEN_WIDTH_KEY, displayMetrics.widthPixels)
        }

        app = try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            buildJsonObject {
                putUndefinedIfNull(
                    APP_NAME_KEY, packageInfo.applicationInfo.loadLabel(
                        packageManager
                    )
                )
                putUndefinedIfNull(APP_VERSION_KEY, packageInfo.versionName)
                putUndefinedIfNull(APP_NAMESPACE_KEY, packageInfo.packageName)
                val appBuild = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toString()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toString()
                }
                put(APP_BUILD_KEY, appBuild)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            emptyJsonObject
        }

        // use empty string to indicate device id not yet ready
        val deviceId = storage.read(Storage.Constants.DeviceId) ?: ""
        device = buildJsonObject {
            put(DEVICE_ID_KEY, deviceId)
            put(DEVICE_MANUFACTURER_KEY, Build.MANUFACTURER)
            put(DEVICE_MODEL_KEY, Build.MODEL)
            put(DEVICE_NAME_KEY, Build.DEVICE)
            put(DEVICE_TYPE_KEY, "android")
        }

        if (deviceId.isEmpty()) {
            loadDeviceId(collectDeviceId)
        }
    }

    private fun loadDeviceId(collectDeviceId: Boolean) {
        // run `getDeviceId` in coroutine, since the DRM API takes a long time
        // to generate device id on certain devices and causes ANR issue.
        analytics.analyticsScope.launch(analytics.analyticsDispatcher) {

            // generate random identifier that does not persist across installations
            // use it as the fallback in case DRM API failed to generate one.
            val fallbackDeviceId = UUID.randomUUID().toString()
            var deviceId = fallbackDeviceId

            // have to use a different scope than analyticsScope.
            // otherwise, timeout cancellation won't work (i.e. the scope can't cancel itself)
            val task = CoroutineScope(SupervisorJob()).async {
                getDeviceId(collectDeviceId, fallbackDeviceId)
            }

            // restrict getDeviceId to 2s to avoid ANR
            withTimeoutOrNull(2_000) {
                deviceId = task.await()
            }

            if (deviceId != fallbackDeviceId) {
                device = updateJsonObject(device) {
                    it[DEVICE_ID_KEY] = deviceId
                }
            }

            storage.write(Storage.Constants.DeviceId, deviceId)
        }
    }

    internal fun getDeviceId(collectDeviceId: Boolean, fallbackDeviceId: String): String {
        if (!collectDeviceId) {
            return storage.read(Storage.Constants.AnonymousId) ?: ""
        }

        // unique id generated from DRM API
        val uniqueId = getUniqueID()
        if (!uniqueId.isNullOrEmpty()) {
            return uniqueId
        }
        // If this still fails, falls back to the random uuid
        return fallbackDeviceId
    }

    @SuppressLint("MissingPermission")
    private fun applyContextData(event: BaseEvent) {
        val newContext = buildJsonObject {
            // copy existing context
            putAll(event.context)

            // putApp
            put(APP_KEY, app)

            // putDevice
            put(DEVICE_KEY, device)

            // putOs
            put(OS_KEY, os)

            // putScreen
            put(SCREEN_KEY, screen)

            // --> Add dynamic context data

            // putNetwork
            val network = buildJsonObject {
                if (hasPermission(context, permission.ACCESS_NETWORK_STATE)) {
                    val connectivityManager =
                        getSystemService<ConnectivityManager>(context, Context.CONNECTIVITY_SERVICE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        var wifiConnected = false
                        var cellularConnected = false
                        var bluetoothConnected = false
                        connectivityManager.allNetworks.forEach {
                            val capabilities = connectivityManager.getNetworkCapabilities(it)
                            // we dont know which network is which at this point, so using
                            // the or-map allows us to capture the value across all networks
                            wifiConnected = wifiConnected ||
                                    capabilities?.hasTransport(TRANSPORT_WIFI) ?: false
                            cellularConnected = cellularConnected ||
                                    capabilities?.hasTransport(TRANSPORT_CELLULAR) ?: false
                            bluetoothConnected = bluetoothConnected ||
                                    capabilities?.hasTransport(TRANSPORT_BLUETOOTH) ?: false
                        }
                        put(NETWORK_WIFI_KEY, wifiConnected)
                        put(NETWORK_BLUETOOTH_KEY, bluetoothConnected)
                        put(NETWORK_CELLULAR_KEY, cellularConnected)
                    } else @Suppress("DEPRECATION") {
                        val wifiInfo =
                            connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                        put(NETWORK_WIFI_KEY, wifiInfo?.isConnected ?: false)

                        val bluetoothInfo =
                            connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_BLUETOOTH)
                        put(NETWORK_BLUETOOTH_KEY, bluetoothInfo?.isConnected ?: false)

                        val cellularInfo =
                            connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
                        put(NETWORK_CELLULAR_KEY, cellularInfo?.isConnected ?: false)
                    }
                }
            }
            put(NETWORK_KEY, network)

            // putLocale
            put(
                LOCALE_KEY,
                Locale.getDefault().language + "-" + Locale.getDefault().country
            )

            // user-agent
            putUndefinedIfNull(USER_AGENT_KEY, JavaSystem.getProperty("http.agent"))

            // timezone
            putUndefinedIfNull(TIMEZONE_KEY, TimeZone.getDefault().id)
        }
        event.context = newContext
    }

    override fun execute(event: BaseEvent): BaseEvent {
        applyContextData(event)
        return event
    }

}

/** Returns the system service for the given string. */
inline fun <reified T> getSystemService(context: Context, serviceConstant: String): T {
    return context.getSystemService(serviceConstant) as T
}

/** Returns true if the application has the given permission.  */
fun hasPermission(context: Context, permission: String): Boolean {
    return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

/** Returns true if the application has the given feature.  */
fun hasFeature(context: Context, feature: String): Boolean {
    return context.packageManager.hasSystemFeature(feature)
}


/**
 * Workaround for not able to get device id on Android 10 or above using DRM API
 * {@see https://stackoverflow.com/questions/58103580/android-10-imei-no-longer-available-on-api-29-looking-for-alternatives}
 * {@see https://developer.android.com/training/articles/user-data-ids}
 */
fun getUniqueID(): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
        return null

    val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
    var wvDrm: MediaDrm? = null
    try {
        wvDrm = MediaDrm(WIDEVINE_UUID)
        val wideVineId = wvDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(wideVineId)
        return  md.digest().toHexString()
    } catch (e: Exception) {
        return null
    } finally {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            wvDrm?.close()
        } else {
            wvDrm?.release()
        }
    }
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }