package com.segment.analytics.main.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.segment.analytics.Analytics
import com.segment.analytics.Storage
import com.segment.analytics.StorageProvider
import com.segment.analytics.System
import com.segment.analytics.UserInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.io.File

fun mockContext(): Context {
    val mockPrefs = MemorySharedPreferences()
    val packageInfo = PackageInfo()
    packageInfo.versionCode = 100
    packageInfo.versionName = "1.0.0"

    val mockPkgMgr = mockk<PackageManager>()
    every { mockPkgMgr.getPackageInfo("com.foo", 0) } returns packageInfo
    val mock = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns mockPrefs
        every { getDir(any(), any()) } returns File("/tmp/analytics-android/")
        every { packageName } returns "com.foo"
        every { packageManager } returns mockPkgMgr
    }
    return mock
}

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