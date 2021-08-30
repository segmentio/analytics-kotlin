package com.segment.analytics.kotlin.core.compat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class XElementBuilders {

    companion object {
        @JvmStatic
        fun buildXsonObjectFunc(action: XsonObjectBuilder.() -> Unit): JsonObject {
            val builder = XsonObjectBuilder()
            builder.action()
            return builder.build()
        }

        @JvmStatic
        @Suppress("NewApi")
        fun buildXsonObject(action: java.util.function.Consumer<in XsonObjectBuilder>): JsonObject =
            buildXsonObjectFunc(action::accept)

        @JvmStatic
        fun buildXsonArrayFunc(action: XsonArrayBuilder.() -> Unit): JsonArray {
            val builder = XsonArrayBuilder()
            builder.action()
            return builder.build()
        }

        @JvmStatic
        @Suppress("NewApi")
        fun buildXsonArray(action: java.util.function.Consumer<in XsonArrayBuilder>): JsonArray =
            buildXsonArrayFunc(action::accept)

    }

    @XsonDslMarker
    class XsonObjectBuilder {
        private val content: MutableMap<String, JsonElement> = linkedMapOf()

        fun put(key: String, element: JsonElement): XsonObjectBuilder = apply { content[key] = element }

        fun put(key: String, value: Boolean?): XsonObjectBuilder = apply { put(key, JsonPrimitive(value)) }

        fun put(key: String, value: Number?) : XsonObjectBuilder = apply { put(key, JsonPrimitive(value)) }

        fun put(key: String, value: String?) : XsonObjectBuilder = apply { put(key, JsonPrimitive(value)) }

        @Suppress("NewApi")
        fun putXsonObject(key: String, action: java.util.function.Consumer<in XsonObjectBuilder>): XsonObjectBuilder = apply{ put(key, buildXsonObject(action)) }

        @Suppress("NewApi")
        fun putXsonArray(key: String, action: java.util.function.Consumer<in XsonArrayBuilder>): XsonObjectBuilder = apply{ put(key, buildXsonArray(action)) }

        internal fun build(): JsonObject = JsonObject(content)
    }

    fun XsonObjectBuilder.putXsonObject(key: String, action: XsonObjectBuilder.() -> Unit): XsonObjectBuilder = apply{ put(key, buildXsonObject(action)) }

    fun XsonObjectBuilder.putXsonArray(key: String, action: XsonArrayBuilder.() -> Unit): XsonObjectBuilder = apply{ put(key, buildXsonArray(action)) }

    @XsonDslMarker
    class XsonArrayBuilder {
        private val content: MutableList<JsonElement> = mutableListOf()

        fun add(element: JsonElement): XsonArrayBuilder = apply { content += element }

        fun add(element: Boolean?): XsonArrayBuilder = apply { add(JsonPrimitive(element)) }

        fun add(element: Number?): XsonArrayBuilder = apply { add(JsonPrimitive(element)) }

        fun add(element: String?): XsonArrayBuilder = apply { add(JsonPrimitive(element)) }

        @Suppress("NewApi")
        fun XsonArrayBuilder.addXsonObject(action: java.util.function.Consumer<in XsonObjectBuilder>): XsonArrayBuilder = apply{ add(buildXsonObject(action)) }

        @Suppress("NewApi")
        fun XsonArrayBuilder.addXsonArray(action: java.util.function.Consumer<in XsonArrayBuilder>): XsonArrayBuilder = apply{ add(buildXsonArray(action)) }

        internal fun build(): JsonArray = JsonArray(content)
    }

    fun XsonArrayBuilder.addXsonObject(action: XsonObjectBuilder.() -> Unit): XsonArrayBuilder = apply{ add(buildXsonObject(action)) }

    fun XsonArrayBuilder.addXsonArray(action: XsonArrayBuilder.() -> Unit): XsonArrayBuilder = apply{ add(buildXsonArray(action)) }

    @DslMarker
    internal annotation class XsonDslMarker
}