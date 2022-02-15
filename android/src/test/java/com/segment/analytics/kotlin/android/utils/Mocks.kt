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
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import sovran.kotlin.Store
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import kotlin.coroutines.CoroutineContext

fun mockAnalytics(testScope: TestScope, testDispatcher: TestDispatcher): Analytics {
    val mock = mockk<Analytics>(relaxed = true)
    val mockStore = spyStore(testScope, testDispatcher)
    every { mock.store } returns mockStore
    every { mock.analyticsScope } returns testScope
    every { mock.fileIODispatcher } returns testDispatcher
    every { mock.networkIODispatcher } returns testDispatcher
    every { mock.analyticsDispatcher } returns testDispatcher
    return mock
}

fun testAnalytics(configuration: Configuration, testScope: TestScope, testDispatcher: TestDispatcher): Analytics {
    return object : Analytics(configuration, TestCoroutineConfiguration(testScope, testDispatcher)) {}
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

class TestCoroutineConfiguration(
    val testScope: TestScope,
    val testDispatcher: TestDispatcher
) : CoroutineConfiguration {

    override val store: Store =
        spyStore(testScope, testDispatcher)

    override val analyticsScope: CoroutineScope
        get() = testScope

    override val analyticsDispatcher: CoroutineDispatcher
        get() = testDispatcher

    override val networkIODispatcher: CoroutineDispatcher
        get() = testDispatcher

    override val fileIODispatcher: CoroutineDispatcher
        get() = testDispatcher
}