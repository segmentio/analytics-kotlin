package com.segment.analytics.destinations.plugins

import com.eclipsesource.v8.Releasable
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.V8Value
import com.eclipsesource.v8.utils.V8ObjectUtils
import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.EventType
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.toContent
import io.alicorn.v8.V8JavaAdapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.util.Hashtable
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


fun BaseEvent.toV8Object(v8: V8): V8Object {
    val result = V8Object(v8)
    // Add type
    when (type) {
        EventType.Track -> result.add("type", "track")
        EventType.Screen -> result.add("type", "screen")
        EventType.Alias -> result.add("type", "alias")
        EventType.Identify -> result.add("type", "identify")
        EventType.Group -> result.add("type", "group")
    }

    // Add strings
    result.add("anonymousId", anonymousId)
    result.add("messageId", messageId)
    result.add("timestamp", timestamp)
    result.add("userId", userId)

    // Add complex types
    result.add("context", toV8Object(v8, context))
    result.add("integrations", toV8Object(v8, integrations))

    when (this) {
        is TrackEvent -> {
            result.add("event", event)
            result.add("properties", toV8Object(v8, properties))
        }
        is ScreenEvent -> {
            result.add("name", name)
            result.add("category", category)
            result.add("properties", toV8Object(v8, properties))
        }
        is AliasEvent -> {
            result.add("previousId", previousId)
        }
        is IdentifyEvent -> {
            result.add("traits", toV8Object(v8, traits))
        }
        is GroupEvent -> {
            result.add("groupId", groupId)
            result.add("traits", toV8Object(v8, traits))
        }
    }

    return result
}

@Suppress("UNCHECKED_CAST")
fun <T : BaseEvent> V8Object.toSegmentEvent(): T? {
    // TODO we are doing lots of native->JS calls here, maybe there is a way to do one call to get
    //  all keys/values and then process that instead, to get a performance benefit
    val event = when (getString("type") ?: "") {
        "track" -> {
            TrackEvent(
                event = getString("event") ?: "",
                properties = fromV8Object(getObject("properties")) ?: emptyJsonObject
            )
        }
        "screen" -> {
            ScreenEvent(
                name = getString("name") ?: "",
                category = getString("category") ?: "",
                properties = fromV8Object(getObject("properties")) ?: emptyJsonObject
            )
        }
        "alias" -> {
            AliasEvent(
                userId = getString("userId") ?: "",
                previousId = getString("previousId") ?: ""
            )
        }
        "identify" -> {
            IdentifyEvent(
                userId = getString("userId") ?: "",
                traits = fromV8Object(getObject("traits")) ?: emptyJsonObject
            )
        }
        "group" -> {
            GroupEvent(
                groupId = getString("groupId") ?: "",
                traits = fromV8Object(getObject("traits")) ?: emptyJsonObject
            )
        }
        else -> {
            // Unidentified Type
            return null
        }
    }

    // Add strings
    event.anonymousId = getString("anonymousId") ?: ""
    event.messageId = getString("messageId") ?: ""
    event.timestamp = getString("timestamp") ?: ""
    event.userId = getString("userId") ?: ""

    // Add complex types
    event.context = fromV8Object(getObject("context")) ?: emptyJsonObject
    event.integrations = fromV8Object(getObject("integrations")) ?: emptyJsonObject

    return event as T // This is ok since `event` will always be one of BaseEvent's subtypes
}

fun toV8Object(v8: V8, map: Map<String, JsonElement>): V8Object {
    val cache = Hashtable<Any, V8Value>() // cache objects to easily free them once done
    return try {
        toV8Object(v8, map, cache).twin()
    } finally {
        for (v8Object in cache.values) {
            v8Object.close()
        }
    }
}

fun toV8Object(v8: V8, map: Map<String, JsonElement>, cache: MutableMap<Any, V8Value>): V8Object {
    if (cache.containsKey(map)) {
        return (cache[map] as V8Object)
    }
    val result = V8Object(v8)
    cache[map] = result
    try {
        for ((key, value) in map) {
            setValue(v8, result, key, value, cache)
        }
    } catch (e: IllegalStateException) {
        result.close()
        throw e
    }
    return result
}

fun toV8Array(v8: V8, list: List<JsonElement>, cache: MutableMap<Any, V8Value>): V8Array {
    if (cache.containsKey(list)) {
        return (cache[list] as V8Array)
    }
    val result = V8Array(v8)
    cache[list] = result
    try {
        for (value in list) {
            pushValue(v8, result, value, cache)
        }
    } catch (e: IllegalStateException) {
        result.close()
        throw e
    }
    return result
}

private fun pushValue(
    v8: V8,
    result: V8Array,
    value: JsonElement?,
    cache: MutableMap<Any, V8Value>,
) {
    when (value) {
        null, JsonNull -> {
            // skip it
        }
        is JsonPrimitive -> {
            when (val serialized = value.toContent()) {
                null -> { /* skip */
                }
                is Boolean -> result.push(serialized)
                is Int -> result.push(serialized)
                is Long -> result.push(serialized.toDouble())
                is Double -> result.push(serialized)
                is String -> result.push(serialized)
            }
        }
        is JsonObject -> {
            val v8Obj = toV8Object(v8, value, cache)
            result.push(v8Obj)
        }
        is JsonArray -> {
            val v8Arr = toV8Array(v8, value, cache)
            result.push(v8Arr)
        }
    }
}


