package com.segment.substrata.kotlin

import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.utils.V8ObjectUtils
import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.toContent
import io.alicorn.v8.V8JavaAdapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class Console {
    fun log(message: String) {
        println("[JS-Console:INFO] $message")
    }

    fun error(message: String) {
        println("[JS-Console:ERROR] $message")
    }
}


class EdgeFunction() : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    private val jsRuntimeExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    lateinit var runtime: V8

    init {
        jsRuntimeExecutor.submit {
            runtime = V8.createV8Runtime().also {
                val console = Console()
                val v8Console = V8Object(it)
                v8Console.registerJavaMethod(console,
                    "log",
                    "log",
                    arrayOf<Class<*>>(String::class.java))
                v8Console.registerJavaMethod(console,
                    "error",
                    "err",
                    arrayOf<Class<*>>(String::class.java))
                it.add("console", v8Console)
            }
            V8JavaAdapter.injectClass(Analytics::class.java, runtime)
        }
    }

    val script = """
        function track(payload) {
            console.log(JSON.stringify(payload));
            if (payload.event === "repeat") {
                console.log("Calling analytics track");
                analytics.track("Track from JS");
            }
            console.log("DONE");
        }
    """.trimIndent()

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        jsRuntimeExecutor.submit {
            V8JavaAdapter.injectObject("analytics", analytics, runtime)
            runtime.executeScript(script)
        }
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        jsRuntimeExecutor.submit {
            println("PRAY running js on ${Thread.currentThread().name}")
            val param = serializeToJS(runtime, event)
            val trackFn = runtime.getObject("track") as V8Function
            trackFn.call(null, V8Array(runtime).apply { push(param) })
        }.get()
        return super.execute(event)
    }
}

fun serializeToJS(v8: V8, event: BaseEvent): V8Object {
    val serialized = when (event) {
        is TrackEvent -> {
            (EncodeDefaultsJson.encodeToJsonElement(TrackEvent.serializer(),
                event) as JsonObject).toContent()
        }
        is IdentifyEvent -> {
            (EncodeDefaultsJson.encodeToJsonElement(IdentifyEvent.serializer(),
                event) as JsonObject).toContent()
        }
        is GroupEvent -> {
            (EncodeDefaultsJson.encodeToJsonElement(GroupEvent.serializer(),
                event) as JsonObject).toContent()
        }
        is ScreenEvent -> {
            (EncodeDefaultsJson.encodeToJsonElement(ScreenEvent.serializer(),
                event) as JsonObject).toContent()
        }
        is AliasEvent -> {
            (EncodeDefaultsJson.encodeToJsonElement(AliasEvent.serializer(),
                event) as JsonObject).toContent()
        }
    }
    return V8ObjectUtils.toV8Object(v8, serialized)
}

fun <T: BaseEvent> deserializeFromJS(v8Object: V8Object): T? {
    val underlying = V8ObjectUtils.toMap(v8Object)
    val json = Json.encodeToJsonElement(underlying)
    val serializer = when(underlying["type"]) {
        "track" -> TrackEvent.serializer()
        "screen" -> ScreenEvent.serializer()
        "alias" -> AliasEvent.serializer()
        "identify" -> IdentifyEvent.serializer()
        "group" -> GroupEvent.serializer()
        else -> {
            return null
        }
    }
    return Json.decodeFromJsonElement(serializer, json) as T
}
