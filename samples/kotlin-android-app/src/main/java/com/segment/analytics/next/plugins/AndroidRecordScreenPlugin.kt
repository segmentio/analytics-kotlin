package com.segment.analytics.next.plugins

import android.app.Activity
import android.content.pm.PackageManager
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.segment.analytics.kotlin.core.platform.plugins.logger.*

class AndroidRecordScreenPlugin : Plugin, AndroidLifecycle {

    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var analytics: Analytics

    override fun onActivityStarted(activity: Activity?) {
        val packageManager = activity?.packageManager
        try {
            val info = packageManager?.getActivityInfo(
                activity.componentName,
                PackageManager.GET_META_DATA
            )
            val activityLabel = info?.loadLabel(packageManager)
            analytics.screen(activityLabel.toString())
        } catch (e: PackageManager.NameNotFoundException) {
            throw AssertionError("Activity Not Found: $e")
        } catch (e: Exception) {
            Analytics.segmentLog(
                "Unable to track screen view for ${activity.toString()}",
                kind = LogKind.ERROR
            )
        }
    }

}