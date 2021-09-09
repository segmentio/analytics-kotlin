package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import io.mockk.clearConstructorMockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HTTPClientTests {
    private val httpClient: HTTPClient

    init {
        clearConstructorMockk(HTTPClient::class)
        httpClient = HTTPClient("1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ")
    }

    @Test
    fun `authHeader is correctly computed`() {
        assertEquals("Basic MXZOZ1Vxd0plQ0htcWdJOVMxc09tOVVIQ3lmWXFiYVE6", httpClient.authHeader)
    }

    @Test
    fun `upload connection has correct configuration`() {
        httpClient.settings("cdn-settings.segment.com/v1").connection.let {
            assertEquals(
                "https://cdn-settings.segment.com/v1/projects/1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ/settings",
                it.url.toString()
            )
            assertEquals(
                "analytics-kotlin/$LIBRARY_VERSION",
                it.getRequestProperty("User-Agent")
            )
        }
    }

    @Test
    fun `settings connection has correct configuration`() {
        httpClient.upload("api.segment.io/v1").connection.let {
            assertEquals(
                "https://api.segment.io/v1/batch",
                it.url.toString()
            )
            assertEquals(
                "analytics-kotlin/$LIBRARY_VERSION",
                it.getRequestProperty("User-Agent")
            )
            assertEquals("gzip", it.getRequestProperty("Content-Encoding"))
            // ideally we would also test if auth Header is set, but due to security concerns this
            // is not possible https://bit.ly/3CVpR3J
        }
    }
}