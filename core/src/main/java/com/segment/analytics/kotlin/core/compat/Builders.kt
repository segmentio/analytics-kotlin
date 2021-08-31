package com.segment.analytics.kotlin.core.compat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class Builders {

    companion object {
        @JvmStatic
        fun buildJsonObjectFunc(action: JsonObjectBuilder.() -> Unit): JsonObject {
            val builder = JsonObjectBuilder()
            builder.action()
            return builder.build()
        }

        @JvmStatic
        @Suppress("NewApi")
        fun buildJsonObject(action: java.util.function.Consumer<in JsonObjectBuilder>): JsonObject =
            buildJsonObjectFunc(action::accept)

        @JvmStatic
        fun buildJsonArrayFunc(action: JsonArrayBuilder.() -> Unit): JsonArray {
            val builder = JsonArrayBuilder()
            builder.action()
            return builder.build()
        }

        @JvmStatic
        @Suppress("NewApi")
        fun buildJsonArray(action: java.util.function.Consumer<in JsonArrayBuilder>): JsonArray =
            buildJsonArrayFunc(action::accept)

    }

    @JsonDslMarker
    class JsonObjectBuilder {
        private val content: MutableMap<String, JsonElement> = linkedMapOf()

        fun put(key: String, element: JsonElement): JsonObjectBuilder = apply { content[key] = element }

        fun put(key: String, value: Boolean?): JsonObjectBuilder = apply { put(key, JsonPrimitive(value)) }

        fun put(key: String, value: Number?) : JsonObjectBuilder = apply { put(key, JsonPrimitive(value)) }

        fun put(key: String, value: String?) : JsonObjectBuilder = apply { put(key, JsonPrimitive(value)) }

        @Suppress("NewApi")
        fun putJsonObject(key: String, action: java.util.function.Consumer<in JsonObjectBuilder>): JsonObjectBuilder = apply{ put(key, buildJsonObject(action)) }

        @Suppress("NewApi")
        fun putJsonArray(key: String, action: java.util.function.Consumer<in JsonArrayBuilder>): JsonObjectBuilder = apply{ put(key, buildJsonArray(action)) }

        internal fun build(): JsonObject = JsonObject(content)
    }

    fun JsonObjectBuilder.putJsonObject(key: String, action: JsonObjectBuilder.() -> Unit): JsonObjectBuilder = apply{ put(key, buildJsonObject(action)) }

    fun JsonObjectBuilder.putJsonArray(key: String, action: JsonArrayBuilder.() -> Unit): JsonObjectBuilder = apply{ put(key, buildJsonArray(action)) }

    @JsonDslMarker
    class JsonArrayBuilder {
        private val content: MutableList<JsonElement> = mutableListOf()

        fun add(element: JsonElement): JsonArrayBuilder = apply { content += element }

        fun add(element: Boolean?): JsonArrayBuilder = apply { add(JsonPrimitive(element)) }

        fun add(element: Number?): JsonArrayBuilder = apply { add(JsonPrimitive(element)) }

        fun add(element: String?): JsonArrayBuilder = apply { add(JsonPrimitive(element)) }

        @Suppress("NewApi")
        fun addJsonObject(action: java.util.function.Consumer<in JsonObjectBuilder>): JsonArrayBuilder = apply{ add(buildJsonObject(action)) }

        @Suppress("NewApi")
        fun addJsonArray(action: java.util.function.Consumer<in JsonArrayBuilder>): JsonArrayBuilder = apply{ add(buildJsonArray(action)) }

        internal fun build(): JsonArray = JsonArray(content)
    }

    fun JsonArrayBuilder.addJsonObject(action: JsonObjectBuilder.() -> Unit): JsonArrayBuilder = apply{ add(buildJsonObject(action)) }

    fun JsonArrayBuilder.addJsonArray(action: JsonArrayBuilder.() -> Unit): JsonArrayBuilder = apply{ add(buildJsonArray(action)) }

    @DslMarker
    internal annotation class JsonDslMarker
}