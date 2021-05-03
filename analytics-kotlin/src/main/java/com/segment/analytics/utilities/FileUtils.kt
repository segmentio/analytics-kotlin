package com.segment.analytics.utilities

import java.io.*

/**
 * Ensures that a directory is created in the given location, throws an IOException otherwise.
 */
@Throws(IOException::class)
fun createDirectory(location: File) {
    if (!(location.exists() || location.mkdirs() || location.isDirectory)) {
        throw IOException("Could not create directory at $location")
    }
}

/**
 * How it works
 * - startWrite(append=false):
 *   - Make a backup file (*.bak) by renaming existing
 *   - If append option (provided as param), copy contents from backup to main file
 *   - return fileOutputStream
 * - finishWrite(fos):
 *   - fsync()
 *   - delete backup file
 * - failWrite(fos):
 *   - delete main file
 *   - rename backup to main
 *
 * - Recovery:
 *   - If main file and backup file exist, then backup is the true file
 */
class AtomicFile(private val underlyingFile: File) {
    private val backupFile = File(underlyingFile.path + ".bak")

    fun startWrite(append: Boolean = false): FileOutputStream {
        if (underlyingFile.exists()) {
            if (backupFile.exists()) {
                // if backup exists, then that is the true file
                underlyingFile.delete()
            } else {
                // if no backup exists, rename underlying to backup
                underlyingFile.renameTo(backupFile)
            }
            if (append) {
                backupFile.copyTo(underlyingFile)
            }
        }
        return FileOutputStream(underlyingFile, append)
    }

    fun finishWrite(stream: FileOutputStream) {
        try {
            fsync(stream)
            stream.close()
            backupFile.delete()
        } catch(ex: IOException) {
            failWrite(stream)
        }
    }

    fun failWrite(stream: FileOutputStream) {
        stream.close()
        underlyingFile.delete()
        backupFile.renameTo(underlyingFile)
    }

    private fun fsync(stream: FileOutputStream): Boolean {
        try {
            stream.fd.sync()
            return true
        } catch (e: IOException) {
        }
        return false
    }
}