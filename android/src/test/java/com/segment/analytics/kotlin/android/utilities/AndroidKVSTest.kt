package com.segment.analytics.kotlin.android.utilities

import com.segment.analytics.kotlin.android.utils.MemorySharedPreferences
import com.segment.analytics.kotlin.core.utilities.KVS
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AndroidKVSTest {

    private lateinit var prefs: KVS

    @BeforeEach
    fun setup(){
        val sharedPreferences = MemorySharedPreferences()
        prefs = AndroidKVS(sharedPreferences)
        prefs.put("int", 1)
        prefs.put("string", "string")
    }

    @Test
    fun getTest() {
        Assertions.assertEquals(1, prefs.get("int", 0))
        Assertions.assertEquals("string", prefs.get("string", null))
        Assertions.assertEquals(0, prefs.get("keyNotExists", 0))
        Assertions.assertEquals(null, prefs.get("keyNotExists", null))
    }

    @Test
    fun putTest() {
        prefs.put("int", 2)
        prefs.put("string", "stringstring")

        Assertions.assertEquals(2, prefs.get("int", 0))
        Assertions.assertEquals("stringstring", prefs.get("string", null))
    }

    @Test
    fun containsAndRemoveTest() {
        Assertions.assertTrue(prefs.contains("int"))
        prefs.remove("int")
        Assertions.assertFalse(prefs.contains("int"))
    }
}