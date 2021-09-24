package com.segment.analytics.destinations.plugins

import android.app.Activity
import android.content.Context
import androidx.core.os.bundleOf
import com.appsflyer.AppsFlyerLib
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.log
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

        mockkStatic(Class.forName("com.segment.analytics.kotlin.core.platform.plugins.LoggerKt").kotlin)
        every { mockedAnalytics.log(any(), any(), any()) } just Runs

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



}
//
//data class MapCheckMatcher(private val expectedMap: Map<String, Any>) : Matcher<Map<String, Any>> {
//    override fun match(arg: Map<String, Any>?): Boolean {
//        if (arg == null) {
//            return false
//        } else {
//            if (expectedMap.size != arg.size) {
//                return false
//            }
//            if (!expectedMap.entries.all { arg[it.key] == it.value }) {
//                return false
//            }
//            return true
//        }
//    }
//
//    override fun toString(): String {
//        return "MapCheckMatcher($expectedMap)"
//    }
//}
//
//private fun MockKMatcherScope.matchMap(expectedMap: Map<String, Any>) = match(MapCheckMatcher(expectedMap))
