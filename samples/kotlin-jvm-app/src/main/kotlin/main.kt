import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.checkSettings
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Date
import kotlin.coroutines.ContinuationInterceptor
import kotlin.system.measureTimeMillis


fun main(args: Array<String>) {
    val destinationCount = 10
    val eventCount = 50000
    runBlocking {
        val dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val analytics = Analytics("gNHARErhCjBxvBErXOMrTTuwoIlxKkCg") {
            application = "MainApp"
            analyticsScope = MainScope()
            analyticsDispatcher = dispatcher
            ioDispatcher = dispatcher
            autoAddSegmentDestination = false
        }
        val destinationList = (1..destinationCount).map {
            object : DestinationPlugin() {
                override val name: String = "FooDestination$it"
            }
        }
        destinationList.forEach {
            analytics.add(it)
        }
        analytics.checkSettings()

        val trackEvent = TrackEvent(
            event = "clicked",
            properties = buildJsonObject { put("behaviour", "good") })
            .apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = Date(0).toInstant().toString()
            }
        val identifyEvent = IdentifyEvent(
            userId = "abc-123",
            traits = buildJsonObject { put("email", "123@abc.com") }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        val screenEvent = ScreenEvent(
            name = "LoginFragment",
            properties = buildJsonObject {
                put("startup", false)
                put("parent", "MainActivity")
            },
            category = "signup_flow"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        val times = (1..10).map {
            measureTimeMillis {
                for (i in 1..eventCount) {
                    analytics.process(trackEvent)
                }
            }
        }
        times.forEach {
            println("Executed $eventCount tracks in ${it}ms")
        }
        println("Average time ${times.average()}ms")
    }
}