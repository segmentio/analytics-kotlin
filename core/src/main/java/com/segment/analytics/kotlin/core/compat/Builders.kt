package com.segment.analytics.kotlin.core.compat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * This class is an extension to {@link JsonElementBuilders kotlinx.serialization.json.JsonElementBuilders},
 * which provides a series of builder methods to help build Kotlin {@link JsonElement kotlinx.serialization.json.JsonElement}.
 * The builders in this class is merely a wrapper but with methods that brings Java compatibility.
 * It's strongly recommended to remain using Kotlin's {@link JsonElementBuilders kotlinx.serialization.json.JsonElementBuilders}
 * in a kotlin based project.
 */
class Builders {

    companion object {

        /**
         * Builder function that takes a lambda (a Function in Java) to build a Kotlin JsonObject
         * @param action a lambda expression or a Function in Java
         * @return a Kotlin JsonObject
         */
        @JvmStatic
        fun buildJsonObjectFunc(action: JsonObjectBuilder.() -> Unit): JsonObject {
            val builder = JsonObjectBuilder()
            builder.action()
            return builder.build()
        }

        /**
         * Builder function that takes a Consumer in Java to build a Kotlin JsonObject. This method
         * is only available to API 24 or above in Android. If below 24, please consider to use
         * @see buildJsonObjectFunc
         * @param action a Consumer in Java
         * @return a Kotlin JsonObject
         */
        @JvmStatic
        @Suppress("NewApi")
        fun buildJsonObject(action: java.util.function.Consumer<in JsonObjectBuilder>): JsonObject =
            buildJsonObjectFunc(action::accept)

        /**
         * Builder function that takes a lambda (a Function in Java) to build a Kotlin JsonArray
         * @param action a lambda expression or a Function in Java
         * @return a Kotlin JsonArray
         */
        @JvmStatic
        fun buildJsonArrayFunc(action: JsonArrayBuilder.() -> Unit): JsonArray {
            val builder = JsonArrayBuilder()
            builder.action()
            return builder.build()
        }

        /**
         * Builder function that takes a Consumer in Java to build a Kotlin JsonArray. This method
         * is only available to API 24 or above in Android. If below 24, please consider to use
         * @see buildJsonArrayFunc
         * @param action a Consumer in Java
         * @return a Kotlin JsonArray
         */
        @JvmStatic
        @Suppress("NewApi")
        fun buildJsonArray(action: java.util.function.Consumer<in JsonArrayBuilder>): JsonArray =
            buildJsonArrayFunc(action::accept)

    }

    /**
     * DSL builder for Kotlin JsonObject
     */
    @JsonDslMarker
    class JsonObjectBuilder {
        private val content: MutableMap<String, JsonElement> = linkedMapOf()

        /**
         * add a JsonElement to current JsonObject with a given key.
         * returns the builder instance
         */
        fun put(key: String, element: JsonElement): JsonObjectBuilder = apply { content[key] = element }

        /**
         * add a boolean value to current JsonObject with a given key.
         * returns the builder instance
         */
        fun put(key: String, value: Boolean?): JsonObjectBuilder = apply { put(key, JsonPrimitive(value)) }

        /**
         * add a numeric value to current JsonObject with a given key.
         * returns the builder instance
         */
        fun put(key: String, value: Number?) : JsonObjectBuilder = apply { put(key, JsonPrimitive(value)) }

        /**
         * add a string to current JsonObject with a given key.
         * returns the builder instance
         */
        fun put(key: String, value: String?) : JsonObjectBuilder = apply { put(key, JsonPrimitive(value)) }

        /**
         * add another JsonObject to current JsonObject with a given key. the other JsonObject is
         * built through a Consumer in Java
         * returns the builder instance
         */
        fun putJsonObject(key: String, action: java.util.function.Consumer<in JsonObjectBuilder>): JsonObjectBuilder = apply{ put(key, buildJsonObject(action)) }

        /**
         * add a JsonArray to current JsonObject with a given key. the JsonArray is built through a
         * Consumer in Java
         * returns the builder instance
         */
        fun putJsonArray(key: String, action: java.util.function.Consumer<in JsonArrayBuilder>): JsonObjectBuilder = apply{ put(key, buildJsonArray(action)) }

        /**
         * build the content in JsonObject form
         */
        internal fun build(): JsonObject = JsonObject(content)
    }

    /**
     * add another JsonObject to current JsonObject with a given key. the other JsonObject is
     * built through a lambda expression or a Function in Java
     * returns the builder instance
     */
    fun JsonObjectBuilder.putJsonObject(key: String, action: JsonObjectBuilder.() -> Unit): JsonObjectBuilder = apply{ put(key, buildJsonObject(action)) }

    /**
     * add a JsonArray to current JsonObject with a given key. the JsonArray is built through a
     * lambda expression or a Function in Java
     * returns the builder instance
     */
    fun JsonObjectBuilder.putJsonArray(key: String, action: JsonArrayBuilder.() -> Unit): JsonObjectBuilder = apply{ put(key, buildJsonArray(action)) }

    /**
     * DSL builder for Kotlin JsonArray
     */
    @JsonDslMarker
    class JsonArrayBuilder {
        private val content: MutableList<JsonElement> = mutableListOf()

        /**
         * add a JsonElement to current JsonArray.
         * returns the builder instance
         */
        fun add(element: JsonElement): JsonArrayBuilder = apply { content += element }

        /**
         * add a boolean value to current JsonArray.
         * returns the builder instance
         */
        fun add(element: Boolean?): JsonArrayBuilder = apply { add(JsonPrimitive(element)) }

        /**
         * add a numeric value to current JsonArray.
         * returns the builder instance
         */
        fun add(element: Number?): JsonArrayBuilder = apply { add(JsonPrimitive(element)) }

        /**
         * add a string to current JsonArray.
         * returns the builder instance
         */
        fun add(element: String?): JsonArrayBuilder = apply { add(JsonPrimitive(element)) }

        /**
         * add a JsonObject to current JsonArray.the JsonObject is
         * built through a Consumer in Java
         * returns the builder instance
         */
        fun addJsonObject(action: java.util.function.Consumer<in JsonObjectBuilder>): JsonArrayBuilder = apply{ add(buildJsonObject(action)) }

        /**
         * add another JsonArray to current JsonArray.the other JsonArray is
         * built through a Consumer in Java
         * returns the builder instance
         */
        fun addJsonArray(action: java.util.function.Consumer<in JsonArrayBuilder>): JsonArrayBuilder = apply{ add(buildJsonArray(action)) }

        /**
         * build the content in JsonArray form
         */
        internal fun build(): JsonArray = JsonArray(content)
    }

    /**
     * add a JsonObject to current JsonArray. the JsonObject is
     * built through a lambda expression or a Function in Java
     * returns the builder instance
     */
    fun JsonArrayBuilder.addJsonObject(action: JsonObjectBuilder.() -> Unit): JsonArrayBuilder = apply{ add(buildJsonObject(action)) }

    /**
     * add another JsonArray to current JsonArray. the other JsonArray is
     * built through a lambda expression or a Function in Java
     * returns the builder instance
     */
    fun JsonArrayBuilder.addJsonArray(action: JsonArrayBuilder.() -> Unit): JsonArrayBuilder = apply{ add(buildJsonArray(action)) }

    @DslMarker
    internal annotation class JsonDslMarker
}