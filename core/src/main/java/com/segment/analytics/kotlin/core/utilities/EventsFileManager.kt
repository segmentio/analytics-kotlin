package com.segment.analytics.kotlin.core.utilities

import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.io.FileOutputStream

/**
 * Responsible for storing events in a batch payload style
 *
 * Contents format
 * {
 *      "batch": [
 *      ...
 *      ],
 *      "sentAt": "2021-04-30T22:06:11"
 * }
 * Each file stored is a batch of events. When uploading events the contents of the file can be
 * sent as-is to the Segment batch upload endpoint.
 *
 * Some terms:
 * - Current open file: the most recent temporary batch file that is being used to store events
 * - Closing the file: ending the batch payload, and renaming the temporary file to a permanent one
 * - Stored file paths: list of file paths that are not temporary and match the write-key of the manager
 *
 * How it works:
 * storeEvent() will store the event in the current open file, ensuring that batch size
 * does not go over the 475KB limit. It will close the current file and create new temp ones
 * when appropriate
 *
 * When read() is called the current file is closed, and a list of stored file paths is returned
 *
 * remove() will delete the file path specified
 */
class EventsFileManager(
    val directory: File,
    private val writeKey: String,
    private val kvs: KVS
) {

    init {
        createDirectory(directory)
        registerShutdownHook()
    }

    private val fileIndexKey = "segment.events.file.index.$writeKey"

    private var os: FileOutputStream? = null

    private var curFile: File? = null

    private val semaphore = Semaphore(1)

    companion object {
        const val MAX_FILE_SIZE = 475_000 // 475KB
    }

    /**
     * closes existing file, if at capacity
     * opens a new file, if current file is full or uncreated
     * stores the event
     */
    suspend fun storeEvent(event: String) = withLock {
        var newFile = false
        var file = currentFile()
        if (!file.exists()) {
            // create it
            file.createNewFile()
            start(file)
            newFile = true
        }

        // check if file is at capacity
        if (file.length() > MAX_FILE_SIZE) {
            finish()
            // update index
            file = currentFile()
            file.createNewFile()
            start(file)
            newFile = true
        }

        var contents = ""
        if (!newFile) {
            contents += ","
        }
        contents += event
        writeToFile(contents.toByteArray(), file)
    }

    private fun incrementFileIndex(): Boolean {
        val index = kvs.getInt(fileIndexKey, 0)
        return kvs.putInt(fileIndexKey, index + 1)
    }

    /**
     * Returns a comma-separated list of file paths that are not yet uploaded
     */
    fun read(): List<String> {
        // we need to filter out .temp file, since its operating on the writing thread
        val fileList = directory.listFiles { _, name ->
            name.contains(writeKey) && !name.endsWith(".tmp")
        } ?: emptyArray()
        return fileList.map {
            it.absolutePath
        }
    }

    /**
     * deletes the file at filePath
     */
    fun remove(filePath: String): Boolean {
        return File(filePath).delete()
    }

    private fun start(file: File) {
        // start batch object and events array
        val contents = """{"batch":["""
        writeToFile(contents.toByteArray(), file)
    }

    /**
     * closes current file, and increase the index
     * so next write go to a new file
     */
    suspend fun rollover() = withLock {
        finish()
    }

    private fun finish() {
        val file = currentFile()
        if (!file.exists()) {
            // if tmp file doesnt exist then we dont need to do anything
            return
        }
        // close events array and batch object


        val contents = """],"sentAt":"${SegmentInstant.now()}","writeKey":"$writeKey"}"""
        writeToFile(contents.toByteArray(), file)
        file.renameTo(File(directory, file.nameWithoutExtension))
        os?.close()
        incrementFileIndex()
        reset()
    }

    // return the current tmp file
    private fun currentFile(): File {
        curFile = curFile ?: run {
            val index = kvs.getInt(fileIndexKey, 0)
            File(directory, "$writeKey-$index.tmp")
        }

        return curFile!!
    }

    // Atomic write to underlying file
    // TODO make atomic
    private fun writeToFile(content: ByteArray, file: File) {
        os = os ?: FileOutputStream(file, true)
        os?.run {
            write(content)
            flush()
        }
    }

    private fun reset() {
        os = null
        curFile = null
    }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                os?.close()
            }
        })
    }

    private suspend fun withLock(block: () -> Unit) {
        semaphore.acquire()
        block()
        semaphore.release()
    }
}

/**
 * Key-value store interface used by eventsFile
 */
interface KVS {
    fun getInt(key: String, defaultVal: Int): Int
    fun putInt(key: String, value: Int): Boolean
}
