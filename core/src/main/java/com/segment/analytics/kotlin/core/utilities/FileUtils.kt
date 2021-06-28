package com.segment.analytics.kotlin.core.utilities

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