package com.segment.analytics.destinations.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Object
import io.alicorn.v8.V8JavaAdapter
import org.junit.Test
import org.junit.runner.RunWith

class MockAnalytics(val writeKey: String) {
    fun track(event: Event) {
        println("track with Event fn")
        println("$writeKey running $event")
    }

    fun track(event: Any?) {
        println("$writeKey running $event")
    }
}

data class Event(
    val context: Map<String, String>,
    val event: String,
    val someKey: Any,
)

@RunWith(AndroidJUnit4::class)
class NativeClassTests {
    @Test
    fun testClassInjection() {
        val runtime: V8 = V8.createV8Runtime().also {
            val console = JSRuntimeBenchmarking.Console()
            val v8Console = V8Object(it)
            v8Console.registerJavaMethod(console,
                "log",
                "log",
                arrayOf<Class<*>>(String::class.java))
            v8Console.registerJavaMethod(console,
                "error",
                "err",
                arrayOf<Class<*>>(String::class.java))
            it.add("console", v8Console)
        }

        V8JavaAdapter.injectClass(Event::class.java, runtime)
        V8JavaAdapter.injectClass(MockAnalytics::class.java, runtime)

        runtime.executeVoidScript("""
            var a = new MockAnalytics("123");
            a.track({
                "context": {
                    "app": "something"
                },
                "event": "Name",
                "someKey": true
            });
            a.track(
                new Event({"foo": "val"},"click", true)            
            );
            a.track(1);
            console.log("DONE");
        """.trimIndent())
    }
}