private fun setValue(
    v8: V8,
    result: V8Object,
    key: String,
    value: JsonElement?,
    cache: MutableMap<Any, V8Value>,
) {
    when (value) {
        null, JsonNull -> result.addUndefined(key)
        is JsonPrimitive -> {
            when (val serialized = value.toContent()) {
                null -> { /* skip */ }
                is String -> result.add(key, serialized)
                is Boolean -> result.add(key, serialized)
                is Int -> result.add(key, serialized)
                is Long -> result.add(key, serialized.toDouble())
                is Double -> result.add(key, serialized)
            }
        }
        is JsonObject -> {
            val v8Obj = toV8Object(v8, value, cache)
            result.add(key, v8Obj)
        }
        is JsonArray -> {
            val v8Arr = toV8Array(v8, value, cache)
            result.add(key, v8Arr)
        }
    }
}

private fun getValue(value: Any?, type: Int): JsonElement? {
    return when (type) {
        V8Value.BOOLEAN -> JsonPrimitive(value as Boolean)
        V8Value.INTEGER, V8Value.DOUBLE -> JsonPrimitive(value as Number)
        V8Value.STRING -> JsonPrimitive(value as String)
        V8Value.V8_ARRAY -> fromV8Array(value as V8Array?)
        V8Value.V8_OBJECT -> fromV8Object(value as V8Object?)
        V8Value.UNDEFINED, V8Value.NULL, V8Value.V8_TYPED_ARRAY, V8Value.V8_FUNCTION, V8Value.V8_ARRAY_BUFFER -> JsonNull
        else -> null
    }
}

fun fromV8Object(obj: V8Object?): JsonObject? {
    if (obj == null) {
        return null
    }
    return buildJsonObject {
        val keys: Array<String> = obj.keys
        for (key in keys) {
            var v8Val: Any? = null
            var type: Int
            try {
                v8Val = obj.get(key)
                type = obj.getType(key)
                val value = getValue(v8Val, type)
                value?.let {
                    if (it != JsonNull) {
                        put(key, it)
                    }
                }
            } finally {
                if (v8Val is Releasable) {
                    v8Val.release()
                }
            }
        }
    }
}

fun fromV8Array(array: V8Array?): JsonArray? {
    if (array == null) {
        return null
    }
    return buildJsonArray {
        for (i in 0 until array.length()) {
            var v8Val: Any? = null
            var type: Int
            try {
                v8Val = array.get(i)
                type = array.getType(i)
                val value = getValue(v8Val, type)
                value?.let {
                    if (it != JsonNull) {
                        add(it)
                    }
                }
            } finally {
                if (v8Val is Releasable) {
                    v8Val.release()
                }
            }
        }
    }
}

