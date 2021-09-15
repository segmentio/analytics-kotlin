package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class StateTest {
    private lateinit var analytics: Analytics

    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope(testDispatcher)

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        val config = Configuration(
            writeKey = "123",
            application = "Test"
        )
        config.ioDispatcher = testDispatcher
        config.analyticsDispatcher = testDispatcher
        config.analyticsScope = testScope

        analytics = Analytics(config)
        analytics.configuration.autoAddSegmentDestination = false
    }

    @Nested
    inner class UserInfoTests {

        @Test
        fun resetAction() {
            val traits = buildJsonObject { put("behaviour", "bad") }
            analytics.store.dispatch(
                UserInfo.SetUserIdAndTraitsAction(
                    "oldUserId",
                    traits),
                UserInfo::class
            )

            assertEquals("oldUserId", analytics.userId())
            assertEquals(traits, analytics.traits())

            analytics.store.dispatch(UserInfo.ResetAction(), UserInfo::class)
            assertNull(analytics.userId())
            assertNull(analytics.traits())
        }

        @Test
        fun setUserIdAction() {
            analytics.store.dispatch(UserInfo.SetUserIdAction("oldUserId"), UserInfo::class)
            assertEquals("oldUserId", analytics.userId())

            analytics.store.dispatch(UserInfo.SetUserIdAction("newUserId"), UserInfo::class)
            assertEquals("newUserId", analytics.userId())
        }

        @Test
        fun setAnonymousIdAction() {
            analytics.store.dispatch(UserInfo.SetAnonymousIdAction("anonymous"), UserInfo::class)
            assertEquals("anonymous", analytics.store.currentState(UserInfo::class)?.anonymousId)
        }

        @Test
        fun setTraitsAction() {
            val traits = buildJsonObject { put("behaviour", "bad") }

            analytics.store.dispatch(UserInfo.SetUserIdAction("oldUserId"), UserInfo::class)
            assertEquals("oldUserId", analytics.userId())
            assertEquals(emptyJsonObject, analytics.traits())

            analytics.store.dispatch(UserInfo.SetTraitsAction(traits), UserInfo::class)
            assertEquals(traits, analytics.traits())
        }

        @Test
        fun setUserIdAndTraitsAction() {
            val traits = buildJsonObject { put("behaviour", "bad") }
            analytics.store.dispatch(
                UserInfo.SetUserIdAndTraitsAction(
                    "oldUserId",
                    traits),
                UserInfo::class
            )

            assertEquals("oldUserId", analytics.userId())
            assertEquals(traits, analytics.traits())
        }
    }
}