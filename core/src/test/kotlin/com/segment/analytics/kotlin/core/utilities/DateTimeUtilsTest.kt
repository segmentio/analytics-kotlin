package com.segment.analytics.kotlin.core.utilities

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

class DateTimeUtilsTest {

    @Test
    fun `dateTimeNowString() produces a string in the correct ISO8601 format`() {
        val dateTimeNowString = dateTimeNowString()
        val date = ISO_DATE_TIME.parse(dateTimeNowString)
        assertNotNull(date)
    }
}
