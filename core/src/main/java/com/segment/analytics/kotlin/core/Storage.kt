package com.segment.analytics.kotlin.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import sovran.kotlin.Store
import java.io.InputStream

/**
 *     The protocol of how events are read and stored.
 *     Implement this interface if you wanna your events
 *     to be read and stored in the way you want (for
 *     example: from/to remote server, from/to local database
 *     from/to encrypted source).
 *     By default, we have implemented read and store events
 *     from/to memory and file storage.
 */
interface Storage {
    companion object {
        /** Our servers only accept payloads < 32KB.  */
        const val MAX_PAYLOAD_SIZE = 32000 // 32KB.

        /**
         * Our servers only accept batches < 500KB. This limit is 475KB to account for extra data that
         * is not present in payloads themselves, but is added later, such as `sentAt`, `integrations` and other json tokens.
         */
        const val MAX_BATCH_SIZE = 475000 // 475KB.

        const val MAX_FILE_SIZE = 475_000 // 475KB
    }

    enum class Constants(val rawVal: String) {
        UserId("segment.userId"),
        Traits("segment.traits"),
        AnonymousId("segment.anonymousId"),
        Settings("segment.settings"),
        Events("segment.events"),
        AppVersion("segment.app.version"),
        AppBuild("segment.app.build"),
        LegacyAppBuild("build"),
        DeviceId("segment.device.id")
    }

    /**
     *  Initialization of the storage.
     *  All prerequisite setups should be done in this method.
     */
    suspend fun initialize()

    /**
     * Write a value of the Storage.Constants type to storage
     *
     * @param key The type of the value
     * @param value Value
     */
    suspend fun write(key: Constants, value: String)

    /**
     * Write a key/value pair to prefs
     *
     * @param key Key
     * @param value Value
     */
    fun writePrefs(key: Constants, value: String)

    /**
     * Read the value of a given type
     *
     * @param key The type of the value
     * @return value of the given type
     */
    fun read(key: Constants): String?

    /**
     * Read the given source stream as an InputStream
     *
     * @param source stream to read
     * @return result as InputStream
     */
    fun readAsStream(source: String): InputStream?

    /**
     * Remove the data of a given type
     *
     * @param key type of the data to remove
     * @return status of the operation
     */
    fun remove(key: Constants): Boolean

    /**
     * Remove a stream
     *
     * @param filePath the fullname/identifier of a stream
     * @return status of the operation
     */
    fun removeFile(filePath: String): Boolean

    /**
     * Close and finish the current stream and start a new one
     */
    suspend fun rollover()

    /**
     * Close and cleanup storage resources
     */
    fun close() {
        // empty body default
    }
}

fun parseFilePaths(filePathStr: String?): List<String> {
    return if (filePathStr.isNullOrEmpty()) {
        emptyList()
    } else {
        filePathStr.split(",").map { it.trim() }
    }
}

/**
 * Interface to provide a Storage Instance to the analytics client
 * Motivation:
 *  In order to support various platforms, plus making testing simpler, we abstract the storage
 *  provider via this interface
 */
interface StorageProvider {
    @Deprecated("Deprecated in favor of create which takes vararg params",
        ReplaceWith("createStorage(analytics, store, writeKey, ioDispatcher, application)")
    )
    fun getStorage(
        analytics: Analytics,
        store: Store,
        writeKey: String,
        ioDispatcher: CoroutineDispatcher,
        application: Any
    ): Storage = createStorage(analytics, store, writeKey, ioDispatcher, application)

    fun createStorage(vararg params: Any): Storage
}