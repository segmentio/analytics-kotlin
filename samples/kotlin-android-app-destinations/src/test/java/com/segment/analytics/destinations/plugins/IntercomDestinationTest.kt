package com.segment.analytics.destinations.plugins

import android.app.Application
import android.util.Log
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import io.intercom.android.sdk.Company
import io.intercom.android.sdk.Intercom
import io.intercom.android.sdk.UserAttributes
import io.intercom.android.sdk.identity.Registration
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class IntercomDestinationTest {

    @MockK(relaxUnitFun = true)
    lateinit var application: Application

    @MockK(relaxUnitFun = true)
    lateinit var intercom: Intercom

    @MockK(relaxUnitFun = true)
    private lateinit var analytics: Analytics

    private lateinit var intercomDestination: IntercomDestination

    val settings: Settings

    init {
        MockKAnnotations.init(this)

        // mock intercom
        mockkStatic(Intercom::class)
        every { Intercom.client() } returns intercom
        every { Intercom.initialize(any(), any(), any()) } just Runs

        // mock java log
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        settings = Json.decodeFromString(
            """
            {"integrations":{"Intercom":{"appId":"qe2y1u8q","collectContext":false,"mobileApiKey":"android_sdk-4c2bc22f45f0f20629d4a70c3bb803845039800b"}},"plan":{},"edgeFunction":{}}
        """.trimIndent()
        )
        intercomDestination = IntercomDestination(application)
        intercomDestination.setup(analytics)
    }

    @Test
    fun `intercom client initialized when settings is updated`() {
        val mobileApiKey = slot<String>()
        val appId = slot<String>()

        intercomDestination.update(settings, Plugin.UpdateType.Initial)

        verify {
            Intercom.initialize(any(), capture(mobileApiKey), capture(appId))
            Intercom.client()
        }
        assertEquals(intercom, intercomDestination.intercom)
        assertEquals("android_sdk-4c2bc22f45f0f20629d4a70c3bb803845039800b", mobileApiKey.captured)
        assertEquals("qe2y1u8q", appId.captured)
    }

    @Test
    fun `intercom client not re-initialized when settings is fresh`()  {
        intercomDestination.update(settings, Plugin.UpdateType.Refresh)
        verify (exactly = 0) {
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

        intercomDestination.update(settings, Plugin.UpdateType.Initial)
        intercomDestination.track(
            TrackEvent(
                event = "track",
                properties = buildJsonObject {
                    put("revenue", 1)
                    put("total", 2)
                    put("currency", "USD")
                    put("products", "products")
                    put("others", "other")
                })
        )

        verify { intercom.logEvent(capture(eventName), capture(event)) }
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

        intercomDestination.update(settings, Plugin.UpdateType.Initial)
        intercomDestination.track(
            TrackEvent(
                event = "track",
                properties = buildJsonObject {
                    put("total", 2)
                })
        )

        verify { intercom.logEvent(capture(eventName), capture(event)) }
        assertEquals("track", eventName.captured)
        assertEquals(expected, event.captured)
    }

    @Test
    fun `identify with empty user id and empty traits`() {
        val attributes = slot<UserAttributes>()
        val expected = UserAttributes.Builder().build()

        intercomDestination.update(settings, Plugin.UpdateType.Initial)
        intercomDestination.identify(
            IdentifyEvent("", emptyJsonObject).apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = "2021-07-13T00:59:09"
            }
        )

        verify { intercom.registerUnidentifiedUser() }
        verify { intercom.updateUser(capture(attributes)) }
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

        intercomDestination.update(settings, Plugin.UpdateType.Initial)
        intercomDestination.identify(
            IdentifyEvent("test", payload).apply {
                messageId = "qwerty-1234"
                anonymousId = "anonId"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = "2021-07-13T00:59:09"
            }
        )

        verify { intercom.registerIdentifiedUser(capture(registration)) }
        verify { intercom.updateUser(capture(attributes)) }
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

        intercomDestination.update(settings, Plugin.UpdateType.Initial)
        intercomDestination.group(
            GroupEvent("company123", emptyJsonObject)
        )

        verify { intercom.updateUser(capture(attributes)) }
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

        intercomDestination.update(settings, Plugin.UpdateType.Initial)
        intercomDestination.group(
            GroupEvent("company123", buildJsonObject {
                put("name", "kotlin company")
                put("monthlySpend", 123456)
                put("plan", "abc")
                put("other", "other")
                put("createdAt", 9876543210987L)
            })
        )

        verify { intercom.updateUser(capture(attributes)) }
        assertEquals(expected, attributes.captured)
    }

    @Test
    fun reset() {
        intercomDestination.update(settings, Plugin.UpdateType.Initial)
        intercomDestination.reset()
        verify { intercom.logout() }
    }
}