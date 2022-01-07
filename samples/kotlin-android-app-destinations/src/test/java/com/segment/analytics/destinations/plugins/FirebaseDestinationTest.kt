package com.segment.analytics.destinations.plugins

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.Plugin
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class FirebaseDestinationTests {

    @MockK
    lateinit var context: Context

    @MockK(relaxUnitFun = true)
    lateinit var mockedAnalytics: Analytics

    @MockK(relaxUnitFun = true)
    lateinit var mockedFirebase: FirebaseAnalytics

    private val firebaseDestination: FirebaseDestination

    init {
        MockKAnnotations.init(this)
        mockkStatic(FirebaseAnalytics::class)
        every { FirebaseAnalytics.getInstance(context) } returns mockedFirebase

        // Need to mock log since its used in the destination
//        mockkStatic("com.segment.analytics.kotlin.core.platform.plugins.LoggerKt")


        firebaseDestination = FirebaseDestination(context)
        firebaseDestination.analytics = mockedAnalytics
    }

    @Test
    fun `settings are updated correctly`() {
        // An example settings blob
        val settingsBlob: Settings = Json.decodeFromString(
            """
            {
              "integrations": {
                "Firebase": {
                }
              }
            }
        """.trimIndent()
        )
        firebaseDestination.update(settingsBlob, Plugin.UpdateType.Initial)

        assertNotNull(firebaseDestination.firebaseAnalytics)
    }

    @Test
    fun `identify is handled correctly`() {
        firebaseDestination.firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        val sampleEvent = IdentifyEvent(
            userId = "abc-123",
            traits = buildJsonObject {
                put("email", "123@abc.com")
                put("first.name", "123")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        val identifyEvent = firebaseDestination.identify(sampleEvent)

        assertNotNull(identifyEvent)

        verify { mockedFirebase.setUserId("abc-123") }
        verify { mockedFirebase.setUserProperty("email", "123@abc.com") }
        verify { mockedFirebase.setUserProperty("first_name", "123") }
    }

    @Test
    fun `onActivityResumed logs activity name`() {
        firebaseDestination.firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        val mockManager = mockk<PackageManager>().apply {
            every { getActivityInfo(any(), any()) } returns mockk<ActivityInfo>().apply {
                every { loadLabel(any()) } returns "MockActivity"
            }
        }
        val mockActivity = spyk(MockActivity())
        every { mockActivity.packageManager } returns mockManager

        firebaseDestination.onActivityResumed(mockActivity)

        val bundleActual = slot<Bundle>()
        verify { mockedFirebase.logEvent("screen_view", capture(bundleActual)) }
        with(bundleActual.captured) {
            assertEquals(1, this.size())
            assertEquals("MockActivity", get("screen_name"))
        }
    }

    @Test
    fun `screen is fired when activity is available`() {
        firebaseDestination.firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        val mockActivity = MockActivity()

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

        firebaseDestination.onActivityStarted(mockActivity)
        val screenEvent = firebaseDestination.screen(sampleEvent)

        /* assertions about new event */
        assertNotNull(screenEvent)

        val bundleActual = slot<Bundle>()

        verify { mockedFirebase.logEvent("screen_view", capture(bundleActual)) }

        with(bundleActual.captured) {
            assertEquals(2, this.size())
            assertEquals("LoginFragment", get("screen_name"))
            assertEquals("MockActivity", get("screen_class"))
        }

    }

    @Test
    fun `screen is not fired when activity is unavailable`() {
        firebaseDestination.firebaseAnalytics = FirebaseAnalytics.getInstance(context)
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

        val screenEvent = firebaseDestination.screen(sampleEvent)

        /* assertions about new event */
        assertNotNull(screenEvent)
        with(screenEvent!! as ScreenEvent) {
            assertEquals("LoginFragment", name)
        }

    }

    @Test
    fun `track changes event name based on mapper`() {
        firebaseDestination.firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        val sampleEvent = TrackEvent(
            event = "Product Clicked",
            properties = emptyJsonObject
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        firebaseDestination.track(sampleEvent)

        verify { mockedFirebase.logEvent("select_content", null) }
    }

    @Test
    fun `track changes event name based on makeKey`() {
        firebaseDestination.firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        val sampleEvent = TrackEvent(
            event = "Button Clicked",
            properties = emptyJsonObject
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        firebaseDestination.track(sampleEvent)
        verify { mockedFirebase.logEvent("Button_Clicked", null) }
    }

    @Test
    fun `track with properties`() {
        firebaseDestination.firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        val sampleEvent = TrackEvent(
            event = "Product Clicked",
            properties = buildJsonObject {
                put("category", "household")
                put("query", "house items")
                put("currency", "USD")
                put("revenue", 160)
                put("products", buildJsonArray {
                    add(buildJsonObject {
                        put("product_id", 1)
                        put("price", 40)
                        put("quantity", 2)
                    })
                    add(buildJsonObject {
                        put("product_id", 2)
                        put("price", 80)
                        put("quantity", 1)
                    })
                })
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        firebaseDestination.track(sampleEvent)

        val bundleActual = slot<Bundle>()
        verify { mockedFirebase.logEvent("select_content", capture(bundleActual)) }

        with(bundleActual.captured) {
            val bundle = bundleOf(
                "item_category" to "household",
                "search_term" to "house items",
                "currency" to "USD",
                "value" to 160
            ).apply {
                putParcelableArrayList(
                    "products", arrayListOf(
                        bundleOf(
                            "item_id" to 1,
                            "price" to 40,
                            "quantity" to 2
                        ),
                        bundleOf(
                            "item_id" to 2,
                            "price" to 80,
                            "quantity" to 1
                        )
                    )
                )
            }
            assertEquals(bundle.size(), size())

            assertEquals("household", getString("item_category"))
            assertEquals("house items", getString("search_term"))
            assertEquals("USD", getString("currency"))
            assertEquals(160, getInt("value"))
            val products = getParcelableArrayList<Bundle>("item_list")
            with(products!!) {
                assertEquals(2, size)
                with(get(0)!!) {
                    assertEquals(1, getInt("item_id"))
                    assertEquals(40, getInt("price"))
                    assertEquals(2, getInt("quantity"))
                }
                with(get(1)!!) {
                    assertEquals(2, getInt("item_id"))
                    assertEquals(80, getInt("price"))
                    assertEquals(1, getInt("quantity"))
                }
            }
        }
    }

}

class MockActivity : Activity()