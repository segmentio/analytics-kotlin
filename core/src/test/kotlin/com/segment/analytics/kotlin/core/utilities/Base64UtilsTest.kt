package com.segment.analytics.kotlin.core.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Base64UtilsTest {

    @Test
    fun testBase64Encoding() {
        assertEquals(encodeToBase64(""), "")
        assertEquals(encodeToBase64("f"), "Zg==")
        assertEquals(encodeToBase64("fo"), "Zm8=")
        assertEquals(encodeToBase64("foo"), "Zm9v")
        assertEquals(encodeToBase64("foob"), "Zm9vYg==")
        assertEquals(encodeToBase64("fooba"), "Zm9vYmE=")
        assertEquals(encodeToBase64("foobar"), "Zm9vYmFy")
    }
}