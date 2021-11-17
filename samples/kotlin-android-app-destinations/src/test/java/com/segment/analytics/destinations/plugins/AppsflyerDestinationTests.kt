package com.segment.analytics.destinations.plugins

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.os.bundleOf
import com.appsflyer.AppsFlyerLib
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AppsflyerDestinationTests {

    var mockContext: Context = mockk()

    @MockK(relaxUnitFun = true)
    lateinit var mockAppsflyer: AppsFlyerLib

    @MockK(relaxUnitFun = true)
    lateinit var mockedAnalytics: Analytics

    private val appsflyerDestination: AppsFlyerDestination = AppsFlyerDestination(mockContext)

    init {
        MockKAnnotations.init(this)
        mockkStatic(AppsFlyerLib::class)
        every { AppsFlyerLib.getInstance() } returns mockAppsflyer
        every { mockAppsflyer.init(any(), any(), any()) } returns mockAppsflyer

        appsflyerDestination.analytics = mockedAnalytics
    }

    @Test
    fun `settings are updated correctly`() {
        // An example settings blob
        val settingsBlob: Settings = Json.decodeFromString(
            """
            {
              "integrations": {
                "AppsFlyer": {
                  "androidAppID": "com.segment.analytics.destinations",
                  "appleAppID": "",
                  "appsFlyerDevKey": "devKey",
                  "httpFallback": false,
                  "rokuAppID": "",
                  "trackAttributionData": true,
                  "versionSettings": {
                    "componentTypes": [
                      "ios",
                      "android",
                      "server"
                    ]
                  },
                  "type": "ios"
                }
              }
            }
        """.trimIndent()
        )
        appsflyerDestination.update(settingsBlob, Plugin.UpdateType.Initial)

        /* assertions about config */
        assertNotNull(appsflyerDestination.settings)
        with(appsflyerDestination.settings!!) {
            assertTrue(trackAttributionData)
            assertEquals("devKey", appsFlyerDevKey)
        }

        verify { mockAppsflyer.init("devKey", isNull(inverse = true), mockContext) }

    }

    @Test
    fun `settings are updated correctly, with trackAttributionData=false`() {
        // An example settings blob
        val settingsBlob: Settings = Json.decodeFromString(
            """
            {
              "integrations": {
                "AppsFlyer": {
                  "androidAppID": "com.segment.analytics.destinations",
                  "appleAppID": "",
                  "appsFlyerDevKey": "devKey",
                  "httpFallback": false,
                  "rokuAppID": "",
                  "trackAttributionData": false,
                  "versionSettings": {
                    "componentTypes": [
                      "ios",
                      "android",
                      "server"
                    ]
                  },
                  "type": "ios"
                }
              }
            }
        """.trimIndent()
        )
        appsflyerDestination.update(settingsBlob, Plugin.UpdateType.Initial)

        /* assertions about config */
        assertNotNull(appsflyerDestination.settings)
        with(appsflyerDestination.settings!!) {
            assertFalse(trackAttributionData)
            assertEquals("devKey", appsFlyerDevKey)
        }

        verify { mockAppsflyer.init("devKey", isNull(), mockContext) }

    }

    @Test
    fun `track fires a logEvent with correct property mappings`() {
        appsflyerDestination.appsflyer = mockAppsflyer
        val sampleEvent = TrackEvent(
            event = "Product Clicked",
            properties = buildJsonObject {
                put("Item Name", "Biscuits")
                put("revenue", 200.0f)
                put("price", "200")
                put("currency", "USD")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        appsflyerDestination.track(sampleEvent)

        verify {
            mockAppsflyer.logEvent(
                mockContext,
                "Product Clicked",
                mapOf(
                    "Item Name" to "Biscuits",
                    "af_revenue" to 200.0,
                    "af_price" to 200,
                    "af_currency" to "USD"
                )
            )
        }
    }

    @Test
    fun `identify updates currency code and customer userID`() {
        appsflyerDestination.appsflyer = mockAppsflyer
        val sampleEvent = IdentifyEvent(
            userId = "abc-123",
            traits = buildJsonObject {
                put("email", "123@abc.com")
                put("currencyCode", "USD")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        appsflyerDestination.identify(sampleEvent)

        verify { mockAppsflyer.setCustomerUserId("abc-123") }
        verify { mockAppsflyer.setCurrencyCode("USD") }
    }

    @Test
    fun `onActivityCreated updates user attributes and starts appsFlyer lib`() {
        appsflyerDestination.appsflyer = mockAppsflyer
        val mockActivity = mockk<Activity>()
        appsflyerDestination.onActivityCreated(mockActivity, bundleOf())

        verify { mockAppsflyer.start(mockActivity) }
        verify { mockAppsflyer.setCurrencyCode("") }
        verify { mockAppsflyer.setCustomerUserId("") }
    }


    @Nested
    inner class ConversionListenerTests {

        private val conversionListener = appsflyerDestination.ConversionListener()

        private val mockSharedPreferences = mockk<SharedPreferences>()
        private val mockEditor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)
        private val mockDelegateListener =
            mockk<AppsFlyerDestination.ExternalAppsFlyerConversionListener>(relaxUnitFun = true)

        init {
            every {
                mockContext.getSharedPreferences(
                    AppsFlyerDestination.AF_SEGMENT_SHARED_PREF,
                    0
                )
            } returns mockSharedPreferences

            every { mockSharedPreferences.edit() } returns mockEditor
            every { mockEditor.putBoolean(any(), any()) } returns mockEditor
            appsflyerDestination.conversionListener = mockDelegateListener
        }

        @Test
        fun `onConversionDataSuccess fires attribution track`() {
            every {
                mockSharedPreferences.getBoolean(AppsFlyerDestination.CONV_KEY, false)
            } returns false

            val attributionData = mapOf(
                "media_source" to "facebook",
                "adgroup" to "gaming",
                "campaign" to "pilot_promotion",
                "product_status" to "pilot",
                "timestamp" to 1632855725,
                "user" to mapOf(
                    "name" to "John Doe",
                    "auth" to true,
                    "previous_games" to listOf("Dark Souls", "Sekiro")
                )
            )
            conversionListener.onConversionDataSuccess(attributionData)

            verify { mockEditor.putBoolean(AppsFlyerDestination.CONV_KEY, true) }

            val expectedProps = buildJsonObject {
                put("campaign", buildJsonObject {
                    put("source", "facebook")
                    put("name", "pilot_promotion")
                    put("ad_group", "gaming")
                })
                put("provider", "AppsFlyer")
                put("product_status", "pilot")
                put("timestamp", 1632855725)
                put("user", buildJsonObject {
                    put("name", "John Doe")
                    put("auth", true)
                    put("previous_games", buildJsonArray {
                        add("Dark Souls")
                        add("Sekiro")
                    })
                })
            }
            verify { mockedAnalytics.track("Install Attributed", expectedProps) }
        }

        @Test
        fun `onConversionDataSuccess does not fire attribution track`() {
            every {
                mockSharedPreferences.getBoolean(AppsFlyerDestination.CONV_KEY, false)
            } returns true

            val attributionData = mapOf(
                "media_source" to "facebook",
                "adgroup" to "gaming",
                "campaign" to "pilot_promotion",
                "product_status" to "pilot",
                "timestamp" to 1632855725,
                "user" to mapOf(
                    "name" to "John Doe",
                    "auth" to true,
                    "previous_games" to listOf("Dark Souls", "Sekiro")
                )
            )
            conversionListener.onConversionDataSuccess(attributionData)

            verify { mockedAnalytics wasNot Called }
        }

        @Test
        fun `onConversionDataSuccess delegates to registered listener`() {
            every {
                mockSharedPreferences.getBoolean(any(), any())
            } returns true
            conversionListener.onConversionDataSuccess(mapOf("basic" to "data"))
            verify { mockDelegateListener.onConversionDataSuccess(mapOf("basic" to "data")) }
        }

        @Test
        fun `onConversionDataFail delegates to registered listener`() {
            conversionListener.onConversionDataFail("error message")
            verify { mockDelegateListener.onConversionDataFail("error message") }
        }

        @Test
        fun `onAppOpenAttribution delegates to registered listener`() {
            conversionListener.onAppOpenAttribution(mapOf("basic" to "data"))
            verify { mockDelegateListener.onAppOpenAttribution(mapOf("basic" to "data")) }
        }

        @Test
        fun `onAttributionFailure delegates to registered listener`() {
            conversionListener.onAttributionFailure("error message")
            verify { mockDelegateListener.onAttributionFailure("error message") }
        }

    }

}