import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.plugins.SegmentDestination
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main(args: Array<String>) {
    runBlocking {
        val analytics = Analytics("gNHARErhCjBxvBErXOMrTTuwoIlxKkCg") {
            application = "MainApp"
        }
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
        // Waiting 30s to ensure auto-flush on Segment.io destination goes through
        delay(30L * 1000)
    }
}
