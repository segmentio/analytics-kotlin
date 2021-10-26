package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import com.segment.analytics.kotlin.core.utilities.encodeToBase64
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class HTTPClient(
    writeKey: String,
    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
) {
    internal var authHeader: String

    private val client: HttpClient

    internal var writeKey: String = writeKey
        set(value) {
            field = value
            authHeader = authorizationHeader(field)
        }

    init {
        authHeader = authorizationHeader(writeKey)
        client = HttpClient(CIO) {
            engine {
                requestTimeout = 20_000
                endpoint {
                    connectTimeout = 15_000
                    socketTimeout = 20_000
                }
            }
        }
    }

    suspend fun settings(cdnHost: String): String {
        val request = makeRequest("https://$cdnHost/projects/$writeKey/settings")
        return client.get<HttpResponse>(request).readText()
    }

    suspend fun upload(apiHost: String, file: File) {
        val request = makeRequest("https://$apiHost/batch")
        request.headers {
            append("Authorization", authHeader)
            append("Content-Encoding", "gzip")
        }
        request.contentType(ContentType.Application.Json)
        request.body = FileContent(file)

        client.post<HttpResponse>(request).apply {
            if (status.value >= 300) {
                throw HTTPException(
                    status.value, readText()
                )
            }
        }
    }

    private fun authorizationHeader(writeKey: String): String {
        val auth = "$writeKey:"
        return "Basic ${encodeToBase64(auth)}"
    }

    /**
     * Configures defaults for connections opened with [.upload], and [ ][.projectSettings].
     */
    private fun makeRequest(url: String): HttpRequestBuilder {
        val request = try {
            HttpRequestBuilder {
                takeFrom(url)
            }
        } catch (e: URLParserException) {
            throw IOException("Attempted to use malformed url: $url", e)
        }

        request.headers {
            append("User-Agent","analytics-kotlin/$LIBRARY_VERSION")
        }

        return request
    }
}


class FileContent(private val file: File): OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        file.inputStream().copyTo(channel, 1024)
    }
    override val contentLength: Long = file.length()
}

internal class HTTPException(
    val responseCode: Int,
    responseBody: String?
) :
    IOException("HTTP $responseCode: Response: ${responseBody ?: "No response"}") {
    fun is4xx(): Boolean {
        return responseCode in 400..499
    }
}