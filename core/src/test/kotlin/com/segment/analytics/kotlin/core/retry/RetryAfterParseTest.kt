package com.segment.analytics.kotlin.core.retry

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RetryAfterParseTest {

    // Wed, 21 Oct 2026 07:28:00 GMT
    private val now = 1_792_567_680_000L

    @Test
    fun `parses delay-seconds`() {
        assertEquals(30, parseRetryAfterSeconds("30", now))
        assertEquals(0, parseRetryAfterSeconds("0", now))
        assertEquals(30, parseRetryAfterSeconds(" 30 ", now))
    }

    @Test
    fun `parses HTTP-date relative to now`() {
        assertEquals(8, parseRetryAfterSeconds("Wed, 21 Oct 2026 07:28:08 GMT", now))
    }

    @Test
    fun `rounds a partial second up`() {
        assertEquals(1, parseRetryAfterSeconds("Wed, 21 Oct 2026 07:28:01 GMT", now + 500))
    }

    @Test
    fun `HTTP-date in the past yields 0`() {
        assertEquals(0, parseRetryAfterSeconds("Wed, 21 Oct 2026 07:27:00 GMT", now))
    }

    @Test
    fun `missing or unparseable values yield null`() {
        assertNull(parseRetryAfterSeconds(null, now))
        assertNull(parseRetryAfterSeconds("", now))
        assertNull(parseRetryAfterSeconds("soon", now))
        assertNull(parseRetryAfterSeconds("2026-10-21T07:28:08Z", now))
    }
}
