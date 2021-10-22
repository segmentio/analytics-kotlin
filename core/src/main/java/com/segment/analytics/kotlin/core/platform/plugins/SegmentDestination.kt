package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Constants.DEFAULT_API_HOST
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.HTTPClient
import com.segment.analytics.kotlin.core.HTTPException
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.parseFilePaths
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.LogFilterKind
import com.segment.analytics.kotlin.core.platform.plugins.logger.SegmentLog
import com.segment.analytics.kotlin.core.platform.plugins.logger.log
import com.segment.analytics.kotlin.core.platform.plugins.logger.segmentLog
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sun.rmi.runtime.Log
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class SegmentSettings(
    var apiKey: String,
    var apiHost: String = DEFAULT_API_HOST,
)

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
    private var apiHost: String = DEFAULT_API_HOST
) : DestinationPlugin() {

    override val key: String = "Segment.io"
    internal val httpClient: HTTPClient = HTTPClient(apiKey)
    internal lateinit var storage: WeakReference<Storage>
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
        val jsonVal = EncodeDefaultsJson.encodeToJsonElement(payload)
            .jsonObject.filterNot { (k, v) ->
                // filter out empty userId and traits values
                (k == "userId" && v.jsonPrimitive.content.isBlank()) || (k == "traits" && v == emptyJsonObject)
            }

        val stringVal = Json.encodeToString(jsonVal)
        analytics.log("$key running $stringVal")
        try {
            storage.get()?.write(Storage.Constants.Events, stringVal)
            if (eventCount.incrementAndGet() >= flushCount) {
                flush()
            }
        } catch (ex: Exception) {
            Analytics.segmentLog("Error adding payload", kind = LogFilterKind.ERROR)
        }
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        storage = WeakReference(analytics.storage)

        // register timer for flush interval
        flushScheduler = Executors.newScheduledThreadPool(1)
        if (flushIntervalInMillis > 0) {
            var initialDelay = flushIntervalInMillis

            // If we have events in queue flush them
            val eventFilePaths =
                parseFilePaths(storage.get()?.read(Storage.Constants.Events))
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

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        if (settings.isDestinationEnabled(key)) {
            settings.destinationSettings<SegmentSettings>(key)?.let {
                apiKey = it.apiKey
                apiHost = it.apiHost
            }
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
        val fileUrls = parseFilePaths(storage.get()?.read(Storage.Constants.Events))
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
                storage.get()?.removeFile(fileUrl)
                analytics.log("$key uploaded $fileUrl")
            } catch (e: HTTPException) {
                Analytics.segmentLog("$key exception while uploading, ${e.message}", kind = LogFilterKind.ERROR)
                if (e.is4xx() && e.responseCode != 429) {
                    // Simply log and proceed to remove the rejected payloads from the queue.
                    Analytics.segmentLog(
                        message = "Payloads were rejected by server. Marked for removal.",
                        kind = LogFilterKind.ERROR
                    )
                    storage.get()?.removeFile(fileUrl)
                } else {
                    Analytics.segmentLog(
                        message = "Error while uploading payloads",
                        kind = LogFilterKind.ERROR
                    )
                }
            } catch (e: Exception) {
                Analytics.segmentLog(
                    """
                    | Error uploading events from batch file
                    | fileUrl="$fileUrl"
                    | msg=${e.message}
                """.trimMargin(), kind = LogFilterKind.ERROR
                )
                e.printStackTrace()
            }
        }
    }
}