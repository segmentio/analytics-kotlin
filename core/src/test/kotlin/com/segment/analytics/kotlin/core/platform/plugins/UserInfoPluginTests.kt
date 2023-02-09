package com.segment.analytics.kotlin.core.platform.plugins

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Timeline

import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utils.testAnalytics
import io.mockk.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserInfoPluginTests {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val testScope = TestScope(testDispatcher)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val config = Configuration(
        writeKey = "123",
        application = "Test",
        autoAddSegmentDestination = false
    )

    private val testAnalytics = testAnalytics(config, testScope, testDispatcher)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val timeline: Timeline

    init {
        timeline = Timeline().also { it.analytics = testAnalytics }
        mockkStatic(Instant::class)
        every { Instant.now() } returns Date(0).toInstant()
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "qwerty-qwerty-123"
    }


    @Test
    fun `Test Execute Identify`() {
        var identifyEvent: IdentifyEvent = IdentifyEvent("MrUser", emptyJsonObject)
        identifyEvent.anonymousId = "MrAnonymous"
        val userInfoPlugin = UserInfoPlugin()
        userInfoPlugin.setup(testAnalytics)
        userInfoPlugin.execute(identifyEvent)
        assertEquals("MrUser", testAnalytics.userInfo.userId)
        assertEquals("MrAnonymous", testAnalytics.userInfo.anonymousId)
    }


    @Test
    fun `Test Execute Alias`() {
        testAnalytics.userInfo.userId = "MrBefore"
        testAnalytics.userInfo.anonymousId = "MrAnonBefore"
        var aliasEvent: AliasEvent = AliasEvent("MrNewUser","MrPrevious")
        aliasEvent.anonymousId = "MrAnonNew"
        val userInfoPlugin = UserInfoPlugin()
        userInfoPlugin.setup(testAnalytics)
        userInfoPlugin.execute(aliasEvent)
        assertEquals("MrBefore", testAnalytics.userInfo.userId)
        assertEquals("MrAnonNew", testAnalytics.userInfo.anonymousId)
    }

    @Test
    fun `Test Execute Other`() {
        testAnalytics.userInfo.userId = "MrBefore"
        testAnalytics.userInfo.anonymousId = "MrAnonBefore"
        var groupEvent: GroupEvent = GroupEvent("MrGroup", emptyJsonObject)
        val userInfoPlugin = UserInfoPlugin()
        userInfoPlugin.setup(testAnalytics)
        groupEvent = userInfoPlugin.execute(groupEvent) as GroupEvent
        assertEquals("MrBefore", groupEvent.userId)
        assertEquals("MrAnonBefore", groupEvent.anonymousId)
    }

}

