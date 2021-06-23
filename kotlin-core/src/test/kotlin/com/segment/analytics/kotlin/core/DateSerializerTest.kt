package com.segment.analytics.kotlin.core

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DateSerializerTest {
    @Test
    fun `dates are properly serialized`() {
        val d1 = Instant.EPOCH
        val d2 = Instant.ofEpochSecond(1615918845)
        val d3 = Instant.parse("2021-03-16T18:20:45Z")
        assertEquals("1970-01-01T00:00:00Z".jsonified(), d1.serialize())
        assertEquals("2021-03-16T18:20:45Z".jsonified(), d2.serialize())
        assertEquals("2021-03-16T18:20:45Z".jsonified(), d3.serialize())
    }

    @Test
    fun `dates are properly de-serialized`() {
        val d1 = "1970-01-01T00:00:00Z".jsonified()
        val d2 = "2021-03-16T18:20:45Z".jsonified()
        val d3 = "2021-03-16T18:20:45.000Z".jsonified()
        val d4 = "2021-03-16T18:20:45.000000000Z".jsonified()
//        val d5 = "2021-03-16T20:20:45+02:00".jsonified() // Need to support offset date time
        val d2Expected = Instant.ofEpochSecond(1615918845)
        assertEquals(Instant.EPOCH, d1.deserialize())
        assertEquals(d2Expected, d2.deserialize())
        assertEquals(d2Expected, d3.deserialize())
        assertEquals(d2Expected, d4.deserialize())
    }

    private fun Instant.serialize(): String {
        return Json.encodeToString(DateSerializer(), this)
    }

    private fun String.deserialize(): Instant {
        return Json.decodeFromString(DateSerializer(), this)
    }

    private fun String.jsonified(): String {
        return "\"$this\""
    }

}
