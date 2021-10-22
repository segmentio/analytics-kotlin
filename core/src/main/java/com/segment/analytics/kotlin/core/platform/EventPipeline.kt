package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.plugins.LogType
import com.segment.analytics.kotlin.core.platform.plugins.log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import java.lang.Exception
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class EventPipeline(
    private val analytics: Analytics,
    private val logTag: String,
    apiKey: String,
    private val flushCount: Int = 20,
    private val flushIntervalInMillis: Long = 30_000, // 30s
    var apiHost: String = Constants.DEFAULT_API_HOST
) {

    private val writeChannel: Channel<String>

    private val uploadChannel: Channel<List<String>>

    private val cleanupChannel: Channel<Path>

    private val eventCount: AtomicInteger = AtomicInteger(0)

    private val httpClient: HTTPClient = HTTPClient(apiKey)

    private val storage get() = analytics.storage

    private val scope get() = analytics.analyticsScope

    private var running: Boolean

    var apiKey: String = apiKey
        set(value) {
            field = value
            httpClient.writeKey = field
        }

    init {
        running = false

        writeChannel = Channel(UNLIMITED)
        uploadChannel = Channel(UNLIMITED)
        cleanupChannel = Channel()

        registerShutdownHook()
    }

    fun put(event: String) {
        writeChannel.trySend(event)
    }

    fun flush() {
        val eventFilePaths = parseFilePaths(storage.read(Storage.Constants.Events))
        if (eventFilePaths.isNotEmpty()) {
            uploadChannel.trySend(eventFilePaths)
        }
    }

    fun start() {
        running = true
        schedule()
        write()
        upload()
        cleanup()
    }

    fun stop() {
        cleanupChannel.cancel()
        uploadChannel.cancel()
        writeChannel.cancel()
        running = false
    }

    private fun write() = scope.launch(analytics.fileIODispatcher) {
        for (event in writeChannel) {
            // write to storage
            storage.write(Storage.Constants.Events, event)

            // if flush condition met, generate paths
            if (eventCount.incrementAndGet() >= flushCount) {
                eventCount.set(0)
                val fileUrls = parseFilePaths(storage.read(Storage.Constants.Events))
                uploadChannel.send(fileUrls)
            }
        }
    }

    private fun upload() = scope.launch(analytics.networkIODispatcher) {
        for (fileUrlList in uploadChannel) {
            analytics.log("$logTag performing flush")

            for (url in fileUrlList) {
                // upload event file
                val path = Path(url)
                var shouldCleanup = true

                try {
                    httpClient.upload(apiHost, path)
                    analytics.log("$logTag uploaded $path")
                } catch (e: Exception) {
                    shouldCleanup = handleUploadException(e, path)
                }

                if (shouldCleanup) {
                    // send path for deletion
                    cleanupChannel.send(path)
                }
            }
        }
    }

    private fun cleanup() = scope.launch(Dispatchers.IO) {
        cleanupChannel.consumeEach {
            it.deleteIfExists()
        }
    }

    private fun schedule() = scope.launch(analytics.fileIODispatcher) {
        if (flushIntervalInMillis > 0) {
            while (isActive && running) {
                flush()

                // use delay to do periodical task
                // this is doable in coroutine, since delay only suspends, allowing thread to
                // do other work and then come back. see:
                // https://github.com/Kotlin/kotlinx.coroutines/issues/1632#issuecomment-545693827
                delay(flushIntervalInMillis)
            }
        }
    }

    private fun handleUploadException(e: Exception, path: Path): Boolean {
        var shouldCleanup = false
        if (e is HTTPException) {
            analytics.log("$logTag exception while uploading, ${e.message}")
            if (e.is4xx() && e.responseCode != 429) {
                // Simply log and proceed to remove the rejected payloads from the queue.
                analytics.log(
                    message = "Payloads were rejected by server. Marked for removal.",
                    type = LogType.ERROR
                )
                shouldCleanup = true
            } else {
                analytics.log(
                    message = "Error while uploading payloads",
                    type = LogType.ERROR
                )
            }
        }
        else {
            analytics.log(
                """
                    | Error uploading events from batch file
                    | fileUrl="$path"
                    | msg=${e.message}
                """.trimMargin(), type = LogType.ERROR
            )
            e.printStackTrace()
        }

        return shouldCleanup
    }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                this@EventPipeline.stop()
            }
        })
    }
}