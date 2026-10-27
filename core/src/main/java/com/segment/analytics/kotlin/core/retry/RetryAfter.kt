package com.segment.analytics.kotlin.core.retry

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses a Retry-After header value into seconds from [nowMs]. The value is either
 * delay-seconds or an HTTP-date; a date that has already passed yields 0.
 * Returns null when the header is missing or unparseable.
 */
internal fun parseRetryAfterSeconds(value: String?, nowMs: Long): Int? {
    val trimmed = value?.trim()
    if (trimmed.isNullOrEmpty()) return null
    trimmed.toIntOrNull()?.let { return it }

    // SimpleDateFormat rather than java.time, which needs API 26 on Android
    val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
        isLenient = false
    }
    val dateMs = try {
        format.parse(trimmed)?.time
    } catch (e: ParseException) {
        null
    } ?: return null

    val deltaMs = dateMs - nowMs
    if (deltaMs <= 0) return 0
    return ((deltaMs + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