object Utils2 {
    fun BaseEvent.toV8Object(v8: V8): V8Object {
        val result = V8Object(v8)
        // Add type
        when (type) {
            EventType.Track -> result.add("type", "track")
            EventType.Screen -> result.add("type", "screen")
            EventType.Alias -> result.add("type", "alias")
            EventType.Identify -> result.add("type", "identify")
            EventType.Group -> result.add("type", "group")
        }

        // Add strings
        result.add("anonymousId", anonymousId)
        result.add("messageId", messageId)
        result.add("timestamp", timestamp)
        result.add("userId", userId)

        // Add complex types
        result.add("context", toV8Object(v8, context))
        result.add("integrations", toV8Object(v8, integrations))

        when (this) {
            is TrackEvent -> {
                result.add("event", event)
                result.add("properties", toV8Object(v8, properties))
            }
            is ScreenEvent -> {
                result.add("name", name)
                result.add("category", category)
                result.add("properties", toV8Object(v8, properties))
            }
            is AliasEvent -> {
                result.add("previousId", previousId)
            }
            is IdentifyEvent -> {
                result.add("traits", toV8Object(v8, traits))
            }
            is GroupEvent -> {
                result.add("groupId", groupId)
                result.add("traits", toV8Object(v8, traits))
            }
        }

        return result
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : BaseEvent> V8Object.toSegmentEvent(): T? {
        // TODO we are doing lots of native->JS calls here, maybe there is a way to do one call to get
        //  all keys/values and then process that instead, to get a performance benefit
        val event = when (getString("type") ?: "") {
            "track" -> {
                TrackEvent(
                    event = getString("event") ?: "",
                    properties = fromV8Object(getObject("properties")) ?: emptyJsonObject
                )
            }
            "screen" -> {
                ScreenEvent(
                    name = getString("name") ?: "",
                    category = getString("category") ?: "",
                    properties = fromV8Object(getObject("properties")) ?: emptyJsonObject
                )
            }
            "alias" -> {
                AliasEvent(
                    userId = getString("userId") ?: "",
                    previousId = getString("previousId") ?: ""
                )
            }
            "identify" -> {
                IdentifyEvent(
                    userId = getString("userId") ?: "",
                    traits = fromV8Object(getObject("traits")) ?: emptyJsonObject
                )
            }
            "group" -> {
                GroupEvent(
                    groupId = getString("groupId") ?: "",
                    traits = fromV8Object(getObject("traits")) ?: emptyJsonObject
                )
            }
            else -> {
                // Unidentified Type
                return null
            }
        }

        // Add strings
        event.anonymousId = getString("anonymousId") ?: ""
        event.messageId = getString("messageId") ?: ""
        event.timestamp = getString("timestamp") ?: ""
        event.userId = getString("userId") ?: ""

        // Add complex types
        event.context = fromV8Object(getObject("context")) ?: emptyJsonObject
        event.integrations = fromV8Object(getObject("integrations")) ?: emptyJsonObject

        return event as T // This is ok since `event` will always be one of BaseEvent's subtypes
    }

    fun toV8Object(v8: V8, map: Map<String, JsonElement>): V8Object {
        val result = V8Object(v8)
        try {
            for ((key, value) in map) {
                setValue(v8, result, key, value)
            }
        } catch (e: IllegalStateException) {
            result.close()
            throw e
        }
        return result
    }

    fun toV8Array(v8: V8, list: List<JsonElement>): V8Array {
        val result = V8Array(v8)
        try {
            for (value in list) {
                pushValue(v8, result, value)
            }
        } catch (e: IllegalStateException) {
            result.close()
            throw e
        }
        return result
    }

    private fun pushValue(
        v8: V8,
        result: V8Array,
        value: JsonElement?,
    ) {
        when (value) {
            null, JsonNull -> {
                // skip it
            }
            is JsonPrimitive -> {
                when (val serialized = value.toContent()) {
                    null -> { /* skip */
                    }
                    is Boolean -> result.push(serialized)
                    is Int -> result.push(serialized)
                    is Long -> result.push(serialized.toDouble())
                    is Double -> result.push(serialized)
                    is String -> result.push(serialized)
                }
            }
            is JsonObject -> {
                val v8Obj = toV8Object(v8, value)
                result.push(v8Obj)
            }
            is JsonArray -> {
                val v8Arr = toV8Array(v8, value)
                result.push(v8Arr)
            }
        }
    }


    private fun setValue(
        v8: V8,
        result: V8Object,
        key: String,
        value: JsonElement?,
    ) {
        when (value) {
            null, JsonNull -> result.addUndefined(key)
            is JsonPrimitive -> {
                when (val serialized = value.toContent()) {
                    null -> { /* skip */
                    }
                    is String -> result.add(key, serialized)
                    is Boolean -> result.add(key, serialized)
                    is Int -> result.add(key, serialized)
                    is Long -> result.add(key, serialized.toDouble())
                    is Double -> result.add(key, serialized)
                }
            }
            is JsonObject -> {
                val v8Obj = toV8Object(v8, value)
                result.add(key, v8Obj)
            }
            is JsonArray -> {
                val v8Arr = toV8Array(v8, value)
                result.add(key, v8Arr)
            }
        }
    }

    private fun getValue(value: Any?, type: Int): JsonElement? {
        return when (type) {
            V8Value.BOOLEAN -> JsonPrimitive(value as Boolean)
            V8Value.INTEGER, V8Value.DOUBLE -> JsonPrimitive(value as Number)
            V8Value.STRING -> JsonPrimitive(value as String)
            V8Value.V8_ARRAY -> fromV8Array(value as V8Array?)
            V8Value.V8_OBJECT -> fromV8Object(value as V8Object?)
            V8Value.UNDEFINED, V8Value.NULL, V8Value.V8_TYPED_ARRAY, V8Value.V8_FUNCTION, V8Value.V8_ARRAY_BUFFER -> JsonNull
            else -> null
        }
    }

    fun fromV8Object(obj: V8Object?): JsonObject? {
        if (obj == null) {
            return null
        }
        return buildJsonObject {
            val keys: Array<String> = obj.keys
            for (key in keys) {
                var v8Val: Any? = null
                var type: Int
                try {
                    v8Val = obj.get(key)
                    type = obj.getType(key)
                    val value = getValue(v8Val, type)
                    value?.let {
                        if (it != JsonNull) {
                            put(key, it)
                        }
                    }
                } finally {
                    if (v8Val is Releasable) {
                        v8Val.release()
                    }
                }
            }
        }
    }

    fun fromV8Array(array: V8Array?): JsonArray? {
        if (array == null) {
            return null
        }
        return buildJsonArray {
            for (i in 0 until array.length()) {
                var v8Val: Any? = null
                var type: Int
                try {
                    v8Val = array.get(i)
                    type = array.getType(i)
                    val value = getValue(v8Val, type)
                    value?.let {
                        if (it != JsonNull) {
                            add(it)
                        }
                    }
                } finally {
                    if (v8Val is Releasable) {
                        v8Val.release()
                    }
                }
            }
        }
    }
}