package com.segment.analytics.kotlin.core.utilities

import java.text.SimpleDateFormat
import java.util.*

/**
 * This function is a replacement for Instant.now().toString(). It produces strings in a
 * compatible format.
 *
 * Ex:
 * Instant.now():       2023-04-19T04:03:46.880969Z
 * dateTimeNowString(): 2023-04-19T04:03:46.880Z
 */
fun dateTimeNowString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.'Szzz", Locale.ROOT)
    val utc = TimeZone.getTimeZone("UTC");
    sdf.timeZone = utc;
    return sdf.format(Date()).replace("UTC", "Z")
}
