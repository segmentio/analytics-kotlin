package com.segment.analytics.kotlin.android

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.android.utilities.AndroidKVS
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.StorageProvider
import com.segment.analytics.kotlin.core.utilities.FileEventStream
import com.segment.analytics.kotlin.core.utilities.StorageImpl
import kotlinx.coroutines.CoroutineDispatcher
import sovran.kotlin.Store

@Deprecated("Use StorageProvider to create storage for Android instead")
class AndroidStorage(
    context: Context,
    private val store: Store,
    writeKey: String,
    private val ioDispatcher: CoroutineDispatcher,
    directory: String? = null,
    subject: String? = null
) : StorageImpl(
    propertiesFile = AndroidKVS(context.getSharedPreferences("analytics-android-$writeKey", Context.MODE_PRIVATE)),
    eventStream = FileEventStream(context.getDir(directory ?: "segment-disk-queue", Context.MODE_PRIVATE)),
    store = store,
    writeKey = writeKey,
    fileIndexKey = if(subject == null) "segment.events.file.index.$writeKey" else "segment.events.file.index.$writeKey.$subject",
    ioDispatcher = ioDispatcher
)

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
        return StorageImpl(propertiesFile, eventStream, analytics.store, config.writeKey, fileIndexKey, analytics.fileIODispatcher)
    }
}