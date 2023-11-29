package com.segment.analytics.kotlin.core.utilities

import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.util.*

class DateTimeUtilsTest {

    @Test
    fun `dateTimeNowString() produces a string in the correct ISO8601 format`() {
        val dateTimeNowString = SegmentInstant.now()
        val date = ISO_DATE_TIME.parse(dateTimeNowString)
        assertNotNull(date)
    }

    @Test
    fun `dateTimeNowString() returns three digit seconds`() {
        val date = Date(1700617928023L)
        val dateTimeNowString = SegmentInstant.from(date)
        assertEquals("2023-11-22T01:52:08.023Z", dateTimeNowString)
    }
}
