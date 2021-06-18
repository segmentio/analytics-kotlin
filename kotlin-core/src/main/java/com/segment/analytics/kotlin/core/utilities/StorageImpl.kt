package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.Storage.Companion.MAX_PAYLOAD_SIZE
import kotlinx.coroutines.CoroutineDispatcher
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Date
import java.util.Properties

class PropertiesKVS(private val properties: Properties): KVS {
    override fun getInt(key: String, defaultVal: Int): Int =
        properties.getProperty(key).toIntOrNull() ?: defaultVal

    override fun putInt(key: String, value: Int): Boolean {
        properties.setProperty(key, value.toString())
        return true
    }

}

class StorageImpl(
    private val store: Store,
    writeKey: String,
    private val ioDispatcher: CoroutineDispatcher
) : Subscriber, Storage {

    private val propertiesFileName = "analytics-kotlin-$writeKey.properties"
    private val propertiesFile: Properties = Properties()
    private val storageDirectory = File("~/analytics-kotlin")
    private val eventsFile = EventsFileManager(storageDirectory, writeKey, PropertiesKVS(propertiesFile))

    init {
        // check if file exists and load properties from it
        val file = File(storageDirectory, propertiesFileName)
        if (file.exists()) {
            propertiesFile.load(FileInputStream(propertiesFileName))
        }
    }

    private fun syncPropertiesFile() {
        propertiesFile.store(FileOutputStream(propertiesFileName), "last saved at ${Date()}")
    }

    override fun subscribeToStore() {
        store.subscribe(
            this,
            UserInfo::class,
            initialState = true,
            queue = ioDispatcher,
            handler = ::userInfoUpdate
        )
        store.subscribe(
            this,
            System::class,
            initialState = true,
            queue = ioDispatcher,
            handler = ::systemUpdate
        )
    }

    override fun write(key: Storage.Constants, value: String) {
        when (key) {
            Storage.Constants.Events -> {
                if (value.length < MAX_PAYLOAD_SIZE) {
                    // write to disk
                    eventsFile.storeEvent(value)
                } else {
                    throw Exception("enqueued payload is too large")
                }
            }
            else -> {
                propertiesFile.setProperty(key.rawVal, value).also { syncPropertiesFile() }
            }
        }
    }

    override fun read(key: Storage.Constants): String? {
        return when (key) {
            Storage.Constants.Events -> {
                eventsFile.read().joinToString()
            }
            else -> {
                propertiesFile.getProperty(key.rawVal, null)
            }
        }
    }

    override fun remove(key: Storage.Constants): Boolean {
        return when (key) {
            Storage.Constants.Events -> {
                true
            }
            else -> {
                propertiesFile.remove(key.rawVal).also { syncPropertiesFile() }
                true
            }
        }
    }

    override fun removeFile(filePath: String): Boolean {
        return eventsFile.remove(filePath)
    }

}

object ConcreteStorageProvider: StorageProvider {
    override fun getStorage(
        analytics: Analytics,
        store: Store,
        writeKey: String,
        ioDispatcher: CoroutineDispatcher,
        application: Any
    ): Storage {
        return StorageImpl(
            ioDispatcher = ioDispatcher,
            writeKey = writeKey,
            store = store
        )
    }
}