package com.segment.analytics.next

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.segment.analytics.next.plugins.ConsentTracking

/**
 * Same as MainActivity but presents the user with a dialog to ask for tracking consent.
 * If not given consent,future events through analytics will be dropped.
 */
class ConsentActivity : AppCompatActivity() {

    val analytics = MainApplication.analytics
    val trackFragment = EventFragment("Track", analytics)
    val identifyFragment = EventFragment("Identify", analytics)
    val screenFragment = EventFragment("Screen", analytics)
    val groupFragment = EventFragment("Group", analytics)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        // Add consent tracking plugin
        analytics.add(ConsentTracking)

        // Show consent tracking dialog
        ConsentTracking.getConsentDialog(this).show()

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