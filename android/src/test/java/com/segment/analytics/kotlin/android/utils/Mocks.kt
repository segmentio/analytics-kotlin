package com.segment.analytics.kotlin.android.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.segment.analytics.kotlin.core.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import sovran.kotlin.Store
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import kotlin.coroutines.CoroutineContext

fun mockAnalytics(): Analytics {
    val mock = mockk<Analytics>(relaxed = true)
    val scope = TestCoroutineScope()
    val dispatcher = TestCoroutineDispatcher()
    val mockStore = spyStore(scope, dispatcher)
    every { mock.store } returns mockStore
    every { mock.analyticsScope } returns scope
    every { mock.fileIODispatcher } returns dispatcher
    every { mock.networkIODispatcher } returns dispatcher
    every { mock.analyticsDispatcher } returns dispatcher
    return mock
}

fun testAnalytics(configuration: Configuration): Analytics {
    return object : Analytics(configuration, TestCoroutineConfiguration()) {}
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
        every { getDir(any(), any()) } returns File("/tmp/analytics-android-test/")
        every { packageName } returns "com.foo"
        every { packageManager } returns mockPkgMgr
    }
    return mock
}

fun clearPersistentStorage() {
    File("/tmp/analytics-android-test/").deleteRecursively()
}

fun spyStore(scope: CoroutineScope, dispatcher: CoroutineDispatcher): Store {
    val store = spyk(Store())
    every { store getProperty "sovranScope" } propertyType CoroutineScope::class returns scope
    every { store getProperty "syncQueue" } propertyType CoroutineContext::class returns dispatcher
    every { store getProperty "updateQueue" } propertyType CoroutineContext::class returns dispatcher
    return store
}

fun mockHTTPClient() {
    mockkConstructor(HTTPClient::class)
    val settingsStream = ByteArrayInputStream(
        """
                {"integrations":{"Segment.io":{"apiKey":"1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ"}},"plan":{},"edgeFunction":{}}
            """.trimIndent().toByteArray()
    )
    val httpConnection: HttpURLConnection = mockk()
    val connection = object : Connection(httpConnection, settingsStream, null) {}
    every { anyConstructed<HTTPClient>().settings("cdn-settings.segment.com/v1") } returns connection
}

class TestCoroutineConfiguration : CoroutineConfiguration {
    private val testScope = TestCoroutineScope()

    private val testCoroutineDispatcher = TestCoroutineDispatcher()

    override val store: Store = spyStore(testScope, testCoroutineDispatcher)

    override val analyticsScope: CoroutineScope
        get() = testScope

    override val analyticsDispatcher: CoroutineDispatcher
        get() = testCoroutineDispatcher

    override val networkIODispatcher: CoroutineDispatcher
        get() = testCoroutineDispatcher

    override val fileIODispatcher: CoroutineDispatcher
        get() = testCoroutineDispatcher
}