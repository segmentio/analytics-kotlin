package com.segment.analytics.kotlin.android.utilities

import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject

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