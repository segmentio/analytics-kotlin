package com.segment.analytics

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.Storage.Companion.MAX_PAYLOAD_SIZE
import com.segment.analytics.utilities.EventsFileManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.io.File

interface Storage {
    companion object {
        /** Our servers only accept payloads < 32KB.  */
        const val MAX_PAYLOAD_SIZE = 32000 // 32KB.

        /**
         * Our servers only accept batches < 500KB. This limit is 475KB to account for extra data that
         * is not present in payloads themselves, but is added later, such as `sentAt`, `integrations` and other json tokens.
         */
        const val MAX_BATCH_SIZE = 475000 // 475KB.
    }

    enum class Constants(val rawVal: String) {
        UserId("segment.userId"),
        Traits("segment.traits"),
        AnonymousId("segment.anonymousId"),
        Settings("segment.settings"),
        Events("segment.events"),
        AppVersion("segment.app.version"),
        AppBuild("segment.app.build")
    }

    fun subscribeToStore()
    fun write(key: Constants, value: String)
    fun read(key: Constants): String?
    fun remove(key: Constants): Boolean
    fun removeFile(filePath: String): Boolean

    fun userInfoUpdate(userInfo: UserInfo) {
        write(Constants.AnonymousId, userInfo.anonymousId)
        userInfo.userId?.let { write(Constants.UserId, it) }
        userInfo.traits?.let {
            write(
                Constants.Traits,
                Json.encodeToString(JsonObject.serializer(), it)
            )
        }
    }

    fun systemUpdate(system: System) {
        system.settings?.let {
            write(
                Constants.Settings,
                Json.encodeToString(Settings.serializer(), it)
            )
        }
    }
}

fun parseFilePaths(filePathStr: String?): List<String> {
    return if (filePathStr.isNullOrEmpty()) {
        emptyList()
    } else {
        filePathStr.split(",")
    }
}

// Android specific
class AndroidStorage(
    internal val analytics: Analytics,
    context: Context,
    private val store: Store,
    writeKey: String,
    private val ioDispatcher: CoroutineDispatcher
) : Subscriber, Storage {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("analytics-android-$writeKey", Context.MODE_PRIVATE)
    private val storageDirectory: File = context.getDir("segment-disk-queue", Context.MODE_PRIVATE)
    internal val eventsFile = EventsFileManager(storageDirectory, writeKey, sharedPreferences)

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

interface StorageProvider {
    fun getStorage(
        analytics: Analytics,
        store: Store,
        writeKey: String,
        ioDispatcher: CoroutineDispatcher,
        application: Any
    ): Storage
}

object ConcreteStorageProvider: StorageProvider {
    override fun getStorage(
        analytics: Analytics,
        store: Store,
        writeKey: String,
        ioDispatcher: CoroutineDispatcher,
        application: Any
    ): Storage {
        return AndroidStorage(
            analytics = analytics,
            store = store,
            writeKey = writeKey,
            ioDispatcher = ioDispatcher,
            context = application as Context
        )
    }
}