package com.segment.analytics.destinations

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
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
            analytics.log("Got to an interesting place")
            analytics.flush()
        }
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
}