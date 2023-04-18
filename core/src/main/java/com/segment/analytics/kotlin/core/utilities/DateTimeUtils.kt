package com.segment.analytics.kotlin.core.utilities

import java.text.SimpleDateFormat
import java.util.*

fun dateTimeNowString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:sszzz")
    val utc = TimeZone.getTimeZone("UTC");
    sdf.timeZone = utc;
    return sdf.format(Date()).replace("UTC", "Z")
}