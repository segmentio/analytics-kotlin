package com.segment.analytics.next

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import com.segment.analytics.kotlin.core.platform.policies.FlushPolicy
import java.lang.ref.WeakReference

/**
 * Flush policy that checks where the Android device is connected to a metered (pay) network or an
 * unmetered network.
 *
 * If the network is NOT metered this Flush policy returns true; false otherwise.
 */
class UnmeteredFlushPolicy(context: Context) : FlushPolicy {

    lateinit var weakContext: WeakReference<Context>

    init {
        weakContext = WeakReference(context)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun shouldFlush(): Boolean {
        val context = weakContext.get()
        var shouldFlush = false
        context?.let {
            val cm = getSystemService(context, ConnectivityManager::class.java) as ConnectivityManager

            val nc = cm.getNetworkCapabilities(cm.activeNetwork)
            nc?.let {
                nc.capabilities.forEach {
                    if (it == NetworkCapabilities.NET_CAPABILITY_NOT_METERED || it == NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED) {
                        println("UMFP: YAY network is NOT metered!!")
                        shouldFlush = true
                    }
                }

                if (!shouldFlush) {
                    println("UMFP: Sorry, this network was IS metered!")
                }
            }
        }

        return shouldFlush
    }
}