package com.segment.analytics.destinations.plugins

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.log
import io.mockk.Called
import io.mockk.Matcher
import io.mockk.MockKAnnotations
import io.mockk.MockKMatcherScope
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode


class MixpanelDestinationTests {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockMixpanel = mockk<MixpanelAPI>(relaxed = true)
    private val mockMixpanelPeople = mockk<MixpanelAPI.People>(relaxed = true)
    private val mockMixpanelGroup = mockk<MixpanelAPI.Group>(relaxed = true)
    private val mixpanelDestination = MixpanelDestination(mockContext)

    @MockK(relaxUnitFun = true)
    lateinit var mockedAnalytics: Analytics

    init {
        MockKAnnotations.init(this)
        mockkStatic(MixpanelAPI::class)
        every { MixpanelAPI.getInstance(mockContext, any()) } returns mockMixpanel
        every { mockMixpanel.people } returns mockMixpanelPeople
        every { mockMixpanel.getGroup(any(), any()) } returns mockMixpanelGroup

        mockkStatic(Class.forName("com.segment.analytics.kotlin.core.platform.plugins.LoggerKt").kotlin)
        every { mockedAnalytics.log(any(), any(), any()) } just Runs

        mixpanelDestination.analytics = mockedAnalytics
    }

    @Test
    fun `settings are updated correctly`() {
        // An example settings blob
        val settingsBlob: Settings = Json.decodeFromString(
            """
            {
              "integrations": {
                "Mixpanel": {
                  "consolidatedPageCalls": false,
                  "eventIncrements": [
                    "Product Clicked",
                    "Product Viewed"
                  ],
                  "groupIdentifierTraits": [
                    "company",
                    "username"
                  ],
                  "people": true,
                  "peopleProperties": [
                    "email",
                    "username",
                    "phone_number"
                  ],
                  "propIncrements": [
                    "logins"
                  ],
                  "setAllTraitsByDefault": false,
                  "superProperties": [
                    
                  ],
                  "token": "token1234",
                  "trackAllPages": false,
                  "trackCategorizedPages": true,
                  "trackNamedPages": false
                }
              }
            }
        """.trimIndent()
        )
        mixpanelDestination.update(settingsBlob, Plugin.UpdateType.Initial)

        /* assertions about config */
        assertNotNull(mixpanelDestination.settings)
        with(mixpanelDestination.settings!!) {
            assertFalse(consolidatedPageCalls)
            assertTrue(isPeopleEnabled)
            assertFalse(trackAllPages)
            assertTrue(trackCategorizedPages)
            assertFalse(trackNamedPages)
            assertFalse(setAllTraitsByDefault)

            assertEquals("token1234", token)

            assertEquals(emptySet<String>(), superPropertiesFilter)
            assertEquals(setOf("email", "username", "phone_number"), peoplePropertiesFilter)
            assertEquals(setOf("Product Clicked", "Product Viewed"), increments)
        }
    }

    @Test
    fun `screen does nothing`() {
        val sampleEvent = ScreenEvent(
            name = "LoginFragment",
            properties = buildJsonObject {
                put("startup", false)
                put("parent", "MainActivity")
            },
            category = "signup_flow"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                consolidatedPageCalls = false,
                trackAllPages = false,
                trackCategorizedPages = false,
                trackNamedPages = false
            )
        )
        val screenEvent = mixpanelDestination.screen(sampleEvent)

