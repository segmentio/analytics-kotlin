@file:JvmName("JsonUtils")

package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.BooleanArraySerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.CharArraySerializer
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.builtins.FloatArraySerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.LongArraySerializer
import kotlinx.serialization.builtins.ShortArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.reflect.KClass

val EncodeDefaultsJson = Json {
    encodeDefaults = true
}

val LenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Convenience method to get current element as [JsonPrimitive?]
 */
public val JsonElement.safeJsonPrimitive get() = this as? JsonPrimitive

/**
 * Convenience method to get current element as [JsonObject?]
 */
public val JsonElement.safeJsonObject get() = this as? JsonObject

/**
 * Convenience method to get current element as [JsonArray?]
 */
public val JsonElement.safeJsonArray get() = this as? JsonArray


// Utility function to convert a jsonPrimitive to its appropriate kotlin type
fun JsonPrimitive.toContent(): Any? {
    this.booleanOrNull?.let {
        return it
    }
    this.intOrNull?.let {
        return it
    }
    this.longOrNull?.let {
        return it
    }
    this.doubleOrNull?.let {
        return it
    }
    return contentOrNull
}

// Utility function to convert a jsonPrimitive to its appropriate kotlin primitive type
fun JsonObject.toContent(): Map<String, Any?> {
    return mapValues { (_, v) -> v.toContent() }
}

// Utility function to convert a jsonPrimitive to its appropriate kotlin primitive type
fun JsonArray.toContent(): List<Any?> {
    return map { v -> v.toContent() }
}

// Utility function to convert a jsonPrimitive to its appropriate kotlin primitive type
fun JsonElement.toContent(): Any? {
    // We use dynamic dispatch to choose the correct function
    return when (this) {
        is JsonPrimitive -> toContent()
        is JsonObject -> toContent()
        is JsonArray -> toContent()
        else -> null
    }
}

// Utility function to put all values of `obj` into current builder
fun JsonObjectBuilder.putAll(obj: JsonObject) {
    obj.forEach { (key, value) ->
        put(key, value)
    }
}

// Utility function to put "undefined" value in-stead of `null` when building JsonObject
fun JsonObjectBuilder.putUndefinedIfNull(key: String, value: CharSequence?): JsonElement? =
    if (value.isNullOrEmpty()) {
        put(key, "undefined")
    } else {
        put(key, value.toString())
    }

// Utility function to retrieve a boolean value from a jsonObject
fun JsonObject.getBoolean(key: String): Boolean? = this[key]?.safeJsonPrimitive?.booleanOrNull

// Utility function to retrieve a string value from a jsonObject
fun JsonObject.getString(key: String): String? = this[key]?.safeJsonPrimitive?.contentOrNull

// Utility function to retrieve a double value from a jsonObject
fun JsonObject.getDouble(key: String): Double? = this[key]?.safeJsonPrimitive?.doubleOrNull

// Utility function to retrieve a int value from a jsonObject
fun JsonObject.getInt(key: String): Int? = this[key]?.safeJsonPrimitive?.intOrNull

// Utility function to retrieve a long value from a jsonObject
fun JsonObject.getLong(key: String): Long? = this[key]?.safeJsonPrimitive?.longOrNull

// Utility function to retrieve a string set (from jsonArray) from a jsonObject
fun JsonObject.getStringSet(key: String): Set<String>? =
    this[key]?.safeJsonArray?.map { it.toContent().toString() }?.toSet()


// Utility function to retrieve a list of Map<String, Any?> from a jsonObject, skips any non-map elements
fun JsonObject.getMapList(key: String): List<Map<String, Any?>>? =
    this[key]?.safeJsonArray?.filterIsInstance<JsonObject>()?.map { it.jsonObject.toContent() }


