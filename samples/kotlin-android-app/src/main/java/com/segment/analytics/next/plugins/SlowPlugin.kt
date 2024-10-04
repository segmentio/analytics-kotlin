package com.segment.analytics.next.plugins

import android.util.Log
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin

class SlowPlugin(val count: Int) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    companion object {
        private const val TAG = "SlowPlugin"
    }

    override fun execute(event: BaseEvent): BaseEvent? {

        // Simulate a blocking call: IO, or cpu intensive
        Log.d(TAG, "I'm a SlowPlugin running on ${Thread.currentThread().name}")
        Log.d(TAG, "Let's count to ${count}")
        for (i in 1..count) {
            Log.d(TAG, "${Thread.currentThread().name}# ${i}")
            Thread.sleep(2500)
        }
        Log.d(TAG, "Done Counting!")
        return event
    }
}