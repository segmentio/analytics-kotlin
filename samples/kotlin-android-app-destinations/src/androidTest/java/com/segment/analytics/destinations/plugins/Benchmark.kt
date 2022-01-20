package com.segment.analytics.destinations.plugins

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.utils.V8ObjectUtils
import com.hippo.quickjs.android.JSBoolean
import com.hippo.quickjs.android.JSContext
import com.hippo.quickjs.android.JSDataException
import com.hippo.quickjs.android.JSFunction
import com.hippo.quickjs.android.JSNumber
import com.hippo.quickjs.android.JSObject
import com.hippo.quickjs.android.JSString
import com.hippo.quickjs.android.JSValue
import com.hippo.quickjs.android.JavaMethod
import com.hippo.quickjs.android.QuickJS
import com.hippo.quickjs.android.TypeAdapter
import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.util.Date


@RunWith(AndroidJUnit4::class)
class Benchmark {
    val e1 = TrackEvent(
        event = "App Opened",
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
        event = "Screen Closed",
        properties = buildJsonObject { put("new", false); put("click", false) }
    ).apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = Date(0).toInstant().toString()
    }

    val e4 = TrackEvent(
        event = "App Closed",
        properties = buildJsonObject { put("new", false); put("click", true) }
    ).apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = Date(0).toInstant().toString()
    }

    val ir1 =
        "[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]]"
    val ir2 =
        "[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]"
    val ir3 =
        "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]"

    private val appContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun usingExpressions() {
        println("[TimeTest] Using Predicates")
        // Setup
        val q1 = Json.decodeFromString(JsonArray.serializer(), ir1)
        val q2 = Json.decodeFromString(JsonArray.serializer(), ir2)
        val q3 = Json.decodeFromString(JsonArray.serializer(), ir3)
        val p1 = compile(q1)
        val p2 = compile(q2)
        val p3 = compile(q3)

        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            p1(e1)
            p1(e2)
            p1(e3)
            p1(e4)
        }
        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            p2(e1)
            p2(e2)
            p2(e3)
            p2(e4)
        }
        println("========================= 3")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            p3(e1)
            p3(e2)
            p3(e3)
            p3(e4)
        }

        val test = {
            assertFalse(p1(e1))
            assertTrue(p1(e2))
            assertTrue(p1(e3))
            assertFalse(p1(e4))

            assertTrue(p2(e1))
            assertFalse(p2(e2))
            assertFalse(p2(e3))
            assertTrue(p2(e4))

            assertTrue(p3(e1))
            assertTrue(p3(e2))
            assertTrue(p3(e3))
            assertFalse(p3(e4))
        }
    }

    @Test
    fun usingFQlQuery() {
        println("[TimeTest] Using Runtime Evaluator")
        // Setup
        val q1 = Json.decodeFromString(JsonArray.serializer(), ir1)
        val q2 = Json.decodeFromString(JsonArray.serializer(), ir2)
        val q3 = Json.decodeFromString(JsonArray.serializer(), ir3)

        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            fqlEvaluate(q1, e1)
            fqlEvaluate(q1, e2)
            fqlEvaluate(q1, e3)
            fqlEvaluate(q1, e4)
        }
        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            fqlEvaluate(q2, e1)
            fqlEvaluate(q2, e2)
            fqlEvaluate(q2, e3)
            fqlEvaluate(q2, e4)
        }
        println("========================= 3")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            fqlEvaluate(q3, e1)
            fqlEvaluate(q3, e2)
            fqlEvaluate(q3, e3)
            fqlEvaluate(q3, e4)
        }

        val test = {
            assertFalse(fqlEvaluate(q1, e1))
            assertTrue(fqlEvaluate(q1, e2))
            assertTrue(fqlEvaluate(q1, e3))
            assertFalse(fqlEvaluate(q1, e4))

            assertTrue(fqlEvaluate(q2, e1))
            assertFalse(fqlEvaluate(q2, e2))
            assertFalse(fqlEvaluate(q2, e3))
            assertTrue(fqlEvaluate(q2, e4))

            assertTrue(fqlEvaluate(q3, e1))
            assertTrue(fqlEvaluate(q3, e2))
            assertTrue(fqlEvaluate(q3, e3))
            assertFalse(fqlEvaluate(q3, e4))
        }
    }

    @Test
    fun benchmark() {
        // Setup
        val q1 = Json.decodeFromString(JsonArray.serializer(), ir1)
        val q2 = Json.decodeFromString(JsonArray.serializer(), ir2)
        val q3 = Json.decodeFromString(JsonArray.serializer(), ir3)
        val p1 = compile(q1)
        val p2 = compile(q2)
        val p3 = compile(q3)

        println("========================= 1")
        compare(
            ITERATIONS = 10000,
            TEST_COUNT = 5,
            WARM_COUNT = 2,
            callback1 = {
                p1(e1)
                p1(e2)
                p1(e3)
                p1(e4)
            },
            callback2 = {
                fqlEvaluate(q1, e1)
                fqlEvaluate(q1, e2)
                fqlEvaluate(q1, e3)
                fqlEvaluate(q1, e4)
            }
        )
        println("========================= 2")
        compare(
            ITERATIONS = 10000,
            TEST_COUNT = 5,
            WARM_COUNT = 2,
            callback1 = {
                p2(e1)
                p2(e2)
                p2(e3)
                p2(e4)
            },
            callback2 = {
                fqlEvaluate(q2, e1)
                fqlEvaluate(q2, e2)
                fqlEvaluate(q2, e3)
                fqlEvaluate(q2, e4)
            }
        )
        println("========================= 3")
        compare(
            ITERATIONS = 10000,
            TEST_COUNT = 5,
            WARM_COUNT = 2,
            callback1 = {
                p3(e1)
                p3(e2)
                p3(e3)
                p3(e4)
            },
            callback2 = {
                fqlEvaluate(q3, e1)
                fqlEvaluate(q3, e2)
                fqlEvaluate(q3, e3)
                fqlEvaluate(q3, e4)
            }
        )
    }

    @Test
    fun usingJsRuntime() {
        println("[TimeTest] Using JSRuntime")
        val localJSMiddlewareInputStream = appContext.assets.open("sample.js")
        val script = localJSMiddlewareInputStream.bufferedReader().use(BufferedReader::readText)
        val runtime = JSRuntime(script)
        val m1 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()

        val m2 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()
        val m3 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()

        println("========================= 1")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            runtime.match(m1, e1)
            runtime.match(m1, e2)
            runtime.match(m1, e3)
            runtime.match(m1, e4)
        }
        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            runtime.match(m2, e1)
            runtime.match(m2, e2)
            runtime.match(m2, e3)
            runtime.match(m2, e4)
        }
        println("========================= 3")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            runtime.match(m3, e1)
            runtime.match(m3, e2)
            runtime.match(m3, e3)
            runtime.match(m3, e4)
        }


        val test = {
            assertFalse(runtime.match(m1, e1))
            assertTrue(runtime.match(m1, e2))
            assertTrue(runtime.match(m1, e3))
            assertFalse(runtime.match(m1, e4))

            assertTrue(runtime.match(m2, e1))
            assertFalse(runtime.match(m2, e2))
            assertFalse(runtime.match(m2, e3))
            assertTrue(runtime.match(m2, e4))

            assertTrue(runtime.match(m3, e1))
            assertTrue(runtime.match(m3, e2))
            assertTrue(runtime.match(m3, e3))
            assertFalse(runtime.match(m3, e4))
        }

