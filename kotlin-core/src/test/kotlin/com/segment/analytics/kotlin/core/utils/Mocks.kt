package com.segment.analytics.kotlin.core.utils

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.StorageProvider
import com.segment.analytics.kotlin.core.UserInfo
import com.segment.analytics.kotlin.core.System
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.io.File

fun mockAnalytics(): Analytics {
    val mock = mockk<Analytics>(relaxed = true)
    val mockStore = Store()
    every { mock.store } returns mockStore
    return mock
}

object TestStorageProvider : StorageProvider {

    class TestStorage(private val store: Store, private val ioDispatcher: CoroutineDispatcher) : Storage, Subscriber {
        val map = mutableMapOf<String, String>()

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
            map[key.rawVal] = value
        }

        override fun read(key: Storage.Constants): String? {
            return map[key.rawVal]
        }

        override fun remove(key: Storage.Constants): Boolean {
            map.remove(key.rawVal)
            return true
        }

        override fun removeFile(path: String): Boolean {
            return File(path).delete()
        }
    }

    override fun getStorage(
        analytics: Analytics,
        store: Store,
        writeKey: String,
        ioDispatcher: CoroutineDispatcher,
        application: Any
    ): Storage {
        return TestStorage(store, ioDispatcher)
    }

}