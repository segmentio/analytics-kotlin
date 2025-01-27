package com.segment.analytics.kotlin.core.utilities

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.StringBuilder
import java.util.concurrent.ConcurrentHashMap

/**
 *     The protocol of how events are read and stored.
 *     Implement this interface if you wanna your events
 *     to be read and stored in the way you want (for
 *     example: from/to remote server, from/to local database
 *     from/to encrypted source).
 *     By default, we have implemented read and store events
 *     from/to memory and file storage.
 *
 *     A stream is defined as something that contains a batch of
 *     events. It can be in the form of any of the following:
 *      * a file
 *      * an in-memory entry
 *      * a table entry in database
 */
interface EventStream {
    /**
     * Length of current stream
     */
    val length: Long

    /**
     * Check if a stream is opened
     */
    val isOpened: Boolean

    /**
     * Open the stream with the given name. Creates a new one if not already exists.
     *
     * @param file name of the stream
     * @return true if a new stream is created
     */
    fun openOrCreate(file: String): Boolean

    /**
     * Append content to the opening stream
     *
     * @param content Content to append
     */
    fun write(content: String)

    /**
     * Read the list of streams in directory
     * @return a list of stream names in directory
     */
    fun read(): List<String>

    /**
     * Remove the stream with the given name
     *
     * @param file name of stream to be removed
     */
    fun remove(file: String)

    /**
     * Close the current opening stream without finish it,
     * so that the stream can be opened for future appends.
     */
    fun close()

    /**
     * Close and finish the current opening stream.
     * Pass a withRename closure if you want to distinguish completed
     * streams from ongoing stream
     *
     * @param withRename a callback that renames a finished stream
     */
    fun finishAndClose(withRename: ((name: String) -> String)? = null)

    /**
     * Read the stream with the given name as an InputStream.
     * Needed for HTTPClient to upload data
     *
     * @param source the full name of a stream
     */
    fun readAsStream(source: String): InputStream?
}

open class InMemoryEventStream: EventStream {
    protected val directory = ConcurrentHashMap<String, InMemoryFile>()

    protected open var currFile: InMemoryFile? = null

    override val length: Long
        get() = (currFile?.length ?: 0).toLong()
    override val isOpened: Boolean
        get() = currFile != null

    override fun openOrCreate(file: String): Boolean {
        currFile?.let {
            if (it.name != file) {
                // the given file is different than the current one
                // close the current one first
                close()
            }
        }

        var newFile = false
        if (currFile == null) {
            newFile = !directory.containsKey(file)
            currFile = if (newFile) InMemoryFile(file) else directory[file]
        }

        currFile?.let { directory[file] = it }

        return newFile
    }

    override fun write(content: String) {
        currFile?.write(content)
    }

    override fun read(): List<String> = directory.keys().toList()

    override fun remove(file: String) {
        directory.remove(file)
    }

    override fun close() {
        currFile = null
    }

    override fun finishAndClose(withRename: ((name: String) -> String)?) {
        currFile?.let {
            withRename?.let { rename ->
                directory.remove(it.name)
                directory[rename(it.name)] = it
            }
            currFile = null
        }

    }

    override fun readAsStream(source: String): InputStream? = directory[source]?.toStream()

    class InMemoryFile(val name: String) {
        val fileStream: StringBuilder = StringBuilder()

        val length: Int
            get() = fileStream.length

        fun write(content: String) = fileStream.append(content)

        fun toStream() = fileStream.toString().byteInputStream()
    }
}

open class FileEventStream(
    internal val directory: File
): EventStream {

    init {
        createDirectory(directory)
        registerShutdownHook()
    }

    protected open var fs: FileOutputStream? = null

    protected open var currFile: File? = null

    override val length: Long
        get() = currFile?.length() ?: 0
    override val isOpened: Boolean
        get() = currFile != null && fs != null

    override fun openOrCreate(file: String): Boolean {
        currFile?.let {
            if (!it.name.endsWith(file)) {
                close()
            }
        }

        if (currFile == null) {
            currFile = File(directory, file)
        }

        var newFile = false
        currFile?.let {
            if (!it.exists()) {
                it.createNewFile()
                newFile = true
            }

            fs = fs ?: FileOutputStream(it, true)
        }

        return newFile
    }

    override fun write(content: String) {
        fs?.run {
            write(content.toByteArray())
            flush()
        }
    }

    override fun read(): List<String> = (directory.listFiles() ?: emptyArray()).map { it.absolutePath }

    /**
     * Remove the given file from disk
     *
     * NOTE: file string has to be the full path of the file
     *
     * @param file full path of the file to be deleted
     */
    override fun remove(file: String) {
        File(file).delete()
    }

    override fun close() {
        fs?.close()
        fs = null
        currFile = null
    }

    override fun finishAndClose(withRename: ((name: String) -> String)?) {
        fs?.close()
        fs = null

        currFile?.let {
            withRename?.let { rename ->
                it.renameTo(File(directory, rename(it.name)))
            }
        }

        currFile = null
    }

    override fun readAsStream(source: String): InputStream? {
        val file = File(source)
        return if (file.exists()) FileInputStream(file) else null
    }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                fs?.close()
            }
        })
    }
}