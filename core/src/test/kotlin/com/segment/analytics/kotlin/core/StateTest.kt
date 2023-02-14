package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.utils.clearPersistentStorage
import com.segment.analytics.kotlin.core.utils.mockHTTPClient
import com.segment.analytics.kotlin.core.utils.testAnalytics
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class StateTest {
    private lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    init {
        mockHTTPClient()
    }

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        val config = Configuration(
            writeKey = "123",
            application = "Test"
        )
        analytics = testAnalytics(config, testScope, testDispatcher)

        analytics.configuration.autoAddSegmentDestination = false
    }

    @Nested
    inner class UserInfoTests {

        @Test
        fun resetAction() = runTest  {
            val traits = buildJsonObject { put("behaviour", "bad") }
            analytics.store.dispatch(
                UserInfo.SetUserIdAndTraitsAction(
                    "oldUserId",
                    traits),
                UserInfo::class
            )

            assertEquals("oldUserId", (analytics.store.currentState(UserInfo::class))?.userId)
            assertEquals(traits, (analytics.store.currentState(UserInfo::class))?.traits)

            analytics.store.dispatch(UserInfo.ResetAction(), UserInfo::class)
            assertNull((analytics.store.currentState(UserInfo::class))?.userId)
            assertNull((analytics.store.currentState(UserInfo::class))?.userId)
        }

        @Test
        fun setUserIdAction() = runTest  {
            analytics.store.dispatch(UserInfo.SetUserIdAction("oldUserId"), UserInfo::class)
            assertEquals("oldUserId", (analytics.store.currentState(UserInfo::class))?.userId)

            analytics.store.dispatch(UserInfo.SetUserIdAction("newUserId"), UserInfo::class)
            assertEquals("newUserId", (analytics.store.currentState(UserInfo::class))?.userId)
        }

        @Test
        fun setAnonymousIdAction() = runTest  {
            analytics.store.dispatch(UserInfo.SetAnonymousIdAction("anonymous"), UserInfo::class)
            assertEquals("anonymous", analytics.store.currentState(UserInfo::class)?.anonymousId)
        }

        @Test
        fun setTraitsAction() = runTest  {
            val traits = buildJsonObject { put("behaviour", "bad") }

            analytics.store.dispatch(UserInfo.SetUserIdAction("oldUserId"), UserInfo::class)
            assertEquals("oldUserId", (analytics.store.currentState(UserInfo::class))?.userId)
            assertEquals(emptyJsonObject, (analytics.store.currentState(UserInfo::class))?.traits)

            analytics.store.dispatch(UserInfo.SetTraitsAction(traits), UserInfo::class)
            assertEquals(traits, (analytics.store.currentState(UserInfo::class))?.traits)
        }

        @Test
        fun setUserIdAndTraitsAction() = runTest  {
            val traits = buildJsonObject { put("behaviour", "bad") }
            analytics.store.dispatch(
                UserInfo.SetUserIdAndTraitsAction(
                    "oldUserId",
                    traits),
                UserInfo::class
            )

            assertEquals("oldUserId", (analytics.store.currentState(UserInfo::class))?.userId)
            assertEquals(traits, (analytics.store.currentState(UserInfo::class))?.traits)
        }
    }
}