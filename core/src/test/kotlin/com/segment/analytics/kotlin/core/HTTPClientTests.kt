package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import io.mockk.clearConstructorMockk
import io.mockk.every
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

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

    @Test
    fun `safeGetInputStream properly catches exception`() {
        val connection = spyk(URL("https://api.segment.io/v1/batch").openConnection() as HttpURLConnection)
        every { connection.inputStream } throws IOException()
        val errorStream: InputStream? = safeGetInputStream(connection)
        assertEquals(connection.errorStream, errorStream)
    }

    @Test
    fun `createPostConnection close`() {
        val connection = spyk(httpClient.upload("api.segment.io/v1"))
        every { connection.connection.responseCode } returns 300
        every { connection.connection.inputStream } throws IOException()
        every { connection.connection.responseMessage } returns "test"
        every { connection.outputStream?.close() } returns Unit
        every { connection.connection.errorStream } returns null
        try {
            connection.close()
        }
        catch (e: IOException) {
            e.message?: fail()
            e.message?.let { assertTrue(it.contains("300")) }
        }
    }
}