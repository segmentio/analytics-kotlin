package com.segment.analytics.next

import android.os.Bundle
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A UI fragment allowing users to send events through the analytics timeline
 * It leverages the Plugin concept to display in-flight events
 */
class EventFragment(val type: EventType, val analytics: Analytics) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val props = fetch(type)
        // Inflate the layout for this fragment
        val view = inflater.inflate(props.second, container, false)

        val traitRoot = view.findViewById<LinearLayout>(R.id.props)
        // add the first one
        addPropertyLayout(traitRoot)
        view.findViewById<Button>(R.id.add).setOnClickListener {
            addPropertyLayout(traitRoot)
        }

        view.findViewById<Button>(R.id.sendEvent).setOnClickListener {
            val input = view.findViewById<EditText>(R.id.input).text.toString().let {
                if (it.isNotEmpty()) {
                    it
                } else {
                    "Placeholder"
                }
            }


            val properties = getUserProps(traitRoot)
            when (type) {
                EventType.Track -> sendTrack(eventName = input, props = properties)
                EventType.Identify -> sendIdentify(userId = input, traits = properties)
                EventType.Screen -> sendScreen(screenName = input, props = properties)
                EventType.Group -> sendGroup(groupId = input, traits = properties)
            }
        }

        val codeView = view.findViewById<TextView>(R.id.code_view)
        codeView.movementMethod = ScrollingMovementMethod()

        analytics.add(object : Plugin {
            override val type: Plugin.Type = Plugin.Type.After
            override lateinit var analytics: Analytics
            override fun execute(event: BaseEvent): BaseEvent? {
                val eventStr = when (event.type) {
                    EventType.Track -> eventStr(event as TrackEvent)
                    EventType.Screen -> eventStr(event as ScreenEvent)
                    EventType.Alias -> eventStr(event as AliasEvent)
                    EventType.Identify -> eventStr(event as IdentifyEvent)
                    EventType.Group -> eventStr(event as GroupEvent)
                }
                val codeString = colorFormat(eventStr)
                activity?.runOnUiThread {
                    codeView.text = Html.fromHtml(codeString)
                }
                return super.execute(event)
            }
        })

        return view
    }

    private fun getUserProps(root: LinearLayout): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until root.childCount) {
            val ll = root.getChildAt(i)
            val key = ll.findViewWithTag<EditText>("key").text.toString()
            val value = ll.findViewWithTag<EditText>("value").text.toString()
            if (key.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    private fun addPropertyLayout(container: LinearLayout) {
        val inflater = LayoutInflater.from(context);
        //to get the MainLayout
        val layoutXml: Int = when (type) {
            EventType.Track -> R.layout.property
            EventType.Identify -> R.layout.trait
            EventType.Screen -> R.layout.property
            EventType.Group -> R.layout.trait
            else -> 0
        }
        val view = inflater.inflate(layoutXml, container, false)
        container.addView(view)
    }

    private inline fun <reified T : BaseEvent> eventStr(event: T) = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(event)

    private fun colorFormat(text: String): String {
        val spacer = fun(match: MatchResult): CharSequence {
            return "<br>" + "&nbsp;".repeat(match.value.length - 1)
        }

        val newString = text
            .replace("\".*\":".toRegex(), "<font color=#52BD94>$0</font>")
            .replace("\\n\\s*".toRegex(), spacer)
        return newString
    }

    private fun sendGroup(groupId: String, traits: Map<String, String>) {
        analytics.group(groupId, traits)
    }

    private fun sendScreen(screenName: String, props: Map<String, String>) {
        analytics.screen(screenName, props)
    }

    private fun sendIdentify(userId: String, traits: Map<String, String>) {
        analytics.identify(userId, traits)
    }

    private fun sendTrack(eventName: String, props: Map<String, String>) {
        analytics.track(eventName, props)
    }

    private fun fetch(type: EventType): Pair<String, Int> {
        return when (type) {
            EventType.Track -> "props" to R.layout.fragment_track
            EventType.Identify -> "traits" to R.layout.fragment_identify
            EventType.Screen -> "props" to R.layout.fragment_screen
            EventType.Group -> "traits" to R.layout.fragment_group
            else -> "" to 0
        }
    }
}

