@file:JvmName("JsonUtils")
package com.segment.analytics.kotlin.core.utilities

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

val EncodeDefaultsJson = Json {
    encodeDefaults = true
}

val LenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

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
fun JsonObject.getBoolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

// Utility function to retrieve a string value from a jsonObject
fun JsonObject.getString(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

// Utility function to retrieve a double value from a jsonObject
fun JsonObject.getDouble(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

// Utility function to retrieve a int value from a jsonObject
fun JsonObject.getInt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

// Utility function to retrieve a long value from a jsonObject
fun JsonObject.getLong(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

// Utility function to retrieve a string set (from jsonArray) from a jsonObject
fun JsonObject.getStringSet(key: String): Set<String>? {
    val value = this[key] as? JsonArray
    return value?.map { it.toContent().toString() }?.toSet()
}

// Utility function to retrieve a list of Map<String, Any?> from a jsonObject, skips any non-map elements
fun JsonObject.getMapList(key: String): List<Map<String, Any?>>? {
    val value = this[key] as? JsonArray
    return value?.filterIsInstance<JsonObject>()?.map { it.jsonObject.toContent() }
}

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