// Utility function to apply key-mappings (deep traversal) and an optional value transform
fun JsonObject.mapTransform(
    keyMapper: Map<String, String>,
    valueTransform: ((key: String, value: JsonElement) -> JsonElement)? = null
): JsonObject = buildJsonObject {
    val original = this@mapTransform
    original.forEach { (key, value) ->
        var newKey: String = key
        var newVal: JsonElement = value
        // does this key1 have a mapping?
        keyMapper[key]?.let { mappedKey ->
            newKey = mappedKey
        }

        // is this value a dictionary?
        if (value is JsonObject) {
            // if so, lets recurse...
            newVal = value.mapTransform(keyMapper, valueTransform)
        } else if (value is JsonArray) {
            newVal = value.mapTransform(keyMapper, valueTransform)
        }
        if (newVal !is JsonObject && valueTransform != null) {
            // it's not a dictionary apply our transform.
            // note: if it's an array, we've processed any dictionaries inside
            // already, but this gives the opportunity to apply a transform to the other
            // items in the array that weren't dictionaries.

            newVal = valueTransform(newKey, newVal)
        }
        put(newKey, newVal)
    }
}

// Utility function to apply key-mappings (deep traversal) and an optional value transform
fun JsonArray.mapTransform(
    keyMapper: Map<String, String>,
    valueTransform: ((key: String, value: JsonElement) -> JsonElement)? = null
): JsonArray = buildJsonArray {
    val original = this@mapTransform
    original.forEach { item: JsonElement ->
        var newValue = item
        if (item is JsonObject) {
            newValue = item.mapTransform(keyMapper, valueTransform)
        } else if (item is JsonArray) {
            newValue = item.mapTransform(keyMapper, valueTransform)
        }
        add(newValue)
    }
}


// Utility function to transform keys in JsonObject. Only acts on root level keys
fun JsonObject.transformKeys(transform: (String) -> String): JsonObject {
    return JsonObject(this.mapKeys { transform(it.key) })
}

// Utility function to transform values in JsonObject. Only acts on root level values
fun JsonObject.transformValues(transform: (JsonElement) -> JsonElement): JsonObject {
    return JsonObject(this.mapValues { transform(it.value) })
}

// Utility function to update a JsonObject. The updated copy is returned. Original copy is not touched
fun updateJsonObject(jsonObject: JsonObject, closure: (MutableMap<String, JsonElement>) -> Unit): JsonObject {
    val content = jsonObject.toMutableMap()
    closure(content)
    return JsonObject(content)
}

operator fun MutableMap<String, JsonElement>.set(key:String, value: String?) {
    if (value == null) {
        remove(key)
    }
    else {
        this[key] = JsonPrimitive(value)
    }
}

operator fun MutableMap<String, JsonElement>.set(key:String, value: Number) {
    this[key] = JsonPrimitive(value)
}

operator fun MutableMap<String, JsonElement>.set(key:String, value: Boolean) {
    this[key] = JsonPrimitive(value)
}


@OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)
val primitiveSerializers = mapOf(
    String::class to String.serializer(),
    Char::class to Char.serializer(),
    CharArray::class to CharArraySerializer(),
    Double::class to Double.serializer(),
    DoubleArray::class to DoubleArraySerializer(),
    Float::class to Float.serializer(),
    FloatArray::class to FloatArraySerializer(),
    Long::class to Long.serializer(),
    LongArray::class to LongArraySerializer(),
    Int::class to Int.serializer(),
    IntArray::class to IntArraySerializer(),
    Short::class to Short.serializer(),
    ShortArray::class to ShortArraySerializer(),
    Byte::class to Byte.serializer(),
    ByteArray::class to ByteArraySerializer(),
    Boolean::class to Boolean.serializer(),
    BooleanArray::class to BooleanArraySerializer(),
    Unit::class to Unit.serializer(),
    UInt::class to UInt.serializer(),
    ULong::class to ULong.serializer(),
    UByte::class to UByte.serializer(),
    UShort::class to UShort.serializer()
)

/**
 * Experimental API that can be used to convert primitive
 * values to their equivalent JsonElement representation.
 */
inline fun <reified T : Any> serializerFor(value: KClass<out T>): KSerializer<T>? {
    val serializer = primitiveSerializers[value] ?: return null
    return serializer as KSerializer<T>
}

/**
 * Experimental API that can be used to convert Map
 * values to their equivalent JsonElement representation.
 */
fun Map<String, Any>.toJsonElement(): JsonElement {
    return buildJsonObject {
        for ((key, value) in this@toJsonElement) {
            if (value is JsonElement) {
                put(key, value)
            } else {
                put(key, value.toJsonElement())
            }
        }
    }
}

/**
 * Experimental API that can be used to convert Array
 * values to their equivalent JsonElement representation.
 */
