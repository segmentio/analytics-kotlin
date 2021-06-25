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

/**
 * Retrieve a relaxed mock of analytics, that can be used while testing plugins
 * Current capabilities:
 * - In-memory sovran.store
 */
fun mockAnalytics(): Analytics {
    val mock = mockk<Analytics>(relaxed = true)
    val mockStore = Store()
    every { mock.store } returns mockStore
    return mock
}