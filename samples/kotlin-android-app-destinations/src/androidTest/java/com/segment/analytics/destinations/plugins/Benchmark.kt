package com.segment.analytics.destinations.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.TrackEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.system.measureTimeMillis
import com.eclipsesource.v8.V8
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class Benchmark {
    val e1 = TrackEvent(
        event = "App Opened",
        properties = buildJsonObject { put("new", true); put("click", true) })
    val e2 = TrackEvent(
        event = "Screen Opened",
        properties = buildJsonObject { put("new", false); put("click", false) })
    val e3 = TrackEvent(
        event = "App Closed",
        properties = buildJsonObject { put("new", false); put("click", true) })

    @Test
    fun usingExpressions() {
        val plugin = DestinationFiltersDropPlugin().apply {
            matchers.add(MatcherExpressionImpl())
        }
        val matcherTime = measureTimeMillis {
            assertNotNull(plugin.execute(e1))
            assertNull(plugin.execute(e2))
            assertNotNull(plugin.execute(e3))
        }
        println(matcherTime)
    }

    @Test
    fun usingJsRuntime() {
        val plugin = DestinationFiltersDropPlugin().apply {
            matchers.add(MatcherJSImpl())
        }
        val matcherTime = measureTimeMillis {
            assertNotNull(plugin.execute(e1))
            assertNull(plugin.execute(e2))
            assertNotNull(plugin.execute(e3))
        }
        println(matcherTime)
    }
}

/*
    {
      "ir": "["!",["or",["=","event",{"value":"App Opened"}],["=","event",{"value":"App Closed"}]]]",
      "type": "fql",
      "config": {
        "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
      }
    }
     */
class MatcherExpressionImpl : Matcher {
    override fun match(payload: BaseEvent): Boolean {
        payload as TrackEvent
        return Not(
            Or(
                Equals(Path("event"), "App Opened"),
                Equals(Path("event"), "App Closed")
            )
        ).evaluate(payload)
    }
}

class MatcherJSImpl : Matcher {

    val runtime = V8.createV8Runtime()
    override fun match(payload: BaseEvent): Boolean {
        payload as TrackEvent
        val result = runtime.executeBooleanScript(
            """
              !("${payload.event}" === "App Opened" || "${payload.event}" === "App Closed")
              """.trimIndent()
        )
//        runtime.close()
        return result
    }
}