fun Array<Any>.toJsonElement(): JsonArray {
    return buildJsonArray {
        for (item in this@toJsonElement) {
            if (item is JsonElement) {
                add(item)
            } else {
                add(item.toJsonElement())
            }
        }
    }
}

/**
 * Experimental API that can be used to convert Collection
 * values to their equivalent JsonElement representation.
 */
fun Collection<Any>.toJsonElement(): JsonArray {
    // Specifically chose Collection over Iterable, bcos
    // Iterable is more widely overriden, whereas Collection
    // is more in line with our target types eg: Lists, Sets etc
    return buildJsonArray {
        for (item in this@toJsonElement) {
            if (item is JsonElement) {
                add(item)
            } else {
                add(item.toJsonElement())
            }
        }
    }
}

/**
 * Experimental API that can be used to convert Pair
 * values to their equivalent JsonElement representation.
 */
fun Pair<Any, Any>.toJsonElement(): JsonElement {
    val v1 = first.toJsonElement()
    val v2 = second.toJsonElement()
    return buildJsonObject {
        put("first", v1)
        put("second", v2)
    }
}

/**
 * Experimental API that can be used to convert Triple
 * values to their equivalent JsonElement representation.
 */
fun Triple<Any, Any, Any>.toJsonElement(): JsonElement {
    val v1 = first.toJsonElement()
    val v2 = second.toJsonElement()
    val v3 = third.toJsonElement()
    return buildJsonObject {
        put("first", v1)
        put("second", v2)
        put("third", v3)
    }
}

/**
 * Experimental API that can be used to convert Map.Entry
 * values to their equivalent JsonElement representation.
 */
fun Map.Entry<Any, Any>.toJsonElement(): JsonElement {
    val key = key.toJsonElement()
    val value = value.toJsonElement()
    return buildJsonObject {
        put("key", key)
        put("value", value)
    }
}

/**
 * Experimental API that can be used to convert most kotlin
 * primitive values to their equivalent JsonElement representation.
 * Primitive here should mean any types declared in Kotlin SDK or JVM,
 * and not brought in by an external library.
 *
 * Any unknown custom type will be representated as JsonNull
 *
 * Currently supported types
 * - String
 * - Char
 * - CharArray
 * - Double
 * - DoubleArray
 * - Float
 * - FloatArray
 * - Long
 * - LongArray
 * - Int
 * - IntArray
 * - Short
 * - ShortArray
 * - Byte
 * - ByteArray
 * - Boolean
 * - BooleanArray
 * - Unit
 * - UInt
 * - ULong
 * - UByte
 * - UShort
 * - Collection<Any>
 * - Map<String, Any>
 * - Array<Any>
 * - Pair<Any, Any>
 * - Triple<Any, Any>
 * - Map.Entry<Any, Any>
 *
 * Happy to accept more supported types in the future
 */
fun Any.toJsonElement(): JsonElement {
    when (this) {
        is Map<*, *> -> {
            val value = this as Map<String, Any>
            return value.toJsonElement()
        }
        is Array<*> -> {
            val value = this as Array<Any>
            return value.toJsonElement()
        }
        is Collection<*> -> {
            val value = this as Collection<Any>
            return value.toJsonElement()
        }
        is Pair<*, *> -> {
            val value = this as Pair<Any, Any>
            return value.toJsonElement()
        }
        is Triple<*, *, *> -> {
            val value = this as Triple<Any, Any, Any>
            return value.toJsonElement()
        }
        is Map.Entry<*, *> -> {
            val value = this as Map.Entry<Any, Any>
            return value.toJsonElement()
        }
        else -> {
            serializerFor(this::class)?.let {
                return Json.encodeToJsonElement(it, this)
            }
        }
    }
    return JsonNull
}

fun JsonObject.toBaseEvent(): BaseEvent? {
    val type = getString("type")

    return when (type) {
        "identify" -> LenientJson.decodeFromJsonElement(IdentifyEvent.serializer(), this)
        "track" -> LenientJson.decodeFromJsonElement(TrackEvent.serializer(), this)
        "screen" -> LenientJson.decodeFromJsonElement(ScreenEvent.serializer(), this)
        "group" -> LenientJson.decodeFromJsonElement(GroupEvent.serializer(), this)
        "alias" -> LenientJson.decodeFromJsonElement(AliasEvent.serializer(), this)
        else -> null
    }
}