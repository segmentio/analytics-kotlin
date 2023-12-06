package com.segment.analytics.kotlin.core.utilities

import java.text.SimpleDateFormat
import java.util.*

class SegmentInstant {
    companion object {
        private val formatters = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.'SSSzzz", Locale.ROOT).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
        }

        /**
         * This function is a replacement for Instant.now().toString(). It produces strings in a
         * compatible format.
         *
         * Ex:
         * Instant.now():       2023-04-19T04:03:46.880969Z
         * dateTimeNowString(): 2023-04-19T04:03:46.880Z
         */
        fun now(): String {
            return from(Date())
        }

        internal fun from(date: Date): String {
            return formatters.get().format(date).replace("UTC", "Z")
        }
    }
}

@Deprecated("Please use SegmentInstant.now() instead", ReplaceWith("SegmentInstant.now()"))
fun dateTimeNowString(): String {
    return SegmentInstant.now()
}