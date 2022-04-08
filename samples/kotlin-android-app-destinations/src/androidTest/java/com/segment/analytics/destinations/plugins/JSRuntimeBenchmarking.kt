package com.segment.analytics.destinations.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.utils.V8ObjectUtils
import com.hippo.quickjs.android.JSFunction
import com.hippo.quickjs.android.JSObject
import com.hippo.quickjs.android.JavaMethod
import com.hippo.quickjs.android.JavaType
import com.hippo.quickjs.android.QuickJS
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.util.Date
import kotlin.system.measureTimeMillis
import com.segment.analytics.destinations.plugins.Utils2.toV8Object as toV8Object2

@RunWith(AndroidJUnit4::class)
class JSRuntimeBenchmarking {
    private val appContext = InstrumentationRegistry.getInstrumentation().context
    val localJSMiddlewareInputStream = appContext.assets.open("sample2.js")
    val script = localJSMiddlewareInputStream.bufferedReader().use(BufferedReader::readText)
    val testScript =
        appContext.assets.open("sample3.js").bufferedReader().use(BufferedReader::readText)

    internal class Console {
        fun log(message: String) {
            println("[INFO] $message")
        }

        fun error(message: String) {
            println("[ERROR] $message")
        }
    }

//    @Test
//    fun buildJ2V8() {
//        val time = benchmark {
//            val runtime: V8 = V8.createV8Runtime().also {
//                val console = Console()
//                val v8Console = V8Object(it)
//                v8Console.registerJavaMethod(console,
//                    "log",
//                    "log",
//                    arrayOf<Class<*>>(String::class.java))
//                v8Console.registerJavaMethod(console,
//                    "error",
//                    "err",
//                    arrayOf<Class<*>>(String::class.java))
//                it.add("console", v8Console)
//            }
//        }
//        println("[Benchmarking] Build time with j2v8 took on average $time ms")
//    }
//
//    @Test
//    fun setupJ2V8() {
//        val time = benchmark {
//            val runtime: V8 = V8.createV8Runtime().also {
//                val console = Console()
//                val v8Console = V8Object(it)
//                v8Console.registerJavaMethod(console,
//                    "log",
//                    "log",
//                    arrayOf<Class<*>>(String::class.java))
//                v8Console.registerJavaMethod(console,
//                    "error",
//                    "err",
//                    arrayOf<Class<*>>(String::class.java))
//                it.add("console", v8Console)
//            }
//            runtime.executeScript(script)
//        }
//        println("[Benchmarking] Evaluate time with tsub-script j2v8 took on average $time ms")
//    }

