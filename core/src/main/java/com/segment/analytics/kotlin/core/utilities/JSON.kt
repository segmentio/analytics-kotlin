package com.segment.analytics.kotlin.core.utilities

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

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
fun JsonObject.getBoolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

// Utility function to retrieve a string value from a jsonObject
fun JsonObject.getString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

// Utility function to retrieve a double value from a jsonObject
fun JsonObject.getDouble(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

// Utility function to retrieve a int value from a jsonObject
fun JsonObject.getInt(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

// Utility function to retrieve a string set (from jsonArray) from a jsonObject
fun JsonObject.getStringSet(key: String): Set<String>? =
    this[key]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()

// Utility function to retrieve a map set from a jsonObject
fun JsonObject.getMapSet(key: String): Set<Map<String, Any>>? {

    val returnList: MutableList<Map<String, Any>> = mutableListOf()

    this[key]?.jsonObject?.let { jsonMap ->
        for ((mapKey, value) in jsonMap) {
            returnList.add(mapOf(mapKey to value))
        }
    }

    return if (returnList.isNotEmpty())
        returnList.toSet()
    else
        null
}
