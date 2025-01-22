@file:JvmName("FileUtils")
package com.segment.analytics.kotlin.core.utilities

import java.io.File
import java.io.IOException

/**
 * Ensures that a directory is created in the given location, throws an IOException otherwise.
 */
@Throws(IOException::class)
fun createDirectory(location: File) {
    if (!(location.exists() || location.mkdirs() || location.isDirectory)) {
        throw IOException("Could not create directory at $location")
    }
}

fun removeFileExtension(fileName: String): String {
    val lastDotIndex = fileName.lastIndexOf('.')
    return if (lastDotIndex != -1 && lastDotIndex > 0) {
        fileName.substring(0, lastDotIndex)
    } else {
        fileName
    }
}