//        runtime.close()
    }

    @Test
    fun usingJsRuntime2() {
        println("[TimeTest] Using JSRuntime2")
        val localJSMiddlewareInputStream = appContext.assets.open("sample.js")
        val script = localJSMiddlewareInputStream.bufferedReader().use(BufferedReader::readText)
        val runtime = JSRuntime(script)
        val m1 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()

        val m2 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()
        val m3 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()

        val e1 = (Json.encodeToJsonElement(e1) as JsonObject).toContent()
        val e2 = (Json.encodeToJsonElement(e2) as JsonObject).toContent()
        val e3 = (Json.encodeToJsonElement(e3) as JsonObject).toContent()
        val e4 = (Json.encodeToJsonElement(e4) as JsonObject).toContent()

        println("========================= 1")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            runtime.match(m1, e1)
            runtime.match(m1, e2)
            runtime.match(m1, e3)
            runtime.match(m1, e4)
        }
        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            runtime.match(m2, e1)
            runtime.match(m2, e2)
            runtime.match(m2, e3)
            runtime.match(m2, e4)
        }
        println("========================= 3")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            runtime.match(m3, e1)
            runtime.match(m3, e2)
            runtime.match(m3, e3)
            runtime.match(m3, e4)
        }


        val test = {
            assertFalse(runtime.match(m1, e1))
            assertTrue(runtime.match(m1, e2))
            assertTrue(runtime.match(m1, e3))
            assertFalse(runtime.match(m1, e4))

            assertTrue(runtime.match(m2, e1))
            assertFalse(runtime.match(m2, e2))
            assertFalse(runtime.match(m2, e3))
            assertTrue(runtime.match(m2, e4))

            assertTrue(runtime.match(m3, e1))
            assertTrue(runtime.match(m3, e2))
            assertTrue(runtime.match(m3, e3))
            assertFalse(runtime.match(m3, e4))
        }

