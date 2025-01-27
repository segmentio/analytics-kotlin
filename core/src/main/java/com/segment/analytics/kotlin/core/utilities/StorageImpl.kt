package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.Storage.Companion.MAX_FILE_SIZE
import com.segment.analytics.kotlin.core.Storage.Companion.MAX_PAYLOAD_SIZE
import com.segment.analytics.kotlin.core.StorageProvider
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.UserInfo
import com.segment.analytics.kotlin.core.reportInternalError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.io.File
import java.io.InputStream

/**
 * Storage implementation for JVM platform, uses {@link com.segment.analytics.kotlin.core.utilities.PropertiesFile}
 * for key-value storage and {@link com.segment.analytics.kotlin.core.utilities.EventsFileManager}
 * for events storage
 */
open class StorageImpl(
    internal val propertiesFile: KVS,
    internal val eventStream: EventStream,
    private val store: Store,
    private val writeKey: String,
    internal  val fileIndexKey: String,
    private val ioDispatcher: CoroutineDispatcher
) : Subscriber, Storage {

    private val semaphore = Semaphore(1)

    internal val begin = """{"batch":["""

    internal val end
        get() = """],"sentAt":"${SegmentInstant.now()}","writeKey":"$writeKey"}"""

    private val ext = "tmp"

    private val currentFile
        get() = "$writeKey-${propertiesFile.get(fileIndexKey, 0)}.$ext"

    override suspend fun initialize() {
        store.subscribe(
            this,
            UserInfo::class,
            initialState = true,
            handler = ::userInfoUpdate,
            queue = ioDispatcher
        )
        store.subscribe(
            this,
            System::class,
            initialState = true,
            handler = ::systemUpdate,
            queue = ioDispatcher
        )
    }

    suspend fun userInfoUpdate(userInfo: UserInfo) {
        write(Storage.Constants.AnonymousId, userInfo.anonymousId)

        userInfo.userId?.let {
            write(Storage.Constants.UserId, it)
        } ?: run {
            remove(Storage.Constants.UserId)
        }

        userInfo.traits?.let {
            write(Storage.Constants.Traits, Json.encodeToString(JsonObject.serializer(), it))
        } ?: run {
            remove(Storage.Constants.Traits)
        }
    }

    suspend fun systemUpdate(system: System) {
        system.settings?.let {
            write(Storage.Constants.Settings, Json.encodeToString(Settings.serializer(), it))
        } ?: run {
            remove(Storage.Constants.Settings)
        }
    }

    override suspend fun write(key: Storage.Constants, value: String) {
        when (key) {
            Storage.Constants.Events -> {
                if (value.length < MAX_PAYLOAD_SIZE) {
                    // write to disk
                    storeEvent(value)
                } else {
                    throw Exception("enqueued payload is too large")
                }
            }
            else -> {
                writePrefs(key, value)
            }
        }
    }

    override fun writePrefs(key: Storage.Constants, value: String) {
        propertiesFile.put(key.rawVal, value)
    }

    override fun read(key: Storage.Constants): String? {
        return when (key) {
            Storage.Constants.Events -> {
                eventStream.read().filter { !it.endsWith(".$ext") }.joinToString()
            }
            else -> {
                propertiesFile.get(key.rawVal, null)
            }
        }
    }

    override fun readAsStream(source: String): InputStream? {
        return eventStream.readAsStream(source)
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
        try {
            eventStream.remove(filePath)
            return true
        }
        catch (e: Exception) {
            Analytics.reportInternalError(e)
            return false
        }
    }

    override suspend fun rollover() = withLock {
        performRollover()
    }

    /**
     * closes existing file, if at capacity
     * opens a new file, if current file is full or uncreated
     * stores the event
     */
    private suspend fun storeEvent(event: String) = withLock {
        var newFile = eventStream.openOrCreate(currentFile)
        if (newFile) {
            eventStream.write(begin)
        }

        // check if file is at capacity
        if (eventStream.length > MAX_FILE_SIZE) {
            performRollover()

            // open the next file
            newFile = eventStream.openOrCreate(currentFile)
            eventStream.write(begin)
        }

        val contents = StringBuilder()
        if (!newFile) {
            contents.append(',')
        }
        contents.append(event)
        eventStream.write(contents.toString())
    }

    private fun performRollover() {
        if (!eventStream.isOpened) return

        eventStream.write(end)
        eventStream.finishAndClose {
            removeFileExtension(it)
        }
        incrementFileIndex()
    }

    private fun incrementFileIndex() {
        val index = propertiesFile.get(fileIndexKey, 0) + 1
        propertiesFile.put(fileIndexKey, index)
    }

    private suspend fun withLock(block: () -> Unit) {
        semaphore.acquire()
        block()
        semaphore.release()
    }
}

object ConcreteStorageProvider : StorageProvider {

    override fun createStorage(vararg params: Any): Storage {
        if (params.isEmpty() || params[0] !is Analytics) {
            throw IllegalArgumentException("Invalid parameters for ConcreteStorageProvider. ConcreteStorageProvider requires at least 1 parameter and the first argument has to be an instance of Analytics")
        }

        val analytics = params[0] as Analytics
        val config = analytics.configuration

        val directory = File("/tmp/analytics-kotlin/${config.writeKey}")
        val eventDirectory = File(directory, "events")
        val fileIndexKey = "segment.events.file.index.${config.writeKey}"
        val userPrefs = File(directory, "analytics-kotlin-${config.writeKey}.properties")

        val propertiesFile = PropertiesFile(userPrefs)
        val eventStream = FileEventStream(eventDirectory)
        return StorageImpl(propertiesFile, eventStream, analytics.store, config.writeKey, fileIndexKey, analytics.fileIODispatcher)
    }
}

class InMemoryStorageProvider: StorageProvider {
    override fun createStorage(vararg params: Any): Storage {
        if (params.isEmpty() || params[0] !is Analytics) {
            throw IllegalArgumentException("Invalid parameters for InMemoryStorageProvider. InMemoryStorageProvider requires at least 1 parameter and the first argument has to be an instance of Analytics")
        }

        val analytics = params[0] as Analytics
        val config = analytics.configuration

        val userPrefs = InMemoryPrefs()
        val eventStream = InMemoryEventStream()
        val fileIndexKey = "segment.events.file.index.${config.writeKey}"

        return StorageImpl(userPrefs, eventStream, analytics.store, config.writeKey, fileIndexKey, analytics.fileIODispatcher)
    }

}