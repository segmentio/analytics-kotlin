package com.segment.analytics.next.plugins

import android.app.AlertDialog
import android.content.Context
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.next.R
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*

object ConsentTracking : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics
    var queuedEvents = mutableListOf<BaseEvent>()

    var consentGiven = false
    var consentAsked = false

    /*
        Usage: ConsentTracking.getConsentDialog(this).create().show()
     */
    fun getConsentDialog(context: Context): AlertDialog.Builder {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
        builder.setTitle("Privacy Notice")
        builder.setMessage("This app tracks you for X, Y and Z.  Do you consent?")
        builder.setPositiveButton("Yes") { dialog, id ->
            analytics.log("consent given")
            consentAsked = true
            consentGiven = true
            replayEvents()
            dialog.dismiss()
        }
        builder.setNegativeButton("No") { dialog, id ->
            analytics.log("consent not given")
            consentAsked = true
            consentGiven = false
            clearQueuedEvents()
            dialog.dismiss()
        }
        return builder
    }

    override suspend fun execute(event: BaseEvent): BaseEvent? {
        // if we've been given consent, let the event pass through.
        if (consentGiven) {
            return event
        }

        // queue the event in case they given consent later
        if (!consentAsked) {
            queuedEvents.add(event)
        }


        // returning null will effectively drop the event.
        return null
    }

    private fun clearQueuedEvents() {
        queuedEvents.clear()
    }

    private fun replayEvents() {
        queuedEvents.forEach {
            analytics.process(it)
        }
    }
}