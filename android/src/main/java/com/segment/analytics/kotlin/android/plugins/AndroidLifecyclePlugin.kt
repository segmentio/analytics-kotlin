package com.segment.analytics.kotlin.android.plugins

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ParseException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.segment.analytics.kotlin.android.utilities.DeepLinkUtils
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// Android specific class that mediates lifecycle plugin callbacks
class AndroidLifecyclePlugin() : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver,
    Plugin {

    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var analytics: Analytics
    private lateinit var packageInfo: PackageInfo
    private lateinit var application: Application

    // config properties
    private var shouldTrackApplicationLifecycleEvents: Boolean = true
    private var trackDeepLinks: Boolean = true
    private var useLifecycleObserver: Boolean = false

    // state properties
    private val trackedApplicationLifecycleEvents = AtomicBoolean(false)
    private val numberOfActivities = AtomicInteger(1)
    private val firstLaunch = AtomicBoolean(false)
    private val isChangingActivityConfigurations = AtomicBoolean(false)
    private lateinit var lifecycle: Lifecycle
    private lateinit var storage: Storage

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        analytics.configuration.let {
            application = it.application as? Application
                ?: error("no android application context registered")

            shouldTrackApplicationLifecycleEvents = it.trackApplicationLifecycleEvents
            trackDeepLinks = it.trackDeepLinks
            useLifecycleObserver = it.useLifecycleObserver
        }
        storage = analytics.storage

        val packageManager: PackageManager = application.packageManager
        packageInfo = try {
            packageManager.getPackageInfo(application.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw AssertionError("Package not found: " + application.packageName)
        }

        // setup lifecycle listeners
        application.registerActivityLifecycleCallbacks(this)
        if (useLifecycleObserver) {
            lifecycle = ProcessLifecycleOwner.get().lifecycle
            // NOTE: addObserver is required to run on UI thread,
            // though we made it compatible to run from background thread,
            // there is a chance that lifecycle events get lost if init
            // analytics from background (i.e. analytics is init, but
            // lifecycle hook is yet to be registered.
            runOnMainThread {
                lifecycle.addObserver(this)
            }
        }
    }

    private fun runOnAnalyticsThread(block: suspend () -> Unit) = with(analytics) {
        analyticsScope.launch(analyticsDispatcher) {
            block()
        }
    }


    /* OLD LIFECYCLE HOOKS */
    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        runOnAnalyticsThread {
            analytics.applyClosureToPlugins { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityCreated(activity, bundle)
                }
            }
        }
        if (!useLifecycleObserver) {
            onCreate(stubOwner)
        }
        if (trackDeepLinks) {
            trackDeepLink(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        runOnAnalyticsThread {
            analytics.applyClosureToPlugins { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityStarted(activity)
                }
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        runOnAnalyticsThread {
            analytics.applyClosureToPlugins { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityResumed(activity)
                }
            }
        }
        if (!useLifecycleObserver) {
            onStart(stubOwner)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        runOnAnalyticsThread {
            analytics.applyClosureToPlugins { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityPaused(activity)
                }
            }
        }
        if (!useLifecycleObserver) {
            onPause(stubOwner)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        runOnAnalyticsThread {
            analytics.applyClosureToPlugins { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityStopped(activity)
                }
            }
        }
        if (!useLifecycleObserver) {
            onStop(stubOwner)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
        runOnAnalyticsThread {
            analytics.applyClosureToPlugins { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivitySaveInstanceState(activity, bundle)
                }
            }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        runOnAnalyticsThread {
            analytics.applyClosureToPlugins { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityDestroyed(activity)
                }
            }
        }
        if (!useLifecycleObserver) {
            onDestroy(stubOwner)
        }
    }

    /* NEW LIFECYCLE HOOKS (These get called alongside the old ones) */
    override fun onStop(owner: LifecycleOwner) {
        // App in background
        if (shouldTrackApplicationLifecycleEvents
            && numberOfActivities.decrementAndGet() == 0 && !isChangingActivityConfigurations.get()
        ) {
            analytics.track("Application Backgrounded")
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // App in foreground
        if (shouldTrackApplicationLifecycleEvents
            && numberOfActivities.incrementAndGet() == 1 && !isChangingActivityConfigurations.get()
        ) {
            val properties = buildJsonObject {
                if (firstLaunch.get()) {
                    put("version", packageInfo.versionName)
                    put("build", packageInfo.getVersionCode().toString())
                }
                put("from_background", !firstLaunch.getAndSet(false))
            }
            analytics.track("Application Opened", properties)
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        // App created
        if (!trackedApplicationLifecycleEvents.getAndSet(true)
            && shouldTrackApplicationLifecycleEvents
        ) {
            numberOfActivities.set(0)
            firstLaunch.set(true)
            trackApplicationLifecycleEvents()
        }
    }

    override fun onResume(owner: LifecycleOwner) {}
    override fun onPause(owner: LifecycleOwner) {}
    override fun onDestroy(owner: LifecycleOwner) {}

    private fun trackDeepLink(activity: Activity?) {
        val intent = activity?.intent

        intent?.let {
            val referrer = getReferrer(activity)?.toString()
            DeepLinkUtils(analytics).trackDeepLinkFrom(referrer, it)
        }
    }

    internal fun trackApplicationLifecycleEvents() {
        // Get the current version.
        val packageInfo = packageInfo
        val currentVersion = packageInfo.versionName
        val currentBuild = packageInfo.getVersionCode().toString()

        // Get the previous recorded version.
        val previousVersion = storage.read(Storage.Constants.AppVersion)
        val previousBuild = storage.read(Storage.Constants.AppBuild)
        val legacyPreviousBuild = storage.read(Storage.Constants.LegacyAppBuild)

        // Check and track Application Installed or Application Updated.
        if (previousBuild == null && legacyPreviousBuild == null) {
            analytics.track(
                "Application Installed",
                buildJsonObject {
                    put(VERSION_KEY, currentVersion)
                    put(BUILD_KEY, currentBuild)
                })
        } else if (currentBuild != previousBuild) {
            analytics.track(
                "Application Updated",
                buildJsonObject {  //
                    put(VERSION_KEY, currentVersion)
                    put(BUILD_KEY, currentBuild)
                    put("previous_$VERSION_KEY", previousVersion)
                    put("previous_$BUILD_KEY", previousBuild.toString())
                })
        }

        // Update the recorded version.
        runOnAnalyticsThread {
            storage.write(Storage.Constants.AppVersion, currentVersion)
            storage.write(Storage.Constants.AppBuild, currentBuild)
        }
    }

    fun unregisterListeners() {
        application.unregisterActivityLifecycleCallbacks(this)
        if (useLifecycleObserver) {
            // only unregister if feature is enabled
            runOnMainThread {
                lifecycle.removeObserver(this)
            }
        }
    }
    private fun runOnMainThread(closure: () -> Unit) {
        analytics.analyticsScope.launch(Dispatchers.Main) {
            closure()
        }
    }

    companion object {
        private const val VERSION_KEY = "version"
        private const val BUILD_KEY = "build"

        // This is just a stub LifecycleOwner which is used when we need to call some lifecycle
        // methods without going through the actual lifecycle callbacks
        private val stubOwner: LifecycleOwner = object : LifecycleOwner {
            var stubLifecycle: Lifecycle = object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                    // NO-OP
                }

                override fun removeObserver(observer: LifecycleObserver) {
                    // NO-OP
                }

                override val currentState: State
                    get() = State.DESTROYED
            }

            override val lifecycle: Lifecycle
                get() = stubLifecycle
        }
    }
}

