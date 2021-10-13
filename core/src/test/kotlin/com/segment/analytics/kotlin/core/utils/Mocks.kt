package com.segment.analytics.kotlin.core.utils

import com.segment.analytics.kotlin.core.*
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import sovran.kotlin.State
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * Retrieve a relaxed mock of analytics, that can be used while testing plugins
 * Current capabilities:
 * - In-memory sovran.store
 */
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

fun clearPersistentStorage() {
    File("/tmp/analytics-kotlin/123").deleteRecursively()
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