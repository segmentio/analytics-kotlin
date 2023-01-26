package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserInfoPluginTests {
    private lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    init {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
       //CRWCRW mockkConstructor(HTTPClient::class)
    }

    @BeforeEach
    fun setup() {
        clearPersistentStorage()
        val config = Configuration(
            writeKey = "123",
            application = "Test",
            autoAddSegmentDestination = false,
        )
        analytics = testAnalytics(config, testScope, testDispatcher)
    }

    @Test
    fun `Test Execute Identify`() {

        assertEquals(null, analytics.find(UserInfoPlugin::class))

        val userInfoPlugin = analytics.find(UserInfoPlugin::class) as UserInfoPlugin
        userInfoPlugin.userInfo.userId = "glug"
        userInfoPlugin.userInfo.anonymousId = "glugo"
        val identifyEvent: BaseEvent
        identifyEvent.userId = "bob"
        identifyEvent.anonymousId = "bobo"
        userInfoPlugin.execute(identifyEvent)

        assertNotEquals(null, userInfoPlugin)
        assertEquals("bob", userInfoPlugin.userInfo.userId)
        assertEquals("bobo", userInfoPlugin.userInfo.anonymousId)

    }

    @Test
    fun `Test Execute Alias`() {

        assertEquals(null, analytics.find(UserInfoPlugin::class))

        val userInfoPlugin = analytics.find(UserInfoPlugin::class) as UserInfoPlugin
        userInfoPlugin.userInfo.userId = "glug"
        userInfoPlugin.userInfo.anonymousId = "glugo"
        val identifyEvent: BaseEvent
        identifyEvent.userId = null
        identifyEvent.anonymousId = "bobo"
        userInfoPlugin.execute(identifyEvent)

        assertNotEquals(null, userInfoPlugin)
        assertEquals("bobo", userInfoPlugin.userInfo.anonymousId)

    }

    @Test
    fun `Test Execute Other`() {

        assertEquals(null, analytics.find(UserInfoPlugin::class))

        val userInfoPlugin = analytics.find(UserInfoPlugin::class) as UserInfoPlugin
        userInfoPlugin.userInfo.userId = "glug"
        userInfoPlugin.userInfo.anonymousId = "glugo"
        val identifyEvent: BaseEvent
        identifyEvent.userId = "bob"
        identifyEvent.anonymousId = "bobo"
        userInfoPlugin.execute(identifyEvent)

        assertNotEquals(null, userInfoPlugin)
        assertEquals("glug", userInfoPlugin.userInfo.userId)
        assertEquals("glugo", userInfoPlugin.userInfo.anonymousId)

    }

}