//        runtime.close()
    }

    @Test
    fun quickjs() {
        val localJSMiddlewareInputStream = appContext.assets.open("sample.js")
        val script = localJSMiddlewareInputStream.bufferedReader().use(BufferedReader::readText)
        val runtime = QuickJSRuntime(script)
        println("[TimeTest] Using QuickJSRuntime2")
        val m1 = """{
        "ir": "[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }"""

        val m2 = """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }"""
        val m3 = """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }"""
//        runtime.match(m1, e1)

        val e1 = Json.encodeToString(TrackEvent.serializer(), e1)
        val e2 = Json.encodeToString(TrackEvent.serializer(), e2)
        val e3 = Json.encodeToString(TrackEvent.serializer(), e3)
        val e4 = Json.encodeToString(TrackEvent.serializer(), e4)

        println("========================= 1")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 10,
            WARM_COUNT = 2
        ) {
            runtime.match(m1, e1)
            runtime.match(m1, e2)
            runtime.match(m1, e3)
            runtime.match(m1, e4)
        }
        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 10,
            WARM_COUNT = 2
        ) {
            runtime.match(m2, e1)
            runtime.match(m2, e2)
            runtime.match(m2, e3)
            runtime.match(m2, e4)
        }
        println("========================= 3")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 10,
            WARM_COUNT = 2
        ) {
            runtime.match(m3, e1)
            runtime.match(m3, e2)
            runtime.match(m3, e3)
            runtime.match(m3, e4)
        }

        val test = {
            assertFalse(runtime.match(m1, e1))
            assertTrue(runtime.match(m1, e2))
            assertTrue(runtime.match(m1, e3))
            assertFalse(runtime.match(m1, e4))

            assertTrue(runtime.match(m2, e1))
            assertFalse(runtime.match(m2, e2))
            assertFalse(runtime.match(m2, e3))
            assertTrue(runtime.match(m2, e4))

            assertTrue(runtime.match(m3, e1))
            assertTrue(runtime.match(m3, e2))
            assertTrue(runtime.match(m3, e3))
            assertFalse(runtime.match(m3, e4))
        }
    }

    @Test
    fun quickjs2() {
        val localJSMiddlewareInputStream = appContext.assets.open("sample.js")
        val script = localJSMiddlewareInputStream.bufferedReader().use(BufferedReader::readText)
        val runtime = QuickJSRuntime(script)
        println("[TimeTest] Using QuickJSRuntime2")
        val m1 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()

        val m2 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()
        val m3 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()
//        runtime.match(m1, e1)

        val e1 = (Json.encodeToJsonElement(e1) as JsonObject).toContent()
        val e2 = (Json.encodeToJsonElement(e2) as JsonObject).toContent()
        val e3 = (Json.encodeToJsonElement(e3) as JsonObject).toContent()
        val e4 = (Json.encodeToJsonElement(e4) as JsonObject).toContent()

        println("========================= 1")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 20,
            WARM_COUNT = 2
        ) {
            runtime.match(m1, e1)
            runtime.match(m1, e2)
            runtime.match(m1, e3)
            runtime.match(m1, e4)
        }
