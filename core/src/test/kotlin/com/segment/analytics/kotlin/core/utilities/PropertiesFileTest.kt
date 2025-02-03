package com.segment.analytics.kotlin.core.utilities


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class PropertiesFileTest {

    private val directory = File("/tmp/analytics-test/123")
    private val kvStore = PropertiesFile(File(directory, "123"))

    @BeforeEach
    internal fun setUp() {
        directory.deleteRecursively()
        kvStore.load()
    }

    @Test
    fun `test properties file operations`() {
        kvStore.putString("string", "test")
        kvStore.putInt("int", 1)

        assertEquals(kvStore.get("string", ""), "test")
        assertEquals(kvStore.get("int", 0), 1)

        kvStore.remove("int")
        assertEquals(kvStore.get("int", 0), 0)
    }
}