// Safely fetch version code managing deprecations across OS versions
private fun PackageInfo.getVersionCode(): Number =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        this.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        this.versionCode
    }

// Returns the referrer who started the Activity.
fun getReferrer(activity: Activity): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        activity.referrer
    } else getReferrerCompatible(activity)
}

// Returns the referrer on devices running SDK versions lower than 22.
private fun getReferrerCompatible(activity: Activity): Uri? {
    var referrerUri: Uri? = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        val intent = activity.intent
        referrerUri = intent.getParcelableExtra(Intent.EXTRA_REFERRER)

        if (referrerUri == null) {
            // Intent.EXTRA_REFERRER_NAME
            referrerUri = intent.getStringExtra("android.intent.extra.REFERRER_NAME")?.let {
                // Try parsing the referrer URL; if it's invalid, return null
                try {
                    Uri.parse(it)
                } catch (e: ParseException) {
                    null
                }
            }
        }
    }
    return referrerUri
}

// Basic interface for a plugin to consume lifecycle callbacks
interface AndroidLifecycle {
    fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {}
    fun onActivityStarted(activity: Activity?) {}
    fun onActivityResumed(activity: Activity?) {}
    fun onActivityPaused(activity: Activity?) {}
    fun onActivityStopped(activity: Activity?) {}
    fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {}
    fun onActivityDestroyed(activity: Activity?) {}
}
