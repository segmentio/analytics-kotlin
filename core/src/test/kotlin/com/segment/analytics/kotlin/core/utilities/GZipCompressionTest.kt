package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.RequestFactory
import com.segment.analytics.kotlin.core.createPostConnection
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GZipCompressionTest {

    @Test
    fun `GZIP compression roundtrip test with OkHttpURLConnection`() {
        val originalData = """
            {
                "userId": "test-user-123",
                "event": "Page Viewed",
                "properties": {
                    "page": "Login",
                    "url": "https://example.com/login",
                    "referrer": "https://google.com",
                    "timestamp": "2023-10-30T12:00:00Z"
                },
                "context": {
                    "library": {
                        "name": "analytics-kotlin",
                        "version": "1.21.0"
                    },
                    "device": {
                        "type": "mobile",
                        "manufacturer": "Apple",
                        "model": "iPhone 14"
                    }
                }
            }
        """.trimIndent()

        val requestFactory = RequestFactory()
        val connection = requestFactory.openConnection("https://api.segment.io/v1/b") as OkHttpURLConnection

        // Set up the connection for GZIP
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Content-Encoding", "gzip")
        connection.doOutput = true

        // Create post connection which wraps output stream in GZIPOutputStream
        val postConnection = connection.createPostConnection()

        // Write data to the GZIP-wrapped stream
        postConnection.outputStream!!.write(originalData.toByteArray())
        postConnection.outputStream!!.close() // Important: close to flush GZIP data

        // Build the request with the compressed data
        val request = connection.buildRequest()

        // Verify the request has a body
        assertNotNull(request.body, "Request body should not be null after writing data")

        // Extract the compressed data from the request body
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val compressedData = buffer.readByteArray()

        // Verify we have some data
        assertTrue(compressedData.isNotEmpty(), "Compressed data should not be empty")

        // Verify the data is actually compressed (should be different from original)
        assertFalse(compressedData.contentEquals(originalData.toByteArray()),
            "Compressed data should be different from original")

        // Decompress the data to verify it matches the original
        val decompressedData = decompressGZIP(compressedData)
        val decompressedString = String(decompressedData)

        // Verify the decompressed data matches the original
        assertEquals(originalData, decompressedString, "Decompressed data should match original")

        // Verify the compressed data is smaller than original (for this size of data)
        assertTrue(compressedData.size < originalData.toByteArray().size,
            "Compressed size: ${compressedData.size}, Original size: ${originalData.toByteArray().size}")
    }
    
    @Test
    fun `GZIP compression with empty data`() {
        val originalData = ""

        val requestFactory = RequestFactory()
        val connection = requestFactory.openConnection("https://api.segment.io/v1/b") as OkHttpURLConnection

        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Content-Encoding", "gzip")
        connection.doOutput = true
        connection.requestMethod = "POST"

        // Create post connection which wraps output stream in GZIPOutputStream
        val postConnection = connection.createPostConnection()

        postConnection.outputStream!!.write(originalData.toByteArray())
        postConnection.outputStream!!.close()

        val request = connection.buildRequest()
        assertNotNull(request.body)

        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val compressedData = buffer.readByteArray()

        val decompressedData = decompressGZIP(compressedData)
        val decompressedString = String(decompressedData)

        assertEquals(originalData, decompressedString)
    }
    
    @Test
    fun `GZIP compression with large data`() {
        // Create a large JSON payload
        val largeData = buildString {
            append("{\"events\": [")
            repeat(1000) { i ->
                if (i > 0) append(",")
                append("""
                    {
                        "userId": "user-$i",
                        "event": "Event $i",
                        "properties": {
                            "index": $i,
                            "data": "This is some sample data for event $i with more content to make it larger",
                            "timestamp": "2023-10-30T12:${i.toString().padStart(2, '0')}:00Z"
                        }
                    }
                """.trimIndent())
            }
            append("]}")
        }

        val requestFactory = RequestFactory()
        val connection = requestFactory.openConnection("https://api.segment.io/v1/b") as OkHttpURLConnection

        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Content-Encoding", "gzip")
        connection.doOutput = true
        connection.requestMethod = "POST"

        // Create post connection which wraps output stream in GZIPOutputStream
        val postConnection = connection.createPostConnection()

        postConnection.outputStream!!.write(largeData.toByteArray())
        postConnection.outputStream!!.close()

        val request = connection.buildRequest()
        assertNotNull(request.body)

        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val compressedData = buffer.readByteArray()

        val decompressedData = decompressGZIP(compressedData)
        val decompressedString = String(decompressedData)

        assertEquals(largeData, decompressedString)

        // Large data should compress significantly
        val compressionRatio = compressedData.size.toDouble() / largeData.toByteArray().size.toDouble()
        assertTrue(compressionRatio < 0.5,
            "Compression ratio should be < 50% for large repetitive data. Actual: ${compressionRatio * 100}%")
    }
    
    @Test
    fun `Compare OkHttp GZIP with standard GZIP`() {
        val testData = """
            {
                "writeKey": "test-key",
                "batch": [
                    {
                        "type": "track",
                        "userId": "user-123",
                        "event": "Button Clicked",
                        "properties": {
                            "button": "Sign Up",
                            "page": "Homepage"
                        }
                    }
                ]
            }
        """.trimIndent()

        // Compress using OkHttpURLConnection with createPostConnection
        val requestFactory = RequestFactory()
        val connection = requestFactory.openConnection("https://api.segment.io/v1/b") as OkHttpURLConnection

        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Content-Encoding", "gzip")
        connection.doOutput = true
        connection.requestMethod = "POST"

        // Create post connection which wraps output stream in GZIPOutputStream
        val postConnection = connection.createPostConnection()

        postConnection.outputStream!!.write(testData.toByteArray())
        postConnection.outputStream!!.close()

        val request = connection.buildRequest()
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val okHttpCompressed = buffer.readByteArray()

        // Compress using standard GZIP
        val standardCompressed = compressGZIP(testData.toByteArray())

        // Both should decompress to the same original data
        val okHttpDecompressed = String(decompressGZIP(okHttpCompressed))
        val standardDecompressed = String(decompressGZIP(standardCompressed))

        assertEquals(testData, okHttpDecompressed)
        assertEquals(testData, standardDecompressed)
        assertEquals(okHttpDecompressed, standardDecompressed)

        // The compressed data might be slightly different due to compression settings,
        // but should be similar in size
        val sizeDifference = kotlin.math.abs(okHttpCompressed.size - standardCompressed.size)
        assertTrue(sizeDifference < 50,
            "Compressed sizes should be similar. OkHttp: ${okHttpCompressed.size}, Standard: ${standardCompressed.size}")
    }
    
    @Test
    fun `Verify no compression when Content-Encoding is not gzip`() {
        val testData = """{"test": "data"}"""

        val requestFactory = RequestFactory()
        val connection = requestFactory.openConnection("https://api.segment.io/v1/b") as OkHttpURLConnection

        connection.setRequestProperty("Content-Type", "application/json")
        // NO Content-Encoding header set
        connection.doOutput = true
        connection.requestMethod = "POST"

        // Create post connection - should NOT wrap in GZIPOutputStream since no gzip encoding header
        val postConnection = connection.createPostConnection()

        postConnection.outputStream!!.write(testData.toByteArray())
        postConnection.outputStream!!.close()

        val request = connection.buildRequest()
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val bodyData = buffer.readByteArray()

        // Data should be uncompressed (same as original)
        assertEquals(testData, String(bodyData))
    }
    
    @Test
    fun `Test UTF-8 encoding with GZIP compression`() {
        val testData = """
            {
                "user": "测试用户",
                "event": "página_vista",
                "properties": {
                    "título": "Página de inicio",
                    "descripción": "Esta es una descripción con caracteres especiales: àáâãäåæçèéêë",
                    "emoji": "🎉🚀💻🌟",
                    "japanese": "こんにちは世界",
                    "russian": "Привет мир",
                    "arabic": "مرحبا بالعالم"
                }
            }
        """.trimIndent()

        val requestFactory = RequestFactory()
        val connection = requestFactory.openConnection("https://api.segment.io/v1/b") as OkHttpURLConnection

        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Content-Encoding", "gzip")
        connection.doOutput = true
        connection.requestMethod = "POST"

        // Create post connection which wraps output stream in GZIPOutputStream
        val postConnection = connection.createPostConnection()

        postConnection.outputStream!!.write(testData.toByteArray(Charsets.UTF_8))
        postConnection.outputStream!!.close()

        val request = connection.buildRequest()
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val compressedData = buffer.readByteArray()

        val decompressedData = decompressGZIP(compressedData)
        val decompressedString = String(decompressedData, Charsets.UTF_8)

        assertEquals(testData, decompressedString)
    }
    
    private fun compressGZIP(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzipStream ->
            gzipStream.write(data)
            gzipStream.flush()
        }
        return outputStream.toByteArray()
    }
    
    private fun decompressGZIP(compressedData: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(compressedData)).use { gzipStream ->
            gzipStream.readBytes()
        }
    }
}