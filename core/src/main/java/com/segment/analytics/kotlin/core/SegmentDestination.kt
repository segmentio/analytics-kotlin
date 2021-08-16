package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.plugins.LogType
import com.segment.analytics.kotlin.core.platform.plugins.log
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Segment Analytics plugin that is used to send events to Segment's tracking api, in the choice of region.
 * How it works
 * - Plugin receives `apiHost` settings
 * - We store events into a file with the batch api format (@link {https://segment.com/docs/connections/sources/catalog/libraries/server/http-api/#batch})
 * - We upload events on ioDispatcher using the batch api
 */
class SegmentDestination(
    private var apiKey: String,
    private val flushCount: Int = 20,
    private val flushIntervalInMillis: Long = 30 * 1000, // 30s
    private var apiHost: String = "api.segment.io/v1"
) : DestinationPlugin() {

    override val key: String = "Segment.io"
    internal val httpClient: HTTPClient = HTTPClient(apiKey)
    internal lateinit var storage: Storage
    lateinit var flushScheduler: ScheduledExecutorService
    internal val eventCount = AtomicInteger(0)

    override fun track(payload: TrackEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    override fun identify(payload: IdentifyEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    override fun group(payload: GroupEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    override fun alias(payload: AliasEvent): BaseEvent {
        enqueue(payload)
        return payload
    }

    private inline fun <reified T : BaseEvent> enqueue(payload: T) {
        // needs to be inline reified for encoding using Json
        val jsonVal = Json {
            encodeDefaults = true
        }.encodeToJsonElement(payload).jsonObject.filterNot { (k, v) ->
            // filter out empty userId and traits values
            (k == "userId" && v.jsonPrimitive.content.isBlank()) || (k == "traits" && v == emptyJsonObject)
        }

        val stringVal = Json.encodeToString(jsonVal)
        analytics.log("$key running $stringVal")
        try {
            storage.write(Storage.Constants.Events, stringVal)
            if (eventCount.incrementAndGet() >= flushCount) {
                flush()
            }
        } catch (ex: Exception) {
            analytics.log("Error adding payload", type = LogType.ERROR, event = payload)
        }
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        storage = analytics.storage

        // register timer for flush interval
        flushScheduler = Executors.newScheduledThreadPool(1)
        if (flushIntervalInMillis > 0) {
            var initialDelay = flushIntervalInMillis

            // If we have events in queue flush them
            val eventFilePaths =
                parseFilePaths(storage.read(Storage.Constants.Events))
            if (eventFilePaths.isNotEmpty()) {
                initialDelay = 0
            }

            flushScheduler.scheduleAtFixedRate(
                ::flush,
                initialDelay,
                flushIntervalInMillis,
                TimeUnit.MILLISECONDS
            )
        }
    }

    override fun update(settings: Settings) {
        settings.integrations[key]?.jsonObject?.let {
            apiKey = it["apiKey"]?.jsonPrimitive?.content ?: apiKey
            apiHost = it["apiHost"]?.jsonPrimitive?.content ?: apiHost
        }
    }

    override fun flush() {
        analytics.run {
            analyticsScope.launch(ioDispatcher) {
                performFlush()
            }
        }
    }

    private fun performFlush() {
        if (eventCount.get() < 1) {
            return
        }
        analytics.log("$key performing flush")
        val fileUrls = parseFilePaths(storage.read(Storage.Constants.Events))
        if (fileUrls.isEmpty()) {
            analytics.log("No events to upload")
            return
        }
        eventCount.set(0)
        for (fileUrl in fileUrls) {
            try {
                val connection = httpClient.upload(apiHost)
                val file = File(fileUrl)
                // flush is executed in a thread pool and file could have been deleted by another thread
                if (!file.exists()) {
                    continue
                }
                connection.outputStream?.let {
                    // Write the payloads into the OutputStream.
                    val fileInputStream = FileInputStream(file)
                    fileInputStream.copyTo(connection.outputStream)
                    fileInputStream.close()
                    connection.outputStream.close()

                    // Upload the payloads.
                    connection.close()
                }
                // Cleanup uploaded payloads
                storage.removeFile(fileUrl)
                analytics.log("$key uploaded $fileUrl")
            } catch (e: HTTPException) {
                analytics.log("$key exception while uploading, ${e.message}")
                if (e.is4xx() && e.responseCode != 429) {
                    // Simply log and proceed to remove the rejected payloads from the queue.
                    analytics.log(
                        message = "Payloads were rejected by server. Marked for removal.",
                        type = LogType.ERROR
                    )
                    storage.removeFile(fileUrl)
                } else {
                    analytics.log(
                        message = "Error while uploading payloads",
                        type = LogType.ERROR
                    )
                }
            } catch (e: Exception) {
                analytics.log(
                    """
                    | Error uploading events from batch file
                    | fileUrl="$fileUrl"
                    | msg=${e.message}
                """.trimMargin(), type = LogType.ERROR
                )
                e.printStackTrace()
            }
        }
    }

    override fun reset() {
        super.reset()
    }
}