package com.segment.analytics.next

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.segment.analytics.kotlin.android.addDeepLinkOpen
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.EventType
import com.segment.analytics.kotlin.core.platform.plugins.logger.log


class MainActivity : AppCompatActivity() {

    val analytics = MainApplication.analytics
    val trackFragment = EventFragment(EventType.Track, analytics)
    val identifyFragment = EventFragment(EventType.Identify, analytics)
    val screenFragment = EventFragment(EventType.Screen, analytics)
    val groupFragment = EventFragment(EventType.Group, analytics)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        findViewById<Button>(R.id.trackBtn).setOnClickListener {
            loadFragment(trackFragment)
        }
        findViewById<Button>(R.id.identifyBtn).setOnClickListener {
            loadFragment(identifyFragment)
        }
        findViewById<Button>(R.id.screenBtn).setOnClickListener {
            loadFragment(screenFragment)
        }
        findViewById<Button>(R.id.groupBtn).setOnClickListener {
            loadFragment(groupFragment)
        }
        findViewById<Button>(R.id.flushBtn).setOnClickListener {
            analytics.log("Kinda a hopewell")
            analytics.flush()
        }

        Analytics.debugLogsEnabled = true
    }

    private fun loadFragment(fragment: Fragment) {
        // create a FragmentManager
        val fm: FragmentManager = this.supportFragmentManager
        // create a FragmentTransaction to begin the transaction and replace the Fragment
        val fragmentTransaction: FragmentTransaction = fm.beginTransaction()
        // replace the FrameLayout with new Fragment
        fragmentTransaction.replace(R.id.frameLayout, fragment)
        fragmentTransaction.commit() // save the changes
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        // Add a manual deep-link opened event.
        // This is necessary when your Activity has a android:launchMode of
        // 'singleInstance', 'singleInstancePerTask', 'singleTop', or any other mode
        // that will re-use an existing Activity instead of creating a new instance.
        // The Analytics SDK automatically identifies when you app is started from a deep link
        // if the Activity is created, but not if it is re-used. Therefore we have to add this
        // code to manually capture the Deep Link info.

        Analytics.addDeepLinkOpen(analytics, "deep-link", intent)
    }
}