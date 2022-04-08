package com.segment.substrata.kotlin.j2v8

import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Object
import com.segment.analytics.kotlin.core.Analytics
import com.segment.substrata.kotlin.JSExtension
import com.segment.substrata.kotlin.JSRuntime
import com.segment.substrata.kotlin.JSValue
import io.alicorn.v8.V8JavaAdapter

class J2V8Runtime(
    override val underlying: V8 = V8.createV8Runtime(),
) : JSRuntime<V8> {

    // Need to add single-thread for synchronizing all ops

    val extensions = mutableSetOf<JSExtension<V8>>()

    override fun configureRuntime() {
        for (extension in extensions) {
            extension.configureExtension()
        }
        TODO("Not yet implemented")
    }

    override fun get(key: String): JSValue {
        val keys = underlying.keys
        if (keys.isEmpty()) {
            return JSValue.JSUndefined
        }
        var result = underlying.getObject(key)
        if (result.isUndefined) {
            result = underlying.executeObjectScript(key) // Blows up when the key does not exist
        }
        return result as JSValue
    }

    override fun set(key: String, value: JSValue) {
        underlying.memScope {
            TODO("Not yet implemented")
        }
    }

    override fun call(functionName: String, params: List<JSValue>): JSValue {
        underlying.memScope {
            TODO("Not yet implemented")
        }
    }

    override fun evaluate(script: String): JSValue {
        underlying.memScope {
            TODO("Not yet implemented")
        }
    }

    override fun expose(extension: JSExtension<V8>) {
        extensions += extension
    }

//    fun <T> run(block: V8.() -> T) {
//        block(underlying)
//    }
}

internal class ConsoleExtension(override val runtime: J2V8Runtime) : JSExtension<J2V8Runtime> {
    fun log(message: String) {
        println("[JS-Console:INFO] $message")
    }

    fun error(message: String) {
        println("[JS-Console:ERROR] $message")
    }

    override val name: String = "J2V8Console"

    override fun configureExtension() {
        val underlyingRuntime = runtime.underlying
        val v8Console = V8Object(underlyingRuntime)
        v8Console.registerJavaMethod(this,
            "log",
            "log",
            arrayOf<Class<*>>(String::class.java))
        v8Console.registerJavaMethod(this,
            "error",
            "err",
            arrayOf<Class<*>>(String::class.java))
        underlyingRuntime.add("console", v8Console)
    }
}

internal class AnalyticsExtension(override val runtime: J2V8Runtime) : JSExtension<J2V8Runtime> {
    override val name: String = "AnalyticsExtension"

    override fun configureExtension() {
        val underlyingRuntime = runtime.underlying
        V8JavaAdapter.injectClass(Analytics::class.java, underlyingRuntime)
    }
}