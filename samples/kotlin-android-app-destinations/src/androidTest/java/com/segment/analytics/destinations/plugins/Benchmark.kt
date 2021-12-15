package com.segment.analytics.destinations.plugins

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.TrackEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.system.measureTimeMillis
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.utils.V8ObjectUtils
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.util.Date


@RunWith(AndroidJUnit4::class)
class Benchmark {
    val e1 = TrackEvent(
        event = "CP VOD - Start video",
        properties = buildJsonObject { put("new", true); put("click", true) }
    ).apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = Date(0).toInstant().toString()
    }
    val e2 = TrackEvent(
        event = "Screen Opened",
        properties = buildJsonObject { put("new", false); put("click", false) }
    ).apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = Date(0).toInstant().toString()
    }
    val e3 = TrackEvent(
        event = "App Closed",
        properties = buildJsonObject { put("new", false); put("click", true) }
    ).apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = Date(0).toInstant().toString()
    }

    private val appContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun usingExpressions() {
        val plugin = DestinationFiltersDropPlugin().apply {
            matchers.add(MatcherExpressionImpl())
        }
        val matcherTime = measureTimeMillis {
            assertNull(plugin.execute(e1))
            assertNull(plugin.execute(e2))
            assertNotNull(plugin.execute(e3))
        }
        println(matcherTime)
    }

    @Test
    fun usingFQlQuery() {
//        val plugin = FQLDropPlugin("[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]]")
        val plugin =
            FQLDropPlugin("""["!",["or",["=","event",{"value":"App Opened"}],["=","event",{"value":"App Closed"}]]]""")
        val matcherTime = measureTimeMillis {
            assertNull(plugin.execute(e1))
            assertNull(plugin.execute(e2))
            assertNotNull(plugin.execute(e3))
        }
        println(matcherTime)
    }

    @Test
    fun usingJsRuntime() {
        val localJSMiddlewareInputStream = appContext.assets.open("sample.js")
        val script = localJSMiddlewareInputStream.bufferedReader().use(BufferedReader::readText)
        val plugin = DestinationFiltersDropPlugin().apply {
            matchers.add(MatcherJSImpl(script))
        }
        plugin.execute(e1)
        plugin.execute(e2)
        plugin.execute(e3)
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
                Equals(
                    FQLValue.Path(payload, "event"),
                    FQLValue.Value(JsonPrimitive("App Opened"))
                ),
                Equals(
                    FQLValue.Path(payload, "event"),
                    FQLValue.Value(JsonPrimitive("App Closed"))
                )
            )
        ).evaluate(payload)
    }
}

class MatcherJSImpl(private val script: String) : Matcher {

    internal class Console {
        fun log(message: String) {
            println("[INFO] $message")
        }

        fun error(message: String) {
            println("[ERROR] $message")
        }
    }

    private val runtime: V8 = V8.createV8Runtime().also {
        val console = Console()
        val v8Console = V8Object(it)
        // todo Allow string array
        v8Console.registerJavaMethod(console, "log", "log", arrayOf<Class<*>>(String::class.java))
        v8Console.registerJavaMethod(console, "error", "err", arrayOf<Class<*>>(String::class.java))
        it.add("console", v8Console)
    }

    fun getObject(key: String): V8Object {
        var result = runtime.getObject(key)
        if (result.isUndefined) {
            result = runtime.executeObjectScript(key) // Blows up when the key does not exist
        }
        return result
    }

    override fun match(payload: BaseEvent): Boolean {
        payload as TrackEvent
        runtime.executeScript(script)

        val fn = getObject("edge_function.fnMatch") as V8Function
        val params = V8Array(runtime.runtime)
        val payloadJson = (Json.encodeToJsonElement(payload) as JsonObject).toContent()
        params.push(V8ObjectUtils.toV8Object(runtime.runtime, payloadJson))
        // call it and pick up the result
        val fnResult = fn.call(null, params) as String
        println(fnResult)
//        runtime.close()
        return true
    }
}