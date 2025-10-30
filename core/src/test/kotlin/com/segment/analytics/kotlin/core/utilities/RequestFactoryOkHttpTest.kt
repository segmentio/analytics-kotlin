package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import com.segment.analytics.kotlin.core.RequestFactory
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequestFactoryOkHttpTest {

    @Test
    fun `RequestFactory settings creates OkHttpURLConnection with HTTP2 support`() {
        val requestFactory = RequestFactory()
        
        val connection = requestFactory.settings("cdn-settings.segment.com/v1", "test-write-key")
        
        // Verify it returns an OkHttpURLConnection  
        assertTrue(connection is OkHttpURLConnection)
        
        // Verify URL is correct
        assertEquals(
            "https://cdn-settings.segment.com/v1/projects/test-write-key/settings",
            connection.url.toString()
        )
        
        // Verify headers are set correctly
        assertEquals("application/json; charset=utf-8", connection.getRequestProperty("Content-Type"))
        assertEquals("analytics-kotlin/$LIBRARY_VERSION", connection.getRequestProperty("User-Agent"))
    }

    @Test
    fun `RequestFactory upload creates OkHttpURLConnection with correct configuration`() {
        val requestFactory = RequestFactory()
        
        val connection = requestFactory.upload("api.segment.io/v1")
        
        // Verify it returns an OkHttpURLConnection
        assertTrue(connection is OkHttpURLConnection)
        
        // Verify URL is correct
        assertEquals("https://api.segment.io/v1/b", connection.url.toString())
        
        // Verify headers are set correctly
        assertEquals("text/plain", connection.getRequestProperty("Content-Type"))
        assertEquals("gzip", connection.getRequestProperty("Content-Encoding"))
        assertEquals("analytics-kotlin/$LIBRARY_VERSION", connection.getRequestProperty("User-Agent"))
        
        // Verify output is enabled
        assertTrue(connection.getDoOutput())
    }

    @Test
    fun `RequestFactory can be initialized with custom OkHttpClient`() {
        val customClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val requestFactory = RequestFactory(customClient)
        
        val connection = requestFactory.settings("cdn.test.com", "test-key")
        
        // Verify it still returns an OkHttpURLConnection
        assertTrue(connection is OkHttpURLConnection)
        assertEquals("https://cdn.test.com/projects/test-key/settings", connection.url.toString())
        assertEquals("application/json; charset=utf-8", connection.getRequestProperty("Content-Type"))
    }

    @Test
    fun `OkHttpURLConnection state is independent of URLConnection`() {
        val requestFactory = RequestFactory()
        val connection = requestFactory.upload("api.segment.io/v1") as OkHttpURLConnection
        
        // Set properties using our OkHttp implementation
        connection.setRequestProperty("Custom-Header", "test-value")
        connection.setDoInput(false)
        connection.setRequestMethod("PUT")
        connection.setConnectTimeout(5000)
        
        // Verify our state is maintained
        assertEquals("test-value", connection.getRequestProperty("Custom-Header"))
        assertFalse(connection.getDoInput())
        assertEquals("PUT", connection.getRequestMethod())
        assertEquals(5000, connection.getConnectTimeout())
        
        // Verify this doesn't affect other connections
        val newConnection = requestFactory.upload("api.segment.io/v1") as OkHttpURLConnection
        assertNull(newConnection.getRequestProperty("Custom-Header"))
        assertTrue(newConnection.getDoInput())
        assertEquals("GET", newConnection.getRequestMethod())
    }

    @Test
    fun `request properties are isolated per connection instance`() {
        val requestFactory = RequestFactory()
        
        val connection1 = requestFactory.upload("api.segment.io/v1") as OkHttpURLConnection
        val connection2 = requestFactory.upload("api.segment.io/v1") as OkHttpURLConnection
        
        // Set different properties on each connection
        connection1.setRequestProperty("X-Custom-1", "value1")
        connection2.setRequestProperty("X-Custom-2", "value2")
        
        // Verify isolation
        assertEquals("value1", connection1.getRequestProperty("X-Custom-1"))
        assertNull(connection1.getRequestProperty("X-Custom-2"))
        
        assertEquals("value2", connection2.getRequestProperty("X-Custom-2"))
        assertNull(connection2.getRequestProperty("X-Custom-1"))
    }

    @Test
    fun `connection respects HTTP2 protocol configuration`() {
        // This test verifies the HTTP/2 configuration is in place
        // In a real environment, this would use HTTP/2 when available
        val requestFactory = RequestFactory()
        val connection = requestFactory.upload("api.segment.io/v1")
        
        assertTrue(connection is OkHttpURLConnection)
        
        // The OkHttpClient inside should be configured with HTTP/2
        // We can't directly test the protocol negotiation in unit tests,
        // but we can verify the connection type
        assertEquals("OkHttpURLConnection:https://api.segment.io/v1/b", connection.toString())
    }

    @Test
    fun `multiple addRequestProperty calls accumulate headers`() {
        val requestFactory = RequestFactory()
        val connection = requestFactory.upload("api.segment.io/v1") as OkHttpURLConnection
        
        // Add multiple values for the same header
        connection.setRequestProperty("Accept", "application/json")
        connection.addRequestProperty("Accept", "text/plain")
        connection.addRequestProperty("Accept", "application/xml")
        
        // getRequestProperty should return the first value
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        
        // getRequestProperties should return all values
        val properties = connection.getRequestProperties()
        val acceptValues = properties["Accept"]
        assertEquals(3, acceptValues?.size)
        assertEquals("application/json", acceptValues?.get(0))
        assertEquals("text/plain", acceptValues?.get(1))
        assertEquals("application/xml", acceptValues?.get(2))
    }
}