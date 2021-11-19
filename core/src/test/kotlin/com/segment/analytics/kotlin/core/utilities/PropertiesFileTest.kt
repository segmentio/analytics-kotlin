package com.segment.analytics.kotlin.core.utilities


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class PropertiesFileTest {

    private val directory = File("/tmp/analytics-android-test/events")
    private val kvStore = PropertiesFile(directory.parentFile, "123")

    @BeforeEach
    internal fun setUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `test properties file operations`() {
        kvStore.putString("string", "test")
        kvStore.putInt("int", 1)

        assertEquals(kvStore.getString("string", ""), "test")
        assertEquals(kvStore.getInt("int", 0), 1)

        kvStore.remove("int")
        assertEquals(kvStore.getInt("int", 0), 0)
    }
}