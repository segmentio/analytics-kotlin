import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.SegmentDestination
import com.segment.analytics.kotlin.core.platform.plugins.LogType
import com.segment.analytics.kotlin.core.platform.plugins.Logger
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.ContinuationInterceptor

fun main(args: Array<String>) {
    runBlocking {
        val dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val analytics = Analytics("gNHARErhCjBxvBErXOMrTTuwoIlxKkCg") {
            application = "MainApp"
//            flushInterval = 0
            analyticsScope = MainScope()
            analyticsDispatcher = dispatcher
            ioDispatcher = dispatcher
        }
        analytics.add(ConsoleLogger)
        analytics.track(
            "Application Started",
            buildJsonObject {
                put("app_name", "Kotlin JVM Sample CLI")
            }
        )
        analytics.track(
            "Application Started",
            buildJsonObject {
                put("app_name", "Kotlin JVM Sample CLI")
            }
        )
        analytics.flush()
        delay(30 * 1000)
        (analytics.find("Segment.io") as SegmentDestination).flushScheduler.shutdown()
    }
}

object ConsoleLogger : Logger("ConsoleLogger") {
    override fun log(type: LogType, message: String, event: BaseEvent?) {
        println("[$type] - $message::$event")
    }
}