package com.segment.analytics.next.plugins

import android.app.Activity
import android.content.pm.PackageManager
import com.segment.analytics.Analytics
import com.segment.analytics.platform.Plugin
import com.segment.analytics.platform.plugins.LogType
import com.segment.analytics.platform.plugins.android.AndroidLifecycle
import com.segment.analytics.platform.plugins.log

class AndroidRecordScreenPlugin : Plugin, AndroidLifecycle {

    override val type: Plugin.Type = Plugin.Type.Utility
    override val name: String = "AndroidRecordScreen"
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
            analytics.log(
                "Unable to track screen view for ${activity.toString()}",
                type = LogType.ERROR
            )
        }
    }

}