//        println("========================= 2")
//        simpleMeasureTest(
//            ITERATIONS = 1000,
//            TEST_COUNT = 10,
//            WARM_COUNT = 2
//        ) {
//            runtime.match(m2, e1)
//            runtime.match(m2, e2)
//            runtime.match(m2, e3)
//            runtime.match(m2, e4)
//        }
//        println("========================= 3")
//        simpleMeasureTest(
//            ITERATIONS = 1000,
//            TEST_COUNT = 10,
//            WARM_COUNT = 2
//        ) {
//            runtime.match(m3, e1)
//            runtime.match(m3, e2)
//            runtime.match(m3, e3)
//            runtime.match(m3, e4)
//        }
    }

    @Test
    fun quickjs3() {
        val localJSMiddlewareInputStream = appContext.assets.open("sample.js")
        val script = localJSMiddlewareInputStream.bufferedReader().use(BufferedReader::readText)
        val runtime = QuickJSRuntime(script)
        println("[TimeTest] Using QuickJSRuntime2")
        val m1 = runtime.jsonObject(Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent())

        val m2 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()
        val m3 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()
//        runtime.match(m1, e1)

        val e1 = runtime.jsonObject((Json.encodeToJsonElement(e1) as JsonObject).toContent())
        val e2 = runtime.jsonObject((Json.encodeToJsonElement(e2) as JsonObject).toContent())
        val e3 = runtime.jsonObject((Json.encodeToJsonElement(e3) as JsonObject).toContent())
        val e4 = runtime.jsonObject((Json.encodeToJsonElement(e4) as JsonObject).toContent())

        println("========================= 1")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 20,
            WARM_COUNT = 2
        ) {
            runtime.match(m1, e1)
            runtime.match(m1, e2)
            runtime.match(m1, e3)
            runtime.match(m1, e4)
        }
//        println("========================= 2")
//        simpleMeasureTest(
//            ITERATIONS = 1000,
//            TEST_COUNT = 10,
//            WARM_COUNT = 2
//        ) {
//            runtime.match(m2, e1)
//            runtime.match(m2, e2)
//            runtime.match(m2, e3)
//            runtime.match(m2, e4)
//        }
//        println("========================= 3")
//        simpleMeasureTest(
//            ITERATIONS = 1000,
//            TEST_COUNT = 10,
//            WARM_COUNT = 2
//        ) {
//            runtime.match(m3, e1)
//            runtime.match(m3, e2)
//            runtime.match(m3, e3)
//            runtime.match(m3, e4)
//        }
    }
}

class JSRuntime(private val script: String) {

    internal class Console {
        fun log(message: String) {
            println("[INFO] $message")
        }

        fun error(message: String) {
            println("[ERROR] $message")
        }
    }

    private val runtime: V8 = V8.createV8Runtime().also {
//        val console = Console()
//        val v8Console = V8Object(it)
//         // todo Allow string array
//        v8Console.registerJavaMethod(console, "log", "log", arrayOf<Class<*>>(String::class.java))
//        v8Console.registerJavaMethod(console, "error", "err", arrayOf<Class<*>>(String::class.java))
//        it.add("console", v8Console)
        it.executeScript(script)
    }

    fun getObject(key: String): V8Object {
        var result = runtime.getObject(key)
        if (result.isUndefined) {
            result = runtime.executeObjectScript(key) // Blows up when the key does not exist
        }
        return result
    }

