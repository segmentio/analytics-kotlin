package com.segment.analytics.kotlin.android

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.android.utilities.AndroidKVS
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.Storage.Companion.MAX_PAYLOAD_SIZE
import com.segment.analytics.kotlin.core.StorageProvider
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.UserInfo
import com.segment.analytics.kotlin.core.utilities.EventsFileManager
import kotlinx.coroutines.CoroutineDispatcher
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.io.File

// Android specific
class AndroidStorage(
    context: Context,
    private val store: Store,
    writeKey: String,
    private val ioDispatcher: CoroutineDispatcher
) : Subscriber, Storage {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("analytics-android-$writeKey", Context.MODE_PRIVATE)
    private val storageDirectory: File = context.getDir("segment-disk-queue", Context.MODE_PRIVATE)
    internal val eventsFile =
        EventsFileManager(storageDirectory, writeKey, AndroidKVS(sharedPreferences))

    override fun subscribeToStore() {
        store.subscribe(
            this,
            UserInfo::class,
            initialState = true,
            handler = ::userInfoUpdate
        )
        store.subscribe(
            this,
            System::class,
            initialState = true,
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
                sharedPreferences.edit().putString(key.rawVal, value).apply()
            }
        }
    }

    /**
     * @returns the String value for the associated key
     * for Constants.Events it will return a file url that can be used to read the contents of the events
     */
    override fun read(key: Storage.Constants): String? {
        return when (key) {
            Storage.Constants.Events -> {
                eventsFile.read().joinToString()
            }
            else -> {
                sharedPreferences.getString(key.rawVal, null)
            }
        }
    }

    override fun remove(key: Storage.Constants): Boolean {
        return when (key) {
            Storage.Constants.Events -> {
                true
            }
            else -> {
                sharedPreferences.edit().putString(key.rawVal, null).apply()
                true
            }
        }
    }

    override fun removeFile(filePath: String): Boolean {
        return eventsFile.remove(filePath)
    }
}

object AndroidStorageProvider : StorageProvider {
    override fun getStorage(
        analytics: Analytics,
        store: Store,
        writeKey: String,
        ioDispatcher: CoroutineDispatcher,
        application: Any
    ): Storage {
        return AndroidStorage(
            store = store,
            writeKey = writeKey,
            ioDispatcher = ioDispatcher,
            context = application as Context
        )
    }
}