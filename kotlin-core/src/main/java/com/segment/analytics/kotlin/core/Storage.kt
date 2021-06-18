package com.segment.analytics.kotlin.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import sovran.kotlin.Store

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
        filePathStr.split(",").map { it.trim() }
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