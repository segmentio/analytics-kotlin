package com.segment.analytics.kotlin.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import sovran.kotlin.Store

/**
 * Storage interface that abstracts storage of
 * - user data
 * - segment settings
 * - segment events
 * - other configs
 *
 * Constraints:
 * - Segment Events must be stored on a file, following the batch format
 * - all storage is in terms of String (to make API simple)
 * - storage is restricted to keys declared in `Storage.Constants`
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

    suspend fun subscribeToStore()
    suspend fun write(key: Constants, value: String)
    fun read(key: Constants): String?
    fun remove(key: Constants): Boolean
    fun removeFile(filePath: String): Boolean
    suspend fun rollover()

    suspend fun userInfoUpdate(userInfo: UserInfo) {
        write(Constants.AnonymousId, userInfo.anonymousId)
        userInfo.userId?.let { write(Constants.UserId, it) }
        userInfo.traits?.let {
            write(
                Constants.Traits,
                Json.encodeToString(JsonObject.serializer(), it)
            )
        }
    }

    suspend fun systemUpdate(system: System) {
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
    fun getStorage(
        analytics: Analytics,
        store: Store,
        writeKey: String,
        ioDispatcher: CoroutineDispatcher,
        application: Any
    ): Storage
}