package com.segment.analytics.kotlin.android.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.segment.analytics.kotlin.android.utils.MemorySharedPreferences
import com.segment.analytics.kotlin.core.Analytics
import io.mockk.every
import io.mockk.mockk
import sovran.kotlin.Store
import java.io.File

fun mockAnalytics(): Analytics {
    val mock = mockk<Analytics>(relaxed = true)
    val mockStore = Store()
    every { mock.store } returns mockStore
    return mock
}

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
