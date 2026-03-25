package cli

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.retry.BackoffConfig
import com.segment.analytics.kotlin.core.retry.HttpConfig
import com.segment.analytics.kotlin.core.retry.RateLimitConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.Collections
import kotlin.system.exitProcess

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
    val apiHost: String? = null,
    val cdnHost: String? = null,
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
            // Create default settings to avoid CDN fetch
            val defaultSettings = Settings(
                integrations = buildJsonObject {
                    put("Segment.io", true)
                }
            )

            // Collect delivery errors from the SDK's error handler.
            // HTTPException is internal, so we parse the message ("HTTP {code}: ...").
            val deliveryErrors = Collections.synchronizedList(mutableListOf<String>())

            val analytics = Analytics(input.writeKey) {
                application = "e2e-cli"
                input.apiHost?.let { apiHost = it }
                input.cdnHost?.let { cdnHost = it }
                flushAt = input.config?.flushAt ?: 20
                flushInterval = input.config?.flushInterval ?: 30
                autoAddSegmentDestination = true
                this.defaultSettings = defaultSettings
                // Enable smart retry (rate limiting + exponential backoff)
                val maxRetries = input.config?.maxRetries ?: 100
                httpConfig = HttpConfig(
                    rateLimitConfig = RateLimitConfig(enabled = true),
                    backoffConfig = BackoffConfig(
                        enabled = true,
                        maxRetryCount = maxRetries,
                        baseBackoffInterval = 0.5
                    )
                )
                errorHandler = { error ->
                    val msg = error.message ?: error.toString()
                    if (msg.startsWith("HTTP ") || msg.startsWith("Batch dropped")) {
                        deliveryErrors.add(msg)
                    }
                }
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

            // Flush and poll until all batch files are processed.
            // Each flush() triggers an upload cycle — retries happen on
            // subsequent flush cycles (batch files persist to disk).
            val timeoutMs = (input.config?.timeout ?: 20) * 1000L
            val deadline = System.currentTimeMillis() + timeoutMs
            deliveryErrors.clear()
            analytics.flush()

            // Wait for initial batch file creation and first upload attempt.
            // Also allows time for settings to fail/timeout and SDK to fallback to defaults.
            delay(1500)

            // Then poll with adaptive intervals
            var pollInterval = 200L // Start with 200ms polls
            var pollCount = 0

            while (System.currentTimeMillis() < deadline) {
                val pending = analytics.pendingUploads()
                if (pending.isEmpty()) break

                // Clear errors before each retry cycle — only the final cycle's errors matter.
                // If a retry succeeds, no error fires and deliveryErrors stays empty.
                deliveryErrors.clear()

                // Trigger another flush cycle for retries
                analytics.flush()
                delay(pollInterval)
                pollCount++

                // Increase poll interval for longer waits (200ms -> 500ms after 5 polls = 1.3s)
                if (pollCount >= 5 && pollInterval < 500) {
                    pollInterval = 500
                }
            }

            val remaining = analytics.pendingUploads()
            output = if (remaining.isNotEmpty()) {
                CLIOutput(
                    success = false,
                    error = "Delivery incomplete: ${remaining.size} batch file(s) still pending"
                )
            } else if (deliveryErrors.isNotEmpty()) {
                // Events were dropped (not delivered) — pendingUploads is empty because
                // the SDK deleted the batch file, but errorHandler captured the HTTP error.
                CLIOutput(
                    success = false,
                    error = "Delivery failed: ${deliveryErrors.joinToString("; ")}"
                )
            } else {
                CLIOutput(success = true, sentBatches = 1)
            }
        }
    } catch (e: Exception) {
        output = CLIOutput(success = false, error = e.message ?: e.toString())
    }

    println(Json.encodeToString(CLIOutput.serializer(), output))

    // Force exit since Analytics has background threads
    exitProcess(if (output.success) 0 else 1)
}

fun sendEvent(analytics: Analytics, event: JsonObject) {
    val type = event["type"]?.jsonPrimitive?.content
        ?: throw IllegalArgumentException("Event missing 'type' field")
    val userId = event["userId"]?.jsonPrimitive?.content ?: ""
    val anonymousId = event["anonymousId"]?.jsonPrimitive?.content ?: ""
    val messageId = event["messageId"]?.jsonPrimitive?.content
    val timestamp = event["timestamp"]?.jsonPrimitive?.content
    val traits = event["traits"]?.jsonObject ?: emptyJsonObject
    val properties = event["properties"]?.jsonObject ?: emptyJsonObject
    val eventName = event["event"]?.jsonPrimitive?.content
    val name = event["name"]?.jsonPrimitive?.content
    val category = event["category"]?.jsonPrimitive?.content
    val groupId = event["groupId"]?.jsonPrimitive?.content
    val previousId = event["previousId"]?.jsonPrimitive?.content
    val context = event["context"]?.jsonObject
    val integrations = event["integrations"]?.jsonObject

    when (type) {
        "identify" -> analytics.identify(userId, traits)
        "track" -> analytics.track(eventName ?: "Unknown Event", properties)
        "page" -> analytics.screen(name ?: "Unknown Page", properties = properties) // Kotlin SDK uses screen for both
        "screen" -> analytics.screen(name ?: "Unknown Screen", properties = properties)
        "alias" -> analytics.alias(userId)
        "group" -> analytics.group(groupId ?: "", traits)
        else -> throw IllegalArgumentException("Unknown event type: $type")
    }
}
