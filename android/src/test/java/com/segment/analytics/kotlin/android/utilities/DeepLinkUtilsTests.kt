package com.segment.analytics.kotlin.android.utilities

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.segment.analytics.kotlin.android.AndroidStorageProvider
import com.segment.analytics.kotlin.android.plugins.getUniqueID
import com.segment.analytics.kotlin.android.utils.MemorySharedPreferences
import com.segment.analytics.kotlin.android.utils.testAnalytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.emptyJsonObject
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeepLinkUtilsTests {
    lateinit var appContext: Context
    lateinit var analytics: Analytics
    lateinit var deepLinkUtils: DeepLinkUtils
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)


    @Before
    fun setup() {
        appContext = spyk(InstrumentationRegistry.getInstrumentation().targetContext)
        val sharedPreferences: SharedPreferences = MemorySharedPreferences()
        every { appContext.getSharedPreferences(any(), any()) } returns sharedPreferences
        mockkStatic("com.segment.analytics.kotlin.android.plugins.AndroidContextPluginKt")
        every { getUniqueID() } returns "unknown"

        analytics = testAnalytics(
            Configuration(
                writeKey = "123",
                application = appContext,
                storageProvider = AndroidStorageProvider
            ),
            testScope, testDispatcher
        )
        deepLinkUtils = DeepLinkUtils(analytics)
    }

    @Test
    fun extractLinkPropertiesTest() {
        val link =
            "https://stockx.com/?utm_source=af&utm_medium=imp&utm_campaign=1310690&impactSiteId=VupTG%3ASM2xyKUReTwuwulVAxUksw710t1yqKR80&clickid=VupTG%3ASM2xyKUReTwuwulVAxUksw710t1yqKR80&utm_term=VupTG%3ASM2xyKUReTwuwulVAxUksw710t1yqKR80&utm_content=1868737_570105&irgwc=1&irclickid=VupTG%3ASM2xyKUReTwuwulVAxUksw710t1yqKR80&ir_campaignid=9060&ir_adid=570105&ir_partnerid=1310690&gad_source=1&referrer=gclid%3DCjwKCAiAiaC-BhBEEiwAjY99qHbSPJ49pAI83Lo4L7bV3GKaUxSyOX4lah88GFkcNGYQ_MLIZGwXcBoCFAwQAvD_BwE&gref=EkQKPAoICICJoL4GEEQSLACNj32odtI8nj2kAjzcujgvttXcYppTFLI5fiVqHzwYWRw0ZhD8wshkbBdwGgIUDBAC8P8HARjt_K_sKQ"

        val expectedProperties = buildJsonObject {
            put(
                "referrer",
                JsonPrimitive("gclid=CjwKCAiAiaC-BhBEEiwAjY99qHbSPJ49pAI83Lo4L7bV3GKaUxSyOX4lah88GFkcNGYQ_MLIZGwXcBoCFAwQAvD_BwE")
            )
            put("utm_source", JsonPrimitive("af"))
            put("utm_medium", JsonPrimitive("imp"))
            put("utm_campaign", JsonPrimitive("1310690"))
            put("impactSiteId", JsonPrimitive("VupTG:SM2xyKUReTwuwulVAxUksw710t1yqKR80"))
            put("clickid", JsonPrimitive("VupTG:SM2xyKUReTwuwulVAxUksw710t1yqKR80"))
            put("utm_term", JsonPrimitive("VupTG:SM2xyKUReTwuwulVAxUksw710t1yqKR80"))
            put("utm_content", JsonPrimitive("1868737_570105"))
            put("irgwc", JsonPrimitive("1"))
            put("irclickid", JsonPrimitive("VupTG:SM2xyKUReTwuwulVAxUksw710t1yqKR80"))
            put("ir_campaignid", JsonPrimitive("9060"))
            put("ir_adid", JsonPrimitive("570105"))
            put("ir_partnerid", JsonPrimitive("1310690"))
            put("gad_source", JsonPrimitive("1"))
            put(
                "gref",
                JsonPrimitive("EkQKPAoICICJoL4GEEQSLACNj32odtI8nj2kAjzcujgvttXcYppTFLI5fiVqHzwYWRw0ZhD8wshkbBdwGgIUDBAC8P8HARjt_K_sKQ")
            )
            put(
                "url",
                JsonPrimitive("https://stockx.com/?utm_source=af&utm_medium=imp&utm_campaign=1310690&impactSiteId=VupTG%3ASM2xyKUReTwuwulVAxUksw710t1yqKR80&clickid=VupTG%3ASM2xyKUReTwuwulVAxUksw710t1yqKR80&utm_term=VupTG%3ASM2xyKUReTwuwulVAxUksw710t1yqKR80&utm_content=1868737_570105&irgwc=1&irclickid=VupTG%3ASM2xyKUReTwuwulVAxUksw710t1yqKR80&ir_campaignid=9060&ir_adid=570105&ir_partnerid=1310690&gad_source=1&referrer=gclid%3DCjwKCAiAiaC-BhBEEiwAjY99qHbSPJ49pAI83Lo4L7bV3GKaUxSyOX4lah88GFkcNGYQ_MLIZGwXcBoCFAwQAvD_BwE&gref=EkQKPAoICICJoL4GEEQSLACNj32odtI8nj2kAjzcujgvttXcYppTFLI5fiVqHzwYWRw0ZhD8wshkbBdwGgIUDBAC8P8HARjt_K_sKQ")
            )
        }

        // This should extract all query parameters as properties including a value for the referer property
        val properties = deepLinkUtils.extractLinkProperties("not used", Uri.parse(link))

        assertEquals(expectedProperties, properties)
    }

    @Test
    fun differentUriTest() {
        var properties = deepLinkUtils.extractLinkProperties(null, Uri.parse("http://example.com?prop1=foo"))
        assertEquals(
            buildJsonObject {
                put("prop1", JsonPrimitive("foo"))
                put("url", JsonPrimitive("http://example.com?prop1=foo"))
            },
            properties
        )

        properties = deepLinkUtils.extractLinkProperties(null, Uri.parse("example.com?prop1=foo"))
        assertEquals(
            buildJsonObject {
                put("prop1", JsonPrimitive("foo"))
                put("url", JsonPrimitive("example.com?prop1=foo"))
            },
            properties
        )

        // Even though this Uri has a "?prop1=foo" string at the end, it's not a known part of
        // the Uri scheme so we won't be able to use it.
        properties = deepLinkUtils.extractLinkProperties(null, Uri.parse("mailto:me@email.com?prop1=foo"))
        assertEquals(
            buildJsonObject {
                put("url", JsonPrimitive("mailto:me@email.com?prop1=foo"))
            },
            properties
        )
    }
}