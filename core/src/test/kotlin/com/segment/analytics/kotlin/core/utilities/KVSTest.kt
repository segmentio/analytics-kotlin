package com.segment.analytics.kotlin.core.utilities

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KVSTest {
    @Nested
    inner class InMemoryPrefsTest {
        private lateinit var prefs: KVS

        @BeforeEach
        fun setup(){
            prefs = InMemoryPrefs()
            prefs.put("int", 1)
            prefs.put("string", "string")
        }

        @Test
        fun getTest() {
            assertEquals(1, prefs.get("int", 0))
            assertEquals("string", prefs.get("string", null))
            assertEquals(0, prefs.get("keyNotExists", 0))
            assertEquals(null, prefs.get("keyNotExists", null))
        }

        @Test
        fun putTest() {
            prefs.put("int", 2)
            prefs.put("string", "stringstring")

            assertEquals(2, prefs.get("int", 0))
            assertEquals("stringstring", prefs.get("string", null))
        }

        @Test
        fun containsAndRemoveTest() {
            assertTrue(prefs.contains("int"))
            prefs.remove("int")
            assertFalse(prefs.contains("int"))
        }
    }
}