    fun match(matcherJson: Map<String, Any?>, payload: BaseEvent): Boolean {
        payload as TrackEvent

        val fn = getObject("edge_function.fnMatch") as V8Function

        val params = V8Array(runtime.runtime)

        val payloadJson = (Json.encodeToJsonElement(payload) as JsonObject).toContent()
        params.push(V8ObjectUtils.toV8Object(runtime.runtime, payloadJson))
        params.push(V8ObjectUtils.toV8Object(runtime.runtime, matcherJson))

        // call it and pick up the result
        val fnResult = fn.call(null, params) as String
//        println(fnResult)
        return fnResult == payload.event
    }

    fun match(matcherJson: Map<String, Any?>, payload: Map<String, Any?>): Boolean {
        val fn = getObject("edge_function.fnMatch") as V8Function

        val params = V8Array(runtime.runtime)

        params.push(V8ObjectUtils.toV8Object(runtime.runtime, payload))
        params.push(V8ObjectUtils.toV8Object(runtime.runtime, matcherJson))

        // call it and pick up the result
        val fnResult = fn.call(null, params) as String
//        println(fnResult)
        return fnResult == (payload["event"] as String)
    }

    fun close() {
        runtime.close()
    }
}

class QuickJSRuntime(private val script: String) {
    private val runtime = QuickJS.Builder().build().createJSRuntime()
    private val context = runtime.createJSContext()

    object Console {
        fun log(msg: String) {
            Log.d("console", msg);
        }

        fun info(msg: String) {
            Log.i("console", msg);
        }

        fun error(msg: String) {
            Log.e("console", msg);
        }
    }

    init {
        val console = context.createJSObject().also {
            it.setProperty("log", context.createJSFunction(
                Console,
                JavaMethod.create(
                    Void::class.java,
                    Console::class.java.getMethod("log", String::class.java)
                )))
        }
        context.globalObject.setProperty("console", console);
        context.evaluate(script, "test.js")
    }

    fun getObject(key: String): JSValue {
        val global = context.globalObject.getProperty("edge_function") as JSObject
        val result = global.getProperty(key)
        return result
    }

    fun match(matcherJson: String, payload: String): Boolean {
        val script by lazy {
            """
            edge_function.fnMatch(
            $payload
            ,
            $matcherJson
            )
        """
        }

        val x = context.evaluate(script, "test.js", String::class.java)

        return true
    }

    fun match(matcherJson: Map<String, Any?>, payload: Map<String, Any?>): Boolean {
        val fn = getObject("fnMatch") as JSFunction

        val payloadParam = context.jsonObject(payload)
        val matcherParam = context.jsonObject(matcherJson)

        // call it and pick up the result
        val fnResult = fn.invoke(null, arrayOf(payloadParam, matcherParam)) as JSString
        return fnResult.string == (payload["event"] as String)
    }

    fun match(matcherParam: JSObject, payloadParam: JSObject): Boolean {
        val fn = getObject("fnMatch") as JSFunction

        // call it and pick up the result
        val fnResult = fn.invoke(null, arrayOf(payloadParam, matcherParam)) as JSString
        return true
    }

    fun close() {
        context.close()
        runtime.close()
    }

    fun jsonObject(payload: Map<String, Any?>) = context.jsonObject(payload)

}

fun JSContext.jsonObject(payload: Map<String, Any?>): JSObject {
    val x = createJSObject()
    for ((key, value) in payload) {
        when (value) {
            is String -> x.setProperty(key, this.createJSString(value))
            is Int -> x.setProperty(key, this.createJSNumber(value))
            is Double -> x.setProperty(key, this.createJSNumber(value))
            is Boolean -> x.setProperty(key, this.createJSBoolean(value))
            is Map<*, *> -> x.setProperty(key, jsonObject(value as Map<String, Any?>))
        }
    }
    return x
}

