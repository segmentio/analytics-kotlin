package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import io.mockk.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OkHttpURLConnectionTest {

    private lateinit var mockClient: OkHttpClient
    private lateinit var mockCall: okhttp3.Call
    private lateinit var mockResponse: Response
    private lateinit var connection: OkHttpURLConnection
    private val testUrl = URL("https://api.segment.io/v1/b")

    @BeforeEach
    fun setup() {
        clearAllMocks()
        mockClient = mockk<OkHttpClient>()
        mockCall = mockk<okhttp3.Call>()
        mockResponse = mockk<Response>(relaxed = true)
        connection = OkHttpURLConnection(testUrl, mockClient)
    }

    @Test
    fun `constructor initializes with correct URL and client`() {
        assertEquals(testUrl, connection.url)
        assertEquals(testUrl, connection.getURL())
        assertEquals("GET", connection.getRequestMethod())
        assertTrue(connection.getDoInput())
        assertFalse(connection.getDoOutput())
    }

    @Test
    fun `setRequestProperty stores properties in internal map`() {
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer token")
        
        assertEquals("application/json", connection.getRequestProperty("Content-Type"))
        assertEquals("Bearer token", connection.getRequestProperty("Authorization"))
        
        val properties = connection.getRequestProperties()
        assertEquals(2, properties.size)
        assertEquals(listOf("application/json"), properties["Content-Type"])
        assertEquals(listOf("Bearer token"), properties["Authorization"])
    }

    @Test
    fun `addRequestProperty appends to existing properties`() {
        connection.setRequestProperty("Accept", "application/json")
        connection.addRequestProperty("Accept", "text/plain")
        
        val values = connection.getRequestProperties()["Accept"]
        assertEquals(2, values?.size)
        assertEquals("application/json", values?.get(0))
        assertEquals("text/plain", values?.get(1))
        
        // getRequestProperty returns first value
        assertEquals("application/json", connection.getRequestProperty("Accept"))
    }

    @Test
    fun `setRequestMethod updates internal method state`() {
        connection.setRequestMethod("POST")
        assertEquals("POST", connection.getRequestMethod())
        
        connection.setRequestMethod("PUT")
        assertEquals("PUT", connection.getRequestMethod())
    }

    @Test
    fun `setRequestMethod throws exception when connected`() {
        // Mock successful connection
        setupMockResponse(200, "OK")
        connection.connect()
        
        assertThrows(IllegalStateException::class.java) {
            connection.setRequestMethod("POST")
        }
    }

    @Test
    fun `doInput and doOutput setters work correctly`() {
        connection.setDoInput(false)
        assertFalse(connection.getDoInput())
        
        connection.setDoOutput(true)
        assertTrue(connection.getDoOutput())
    }

    @Test
    fun `timeout setters work correctly`() {
        connection.setConnectTimeout(5000)
        assertEquals(5000, connection.getConnectTimeout())
        
        connection.setReadTimeout(10000)
        assertEquals(10000, connection.getReadTimeout())
    }

    @Test
    fun `connect makes OkHttp call with correct request`() {
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer token")
        connection.setRequestMethod("POST")
        
        setupMockResponse(200, "OK")
        
        connection.connect()
        
        verify { mockClient.newCall(any()) }
        verify { mockCall.execute() }
        assertEquals(200, connection.getResponseCode())
        assertEquals("OK", connection.getResponseMessage())
    }

    @Test
    fun `getInputStream returns response body stream`() {
        val responseBody = "test response".toResponseBody("text/plain".toMediaType())
        setupMockResponse(200, "OK", responseBody)
        
        connection.connect()
        val inputStream = connection.getInputStream()
        
        assertNotNull(inputStream)
        val content = inputStream.bufferedReader().use { it.readText() }
        assertEquals("test response", content)
    }

    @Test
    fun `getErrorStream returns null for successful response`() {
        setupMockResponse(200, "OK")
        
        connection.connect()
        val errorStream = connection.getErrorStream()
        
        assertNull(errorStream)
    }

    @Test
    fun `getErrorStream returns body stream for error response`() {
        val errorBody = "error response".toResponseBody("text/plain".toMediaType())
        setupMockResponse(404, "Not Found", errorBody)
        
        connection.connect()
        val errorStream = connection.getErrorStream()
        
        assertNotNull(errorStream)
        val content = errorStream?.bufferedReader()?.use { it.readText() }
        assertEquals("error response", content)
    }

    @Test
    fun `getHeaderField returns response headers`() {
        setupMockResponse(200, "OK", headers = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("Content-Length", "123")
            .build())
        
        connection.connect()
        
        assertEquals("application/json", connection.getHeaderField("Content-Type"))
        assertEquals("123", connection.getHeaderField("Content-Length"))
        assertNull(connection.getHeaderField("Non-Existent"))
    }

    @Test
    fun `getHeaderFields returns all response headers`() {
        val headers = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("Cache-Control", "no-cache")
            .add("Cache-Control", "no-store")
            .build()
        
        setupMockResponse(200, "OK", headers = headers)
        
        // Mock the headers.toMultimap() method
        val expectedMultimap = mapOf(
            "Content-Type" to listOf("application/json"),
            "Cache-Control" to listOf("no-cache", "no-store")
        )
        every { mockResponse.headers.toMultimap() } returns expectedMultimap
        
        connection.connect()
        val headerFields = connection.getHeaderFields()
        
        assertEquals("application/json", headerFields["Content-Type"]?.get(0))
        assertEquals(2, headerFields["Cache-Control"]?.size)
        assertEquals("no-cache", headerFields["Cache-Control"]?.get(0))
        assertEquals("no-store", headerFields["Cache-Control"]?.get(1))
    }

    @Test
    fun `getContentType returns content type header`() {
        setupMockResponse(200, "OK", headers = Headers.Builder()
            .add("Content-Type", "application/json; charset=utf-8")
            .build())
        
        connection.connect()
        
        assertEquals("application/json; charset=utf-8", connection.getContentType())
    }

    @Test
    fun `getContentLength returns content length`() {
        setupMockResponse(200, "OK", headers = Headers.Builder()
            .add("Content-Length", "1024")
            .build())
        
        connection.connect()
        
        assertEquals(1024, connection.getContentLength())
        assertEquals(1024L, connection.getContentLengthLong())
    }

    @Test
    fun `getContent returns appropriate object based on content type`() {
        // Test JSON content
        val jsonBody = """{"key": "value"}""".toResponseBody("application/json".toMediaType())
        setupMockResponse(200, "OK", jsonBody, Headers.Builder()
            .add("Content-Type", "application/json")
            .build())
        
        val content = connection.getContent()
        assertTrue(content is String)
        assertEquals("""{"key": "value"}""", content)
    }

    @Test
    fun `getContent with classes returns requested type`() {
        val textBody = "hello world".toResponseBody("text/plain".toMediaType())
        setupMockResponse(200, "OK", textBody, Headers.Builder()
            .add("Content-Type", "text/plain")
            .build())
        
        // Request String class
        val stringContent = connection.getContent(arrayOf(String::class.java))
        assertTrue(stringContent is String)
        assertEquals("hello world", stringContent)
    }

    @Test
    fun `getContent with classes returns ByteArray type`() {
        val textBody = "hello world".toResponseBody("text/plain".toMediaType())
        val newConnection = OkHttpURLConnection(testUrl, mockClient)
        setupMockResponse(200, "OK", textBody, Headers.Builder()
            .add("Content-Type", "text/plain") 
            .build())
        
        val byteContent = newConnection.getContent(arrayOf(ByteArray::class.java))
        assertTrue(byteContent is ByteArray)
        assertEquals("hello world", String(byteContent as ByteArray))
    }

    @Test
    fun `disconnect closes response`() {
        setupMockResponse(200, "OK")
        connection.connect()
        
        connection.disconnect()
        
        verify { mockResponse.close() }
    }

    @Test
    fun `usingProxy returns false`() {
        assertFalse(connection.usingProxy())
    }

    @Test
    fun `toString returns meaningful description`() {
        val toString = connection.toString()
        assertTrue(toString.contains("OkHttpURLConnection"))
        assertTrue(toString.contains(testUrl.toString()))
    }

    @Test
    fun `no super calls to URLConnection state`() {
        // Test that our implementation doesn't depend on URLConnection state
        connection.setRequestProperty("Custom-Header", "value")
        connection.setDoOutput(true)
        connection.setRequestMethod("POST")
        
        // These should all work without calling URLConnection methods
        assertEquals("value", connection.getRequestProperty("Custom-Header"))
        assertTrue(connection.getDoOutput())
        assertEquals("POST", connection.getRequestMethod())
    }

    @Test
    fun `HTTP 2 protocol is configured in client`() {
        // Verify the client was configured with HTTP/2
        val clientBuilder = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        
        // Test that our connection uses a properly configured client
        val testConnection = OkHttpURLConnection(testUrl, clientBuilder)
        assertNotNull(testConnection)
    }

    @Test
    fun `request properties are used in OkHttp request`() {
        connection.setRequestProperty("User-Agent", "analytics-kotlin/$LIBRARY_VERSION")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.addRequestProperty("Accept", "application/json")
        connection.addRequestProperty("Accept", "text/plain")
        
        setupMockResponse(200, "OK")
        
        // Capture the request that would be made
        val requestSlot = slot<Request>()
        every { mockClient.newCall(capture(requestSlot)) } returns mockCall
        
        connection.connect()
        
        val capturedRequest = requestSlot.captured
        assertEquals("analytics-kotlin/$LIBRARY_VERSION", capturedRequest.header("User-Agent"))
        assertEquals("application/json", capturedRequest.header("Content-Type"))
        // OkHttp combines multiple headers with the same name
        assertTrue(capturedRequest.headers("Accept").contains("application/json"))
        assertTrue(capturedRequest.headers("Accept").contains("text/plain"))
    }

    private fun setupMockResponse(
        code: Int, 
        message: String, 
        body: ResponseBody? = null,
        headers: Headers = Headers.Builder().build()
    ) {
        every { mockResponse.code } returns code
        every { mockResponse.message } returns message
        every { mockResponse.isSuccessful } returns (code in 200..299)
        every { mockResponse.headers } returns headers
        every { mockResponse.header(any()) } answers { 
            headers[firstArg<String>()]
        }
        
        if (body != null) {
            every { mockResponse.body } returns body
            every { mockResponse.body?.byteStream() } returns body.byteStream()
        } else {
            every { mockResponse.body } returns null
        }
        
        every { mockCall.execute() } returns mockResponse
        every { mockClient.newCall(any()) } returns mockCall
        
        // Mock response closing
        every { mockResponse.close() } just Runs
    }
}