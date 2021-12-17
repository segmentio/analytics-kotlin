package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import java.lang.System
import java.util.concurrent.atomic.AtomicInteger

internal class EventPipeline(
    private val analytics: Analytics,
    private val logTag: String,
    apiKey: String,
    private val flushCount: Int = 20,
    private val flushIntervalInMillis: Long = 30_000, // 30s
    var apiHost: String = Constants.DEFAULT_API_HOST
) {

    private val writeChannel: Channel<String>

    private val uploadChannel: Channel<String>

    private val eventCount: AtomicInteger = AtomicInteger(0)

    private val httpClient: HTTPClient = HTTPClient(apiKey)

    private val storage get() = analytics.storage

    private val scope get() = analytics.analyticsScope

    var running: Boolean
        private set

    companion object {
        internal const val FLUSH_POISON = "#!flush"

        internal const val UPLOAD_SIG = "#!upload"
    }

    init {
        running = false

        writeChannel = Channel(UNLIMITED)
        uploadChannel = Channel(UNLIMITED)

        registerShutdownHook()
    }

    var putCount = 0
    fun put(event: String) {
        putCount++

        if (putCount == 100000) {
            analytics.log(
                "segment.test events consumed when generation finished: $count",
                type = LogType.WARNING
            )
        }

        writeChannel.trySend(event)
    }

    fun flush() {
        writeChannel.trySend(FLUSH_POISON)
    }

    fun start() {
        running = true
        schedule()
        write()
        upload()
    }

    fun stop() {
        uploadChannel.cancel()
        writeChannel.cancel()
        running = false
    }

    var count = 0
    private fun write() = scope.launch(analytics.fileIODispatcher) {
        for (event in writeChannel) {
            // write to storage
            val isPoison = (event == FLUSH_POISON)
            if (!isPoison) try {
                storage.write(Storage.Constants.Events, event)
            }
            catch (e : Exception) {
                Analytics.segmentLog("Error adding payload: $event", kind = LogFilterKind.ERROR)
            }

            // if flush condition met, generate paths
            if (eventCount.incrementAndGet() >= flushCount || isPoison) {
                eventCount.set(0)
                uploadChannel.trySend(UPLOAD_SIG)
            }

            count++

            if (count == 100000) {
                analytics.log(
                    "segment.test network calls when generation finished: $uploadCount",
                    type = LogType.WARNING
                )
                analytics.log(
                    "segment.test end time ${System.currentTimeMillis()}",
                    type = LogType.WARNING
                )
            }
        }
    }

    var uploadCount = 0
    private fun upload() = scope.launch(analytics.networkIODispatcher) {
        uploadChannel.consumeEach {
            analytics.log("$logTag performing flush")

            withContext(analytics.fileIODispatcher) {
                storage.rollover()
            }

            val fileUrlList = parseFilePaths(storage.read(Storage.Constants.Events))
            for (url in fileUrlList) {
                // upload event file
                val file = File(url)
                if (!file.exists()) continue

                var shouldCleanup = true
                try {
                    val connection = httpClient.upload(apiHost)
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
                    analytics.log("$logTag uploaded $url")
                } catch (e: Exception) {
                    shouldCleanup = handleUploadException(e, file)
                }

                if (shouldCleanup) {
                    storage.removeFile(file.path)
                }

                uploadCount++

            }
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

    private fun handleUploadException(e: Exception, file: File): Boolean {
        var shouldCleanup = false
        if (e is HTTPException) {
            analytics.log("$logTag exception while uploading, ${e.message}")
            if (e.is4xx() && e.responseCode != 429) {
                // Simply log and proceed to remove the rejected payloads from the queue.
                Analytics.segmentLog(
                    message = "Payloads were rejected by server. Marked for removal.",
                    kind = LogFilterKind.ERROR
                )
                shouldCleanup = true
            } else {
                Analytics.segmentLog(
                    message = "Error while uploading payloads",
                    kind = LogFilterKind.ERROR
                )
            }
        }
        else {
            Analytics.segmentLog(
                """
                    | Error uploading events from batch file
                    | fileUrl="${file.path}"
                    | msg=${e.message}
                """.trimMargin(), kind = LogFilterKind.ERROR
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