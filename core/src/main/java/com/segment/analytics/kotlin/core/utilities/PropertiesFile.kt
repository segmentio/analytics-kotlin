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
class PropertiesFile(val file: File) : KVS {
    private val properties: Properties = Properties()

    init {
        load()
    }

    /**
     * Check if underlying file exists, and load properties if true
     */
    fun load() {
        if (file.exists()) {
            FileInputStream(file).use {
                properties.load(it)
            }
        }
        else {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
    }

    fun save() {
        FileOutputStream(file).use {
            properties.store(it, null)
        }
    }

    override fun get(key: String, defaultVal: Int): Int {
        return properties.getProperty(key, "").toIntOrNull() ?: defaultVal
    }

    override fun get(key: String, defaultVal: String?): String? {
        return properties.getProperty(key, defaultVal)
    }

    override fun put(key: String, value: Int): Boolean {
        properties.setProperty(key, value.toString())
        save()
        return true
    }

    override fun put(key: String, value: String): Boolean {
        properties.setProperty(key, value)
        save()
        return true
    }

    fun putString(key: String, value: String): Boolean {
        properties.setProperty(key, value)
        save()
        return true
    }

    fun getString(key: String, defaultVal: String?): String? =
        properties.getProperty(key, defaultVal)

    override fun remove(key: String): Boolean {
        properties.remove(key)
        save()
        return true
    }
}
