package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.Constants.LIBRARY_VERSION
import com.segment.analytics.kotlin.core.utilities.encodeToBase64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.future.await
import java.io.IOException
import java.net.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors

class HTTPClient(
    private val writeKey: String,
    dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
) {
    internal val authHeader: String

    private val client: HttpClient

    init {
        authHeader = authorizationHeader(writeKey)
        client = HttpClient.newBuilder()
            .executor(dispatcher.asExecutor())
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }

    suspend fun settings(cdnHost: String): String {
        val request = makeRequest("https://$cdnHost/projects/$writeKey/settings")
            .GET()
            .build()
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await().body()
    }

    suspend fun upload(apiHost: String, path: Path) {
        val request = makeRequest("https://$apiHost/batch")
            .POST(HttpRequest.BodyPublishers.ofFile(path))
            .header("Authorization", authHeader)
            .header("Content-Encoding", "gzip")
            .build()

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await().apply {
            if (statusCode() >= 300) {
                throw HTTPException(
                    statusCode(), body()
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
    private fun makeRequest(url: String): HttpRequest.Builder {
        val requestedURL = try {
            URI.create(url)
        } catch (e: URISyntaxException) {
            throw IOException("Attempted to use malformed url: $url", e)
        }

        val request = HttpRequest.newBuilder()
            .uri(requestedURL)
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json; charset=utf-8")
            .header(
                "User-Agent",
                "analytics-kotlin/$LIBRARY_VERSION")
        return request
    }
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