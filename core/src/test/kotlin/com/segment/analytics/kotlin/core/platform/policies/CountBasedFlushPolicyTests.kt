package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Properties
import com.segment.analytics.kotlin.core.ScreenEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CountBasedFlushPolicyTests {

    @Test
    fun `Policy defaults to 20 events`() {
        assertEquals(20, CountBasedFlushPolicy().flushAt)
    }

    @Test
    fun `Policy respects flushAt constructor arg`() {
        assertEquals(20, CountBasedFlushPolicy().flushAt) // default
        assertEquals(30, CountBasedFlushPolicy(30).flushAt)
        assertEquals(1, CountBasedFlushPolicy(1).flushAt)
        assertEquals(100, CountBasedFlushPolicy(100).flushAt)
    }

    @Test
    fun `Policy flushes at appropriate time`() {
        val flushAt = 10
        val defaultPolicy = CountBasedFlushPolicy(flushAt) // default count based policy with a 20 event flushAt value.
        assertFalse(defaultPolicy.shouldFlush()) // Should NOT flush before any events

        // all the first 19 events should not cause the policy to be flushed
        for( i in 1 until flushAt) {
            defaultPolicy.updateState(ScreenEvent("foo", "bar", Properties(emptyMap())))
            assertFalse(defaultPolicy.shouldFlush())
        }

        // next event should trigger the flush
        defaultPolicy.updateState(ScreenEvent("foo", "bar", Properties(emptyMap())))
        assertTrue(defaultPolicy.shouldFlush())

        // Even if we somehow go over the flushAt event limit, the policy should still want to flush
        // events
        defaultPolicy.updateState(ScreenEvent("foo", "bar", Properties(emptyMap())))
        assertTrue(defaultPolicy.shouldFlush())

        // Only when we reset the policy will it not want to flush
        defaultPolicy.reset()
        assertFalse(defaultPolicy.shouldFlush())

        // The policy will then be ready to count another N events
        for( i in 1 until flushAt) {
            defaultPolicy.updateState(ScreenEvent("foo", "bar", Properties(emptyMap())))
            assertFalse(defaultPolicy.shouldFlush())
        }

        // but once again the next event will trigger a flush request
        defaultPolicy.updateState(ScreenEvent("foo", "bar", Properties(emptyMap())))
        assertTrue(defaultPolicy.shouldFlush())
    }

    @Test
    fun `Policy constructor param flushAt ignored for values less than 1`() {
        assertEquals(1, CountBasedFlushPolicy(1).flushAt) // Lowest flushAt that is allowed
        assertEquals(20, CountBasedFlushPolicy(0).flushAt)
        assertEquals(20, CountBasedFlushPolicy(-1).flushAt)
        assertEquals(20, CountBasedFlushPolicy(-20).flushAt)
        assertEquals(20, CountBasedFlushPolicy(-1000).flushAt)
        assertEquals(20, CountBasedFlushPolicy(-2439872).flushAt)
    }
}