package com.segment.analytics.kotlin.core

import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.zip.GZIPOutputStream
import java.util.Base64

class HTTPClient {
    fun settings(writeKey: String): Connection {
        val connection: HttpURLConnection = openConnection("https://cdn-settings.segment.com/v1/projects/$writeKey/settings")
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("HTTP " + responseCode + ": " + connection.responseMessage)
        }
        return connection.createGetConnection()
    }

    fun upload(apiHost: String, writeKey: String): Connection {
        val connection: HttpURLConnection = openConnection("https://$apiHost/import")
        connection.setRequestProperty("Authorization", authorizationHeader(writeKey))
        connection.setRequestProperty("Content-Encoding", "gzip")
        connection.doOutput = true
        connection.setChunkedStreamingMode(0)
        return connection.createPostConnection()
    }

    private fun authorizationHeader(writeKey: String): String {
        return "Basic " + Base64.getEncoder().encodeToString(writeKey.toByteArray())
    }

    /**
     * Configures defaults for connections opened with [.upload], and [ ][.projectSettings].
     */
    @Throws(IOException::class)
    private fun openConnection(url: String): HttpURLConnection {
        val requestedURL: URL
        requestedURL = try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw IOException("Attempted to use malformed url: $url", e)
        }
        val connection = requestedURL.openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000 // 15s
        connection.readTimeout = 20_1000 // 20s

        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty(
            "User-Agent",
            "analytics-android/1.0.0"
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

fun safeGetInputStream(connection: HttpURLConnection):InputStream {
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
    val outputStream: OutputStream
    outputStream = GZIPOutputStream(this.outputStream)
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
                        responseBody = inputStream.bufferedReader().use(BufferedReader::readText)
                    } catch (e: IOException) {
                        responseBody = ("Could not read response body for rejected message: "
                                + e.toString())
                    } finally {
                        inputStream?.close()
                    }
                    throw HTTPException(
                        responseCode, connection.responseMessage, responseBody
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
    val responseBody: String?
) :
    IOException("HTTP $responseCode: $responseMessage. Response: ${responseBody ?: "No response"}") {
    fun is4xx(): Boolean {
        return responseCode in 400..499
    }
}