        assertNotNull(screenEvent)
        verify { mockMixpanel wasNot Called }
    }

    @Test
    fun `mixpanel fires event on screen when trackAllPage=true`() {
        val sampleEvent = ScreenEvent(
            name = "LoginFragment",
            properties = buildJsonObject {
                put("startup", false)
                put("parent", "MainActivity")
            },
            category = "signup_flow"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                consolidatedPageCalls = false,
                trackAllPages = true // this is the changed config
            )
        )
        val screenEvent = mixpanelDestination.screen(sampleEvent)

        assertNotNull(screenEvent)
        verify {
            mockMixpanel.track(
                "Viewed LoginFragment Screen",
                matchJsonObject(
                    JSONObject()
                        .put("startup", false)
                        .put("parent", "MainActivity")
                )
            )
        }
    }

    @Test
    fun `mixpanel fires event on screen when consolidatePageCalls=true`() {
        val sampleEvent = ScreenEvent(
            name = "LoginFragment",
            properties = buildJsonObject {
                put("startup", false)
                put("parent", "MainActivity")
            },
            category = "signup_flow"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                isPeopleEnabled = true,
                consolidatedPageCalls = true, // this is the changed config
            )
        )
        val screenEvent = mixpanelDestination.screen(sampleEvent)

        assertNotNull(screenEvent)
        verify {
            mockMixpanel.track(
                "Loaded a Screen",
                matchJsonObject(
                    JSONObject()
                        .put("name", "LoginFragment")
                        .put("startup", false)
                        .put("parent", "MainActivity")
                )
            )
        }
    }

    @Test
    fun `mixpanel fires event on screen when trackNamedPages=true & screen name is not empty`() {
        val sampleEvent = ScreenEvent(
            name = "LoginFragment",
            properties = buildJsonObject {
                put("startup", false)
                put("parent", "MainActivity")
            },
            category = "signup_flow"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                consolidatedPageCalls = false,
                isPeopleEnabled = true,
                trackNamedPages = true, // this is the changed config
            )
        )
        val screenEvent = mixpanelDestination.screen(sampleEvent)

        assertNotNull(screenEvent)
        verify {
            mockMixpanel.track(
                "Viewed LoginFragment Screen",
                matchJsonObject(
                    JSONObject()
                        .put("startup", false)
                        .put("parent", "MainActivity")
                )
            )
        }
    }

    @Test
    fun `mixpanel fires event on screen when trackNamedPages=true & screen name is empty`() {
        val sampleEvent = ScreenEvent(
            name = "",
            properties = buildJsonObject {
                put("startup", false)
                put("parent", "MainActivity")
            },
            category = "signup_flow"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                consolidatedPageCalls = false,
                isPeopleEnabled = true,
                trackNamedPages = true, // this is the changed config
            )
        )
        val screenEvent = mixpanelDestination.screen(sampleEvent)

        assertNotNull(screenEvent)
        verify { mockMixpanel wasNot Called }
    }

    @Test
    fun `mixpanel fires event on screen when trackCategorizedPages=true & category is not empty`() {
        val sampleEvent = ScreenEvent(
            name = "LoginFragment",
            properties = buildJsonObject {
                put("startup", false)
                put("parent", "MainActivity")
            },
            category = "signup_flow"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                consolidatedPageCalls = false,
                isPeopleEnabled = true,
                trackCategorizedPages = true, // this is the changed config
            )
        )
        val screenEvent = mixpanelDestination.screen(sampleEvent)

        assertNotNull(screenEvent)
        verify {
            mockMixpanel.track(
                "Viewed signup_flow Screen",
                matchJsonObject(
                    JSONObject()
                        .put("startup", false)
                        .put("parent", "MainActivity")
                )
            )
        }
    }

    @Test
    fun `mixpanel fires event on screen when trackCategorizedPages=true & category is empty`() {
        val sampleEvent = ScreenEvent(
            name = "LoginFragment",
            properties = buildJsonObject {
                put("startup", false)
                put("parent", "MainActivity")
            },
            category = ""
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                consolidatedPageCalls = false,
                isPeopleEnabled = true,
                trackCategorizedPages = true, // this is the changed config
            )
        )
        val screenEvent = mixpanelDestination.screen(sampleEvent)

        assertNotNull(screenEvent)
        verify { mockMixpanel wasNot Called }
    }

    @Test
    fun `track is handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Product Clicked",
            properties = buildJsonObject { put("Item Name", "Biscuits") }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = ""
            )
        )
        val trackEvent = mixpanelDestination.track(sampleEvent)


        assertNotNull(trackEvent)

        verify {
            mockMixpanel.track(
                "Product Clicked",
                matchJsonObject(
                    JSONObject()
                        .put("Item Name", "Biscuits")
                )
            )
        }
    }

    @Test
    fun `track increments`() {
        val sampleEvent = TrackEvent(
            event = "Product Clicked",
            properties = buildJsonObject { put("Item Name", "Biscuits") }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                isPeopleEnabled = true,
                increments = setOf("Product Clicked")
            )
        )
        val trackEvent = mixpanelDestination.track(sampleEvent)


        assertNotNull(trackEvent)

        verify {
            mockMixpanel.track(
                "Product Clicked",
                matchJsonObject(
                    JSONObject()
                        .put("Item Name", "Biscuits")
                )
            )
        }

        verify {
            mockMixpanelPeople.increment("Product Clicked", 1.0)
        }

        verify {
            mockMixpanelPeople.set("Last Product Clicked", any());
        }
    }

    @Test
    fun `track without People, does not track increments`() {
        val sampleEvent = TrackEvent(
            event = "Product Clicked",
            properties = buildJsonObject { put("Item Name", "Biscuits") }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                isPeopleEnabled = false,
                increments = setOf("Product Clicked")
            )
        )
        val trackEvent = mixpanelDestination.track(sampleEvent)


        assertNotNull(trackEvent)

        verify {
            mockMixpanel.track(
                "Product Clicked",
                matchJsonObject(
                    JSONObject()
                        .put("Item Name", "Biscuits")
                )
            )
        }

        verify { mockMixpanel.people wasNot Called }
    }

    @Test
    fun `alias changes mixpanel userId & uses mixpanel id`() {
        val sampleEvent = AliasEvent(
            userId = "dbid-123",
            previousId = "anonId"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        every { mockMixpanel.distinctId } returns "mixpanelId-123"
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                increments = setOf("Product Clicked")
            )
        )
        val trackEvent = mixpanelDestination.alias(sampleEvent)


        assertNotNull(trackEvent)

        verify {
            mockMixpanel.alias(
                "dbid-123",
                "mixpanelId-123"
            )
        }
    }

    @Test
    fun `alias changes mixpanel userId`() {
        val sampleEvent = AliasEvent(
            userId = "dbid-123",
            previousId = "john"
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        every { mockMixpanel.distinctId } returns "mixpanelId-123"
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                increments = setOf("Product Clicked")
            )
        )
        val trackEvent = mixpanelDestination.alias(sampleEvent)


        assertNotNull(trackEvent)

        verify {
            mockMixpanel.alias(
                "dbid-123",
                "john"
            )
        }
    }

    @Test
    fun `identify with userId registers super properties`() {
        val sampleEvent = IdentifyEvent(
            userId = "abc-123",
            traits = buildJsonObject { put("email", "123@abc.com") }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = ""
            )
        )
        val identifyEvent = mixpanelDestination.identify(sampleEvent)

        assertNotNull(identifyEvent)

        verify { mockMixpanel.identify("abc-123") }
        verify {
            mockMixpanel.registerSuperProperties(
                matchJsonObject(
                    JSONObject().put("\$email", "123@abc.com")
                )
            )
        }
    }

    @Test
    fun `identify without userId registers super properties`() {
        val sampleEvent = IdentifyEvent(
            userId = "",
            traits = buildJsonObject { put("email", "123@abc.com") }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = ""
            )
        )
        val identifyEvent = mixpanelDestination.identify(sampleEvent)

        assertNotNull(identifyEvent)

        verify(exactly = 0) { mockMixpanel.identify("abc-123") }
        verify {
            mockMixpanel.registerSuperProperties(
                matchJsonObject(
                    JSONObject().put("\$email", "123@abc.com")
                )
            )
        }
    }

    @Test
    fun `identify with people`() {
        val sampleEvent = IdentifyEvent(
            userId = "abc-123",
            traits = buildJsonObject { put("email", "123@abc.com") }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                isPeopleEnabled = true
            )
        )
        val identifyEvent = mixpanelDestination.identify(sampleEvent)

        assertNotNull(identifyEvent)

        verify { mockMixpanel.identify("abc-123") }
        verify {
            mockMixpanel.registerSuperProperties(
                matchJsonObject(
                    JSONObject().put("\$email", "123@abc.com")
                )
            )
        }
        verify {
            mockMixpanelPeople.identify("abc-123")
        }
        verify {
            mockMixpanelPeople.set(
                matchJsonObject(
                    JSONObject().put("\$email", "123@abc.com")
                )
            )
        }
    }

    @Test
    fun `identify with superProperties`() {
        val sampleEvent = IdentifyEvent(
            userId = "abc-123",
            traits = buildJsonObject {
                put("email", "123@abc.com")
                put("phone", "987-654-3210")
                put("createdAt", "20th Sept, 2021")
                put("username", "john")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                isPeopleEnabled = true
            )
        )
        val identifyEvent = mixpanelDestination.identify(sampleEvent)

        assertNotNull(identifyEvent)

        val expectedTraits = JSONObject()
            .put("\$email", "123@abc.com")
            .put("\$phone", "987-654-3210")
            .put("\$username", "john")
            .put("\$created", "20th Sept, 2021")

        verify { mockMixpanel.identify("abc-123") }
        verify {
            mockMixpanel.registerSuperProperties(
                matchJsonObject(expectedTraits)
            )
        }
        verify {
            mockMixpanelPeople.identify("abc-123")
        }
        verify {
            mockMixpanelPeople.set(
                matchJsonObject(expectedTraits)
            )
        }
    }

    @Test
    fun `identify with superPropertiesValues`() {
        val sampleEvent = IdentifyEvent(
            userId = "abc-123",
            traits = buildJsonObject {
                put("email", "123@abc.com")
                put("phone", "987-654-3210")
                put("createdAt", "20th Sept, 2021")
                put("username", "john")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                isPeopleEnabled = true,
                superPropertiesFilter = setOf("phone"),
                setAllTraitsByDefault = false
            )
        )
        val identifyEvent = mixpanelDestination.identify(sampleEvent)

        assertNotNull(identifyEvent)

        val expectedTraits = JSONObject()
            .put("\$phone", "987-654-3210")

        verify { mockMixpanel.identify("abc-123") }
        verify {
            mockMixpanel.registerSuperProperties(
                matchJsonObject(expectedTraits)
            )
        }
        verify {
            mockMixpanelPeople.identify("abc-123")
        }
        verify(exactly = 0) {
            mockMixpanelPeople.set(any())
        }
    }

    @Test
    fun `identify with peopleProperties`() {
        val sampleEvent = IdentifyEvent(
            userId = "abc-123",
            traits = buildJsonObject {
                put("email", "123@abc.com")
                put("phone", "987-654-3210")
                put("createdAt", "20th Sept, 2021")
                put("username", "john")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
                isPeopleEnabled = true,
                peoplePropertiesFilter = setOf("phone"),
                setAllTraitsByDefault = false
            )
        )
        val identifyEvent = mixpanelDestination.identify(sampleEvent)

        assertNotNull(identifyEvent)

        val expectedTraits = JSONObject()
            .put("\$phone", "987-654-3210")

        verify { mockMixpanel.identify("abc-123") }
        verify(exactly = 0) {
            mockMixpanel.registerSuperProperties(
                matchJsonObject(expectedTraits)
            )
        }
        verify {
            mockMixpanelPeople.identify("abc-123")
        }
        verify {
            mockMixpanelPeople.set(
                matchJsonObject(expectedTraits)
            )
        }
    }

    @Test
    fun `group is handled correctly`() {
        val sampleEvent = GroupEvent(
            groupId = "grp-123",
            traits = buildJsonObject { put("company", "abc") }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
            )
        )
        val groupEvent = mixpanelDestination.group(sampleEvent)

        assertNotNull(groupEvent)

        // check mixpanel getGroup called with groupKey default "[Segment] Group" and groupID "grp-123"
        verify { mockMixpanel.getGroup("[Segment] Group", "grp-123") }

        // verify to see that the same Traits passed in the integration
        // transformed to a JsonObject are setOnce on the Group object
        verify {
            mockMixpanelGroup.setOnce(
                matchJsonObject(
                    JSONObject().put("company", "abc")
                )
            )
        }

        // groupKey defaults to groupId, since no name is set
        verify { mockMixpanel.setGroup("[Segment] Group", "grp-123") }
    }

    @Test
    fun `group with group name`() {
        val sampleEvent = GroupEvent(
            groupId = "grp-123",
            traits = buildJsonObject {
                put("name", "ABC network")
                put("company", "abc")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mixpanelDestination.setupTest(
            MixpanelSettings(
                token = "",
            )
        )
        val groupEvent = mixpanelDestination.group(sampleEvent)

        assertNotNull(groupEvent)

        // check mixpanel getGroup called with groupKey default "ABC network" and groupID "grp-123"
        verify { mockMixpanel.getGroup("ABC network", "grp-123") }
        // verify to see that the same Traits passed in the integration
        // transformed to a JsonObject are setOnce on the Group object
        verify {
            mockMixpanelGroup.setOnce(
                matchJsonObject(
                    JSONObject()
                        .put("name", "ABC network")
                        .put("company", "abc")
                )
            )
        }

        // groupKey defaults to groupId, since no name is set
        verify { mockMixpanel.setGroup("ABC network", "grp-123") }
    }

    /* PRIVATE TEST FUNCTIONS */

    private fun MixpanelDestination.setupTest(mixpanelSettings: MixpanelSettings) {
        this.mixpanel = mockMixpanel
        this.settings = mixpanelSettings
    }

    data class JsonObjectMatcher(
        val expectedJSON: JSONObject
    ) : Matcher<JSONObject> {

        override fun match(arg: JSONObject?): Boolean {
            if (arg == null) return false
            return try {
                JSONAssert.assertEquals(expectedJSON, arg, JSONCompareMode.STRICT)
                true
            } catch (e: JSONException) {
                false
            }
        }

        override fun toString() = "matchJSONObject($expectedJSON)"
    }

    private fun MockKMatcherScope.matchJsonObject(expectedJSON: JSONObject): JSONObject =
        match(JsonObjectMatcher(expectedJSON))
}