    @Test
    fun executeJ2V8() {
        val runtime: V8 = V8.createV8Runtime().also {
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
        runtime.executeScript(script)
        val time = benchmark {
            runtime.executeScript("""
                edge_function.fnMatch(
                  {
                    "anonymousId": "8db9b579-bc68-4c7b-bbe8-a4d8adda24a1",
                    "event": "App Opened",
                    "integrations": {
                      "AppBoy": false,
                      "AppsFlyer": true
                    },
                    "messageId": "08e5f7b7-6f7a-49f4-8fe6-3c89e330698f",
                    "originalTimestamp": "2022-01-11T01:30:19.807Z",
                    "properties": {
                      "build": "3",
                      "from_background": false,
                      "version": "2.0"
                    },
                    "receivedAt": "2022-01-11T01:30:21.305Z",
                    "sentAt": "2022-01-11T01:30:20.224Z",
                    "timestamp": "2022-01-11T01:30:20.888Z",
                    "type": "track",
                    "writeKey": "2Iv5ifBwYxcTWwYPsfIwrzY9bKRtabKi"
                  }
                  ,
                  {
                    "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]",
                    "type": "fql",
                    "config": {
                      "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
                    }
                  }
                )
            """)
        }
        println("[Benchmarking] Matching Execution time with tsub-script j2v8 took on average $time ms")
    }

//    @Test
//    fun buildQuick() {
//        val time = benchmark {
//            val runtime = QuickJS.Builder().build().createJSRuntime()
//            val context = runtime.createJSContext()
//            val console = context.createJSObject().also {
//                it.setProperty("log", context.createJSFunction(
//                    QuickJSRuntime.Console,
//                    JavaMethod.create(
//                        Void::class.java,
//                        QuickJSRuntime.Console::class.java.getMethod("log", String::class.java)
//                    )))
//            }
//            context.globalObject.setProperty("console", console);
//            runtime.close()
//        }
//        println("[Benchmarking] Build time with quickJs took on average $time ms")
//    }
//
//    @Test
//    fun setupQuick() {
//        val time = benchmark {
//            val runtime = QuickJS.Builder().build().createJSRuntime()
//            val context = runtime.createJSContext()
//            val console = context.createJSObject().also {
//                it.setProperty("log", context.createJSFunction(
//                    QuickJSRuntime.Console,
//                    JavaMethod.create(
//                        Void::class.java,
//                        QuickJSRuntime.Console::class.java.getMethod("log", String::class.java)
//                    )))
//            }
//            context.globalObject.setProperty("console", console);
//            context.evaluate(script, "test.js")
//            runtime.close()
//        }
//        println("[Benchmarking] Evaluate time with tsub-script quickJs took on average $time ms")
//    }

    @Test
    fun executeQuick() {
        val runtime = QuickJS.Builder().build().createJSRuntime()
        val context = runtime.createJSContext()
        val console = context.createJSObject().also {
            it.setProperty("log", context.createJSFunction(
                QuickJSRuntime.Console,
                JavaMethod.create(
                    Void::class.java,
                    QuickJSRuntime.Console::class.java.getMethod("log", String::class.java)
                )))
        }
        context.globalObject.setProperty("console", console);
        context.evaluate(script, "test.js")
        val time = benchmark {
            context.evaluate("""
                edge_function.fnMatch(
                  {
                    "anonymousId": "8db9b579-bc68-4c7b-bbe8-a4d8adda24a1",
                    "event": "App Opened",
                    "integrations": {
                      "AppBoy": false,
                      "AppsFlyer": true
                    },
                    "messageId": "08e5f7b7-6f7a-49f4-8fe6-3c89e330698f",
                    "originalTimestamp": "2022-01-11T01:30:19.807Z",
                    "properties": {
                      "build": "3",
                      "from_background": false,
                      "version": "2.0"
                    },
                    "receivedAt": "2022-01-11T01:30:21.305Z",
                    "sentAt": "2022-01-11T01:30:20.224Z",
                    "timestamp": "2022-01-11T01:30:20.888Z",
                    "type": "track",
                    "writeKey": "2Iv5ifBwYxcTWwYPsfIwrzY9bKRtabKi"
                  }
                  ,
                  {
                    "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]",
                    "type": "fql",
                    "config": {
                      "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
                    }
                  }
                )
            """, "test.js")
        }
        println("[Benchmarking] Matching Execution time with tsub-script quickJs took on average $time ms")
        context.close()
        runtime.close()
    }

    @Test
    fun testDataBridgeQuick() {
        val runtime = QuickJS.Builder().build().createJSRuntime()
        val context = runtime.createJSContext()
        val console = context.createJSObject().also {
            it.setProperty("log", context.createJSFunction(
                QuickJSRuntime.Console,
                JavaMethod.create(
                    Void::class.java,
                    QuickJSRuntime.Console::class.java.getMethod("log", String::class.java)
                )))
        }
        context.globalObject.setProperty("console", console);
        context.evaluate(testScript, "test.js")
        val _e4 = TrackEvent(
            event = "App Closed",
            properties = buildJsonObject { put("new", false); put("click", true) }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            this.context = emptyJsonObject
            timestamp = Date(0).toInstant().toString()
        }
        val e4 = (Json.encodeToJsonElement(TrackEvent.serializer(), _e4) as JsonObject).toContent()

        val timeToEncode = benchmark {
            context.jsonObject(e4)
        }
        println("[Benchmarking] (QuickJS) Time to encode $timeToEncode ms")

        val param = context.jsonObject(e4)
        val timeExecute = benchmark {
            val fn = context.globalObject.getProperty("fn") as JSFunction
            fn.invoke(null, arrayOf(param))
        }
        println("[Benchmarking] (QuickJS) Time to do only execution $timeExecute ms")

        val timeTotal = benchmark {
            val param = context.jsonObject(e4)
            val fn = context.globalObject.getProperty("fn") as JSFunction
            fn.invoke(null, arrayOf(param))
        }
        println("[Benchmarking] (QuickJS) Time to do all of it $timeTotal ms")
        context.close()
        runtime.close()
    }

    @Test
    fun testDataBridgeJ2V8() {
        val runtime: V8 = V8.createV8Runtime().also {
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
        runtime.executeScript(testScript)

        val _e4 = TrackEvent(
            event = "App Closed",
            properties = buildJsonObject { put("new", false); put("click", true) }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            this.context = emptyJsonObject
            timestamp = Date(0).toInstant().toString()
        }
        val e4 = (Json.encodeToJsonElement(TrackEvent.serializer(), _e4) as JsonObject).toContent()

        val timeToEncode = benchmark {
            V8ObjectUtils.toV8Object(runtime.runtime, e4)
        }
        println("[Benchmarking] (J2V8) Time to encode $timeToEncode ms")

        val param = V8ObjectUtils.toV8Object(runtime.runtime, e4)
        val timeExecute = benchmark {
            val fn = runtime.getObject("fn") as V8Function
            fn.call(null, V8Array(runtime.runtime).apply { push(param) })
        }
        println("[Benchmarking] (J2V8) Time to do only execution $timeExecute ms")

        val timeTotal = benchmark {
            val param = V8ObjectUtils.toV8Object(runtime.runtime, e4)
            val fn = runtime.getObject("fn") as V8Function
            fn.call(null, V8Array(runtime.runtime).apply { push(param) })
        }
        println("[Benchmarking] (J2V8) Time to do all of it $timeTotal ms")
    }


    @Test
    fun testReturnCallbackQuick() {
        val mapType = object : JavaType<String>() {}.type

        val runtime = QuickJS.Builder()
//            .registerTypeAdapter(mapType, MapTypeAdapter())
            .build()
            .createJSRuntime()
        val context = runtime.createJSContext()
        val console = context.createJSObject().also {
            it.setProperty("log", context.createJSFunction(
                QuickJSRuntime.Console,
                JavaMethod.create(
                    Void::class.java,
                    QuickJSRuntime.Console::class.java.getMethod("log", String::class.java)
                )))
        }
        context.globalObject.setProperty("console", console);
        context.evaluate(testScript, "test.js")
        val _e4 = TrackEvent(
            event = "App Closed",
            properties = buildJsonObject { put("new", false); put("click", true) }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            this.context = emptyJsonObject
            timestamp = Date(0).toInstant().toString()
        }
        val e4 =
            (Json.encodeToJsonElement(TrackEvent.serializer(), _e4) as JsonObject).toContent()
        val time = benchmark {
            val param = MapTypeAdapter.toJSValue(context, e4)
            val fn = context.globalObject.getProperty("fn") as JSFunction
            val new = fn.invoke(null, arrayOf(param)) as JSObject
            val x = MapTypeAdapter.fromJSValue(context, new)
            println("[Benchmarking-Debug] (Quick) returned value=$x")
        }
        println("[Benchmarking] (Quick) Time to run + encode/decode is $time ms")

        context.close()
        runtime.close()
    }

    @Test
    fun testReturnCallbackJ2V8() {
        val runtime: V8 = V8.createV8Runtime().also {
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
        runtime.executeScript(testScript)

        val _e4 = TrackEvent(
            event = "App Closed",
            properties = buildJsonObject { put("new", false); put("click", true) }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            this.context = emptyJsonObject
            timestamp = Date(0).toInstant().toString()
        }
        val e4 =
            (Json.encodeToJsonElement(TrackEvent.serializer(), _e4) as JsonObject).toContent()
        val time = benchmark {
            val param = V8ObjectUtils.toV8Object(runtime.runtime, e4)
            val fn = runtime.getObject("fn") as V8Function
            val fnResult =
                fn.call(null, V8Array(runtime.runtime).apply { push(param) }) as V8Object?
            val x: Map<String, *> = V8ObjectUtils.toMap(fnResult)
            println("[Benchmarking-Debug] (J2V8) returned value=$x")
        }
        println("[Benchmarking] (J2V8) Time to run + encode/decode is $time ms")
    }

    @Test
    fun benchmarkSerializationJ2V8() {
        val runtime: V8 = V8.createV8Runtime().also {
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
        val e = TrackEvent(
            event = "App Closed",
            properties = buildJsonObject { put("new", false); put("click", true) }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = buildJsonObject {
                put("key1", "true")
                put("key2", "2")
                put("key3", "4f")
                put("key4", "4f")
            }
            this.context = buildJsonObject {
                put("key1", true)
                put("key2", 2)
                put("key3", 4f)
                put("key4", 4f)
                put("key4", buildJsonObject {
                    put("key1", true)
                    put("key2", false)
                })
            }
            timestamp = Date(0).toInstant().toString()
        }

        val time1 = benchmark {
            serializeToJS(runtime, e)
        }
        println("[Benchmarking] (J2V8) Native Encode is $time1 ms")
        val time2 = benchmark {
            e.toV8Object(runtime)
        }
        println("[Benchmarking] (J2V8) Custom Encode is $time2 ms")
        val time3 = benchmark {
            e.toV8Object2(runtime)
        }
        println("[Benchmarking] (J2V8) Custom Encode w/o cache is $time3 ms")
//
//
//        val time4 = benchmark {
//            e.toV8Object(runtime)
//        }
//        println("[Benchmarking] (J2V8) Custom Encode is $time4 ms")
//        val time3 = benchmark {
//            serializeToJS(runtime, e)
//        }
//        println("[Benchmarking] (J2V8) Native Encode is $time3 ms")
    }

    @Test
    fun benchmarkDeSerializationJ2V8() {
        val runtime: V8 = V8.createV8Runtime().also {
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
        val e = TrackEvent(
            event = "App Closed",
            properties = buildJsonObject { put("new", false); put("click", true) }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = buildJsonObject {
                put("key1", "true")
                put("key2", "2")
                put("key3", "4f")
                put("key4", "4f")
            }
            this.context = buildJsonObject {
                put("key1", true)
                put("key2", 2)
                put("key3", 4f)
                put("key4", 4f)
                put("key4", buildJsonObject {
                    put("key1", true)
                    put("key2", false)
                })
            }
            timestamp = Date(0).toInstant().toString()
        }

        val obj = e.toV8Object(runtime)
        println(obj)
//        val time1 = benchmark {
//            deserializeFromJS<TrackEvent>(obj)
//        }
//        println("[Benchmarking] (J2V8) Native Decode is $time1 ms")
        val time2 = benchmark {
            obj.toSegmentEvent<TrackEvent>()
        }
        println("[Benchmarking] (J2V8) Custom Decode is $time2 ms")
        val x: TrackEvent? = obj.toSegmentEvent()
        x?.let {
            println(Json { prettyPrint = true }.encodeToString(TrackEvent.serializer(), it))
        }
    }


//    @Test
//    fun testPerformance() {
//        for (i in 0..0) {
//        testDataBridgeQuick()
//        testDataBridgeJ2V8()
//            testReturnCallbackQuick()
//            testReturnCallbackJ2V8()
//        }
//    }
}

fun benchmark(times: Int = 10000, closure: () -> Unit): Double {
    val list = mutableListOf<Long>()
    for (i in 1..times) {
        list.add(measureTimeMillis(closure))
    }
    return list.average()
}
