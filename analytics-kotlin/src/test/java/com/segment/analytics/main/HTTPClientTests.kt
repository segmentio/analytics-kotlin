package com.segment.analytics.main

import com.segment.analytics.HTTPClient
import com.segment.analytics.Settings
import com.segment.analytics.emptyJsonObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.BufferedReader
import java.util.zip.GZIPInputStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HTTPClientTests {

    private val httpClient = HTTPClient()

    @BeforeEach
    fun setup() {

    }

    @Test
    fun `fetch settings works`() = runBlocking {
        val connection = httpClient.settings("1vNgUqwJeCHmqgI9S1sOm9UHCyfYqbaQ")
        val settingsString = connection.inputStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
        val settingsObj: Settings = Json { ignoreUnknownKeys = true }.decodeFromString(settingsString)
        assertEquals(emptyJsonObject, settingsObj.edgeFunction)
        assertNotEquals(emptyJsonObject, settingsObj.integrations)
        assertEquals(1, settingsObj.integrations.size)
    }
}