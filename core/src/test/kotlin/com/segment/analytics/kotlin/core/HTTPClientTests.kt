package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import io.mockk.clearConstructorMockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.http.HttpClient
import java.util.zip.GZIPOutputStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HTTPClientTests {
    private val httpClient: HTTPClient

    init {
        Telemetry.enable = false
        clearConstructorMockk(HTTPClient::class)
        httpClient = HTTPClient("1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ")
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
                "https://api.segment.io/v1/b",
                it.url.toString()
            )
            assertEquals(
                "analytics-kotlin/$LIBRARY_VERSION",
                it.getRequestProperty("User-Agent")
            )
            assertEquals("gzip", it.getRequestProperty("Content-Encoding"))
            assertEquals("text/plain", it.getRequestProperty("Content-Type"))
            // ideally we would also test if auth Header is set, but due to security concerns this
            // is not possible https://bit.ly/3CVpR3J
        }
    }

    @Test
    fun `safeGetInputStream properly catches exception`() {
        val connection = spyk(URL("https://api.segment.io/v1/b").openConnection() as HttpURLConnection)
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
        every { connection.connection.headerFields } returns mutableMapOf<String, List<String> >()
        try {
            connection.close()
        }
        catch (e: IOException) {
            e.message?: fail()
            e.message?.let { assertTrue(it.contains("300")) }
        }
    }

    @Test
    fun `custom requestFactory takes effect`() {
        val httpClient = HTTPClient("123", object : RequestFactory() {
            override fun settings(cdnHost: String, writeKey: String): HttpURLConnection {
                return openConnection("https://cdn.test.com")
            }

            override fun upload(apiHost: String): HttpURLConnection {
                return openConnection("https://api.test.com").apply { doOutput = true }
            }

            override fun openConnection(url: String): HttpURLConnection {
                val requestedURL: URL = try {
                    URL(url)
                } catch (e: MalformedURLException) {
                    throw IOException("Attempted to use malformed url: $url", e)
                }

                return object : HttpURLConnection(requestedURL) {
                    override fun connect() {

                    }

                    override fun disconnect() {
                    }

                    override fun usingProxy() = false

                    override fun getOutputStream(): OutputStream {
                        return ByteArrayOutputStream()
                    }
                }
            }
        })

        httpClient.settings("cdn-settings.segment.com/v1").connection.let {
            assertEquals(
                "https://cdn.test.com",
                it.url.toString()
            )
        }

        httpClient.upload("api.segment.io/v1").connection.let {
            assertEquals(
                "https://api.test.com",
                it.url.toString()
            )
            assertFalse(it.outputStream is GZIPOutputStream)
        }
    }

    @Test
    fun `custom requestFactory can remove gzip`() {
        val httpClient = HTTPClient("123", object : RequestFactory() {
            override fun upload(apiHost: String): HttpURLConnection {
                val connection: HttpURLConnection = openConnection("https://$apiHost/b")
                connection.setRequestProperty("Content-Type", "text/plain")
                connection.doOutput = true
                connection.setChunkedStreamingMode(0)
                return connection
            }
        })

        httpClient.upload("api.segment.io/v1").connection.let {
            assertFalse(it.outputStream is GZIPOutputStream)
        }
    }
}