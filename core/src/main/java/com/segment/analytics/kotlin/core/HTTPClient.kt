package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import com.segment.analytics.kotlin.core.utilities.OkHttpURLConnection
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
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