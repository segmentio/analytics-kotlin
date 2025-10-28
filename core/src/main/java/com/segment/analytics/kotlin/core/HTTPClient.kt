package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.GzipSink
import okio.buffer
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
class HTTPClient(
    private val writeKey: String,
    private val requestFactory: RequestFactory = RequestFactory()
) {

    fun settings(cdnHost: String): Connection {
        val connection: HttpURLConnection = requestFactory.settings(cdnHost, writeKey)
        return connection.createGetConnection()
    }

    fun upload(apiHost: String): Connection {
        val connection: HttpURLConnection = requestFactory.upload(apiHost)
        return connection.createPostConnection()
    }

    /**
     * Configures defaults for connections opened with [.upload], and [ ][.projectSettings].
     */
    @Throws(IOException::class)
    private fun openConnection(url: String): HttpURLConnection {
        val requestedURL: URL = try {
            URL(url)
        } catch (e: MalformedURLException) {
            val error = IOException("Attempted to use malformed url: $url", e)
            reportErrorWithMetrics(null, e,"Attempted to use malformed url: $url",
                Telemetry.INVOKE_ERROR_METRIC, e.stackTraceToString()) {
                it["error"] = e.toString()
                it["writekey"] = writeKey
                it["message"] = "Malformed url"
            }
            throw error
        }
        val connection = requestedURL.openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000 // 15s
        connection.readTimeout = 20_000 // 20s

        connection.setRequestProperty(
            "User-Agent",
            "analytics-kotlin/$LIBRARY_VERSION"
        )
        connection.doInput = true
        return connection
    }
}

/**
 * Wraps an HTTP connection. Callers can either read from the connection via the [ ] or write to the connection via [OutputStream].
 */
abstract class Connection(
    val connection: HttpURLConnection,
    val inputStream: InputStream?,
    val outputStream: OutputStream?
) : Closeable {
    @Throws(IOException::class)
    override fun close() {
        connection.disconnect()
    }
}

fun safeGetInputStream(connection: HttpURLConnection): InputStream? {
    return try {
        connection.inputStream
    } catch (ignored: IOException) {
        connection.errorStream
    }
}

internal fun HttpURLConnection.createGetConnection(): Connection {

    return object : Connection(this, safeGetInputStream(this), null) {
        override fun close() {
            super.close()
            inputStream?.close()
        }
    }
}

internal fun HttpURLConnection.createPostConnection(): Connection {
    val encoding = getRequestProperty("Content-Encoding") ?: ""
    val outputStream: OutputStream =
        if (encoding.contains("gzip")) {
            GZIPOutputStream(this.outputStream)
        }
        else {
            this.outputStream
        }
    return object : Connection(this, null, outputStream) {
        @Throws(IOException::class)
        override fun close() {
            try {
                val responseCode: Int = connection.responseCode
                if (responseCode >= 300) {
                    var responseBody: String?
                    var inputStream: InputStream? = null
                    try {
                        inputStream = safeGetInputStream(this.connection)
                        responseBody = inputStream?.bufferedReader()?.use(BufferedReader::readText)
                    } catch (e: IOException) {
                        Analytics.reportInternalError(e)
                        responseBody = ("Could not read response body for rejected message: "
                                + e.toString())
                    } finally {
                        inputStream?.close()
                    }
                    throw HTTPException(
                        responseCode, connection.responseMessage, responseBody, connection.headerFields
                    )
                }
            } finally {
                super.close()
                this.outputStream?.close()
            }
        }
    }
}

internal class HTTPException(
    val responseCode: Int,
    val responseMessage: String,
    val responseBody: String?,
    val responseHeaders: MutableMap<String, MutableList<String>>
) :
    IOException("HTTP $responseCode: $responseMessage. Response: ${responseBody ?: "No response"}") {
    fun is4xx(): Boolean {
        return responseCode in 400..499
    }
}

