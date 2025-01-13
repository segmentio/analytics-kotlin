package com.segment.analytics.kotlin.core.utilities

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.StringBuilder
import java.util.concurrent.ConcurrentHashMap

interface EventStream {
    val length: Long

    val isOpened: Boolean

    /**
     * open or create a file
     * @param file name of file
     * @return true if a new file is created
     */
    fun openOrCreate(file: String): Boolean

    fun write(content: String)

    /**
     * read the list of files in directory
     * @return a list of file names in directory
     */
    fun read(): List<String>

    fun remove(file: String)

    fun close()

    fun finishAndClose()

    fun readAsStream(source: String): InputStream?
}

class InMemoryEventStream: EventStream {
    private val directory = ConcurrentHashMap<String, InMemoryFile>()

    private var currFile: InMemoryFile? = null

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

    override fun finishAndClose() {
        currFile ?: return

        currFile?.let {
            val nameWithoutExtension = removeFileExtension(it.name)
            directory.remove(it.name)
            directory[nameWithoutExtension] = it
        }

        currFile = null
    }

    override fun readAsStream(source: String): InputStream? = directory[source]?.toStream()

    private fun removeFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex != -1 && lastDotIndex > 0) {
            fileName.substring(0, lastDotIndex)
        } else {
            fileName
        }
    }

    class InMemoryFile(val name: String) {
        val fileStream: StringBuilder = StringBuilder()

        val length: Int
            get() = fileStream.length

        fun write(content: String) = fileStream.append(content)

        fun toStream() = fileStream.toString().byteInputStream()
    }
}

class FileEventStream(
    private val directory: File
): EventStream {

    init {
        createDirectory(directory)
        registerShutdownHook()
    }

    private var fs: FileOutputStream? = null

    private var currFile: File? = null

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

    override fun remove(file: String) {
        File(file).delete()
    }

    override fun close() {
        fs?.close()
        fs = null
        currFile = null
    }

    override fun finishAndClose() {
        fs?.close()
        fs = null

        currFile?.let {
            it.renameTo(File(directory, it.nameWithoutExtension))
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