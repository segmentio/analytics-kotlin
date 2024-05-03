package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.mockHTTPClient
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErrorsTest {
    private lateinit var analytics: Analytics
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val errorHandler = spyk<ErrorHandler>()

    init {
        Telemetry.enable = false
    }

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        mockHTTPClient()
        val config = Configuration(
            writeKey = "123",
            application = "Test",
            errorHandler = errorHandler,
        )

        analytics = testAnalytics(config, testScope, testDispatcher)
        analytics.configuration.autoAddSegmentDestination = false
    }

    @Test
    fun `custom errorHandler handles error`() {
        val error = Exception()
        every { anyConstructed<HTTPClient>().upload(any()) } throws error

        analytics.track("test")
        analytics.flush()

        verify { errorHandler.invoke(error) }
    }
}