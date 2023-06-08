package com.segment.analytics.kotlin.core.utils

import com.segment.analytics.kotlin.core.*
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.*
import sovran.kotlin.Store
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import kotlin.coroutines.CoroutineContext

/**
 * Retrieve a relaxed mock of analytics, that can be used while testing plugins
 * Current capabilities:
 * - In-memory sovran.store
 */
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

fun clearPersistentStorage(writeKey: String = "123") {
    File("/tmp/analytics-kotlin/$writeKey").deleteRecursively()
}

fun spyStore(scope: TestScope, dispatcher: TestDispatcher): Store {
    val store = spyk(Store())
    every { store getProperty "sovranScope" } propertyType CoroutineScope::class returns scope
    every { store getProperty "syncQueue" } propertyType CoroutineContext::class returns dispatcher
    every { store getProperty "updateQueue" } propertyType CoroutineContext::class returns dispatcher
    return store
}
val settingsDefault = """
                {"integrations":{"Segment.io":{"apiKey":"1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ"}},"plan":{},"edgeFunction":{}}
            """.trimIndent()

fun mockHTTPClient(settings: String = settingsDefault) {
    mockkConstructor(HTTPClient::class)
    val settingsStream = ByteArrayInputStream(
        settings.toByteArray()
    )
    val httpConnection: HttpURLConnection = mockk()
    val connection = object : Connection(httpConnection, settingsStream, null) {}
    every { anyConstructed<HTTPClient>().settings("cdn-settings.segment.com/v1") } returns connection
}

class TestCoroutineConfiguration(
        val testScope: TestScope,
        val testDispatcher: TestDispatcher
    ) : CoroutineConfiguration {

    override val store: Store = spyStore(testScope, testDispatcher)

    override val analyticsScope: CoroutineScope
        get() = testScope

    override val analyticsDispatcher: CoroutineDispatcher
        get() = testDispatcher

    override val networkIODispatcher: CoroutineDispatcher
        get() = testDispatcher

    override val fileIODispatcher: CoroutineDispatcher
        get() = testDispatcher
}