package com.segment.substrata.kotlin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// Figure out how dataBridge is gonna fit in? maybe an extension?

interface JSExtension<T> {
    val name: String
    val runtime: T

    fun configureExtension()
}

/**
 * We cant add extensions to existing types like in swift, so its up to the user to provide
 * serializer/deserializer for custom types. We will provide all primitive ones.
 */
sealed interface JSValue {
    class JSString(val content: String) : JSValue

    class JSBool(val content: Boolean) : JSValue

    abstract class JSNumber(val content: Number) : JSValue
    class JSInt(content: Int) : JSNumber(content)
    class JSFloat(content: Float) : JSNumber(content)

    class JSObject(val content: JsonObject) : JSValue

    class JSArray(val content: JsonArray) : JSValue

    /*
        fun foo() {
            val x = JSValue.JSFunction { params -> params[0] }
            x(JSValue.JSUndefined)
        }
     */
    class JSFunction(val fn: (params: List<JSValue>) -> JSValue) : JSValue {
        // Non-null return type bcos JSValue.JSUndefined represents null
        operator fun invoke(vararg params: JSValue): JSValue {
            return fn(params.toList())
        }
    }

    object JSUndefined : JSValue
}

interface JSRuntime<T> {
    val underlying: T

    /**
     * Not everything related to configuration can happen in the constructor, this block allows the
     * runtime to be configured at a later point when all resources are available
     */
    fun configureRuntime()

    // Retrieve a value from the runtime
    fun get(key: String): JSValue

    // Set a key-value in the runtime
    fun set(key: String, value: JSValue)

    // Call a JS function with given params
    fun call(functionName: String, params: List<JSValue> = emptyList()): JSValue

    // Evaluate a script
    fun evaluate(script: String): JSValue

    // Add extensions to the runtime, used to add custom features to the runtime
    fun expose(extension: JSExtension<T>) // Brandon has this, but not sure what its supposed to do? is this the "registration" function?
}