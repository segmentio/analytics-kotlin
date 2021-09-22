package com.segment.analytics.destinations.plugins

import android.app.Application
import android.util.Log
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.getString
import io.intercom.android.sdk.Company
import io.intercom.android.sdk.Intercom
import io.intercom.android.sdk.UserAttributes
import io.intercom.android.sdk.identity.Registration
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection

internal class IntercomDestinationTest {

    @MockK(relaxUnitFun = true)
    lateinit var application: Application

    @MockK(relaxUnitFun = true)
    lateinit var intercom: Intercom

    private var configuration: Configuration

    private lateinit var analytics: Analytics

    private lateinit var intercomDestination: IntercomDestination

    private val testDispatcher = TestCoroutineDispatcher()

    private val testScope = TestCoroutineScope(testDispatcher)

    init {
        MockKAnnotations.init(this)

        // mock intercom
        mockkStatic(Intercom::class)
        every { Intercom.client() } returns intercom
        every { Intercom.initialize(any(), any(), any()) } just Runs

        // mock configuration
        configuration = spyk(Configuration("123", application))
        every { configuration getProperty "ioDispatcher" } propertyType CoroutineDispatcher::class returns testDispatcher
        every { configuration getProperty "analyticsScope"} propertyType CoroutineScope::class returns testScope

        // mock java log
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        // mock http client
        mockkConstructor(HTTPClient::class)
        val settingsStream = ByteArrayInputStream(
            """
                {"integrations":{"Intercom":{"appId":"qe2y1u8q","collectContext":false,"mobileApiKey":"android_sdk-4c2bc22f45f0f20629d4a70c3bb803845039800b"}},"plan":{},"edgeFunction":{}}
            """.trimIndent().toByteArray()
        )
        val httpConnection: HttpURLConnection = mockk()
        val connection = object : Connection(httpConnection, settingsStream, null) {}
        every { anyConstructed<HTTPClient>().settings(any()) } returns connection
    }

    @BeforeEach
    internal fun setUp() {
        analytics = Analytics(configuration)
        intercomDestination = spyk(IntercomDestination(application))
    }

    @Test
    fun `intercom client initialized when settings is updated`() {
        val settings = slot<Settings>()

        analytics.add(intercomDestination)

        verify {
            intercomDestination.update(capture(settings), Plugin.UpdateType.Initial)
            Intercom.initialize(any(), any(), any())
            Intercom.client()
        }
        assertEquals(intercom, intercomDestination.intercom)
        with(settings.captured) {
            val integration = this.integrations[intercomDestination.key]?.jsonObject
            assertNotNull(integration)
            assertEquals("android_sdk-4c2bc22f45f0f20629d4a70c3bb803845039800b", integration?.getString("mobileApiKey"))
            assertEquals("qe2y1u8q", integration?.getString("appId"))
        }
    }

    @Test
    fun `intercom client not re-initialized when settings is fresh`() {
        analytics.add(intercomDestination)
        analytics.checkSettings()
        verify (exactly = 1) {
            intercomDestination.update(any(), Plugin.UpdateType.Initial)
            Intercom.initialize(any(), any(), any())
            Intercom.client()
        }
    }

    @Test
    fun `track when all fields are presented`() {
        val eventName = slot<String>()
        val event = slot<JsonObject>()
        val expected = buildJsonObject {
            putJsonObject("price") {
                put("amount", 100.0)
                put("currency", "USD")
            }
            put("others", "other")
        }

        analytics.add(intercomDestination)
        analytics.track("track", buildJsonObject {
            put("revenue", 1)
            put("total", 2)
            put("currency", "USD")
            put("products", "products")
            put("others", "other")
        })

        verify (timeout = 1000) { intercom.logEvent(capture(eventName), capture(event)) }
        assertEquals("track", eventName.captured)
        assertEquals(expected, event.captured)
    }

    @Test
    fun `track when all fields except total are absent`() {
        val eventName = slot<String>()
        val event = slot<JsonObject>()
        val expected = buildJsonObject {
            putJsonObject("price") {
                put("amount", 200.0)
            }
        }

        analytics.add(intercomDestination)
        analytics.track("track", buildJsonObject {
            put("total", 2)
        })

        verify (timeout = 1000) { intercom.logEvent(capture(eventName), capture(event)) }
        assertEquals("track", eventName.captured)
        assertEquals(expected, event.captured)
    }

    @Test
    fun `identify with empty user id and empty traits`() {
        val attributes = slot<UserAttributes>()
        val expected = UserAttributes.Builder().build()

        analytics.add(intercomDestination)
        analytics.identify("")

        verify (timeout = 1000) { intercom.registerUnidentifiedUser() }
        verify (timeout = 1000) { intercom.updateUser(capture(attributes)) }
        assertEquals(expected, attributes.captured)
    }

    @Test
    fun `identify with user id and traits`() {
        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } returns false

        val registration = slot<Registration>()
        val attributes = slot<UserAttributes>()
        val expectedRegistration = Registration.create().withUserId("test")
        val expectedAttributes = UserAttributes.Builder()
            .withName("kotlin")
            .withEmail("kotlin@test.com")
            .withPhone("1234567890")
            .withCustomAttribute("other", "other")
            .withCompany(Company.Builder()
                .withCompanyId("company123")
                .withName("kotlin company")
                .withCreatedAt(9876543210987L)
                .withMonthlySpend(123456)
                .withPlan("abc")
                .withCustomAttribute("other", "other")
                .build()
            )
            .build()
        val payload = buildJsonObject {
            put("name", "kotlin")
            put("email", "kotlin@test.com")
            put("phone", "1234567890")
            put("other", "other")
            putJsonObject("company") {
                put("id", "company123")
                put("name", "kotlin company")
                put("monthlySpend", 123456)
                put("plan", "abc")
                put("other", "other")
                put("createdAt", 9876543210987L)
            }
        }

        analytics.add(intercomDestination)
        analytics.identify("test", payload)

        verify (timeout = 1000) { intercom.registerIdentifiedUser(capture(registration)) }
        verify (timeout = 1000) { intercom.updateUser(capture(attributes)) }
        assertEquals(expectedRegistration, registration.captured)
        assertEquals(expectedAttributes, attributes.captured)
    }

    @Test
    fun `group with group id`() {
        val attributes = slot<UserAttributes>()
        val expected = UserAttributes.Builder()
            .withCompany(Company.Builder()
                .withCompanyId("company123")
                .build()
            )
            .build()

        analytics.add(intercomDestination)
        analytics.group("company123")

        verify (timeout = 1000) { intercom.updateUser(capture(attributes)) }
        assertEquals(expected, attributes.captured)
    }

    @Test
    fun `group with group id and traits`() {
        val attributes = slot<UserAttributes>()
        val expected = UserAttributes.Builder()
            .withCompany(Company.Builder()
                .withCompanyId("company123")
                .withName("kotlin company")
                .withCreatedAt(9876543210987L)
                .withMonthlySpend(123456)
                .withPlan("abc")
                .withCustomAttribute("other", "other")
                .build()
            )
            .build()

        analytics.add(intercomDestination)
        analytics.group("company123", buildJsonObject {
            put("name", "kotlin company")
            put("monthlySpend", 123456)
            put("plan", "abc")
            put("other", "other")
            put("createdAt", 9876543210987L)
        })

        verify (timeout = 1000) { intercom.updateUser(capture(attributes)) }
        assertEquals(expected, attributes.captured)
    }

    @Test
    fun reset() {
        analytics.add(intercomDestination)
        intercomDestination.reset()
        verify { intercom.logout() }
    }
}