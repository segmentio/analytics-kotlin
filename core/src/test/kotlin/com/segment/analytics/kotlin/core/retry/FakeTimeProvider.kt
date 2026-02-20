package com.segment.analytics.kotlin.core.retry

class FakeTimeProvider(private var currentTime: Long = 0L) : TimeProvider {
    override fun currentTimeMillis(): Long = currentTime

    fun setTime(millis: Long) {
        currentTime = millis
    }

    fun advanceBy(millis: Long) {
        currentTime += millis
    }
}
