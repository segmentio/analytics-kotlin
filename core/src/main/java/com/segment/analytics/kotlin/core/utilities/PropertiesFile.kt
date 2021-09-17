package com.segment.analytics.kotlin.core.utilities

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * A key-value storage built on top of {@link java.util.Properties}
 * conforming to {@link com.segment.analytics.kotlin.core.utilities.KVS} interface.
 * Ideal for use on JVM systems to store k-v pairs on a file.
 */
class PropertiesFile(private val directory: File, writeKey: String) : KVS {
    private val underlyingProperties: Properties = Properties()
    private val propertiesFileName = "analytics-kotlin-$writeKey.properties"
    private val propertiesFile = File(directory, propertiesFileName)

    /**
     * Check if underlying file exists, and load properties if true
     */
    fun load() {
        if (propertiesFile.exists()) {
            underlyingProperties.load(FileInputStream(propertiesFile))
        }
    }

    fun save() {
        underlyingProperties.store(FileOutputStream(propertiesFile), null)
    }

    override fun getInt(key: String, defaultVal: Int): Int =
        underlyingProperties.getProperty(key, "").toIntOrNull() ?: defaultVal

    override fun putInt(key: String, value: Int): Boolean {
        underlyingProperties.setProperty(key, value.toString())
        save()
        return true
    }

    fun putString(key: String, value: String): Boolean {
        underlyingProperties.setProperty(key, value)
        save()
        return true
    }

    fun getString(key: String, defaultVal: String?): String? =
        underlyingProperties.getProperty(key, defaultVal)

    fun remove(key: String): Boolean {
        underlyingProperties.remove(key)
        save()
        return true
    }
}
