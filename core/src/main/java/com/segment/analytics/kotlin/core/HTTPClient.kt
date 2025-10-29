package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
    
    private var response: Response? = null
    private var requestBodyBuffer: Buffer? = null
    private var connected = false
    
    private val requestBuilder = Request.Builder().url(url)
    private val requestProperties = mutableMapOf<String, MutableList<String>>()
    
    // OkHttp-only state management
    private var _requestMethod = "GET"
    private var _doInput = true
    private var _doOutput = false
    private var _allowUserInteraction = false
    private var _useCaches = true
    private var _ifModifiedSince = 0L
    private var _connectTimeout = 15000
    private var _readTimeout = 20000
    private var _instanceFollowRedirects = true
    
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
        
        requestProperties.forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }
        
        when (_requestMethod) {
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
            else -> throw ProtocolException("Unknown method: $_requestMethod")
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
    
    // Override ALL URLConnection methods to prevent bypassing OkHttp
    override fun getURL(): URL = url
    
    override fun getHeaderFieldKey(n: Int): String? {
        if (!connected) {
            try { connect() } catch (e: IOException) { return null }
        }
        val headers = response?.headers ?: return null
        return if (n in 0 until headers.size) headers.name(n) else null
    }
    
    override fun getHeaderField(n: Int): String? {
        if (!connected) {
            try { connect() } catch (e: IOException) { return null }
        }
        val headers = response?.headers ?: return null
        return if (n in 0 until headers.size) headers.value(n) else null
    }
    
    
    override fun getPermission(): java.security.Permission? {
        return java.net.SocketPermission("${url.host}:${if (url.port == -1) url.defaultPort else url.port}", "connect,resolve")
    }
    
    override fun setAuthenticator(auth: java.net.Authenticator?) {
        // OkHttp handles authentication differently, this is a no-op
    }
    
    override fun setChunkedStreamingMode(chunklen: Int) {
        // OkHttp handles chunked encoding automatically, store for compatibility
    }
    
    override fun setFixedLengthStreamingMode(contentLength: Int) {
        // OkHttp handles content length automatically, store for compatibility
    }
    
    override fun setFixedLengthStreamingMode(contentLength: Long) {
        // OkHttp handles content length automatically, store for compatibility
    }
    
    override fun setRequestMethod(method: String) {
        if (connected) {
            throw IllegalStateException("Already connected")
        }
        _requestMethod = method
    }
    
    override fun getHeaderFieldInt(name: String?, default: Int): Int {
        return getHeaderField(name)?.toIntOrNull() ?: default
    }
    
    override fun getHeaderFieldLong(name: String?, default: Long): Long {
        return getHeaderField(name)?.toLongOrNull() ?: default
    }
    
    override fun getHeaderFieldDate(name: String?, default: Long): Long {
        return getHeaderField(name)?.let { dateStr ->
            try {
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                    .parse(dateStr)?.time ?: default
            } catch (e: Exception) { default }
        } ?: default
    }
    
    override fun getContentLength(): Int {
        return getHeaderField("Content-Length")?.toIntOrNull() ?: -1
    }
    
    override fun getContentLengthLong(): Long {
        return getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
    }
    
    override fun getContentType(): String? {
        return getHeaderField("Content-Type")
    }
    
    override fun getContentEncoding(): String? {
        return getHeaderField("Content-Encoding")
    }
    
    override fun getDate(): Long {
        return getHeaderField("Date")?.let { dateStr ->
            try {
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                    .parse(dateStr)?.time ?: 0L
            } catch (e: Exception) { 0L }
        } ?: 0L
    }
    
    override fun getExpiration(): Long {
        return getHeaderField("Expires")?.let { dateStr ->
            try {
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                    .parse(dateStr)?.time ?: 0L
            } catch (e: Exception) { 0L }
        } ?: 0L
    }
    
    override fun getLastModified(): Long {
        return getHeaderField("Last-Modified")?.let { dateStr ->
            try {
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                    .parse(dateStr)?.time ?: 0L
            } catch (e: Exception) { 0L }
        } ?: 0L
    }
    
    override fun getDoInput(): Boolean = _doInput
    override fun setDoInput(doinput: Boolean) { _doInput = doinput }
    
    override fun getDoOutput(): Boolean = _doOutput
    override fun setDoOutput(dooutput: Boolean) { _doOutput = dooutput }
    
    override fun getAllowUserInteraction(): Boolean = _allowUserInteraction
    override fun setAllowUserInteraction(allowuserinteraction: Boolean) { 
        _allowUserInteraction = allowuserinteraction 
    }
    
    override fun getUseCaches(): Boolean = _useCaches
    override fun setUseCaches(usecaches: Boolean) { _useCaches = usecaches }
    
    override fun getIfModifiedSince(): Long = _ifModifiedSince
    override fun setIfModifiedSince(ifmodifiedsince: Long) { _ifModifiedSince = ifmodifiedsince }
    
    override fun getDefaultUseCaches(): Boolean = _useCaches
    override fun setDefaultUseCaches(defaultusecaches: Boolean) { _useCaches = defaultusecaches }
    
    override fun setRequestProperty(key: String?, value: String?) {
        if (connected) throw IllegalStateException("Already connected")
        if (key != null && value != null) {
            requestProperties[key] = mutableListOf(value)
        }
    }
    
    override fun addRequestProperty(key: String?, value: String?) {
        if (connected) throw IllegalStateException("Already connected")
        if (key != null && value != null) {
            requestProperties.computeIfAbsent(key) { mutableListOf() }.add(value)
        }
    }
    
    override fun getRequestProperty(key: String?): String? {
        return requestProperties[key]?.firstOrNull()
    }
    
    override fun getRequestProperties(): MutableMap<String, MutableList<String>> {
        if (connected) throw IllegalStateException("Already connected")
        return requestProperties.toMutableMap()
    }
    
    override fun setConnectTimeout(timeout: Int) {
        _connectTimeout = timeout
    }
    
    override fun getConnectTimeout(): Int = _connectTimeout
    
    override fun setReadTimeout(timeout: Int) {
        _readTimeout = timeout
    }
    
    override fun getReadTimeout(): Int = _readTimeout
    
    override fun toString(): String {
        return "OkHttpURLConnection:$url"
    }
    
    // Override ALL HttpURLConnection specific methods
    override fun getRequestMethod(): String = _requestMethod
    
    override fun getInstanceFollowRedirects(): Boolean = _instanceFollowRedirects
    override fun setInstanceFollowRedirects(followRedirects: Boolean) { 
        _instanceFollowRedirects = followRedirects 
    }

    override fun getContent(): Any? {
        if (!connected) connect()
        
        val contentType = getContentType()
        val inputStream = getInputStream()
        
        return when {
            contentType?.startsWith("text/") == true -> {
                inputStream.bufferedReader().use { it.readText() }
            }
            contentType?.startsWith("image/") == true -> {
                inputStream.readBytes()
            }
            contentType?.startsWith("application/json") == true -> {
                inputStream.bufferedReader().use { it.readText() }
            }
            contentType?.startsWith("application/xml") == true -> {
                inputStream.bufferedReader().use { it.readText() }
            }
            else -> {
                inputStream
            }
        }
    }

    override fun getContent(classes: Array<out Class<*>?>?): Any? {
        if (!connected) connect()
        
        val content = getContent()
        if (classes == null || classes.isEmpty()) {
            return content
        }
        
        // Try to match the content to one of the requested classes
        for (clazz in classes) {
            when (clazz) {
                String::class.java -> {
                    if (content is String) return content
                    if (content is InputStream) {
                        return content.bufferedReader().use { it.readText() }
                    }
                    if (content is ByteArray) {
                        return String(content)
                    }
                }
                ByteArray::class.java -> {
                    if (content is ByteArray) return content
                    if (content is String) return content.toByteArray()
                    if (content is InputStream) {
                        return content.readBytes()
                    }
                }
                InputStream::class.java -> {
                    if (content is InputStream) return content
                    return getInputStream()
                }
            }
        }
        
        return content
    }
}

open class RequestFactory(
    httpClient: OkHttpClient? = null
) {
    private val okHttpClient = httpClient ?: OkHttpClient.Builder()
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