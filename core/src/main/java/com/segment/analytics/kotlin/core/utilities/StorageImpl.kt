package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.Storage.Companion.MAX_PAYLOAD_SIZE
import com.segment.analytics.kotlin.core.StorageProvider
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.UserInfo
import kotlinx.coroutines.CoroutineDispatcher
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.io.File

/**
 * Storage implementation for JVM platform, uses {@link com.segment.analytics.kotlin.core.utilities.PropertiesFile}
 * for key-value storage and {@link com.segment.analytics.kotlin.core.utilities.EventsFileManager}
 * for events storage
 */
class StorageImpl(
    private val store: Store,
    writeKey: String,
    private val ioDispatcher: CoroutineDispatcher
) : Subscriber, Storage {

    private val storageDirectory = File("/tmp/analytics-kotlin/$writeKey")
    private val storageDirectoryEvents = File(storageDirectory, "events")

    internal val propertiesFile = PropertiesFile(storageDirectory, writeKey)
    internal val eventsFile = EventsFileManager(storageDirectoryEvents, writeKey, propertiesFile)

    init {
        propertiesFile.load()
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
                propertiesFile.putString(key.rawVal, value)
            }
        }
    }

    override fun read(key: Storage.Constants): String? {
        return when (key) {
            Storage.Constants.Events -> {
                val read = eventsFile.read()
                read.joinToString()
            }
            else -> {
                propertiesFile.getString(key.rawVal, null)
            }
        }
    }

    override fun remove(key: Storage.Constants): Boolean {
        return when (key) {
            Storage.Constants.Events -> {
                true
            }
            else -> {
                propertiesFile.remove(key.rawVal)
                true
            }
        }
    }

    override fun removeFile(filePath: String): Boolean {
        return eventsFile.remove(filePath)
    }

}

object ConcreteStorageProvider : StorageProvider {
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