object MapTypeAdapter : TypeAdapter<Map<String, Any?>>() {
    override fun toJSValue(context: JSContext, value: Map<String, Any?>): JSValue {
        val x = context.createJSObject()
        value.forEach { (key: String, v: Any?) ->
            when (v) {
                is String -> x.setProperty(key, context.createJSString(v))
                is Int -> x.setProperty(key, context.createJSNumber(v))
                is Double -> x.setProperty(key, context.createJSNumber(v))
                is Boolean -> x.setProperty(key, context.createJSBoolean(v))
                is Map<*, *> -> x.setProperty(key, toJSValue(context, v as Map<String, Any?>))
            }
        }
        return x
    }

    override fun fromJSValue(context: JSContext, value: JSValue): Map<String, Any?> {
        val jo = value.cast(JSObject::class.java)
        val keysFunction = context.globalObject
            .getProperty("Object").cast(JSObject::class.java)
            .getProperty("keys").cast(JSFunction::class.java)
        val adapter: TypeAdapter<Array<String>> =
            context.quickJS.getAdapter(Array<String>::class.java)
        val keysResult = keysFunction.invoke(null, arrayOf<JSValue>(jo))
        val keys: Array<String> = adapter.fromJSValue(context, keysResult)
        val map = mutableMapOf<String, Any?>()
        for (key in keys) {
            val value = jo.getProperty(key)
            when (value) {
                is JSString -> map[key] = value.string
                is JSNumber -> {
                    try {
                        val intVal = value.int
                        map[key] = intVal
                    } catch (ignore: JSDataException) {
                        map[key] = value.double
                    }
                }
                is JSBoolean -> map[key] = value.boolean
                is JSObject -> map[key] = fromJSValue(context, value)
            }
        }
        return map
    }
}


object EventTypeAdapter : TypeAdapter<BaseEvent>() {
    override fun toJSValue(context: JSContext, value: BaseEvent): JSValue {
        val x = context.createJSObject()
        x.setProperty("type", context.createJSString(value.type.name))
        x.setProperty("timestamp", context.createJSString(value.timestamp))
        x.setProperty("messageId", context.createJSString(value.messageId))
        x.setProperty("anonymousId", context.createJSString(value.anonymousId))
        if (value.userId.isNotBlank()) {
            x.setProperty("userId", context.createJSString(value.timestamp))
        }
        x.setProperty("context", context.jsonObject(value.context))
        x.setProperty("integrations", context.jsonObject(value.integrations))
        when (value) {
            is AliasEvent -> {
                x.setProperty("previousId", context.createJSString(value.previousId))
                x.setProperty("userId", context.createJSString(value.userId))
            }
            is GroupEvent -> {
                x.setProperty("traits", context.jsonObject(value.traits))
                x.setProperty("userId", context.createJSString(value.groupId))
            }
            is IdentifyEvent -> {
                x.setProperty("traits", context.jsonObject(value.traits))
                x.setProperty("userId", context.createJSString(value.userId))
            }
            is ScreenEvent -> {
                x.setProperty("properties", context.jsonObject(value.properties))
                x.setProperty("name", context.createJSString(value.name))
                x.setProperty("category", context.createJSString(value.category))
            }
            is TrackEvent -> {
                x.setProperty("properties", context.jsonObject(value.properties))
                x.setProperty("event", context.createJSString(value.event))
            }
        }
        return x
    }

    override fun fromJSValue(context: JSContext, value: JSValue): BaseEvent {
        val jo = value.cast(JSObject::class.java)
        val event: BaseEvent = when (jo.getProperty("type").cast(JSString::class.java).string) {
            "track" -> {
                val properties = MapTypeAdapter.fromJSValue(context, jo.getProperty("properties"))
                val event = jo.getProperty("event").cast(JSString::class.java).string
                TrackEvent(properties = JsonObject(properties), event = event)
            }
            "identify" -> {
                TrackEvent()
            }
            "screen" -> {
                TrackEvent()
            }
            "page" -> {
                TrackEvent()
            }
            "group" -> {
                TrackEvent()
            }
            "alias" -> {
                TrackEvent()
            }
        }
        // apply common fields
    }
}