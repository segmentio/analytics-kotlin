package com.segment.analytics.next

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.segment.analytics.kotlin.core.EventType
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random


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
            launchTest(1)
            launchTest(2)
            launchFlush()
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

    private fun launchTest(thread: Int) = GlobalScope.launch {
        var count = 100000
        while (count-- > 0) {
            analytics.track("Stress Test from thread $thread")
        }
    }

    private fun launchFlush() = GlobalScope.launch {
        var count = 100
        while (count-- > 0) {
            val sleepTime = Random.nextLong(1, 2000)
            delay(sleepTime)
            analytics.flush()
            print("slept $sleepTime, and flushed")
        }
    }
}