internal class OkHttpURLConnection(
    url: URL,
    private val client: OkHttpClient
) : HttpURLConnection(url) {
    
    private var request: Request? = null
    private var response: Response? = null
    private var requestBodyBuffer: Buffer? = null
    private var connected = false
    
    private val requestBuilder = Request.Builder().url(url)
    
    override fun disconnect() {
        response?.close()
        connected = false
    }
    
    override fun usingProxy(): Boolean = false
    
    @Throws(IOException::class)
    override fun connect() {
        if (connected) return
        
        try {
            val finalRequest = buildRequest()
            response = client.newCall(finalRequest).execute()
            responseCode = response!!.code
            responseMessage = response!!.message
            connected = true
        } catch (e: Exception) {
            throw IOException("Connection failed", e)
        }
    }
    
    private fun buildRequest(): Request {
        val builder = requestBuilder
        
        requestProperties?.forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }
        
        when (method) {
            "GET" -> builder.get()
            "POST" -> {
                val body = requestBodyBuffer?.let { buffer ->
                    val isGzipped = getRequestProperty("Content-Encoding")?.contains("gzip") == true
                    val mediaType = getRequestProperty("Content-Type")?.toMediaType() 
                        ?: "text/plain".toMediaType()
                    
                    if (isGzipped) {
                        val gzippedBuffer = Buffer()
                        val gzipSink = GzipSink(gzippedBuffer).buffer()
                        gzipSink.writeAll(buffer)
                        gzipSink.close()
                        gzippedBuffer.readByteArray().toRequestBody(mediaType)
                    } else {
                        buffer.readByteArray().toRequestBody(mediaType)
                    }
                } ?: "".toRequestBody("text/plain".toMediaType())
                builder.post(body)
            }
            "PUT" -> {
                val body = requestBodyBuffer?.readByteArray()?.toRequestBody(
                    getRequestProperty("Content-Type")?.toMediaType() ?: "text/plain".toMediaType()
                ) ?: "".toRequestBody("text/plain".toMediaType())
                builder.put(body)
            }
            "DELETE" -> builder.delete()
            "HEAD" -> builder.head()
            else -> throw ProtocolException("Unknown method: $method")
        }
        
        return builder.build()
    }
    
    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        if (!connected) connect()
        return response?.body?.byteStream() ?: throw IOException("No response body")
    }
    
    @Throws(IOException::class)
    override fun getErrorStream(): InputStream? {
        if (!connected) connect()
        return if (responseCode >= 400) {
            response?.body?.byteStream()
        } else null
    }
    
    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        if (requestBodyBuffer == null) {
            requestBodyBuffer = Buffer()
        }
        return requestBodyBuffer!!.outputStream()
    }
    
    override fun getHeaderField(name: String?): String? {
        if (!connected) {
            try { connect() } catch (e: IOException) { return null }
        }
        return response?.header(name ?: return null)
    }
    
    override fun getHeaderFields(): MutableMap<String, MutableList<String>> {
        if (!connected) {
            try { connect() } catch (e: IOException) { return mutableMapOf() }
        }
        return response?.headers?.toMultimap()?.mapValues { it.value.toMutableList() }?.toMutableMap() 
            ?: mutableMapOf()
    }
    
    override fun getResponseCode(): Int {
        if (!connected) connect()
        return responseCode
    }
    
    override fun getResponseMessage(): String {
        if (!connected) connect()
        return responseMessage ?: ""
    }
}

open class RequestFactory {
    private val okHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    open fun settings(cdnHost: String, writeKey: String): HttpURLConnection {
        val connection: HttpURLConnection = openConnection("https://$cdnHost/projects/$writeKey/settings")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("HTTP " + responseCode + ": " + connection.responseMessage)
        }
        return connection
    }

    open fun upload(apiHost: String): HttpURLConnection {
        val connection: HttpURLConnection = openConnection("https://$apiHost/b")
        connection.setRequestProperty("Content-Type", "text/plain")
        connection.setRequestProperty("Content-Encoding", "gzip")
        connection.doOutput = true
        connection.setChunkedStreamingMode(0)
        return connection
    }

    /**
     * Configures defaults for connections opened with [.upload], and [ ][.projectSettings].
     */
    open fun openConnection(url: String): HttpURLConnection {
        val requestedURL: URL = try {
            URL(url)
        } catch (e: MalformedURLException) {
            val error = IOException("Attempted to use malformed url: $url", e)
            Analytics.reportInternalError(error)
            throw error
        }
        val connection = requestedURL.openOkHttpConnection() as HttpURLConnection
        connection.connectTimeout = 15_000 // 15s
        connection.readTimeout = 20_000 // 20s

        connection.setRequestProperty(
            "User-Agent",
            "analytics-kotlin/$LIBRARY_VERSION"
        )
        connection.doInput = true
        return connection
    }

    private fun URL.openOkHttpConnection(): OkHttpURLConnection {
        return OkHttpURLConnection(this, okHttpClient)
    }
}