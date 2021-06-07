package com.segment.analytics.utilities

import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject

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

// Transform kotlinx.JsonArray to org.json.JSONArray
fun List<JsonElement>.toJSONArray(): JSONArray {
    val constructed = JSONArray()
    for (item in this) {
        when (item) {
            is JsonPrimitive -> {
                constructed.put(item.toContent())
            }
            is JsonObject -> {
                constructed.put(item.toJSONObject())
            }
            is JsonArray -> {
                constructed.put(item.toJSONArray())
            }
        }
    }
    return constructed
}

// Transform kotlinx.JsonArray to org.json.JSONArray
fun Map<String, JsonElement>.toJSONObject(): JSONObject {
    val constructed = JSONObject()
    for ((k, v) in entries) {
        when (v) {
            is JsonPrimitive -> {
                constructed.put(k, v.toContent())
            }
            is JsonObject -> {
                constructed.put(k, v.toJSONObject())
            }
            is JsonArray -> {
                constructed.put(k, v.toJSONArray())
            }
        }
    }
    return constructed
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
    this.floatOrNull?.let {
        return it
    }
    this.doubleOrNull?.let {
        return it
    }
    return contentOrNull
}

// Utility function to retrieve a boolean value from a jsonObject
fun JsonObject.getBoolean(key: String): Boolean? = this[key]?.jsonPrimitive?.boolean

// Utility function to retrieve a string value from a jsonObject
fun JsonObject.getString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

// Utility function to retrieve a string set (from jsonArray) from a jsonObject
fun JsonObject.getStringSet(key: String): Set<String>? =
    this[key]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
