package com.segment.analytics.kotlin.android

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.android.utilities.AndroidKVS
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.StorageProvider
import com.segment.analytics.kotlin.core.utilities.EventStream
import com.segment.analytics.kotlin.core.utilities.FileEventStream
import com.segment.analytics.kotlin.core.utilities.KVS
import com.segment.analytics.kotlin.core.utilities.StorageImpl
import kotlinx.coroutines.CoroutineDispatcher
import sovran.kotlin.Store

@Deprecated("Use StorageProvider to create storage for Android instead")
class AndroidStorage(
    context: Context,
    store: Store,
    writeKey: String,
    ioDispatcher: CoroutineDispatcher,
    directory: String? = null,
    subject: String? = null
) : AndroidStorageImpl(
    propertiesFile = AndroidKVS(context.getSharedPreferences("analytics-android-$writeKey", Context.MODE_PRIVATE)),
    eventStream = FileEventStream(context.getDir(directory ?: "segment-disk-queue", Context.MODE_PRIVATE)),
    store = store,
    writeKey = writeKey,
    fileIndexKey = if(subject == null) "segment.events.file.index.$writeKey" else "segment.events.file.index.$writeKey.$subject",
    ioDispatcher = ioDispatcher
)

open class AndroidStorageImpl(
    propertiesFile: KVS,
    eventStream: EventStream,
    store: Store,
    writeKey: String,
    fileIndexKey: String,
    ioDispatcher: CoroutineDispatcher
) : StorageImpl(
    propertiesFile = propertiesFile,
    eventStream = eventStream,
    store = store,
    writeKey = writeKey,
    fileIndexKey = fileIndexKey,
    ioDispatcher = ioDispatcher
) {
    override fun read(key: Storage.Constants): String? {
        return if (key == Storage.Constants.LegacyAppBuild) {
            // The legacy app build number was stored as an integer so we have to get it
            // as an integer and convert it to a String.
            val noBuild = -1
            val build = propertiesFile.get(key.rawVal, noBuild)
            if (build != noBuild) {
                build.toString()
            } else {
                null
            }
        } else {
            super.read(key)
        }
    }
}

object AndroidStorageProvider : StorageProvider {
    override fun createStorage(vararg params: Any): Storage {

        if (params.size < 2 || params[0] !is Analytics || params[1] !is Context) {
            throw IllegalArgumentException("""
                Invalid parameters for AndroidStorageProvider. 
                AndroidStorageProvider requires at least 2 parameters.
                 The first argument has to be an instance of Analytics,
                 an the second argument has to be an instance of Context
            """.trimIndent())
        }

        val analytics = params[0] as Analytics
        val context = params[1] as Context
        val config = analytics.configuration

        val eventDirectory = context.getDir("segment-disk-queue", Context.MODE_PRIVATE)
        val fileIndexKey = "segment.events.file.index.${config.writeKey}"
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences("analytics-android-${config.writeKey}", Context.MODE_PRIVATE)

        val propertiesFile = AndroidKVS(sharedPreferences)
        val eventStream = FileEventStream(eventDirectory)
        return AndroidStorageImpl(propertiesFile, eventStream, analytics.store, config.writeKey, fileIndexKey, analytics.fileIODispatcher)
    }
}