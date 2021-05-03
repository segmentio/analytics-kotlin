package com.segment.analytics.next

import android.os.Bundle
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.segment.analytics.*
import com.segment.analytics.platform.Plugin
import com.segment.analytics.platform.plugins.log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A UI fragment allowing users to send events through the analytics timeline
 * It leverages the Plugin concept to display in-flight events
 */
class EventFragment(val type: String, val analytics: Analytics) : Fragment() {
    val properties = mutableMapOf<String, String>()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val props = fetch(type)
        // Inflate the layout for this fragment
        val view = inflater.inflate(props.second, container, false)
        view.findViewById<Button>(R.id.sendEvent).setOnClickListener {
            val input = view.findViewById<EditText>(R.id.input).text.toString()
            when (type) {
                "Track" -> sendTrack(eventName = input, props = properties)
                "Identify" -> sendIdentify(userId = input, traits = properties)
                "Screen" -> sendScreen(screenName = input, props = properties)
                "Group" -> sendGroup(groupId = input, traits = properties)
                else -> "" to 0
            }
        }
        val codeView = view.findViewById<TextView>(R.id.code_view)
        codeView.movementMethod = ScrollingMovementMethod()
        analytics.add(object : Plugin {
            override val type: Plugin.Type = Plugin.Type.After
            override val name: String = "TempResult-$type"
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
        // TODO implement default properties logic for events
        // TODO implement add property button logic
        return view
    }

    private inline fun <reified T : BaseEvent> eventStr(event: T) = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(event)

    private fun colorFormat(text: String): String {
        val spacer = fun (match: MatchResult): CharSequence {
            return "<br>" + "&nbsp;".repeat(match.value.length - 1)
        }

        val newString = text
            .replace("\".*\":".toRegex(), "<font color=#52BD94>$0</font>")
            .replace("\\n\\s*".toRegex(), spacer)
        return newString
    }

    private fun sendGroup(groupId: String, traits: MutableMap<String, String>) {
        analytics.group(groupId, traits)
    }

    private fun sendScreen(screenName: String, props: MutableMap<String, String>) {
        analytics.screen(screenName, props)
    }

    private fun sendIdentify(userId: String, traits: MutableMap<String, String>) {
        analytics.identify(userId, traits)
    }

    private fun sendTrack(eventName: String, props: MutableMap<String, String>) {
        analytics.track(eventName, props)
    }

    fun fetch(type: String): Pair<String, Int> {
        return when (type) {
            "Track" -> "props" to R.layout.fragment_track
            "Identify" -> "traits" to R.layout.fragment_identify
            "Screen" -> "props" to R.layout.fragment_screen
            "Group" -> "traits" to R.layout.fragment_group
            else -> "" to 0
        }
    }
}

