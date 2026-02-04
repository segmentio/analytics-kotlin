package cli

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.emptyJsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class CLIOutput(
    val success: Boolean,
    val error: String? = null,
    val sentBatches: Int = 0
)

@Serializable
data class CLIConfig(
    val flushAt: Int? = null,
    val flushInterval: Int? = null,
    val maxRetries: Int? = null,
    val timeout: Int? = null
)

@Serializable
data class EventSequence(
    val delayMs: Long = 0,
    val events: List<JsonObject>
)

@Serializable
data class CLIInput(
    val writeKey: String,
    val apiHost: String,
    val sequences: List<EventSequence>,
    val config: CLIConfig? = null
)

fun main(args: Array<String>) {
    var output = CLIOutput(success = false, error = "Unknown error")

    try {
        // Parse --input argument
        val inputIndex = args.indexOf("--input")
        if (inputIndex == -1 || inputIndex + 1 >= args.size) {
            throw IllegalArgumentException("Missing required --input argument")
        }

        val inputJson = args[inputIndex + 1]
        val input = Json.decodeFromString<CLIInput>(inputJson)

        runBlocking {
            val analytics = Analytics(input.writeKey) {
                application = "e2e-cli"
                apiHost = input.apiHost
                flushAt = input.config?.flushAt ?: 20
                flushInterval = input.config?.flushInterval ?: 30
                autoAddSegmentDestination = true
            }

            // Process event sequences
            for (seq in input.sequences) {
                if (seq.delayMs > 0) {
                    delay(seq.delayMs)
                }

                for (event in seq.events) {
                    sendEvent(analytics, event)
                }
            }

            // Flush and wait
            analytics.flush()
            // Give time for async flush to complete
            delay(5000)
        }

        output = CLIOutput(success = true, sentBatches = 1)
    } catch (e: Exception) {
        output = CLIOutput(success = false, error = e.message ?: e.toString())
    }

    println(Json.encodeToString(CLIOutput.serializer(), output))
}

fun sendEvent(analytics: Analytics, event: JsonObject) {
    val type = event["type"]?.jsonPrimitive?.content
        ?: throw IllegalArgumentException("Event missing 'type' field")
    val userId = event["userId"]?.jsonPrimitive?.content ?: ""
    val anonymousId = event["anonymousId"]?.jsonPrimitive?.content ?: ""
    val traits = event["traits"]?.jsonObject ?: emptyJsonObject
    val properties = event["properties"]?.jsonObject ?: emptyJsonObject
    val eventName = event["event"]?.jsonPrimitive?.content
    val name = event["name"]?.jsonPrimitive?.content
    val groupId = event["groupId"]?.jsonPrimitive?.content
    val previousId = event["previousId"]?.jsonPrimitive?.content

    when (type) {
        "identify" -> analytics.identify(userId, traits)
        "track" -> analytics.track(eventName ?: "Unknown Event", properties)
        "page" -> analytics.page(name ?: "Unknown Page", properties = properties)
        "screen" -> analytics.screen(name ?: "Unknown Screen", properties = properties)
        "alias" -> analytics.alias(userId, previousId)
        "group" -> analytics.group(groupId ?: "", traits)
        else -> throw IllegalArgumentException("Unknown event type: $type")
    }
}
