package com.segment.analytics.kotlin.core.utilities

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DateTimeUtilsTest {

    @Test
    fun `dateTimeNowString() produces a string in the correct format`() {
        val dateTimeNowString = dateTimeNowString()
        val regex = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:\\d{2}\\.\\d{3}Z$")
        assertTrue(regex.matches(dateTimeNowString))
    }
}
