package com.segment.analytics.destinations.plugins

import android.content.Context
import com.comscore.streaming.StreamingAnalytics
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.utilities.getString
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import com.segment.analytics.kotlin.core.Analytics as SegmentAnalytics
import com.comscore.streaming.StreamingConfiguration
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.LenientJson
import org.junit.jupiter.api.Test

class ComscoreDestinationTests {

    @MockK(relaxUnitFun = true)
    lateinit var mockedComscoreAnalytics: ComscoreAnalytics

    @MockK(relaxUnitFun = true)
    lateinit var mockedStreamingAnalytics: StreamingAnalytics

    @MockK(relaxUnitFun = true)
    lateinit var streamingConfiguration: StreamingConfiguration


    lateinit var comscoreDestination: ComscoreDestination

    @MockK
    lateinit var mockedAnalytics: SegmentAnalytics

    @MockK
    lateinit var mockedContext: Context

    init {
        MockKAnnotations.init(this)
    }

    @BeforeEach
    fun setup() {
        clearMocks(mockedStreamingAnalytics)
        comscoreDestination = ComscoreDestination(mockedComscoreAnalytics)
        every { mockedAnalytics.configuration.application } returns mockedContext
        comscoreDestination.setup(mockedAnalytics)
        every { mockedComscoreAnalytics.createStreamingAnalytics() } returns mockedStreamingAnalytics
        every { mockedStreamingAnalytics.configuration } returns streamingConfiguration
    }

    @Test
    fun `settings are updated correctly`() {
        // An example settings blob
        val settingsBlob: Settings = LenientJson.decodeFromString(
            """
            {
              "integrations": {
                "comScore": {
                  "appName": "TestApp",
                  "autoUpdate": true,
                  "autoUpdateInterval": 60,
                  "beaconParamMap": {
                    
                  },
                  "c2": "123456",
                  "comscorekw": "",
                  "foregroundOnly": true,
                  "publisherSecret": "my-random-secret",
                  "useHTTPS": true,
                  "versionSettings": {
                    "version": "3.0.0",
                    "componentTypes": [
                      "browser",
                      "ios",
                      "android"
                    ]
                  },
                  "type": "browser",
                  "bundlingStatus": "bundled"
                }
              }
            }
        """.trimIndent()
        )

        val partnerId = slot<String>()
        every { mockedComscoreAnalytics.start(any(), capture(partnerId), any()) } just Runs

        comscoreDestination.update(settingsBlob, Plugin.UpdateType.Initial)

        /* assertions about config */
        assertNotNull(comscoreDestination.settings)
        with(comscoreDestination.settings!!) {
            assertTrue(autoUpdate)
            assertTrue(foregroundOnly)
            assertTrue(useHTTPS)
            assertEquals(60, autoUpdateInterval)
            assertEquals("123456", c2)
            assertEquals("TestApp", appName)
            assertEquals("my-random-secret", publisherSecret)
        }

        assertEquals("24186693", partnerId.captured)
    }

    @Test
    fun `identify is handled correctly`() {
        val sampleEvent = IdentifyEvent(
            userId = "kyloren",
            traits = buildJsonObject {
                put("email", "kylo@sith.com")
                put("firstName", "Kylo")
                put("lastName", "Ren")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        val identifyEvent = comscoreDestination.identify(sampleEvent)

        assertNotNull(identifyEvent)
        with(identifyEvent as IdentifyEvent) {
            assertEquals("kyloren", userId)
            with(traits) {
                assertEquals(5, size)
                assertEquals("kyloren", getString("userId"))
                assertEquals("anonId", getString("anonymousId"))
            }
        }

        val expectedLabels = mapOf(
            "userId" to "kyloren",
            "anonymousId" to "anonId",
            "email" to "kylo@sith.com",
            "firstName" to "Kylo",
            "lastName" to "Ren"
        )

        verify { mockedComscoreAnalytics.setPersistentLabels(expectedLabels) }
    }

    @Test
    fun `screen is handled correctly`() {
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
        val screenEvent = comscoreDestination.screen(sampleEvent)

        assertNotNull(screenEvent)
        with(screenEvent as ScreenEvent) {
            assertEquals("LoginFragment", name)
            with(properties) {
                assertEquals(4, size)
                assertEquals("LoginFragment", getString("name"))
                assertEquals("signup_flow", getString("category"))
            }
        }

        val expectedProps = mapOf(
            "name" to "LoginFragment",
            "category" to "signup_flow",
            "startup" to "false",
            "parent" to "MainActivity"
        )

        verify { mockedComscoreAnalytics.notifyViewEvent(expectedProps) }
    }


    @Test
    fun `track for non-video event`() {
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
        val trackEvent = comscoreDestination.track(sampleEvent)

        /* assertions about new event */
        assertNotNull(trackEvent)
        with(trackEvent as TrackEvent) {
            assertEquals("Product Clicked", event)
        }

        val expectedProperties = mapOf(
            "Item Name" to "Biscuits",
            "name" to "Product Clicked"
        )
        verify { mockedComscoreAnalytics.notifyHiddenEvent(expectedProperties) }
    }

    @ParameterizedTest(name = "{displayName}: {arguments}")
    @ValueSource(booleans = [true, false])
    fun `track video playback started with properties`(useCamelCaseProps: Boolean) {
        val expected = mapOf(
            "ns_st_mp" to "youtube",
            "ns_st_vo" to "80",
            "ns_st_ws" to "norm",
            "ns_st_br" to "0",
            "c3" to "some value",
            "c4" to "another value",
            "c6" to "and another one",
        )

        val playbackStarted = if (useCamelCaseProps) {
            TrackEvent(
                event = "Video Playback Started",
                properties = buildJsonObject {
                    put("assetId", 1234)
                    put("adType", "pre-roll")
                    put("totalLength", 120)
                    put("videoPlayer", "youtube")
                    put("sound", 80)
                    put("fullScreen", false)
                    put("c3", "some value")
                    put("c4", "another value")
                    put("c6", "and another one")
                }
            )
        } else {
            TrackEvent(
                event = "Video Playback Started",
                properties = buildJsonObject {
                    put("asset_id", 1234)
                    put("ad_type", "pre-roll")
                    put("total_length", 120)
                    put("video_player", "youtube")
                    put("sound", 80)
                    put("full_screen", false)
                    put("c3", "some value")
                    put("c4", "another value")
                    put("c6", "and another one")
                }
            )
        }.apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }

        comscoreDestination.track(playbackStarted)

        verify { mockedStreamingAnalytics.createPlaybackSession() }
        verify { streamingConfiguration.addLabels(expected) }

        with(comscoreDestination.configurationLabels) {
            assertEquals("1234", get("ns_st_ci"))
            assertEquals("pre-roll", get("ns_st_ad"))
        }
    }

    private fun trackVideoPlaybackStarted() {
        val playbackStarted = TrackEvent(
            event = "Video Playback Started",
            properties = buildJsonObject {
                put("asset_id", 1234)
                put("ad_type", "post-roll")
                put("total_length", 120)
                put("video_player", "youtube")
                put("sound", 80)
                put("bitrate", 40)
                put("full_screen", true)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }

        comscoreDestination.track(playbackStarted)
    }

    // MIGHT BE REDUNDANT
    @Test
    fun `track video playback started`() {
        trackVideoPlaybackStarted()

        val expected = mapOf(
            "ns_st_mp" to "youtube",
            "ns_st_vo" to "80",
            "ns_st_ws" to "full",
            "ns_st_br" to "40000",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null",
        )

        assertEquals(mockedStreamingAnalytics, comscoreDestination.streamingAnalytics)

        verify { mockedStreamingAnalytics.createPlaybackSession() }
        verify { streamingConfiguration.addLabels(expected) }

        with(comscoreDestination.configurationLabels) {
            assertEquals("1234", get("ns_st_ci"))
            assertEquals("post-roll", get("ns_st_ad"))
        }
    }

    @Test
    fun `track video playback paused without video playback started`() {
        val playbackPaused = TrackEvent(
            event = "Video Playback Paused",
            properties = buildJsonObject {
                put("asset_id", 1234)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(playbackPaused)

        assertNull(comscoreDestination.streamingAnalytics)
    }

    @Test
    fun `track video playback paused`() {
        TODO("Relies on options talk to sneed")
        trackVideoPlaybackStarted()
        val playbackPaused = TrackEvent(
            event = "Video Playback Paused",
            properties = buildJsonObject {
                put("asset_id", 1234)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(playbackPaused)

        assertNull(comscoreDestination.streamingAnalytics)
    }


    @Test
    fun videoPlaybackBufferStarted() {
        trackVideoPlaybackStarted()
        val playbackBufferStarted = TrackEvent(
            event = "Video Playback Buffer Started",
            properties = buildJsonObject {
                put("assetId", 7890)
                put("adType", "post-roll")
                put("totalLength", 700)
                put("videoPlayer", "youtube")
                put("playbackPosition", 20)
                put("fullScreen", false)
                put("bitrate", 500)
                put("sound", 80)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(playbackBufferStarted)
        val expected = mapOf(
            "ns_st_mp" to "youtube",
            "ns_st_vo" to "80",
            "ns_st_ws" to "norm",
            "ns_st_br" to "500000",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null",
        )
        verify { mockedStreamingAnalytics.startFromPosition(20) }
        verify { mockedStreamingAnalytics.notifyBufferStart() }
        verify { streamingConfiguration.addLabels(expected) }
    }

    @Test
    fun videoPlaybackBufferCompleted() {
        trackVideoPlaybackStarted()
        val playbackBufferCompleted = TrackEvent(
            event = "Video Playback Buffer Completed",
            properties = buildJsonObject {
                put("asset_id", 1029)
                put("ad_type", "pre-roll")
                put("total_length", 800)
                put("video_player", "vimeo")
                put("position", 30)
                put("full_screen", true)
                put("bitrate", 500)
                put("sound", 80)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(playbackBufferCompleted)
        val expected = mapOf(
            "ns_st_mp" to "vimeo",
            "ns_st_vo" to "80",
            "ns_st_ws" to "full",
            "ns_st_br" to "500000",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null",
        )
        verify { mockedStreamingAnalytics.startFromPosition(30) }
        verify { mockedStreamingAnalytics.notifyBufferStop() }
        verify { streamingConfiguration.addLabels(expected) }
    }

    @Test
    fun videoPlaybackSeekStarted() {
        trackVideoPlaybackStarted()
        val playbackSeekStarted = TrackEvent(
            event = "Video Playback Seek Started",
            properties = buildJsonObject {
                put("asset_id", 3948)
                put("ad_type", "mid-roll")
                put("total_length", 900)
                put("video_player", "youtube")
                put("position", 40)
                put("full_screen", true)
                put("bitrate", 500)
                put("sound", 80)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(playbackSeekStarted)
        val expected = mapOf(
            "ns_st_mp" to "youtube",
            "ns_st_vo" to "80",
            "ns_st_ws" to "full",
            "ns_st_br" to "500000",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null",
        )
        verify { mockedStreamingAnalytics.notifySeekStart() }
        verify { streamingConfiguration.addLabels(expected) }
    }

    @Test
    fun videoPlaybackSeekCompleted() {
        trackVideoPlaybackStarted()
        val playbackSeekCompleted = TrackEvent(
            event = "Video Playback Seek Completed",
            properties = buildJsonObject {
                put("assetId", 6767)
                put("adType", "post-roll")
                put("totalLength", 400)
                put("videoPlayer", "vimeo")
                put("playbackPosition", 50)
                put("fullScreen", true)
                put("bitrate", 500)
                put("sound", 80)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(playbackSeekCompleted)
        val expected: LinkedHashMap<String, String> = LinkedHashMap()
        expected["ns_st_mp"] = "vimeo"
        expected["ns_st_vo"] = "80"
        expected["ns_st_ws"] = "full"
        expected["ns_st_br"] = "500000"
        expected["c3"] = "*null"
        expected["c4"] = "*null"
        expected["c6"] = "*null"
        verify { mockedStreamingAnalytics.startFromPosition(50) }
        verify { mockedStreamingAnalytics.notifyPlay() }
        verify { streamingConfiguration.addLabels(expected) }
    }

    @Test
    fun videoPlaybackResumed() {
        trackVideoPlaybackStarted()
        val playbackResumed = TrackEvent(
            event = "Video Playback Resumed",
            properties =
            buildJsonObject {
                put("assetId", 5332)
                put("adType", "post-roll")
                put("totalLength", 100)
                put("videoPlayer", "youtube")
                put("playbackPosition", 60)
                put("fullScreen", true)
                put("bitrate", 500)
                put("sound", 80)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(playbackResumed)
        val expected: LinkedHashMap<String, String> = LinkedHashMap()
        expected["ns_st_mp"] = "youtube"
        expected["ns_st_vo"] = "80"
        expected["ns_st_ws"] = "full"
        expected["ns_st_br"] = "500000"
        expected["c3"] = "*null"
        expected["c4"] = "*null"
        expected["c6"] = "*null"
        verify { mockedStreamingAnalytics.startFromPosition(60) }
        verify { mockedStreamingAnalytics.notifyPlay() }
        verify { streamingConfiguration.addLabels(expected) }
    }

    @Test
    fun videoContentStartedWithDigitalAirdate() {
        TODO()
//        trackVideoPlaybackStarted()
//        val comScoreOptions: MutableMap<String, Any> = LinkedHashMap()
//        comScoreOptions["digitalAirdate"] = "2014-01-20"
//        comScoreOptions["contentClassificationType"] = "vc12"
//        integration.track(
//            TrackEvent(
//                event = "Video Content Started"
//            )
//                    properties =
//                    buildJsonObject {
//                put("assetId", 9324)
//                put("title", "Meeseeks and Destroy")
//                put("season", 1)
//                put("episode", 5)
//                put("genre", "cartoon")
//                put("program", "Rick and Morty")
//                put("channel", "cartoon network")
//                put("publisher", "Turner Broadcasting System")
//                put("fullEpisode", true)
//                put("podId", "segment A")
//                put("totalLength", "120")
//                put("playbackPosition", 70)
//                )
//                .integration("comScore", comScoreOptions
//            }
//        )
//        val expected: LinkedHashMap<String, String> = LinkedHashMap()
//        expected["ns_st_ci"] = "9324"
//        expected["ns_st_ep"] = "Meeseeks and Destroy"
//        expected["ns_st_sn"] = "1"
//        expected["ns_st_en"] = "5"
//        expected["ns_st_ge"] = "cartoon"
//        expected["ns_st_pr"] = "Rick and Morty"
//        expected["ns_st_st"] = "cartoon network"
//        expected["ns_st_pu"] = "Turner Broadcasting System"
//        expected["ns_st_ce"] = "true"
//        expected["ns_st_ddt"] = "2014-01-20"
//        expected["ns_st_pn"] = "segment A"
//        expected["ns_st_cl"] = "120000"
//        expected["ns_st_ct"] = "vc12"
//        expected["c3"] = "*null"
//        expected["c4"] = "*null"
//        expected["c6"] = "*null"
//        verify {streamingAnalytics).startFromPosition(70)
//        verify {streamingAnalytics).notifyPlay()
//        verify {streamingAnalytics, atLeast(1))
//            .setMetadata(refEq(getContentMetadata(expected)))
    }

    @Test
    fun videoContentStartedWithTVAirdate() {
        TODO()
//        trackVideoPlaybackStarted()
//        val comScoreOptions: MutableMap<String, Any> = LinkedHashMap()
//        comScoreOptions["tvAirdate"] = "2017-05-14"
//        comScoreOptions["contentClassificationType"] = "vc12"
//        integration.track(
//            TrackEvent(
//                event = "Video Content Started"
//            )
//                    properties =
//                    buildJsonObject {
//                put("title", "Meeseeks and Destroy")
//                put("season", 1)
//                put("episode", 5)
//                put("genre", "cartoon")
//                put("program", "Rick and Morty")
//                put("channel", "cartoon network")
//                put("publisher", "Turner Broadcasting System")
//                put("full_episode", true)
//                put("pod_id", "segment A")
//                put("total_length", "120")
//                put("position", 70)
//                )
//                .integration("comScore", comScoreOptions
//            }
//        )
//        val expected: LinkedHashMap<String, String> = LinkedHashMap()
//        expected["ns_st_ci"] = "0"
//        expected["ns_st_ep"] = "Meeseeks and Destroy"
//        expected["ns_st_sn"] = "1"
//        expected["ns_st_en"] = "5"
//        expected["ns_st_ge"] = "cartoon"
//        expected["ns_st_pr"] = "Rick and Morty"
//        expected["ns_st_st"] = "cartoon network"
//        expected["ns_st_pu"] = "Turner Broadcasting System"
//        expected["ns_st_ce"] = "true"
//        expected["ns_st_tdt"] = "2017-05-14"
//        expected["ns_st_pn"] = "segment A"
//        expected["ns_st_cl"] = "120000"
//        expected["ns_st_ct"] = "vc12"
//        expected["c3"] = "*null"
//        expected["c4"] = "*null"
//        expected["c6"] = "*null"
//        verify {streamingAnalytics).startFromPosition(70)
//        verify {streamingAnalytics).notifyPlay()
//        verify {streamingAnalytics, atLeast(1))
//            .setMetadata(refEq(getContentMetadata(expected)))
    }

    @Test
    fun videoContentStartedWithoutVideoPlaybackStarted() {
        comscoreDestination.track(
            TrackEvent(
                event = "Video Content Started",
                properties = buildJsonObject { put("assetId", 5678) }
            ).apply {
                messageId = "qwerty-1234"
                anonymousId = "foo"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = "2021-07-13T00:59:09"
            })
        assertNull(comscoreDestination.streamingAnalytics)
    }

    @Test
    fun videoContentPlaying() {
        trackVideoPlaybackStarted()
        assertNotNull(comscoreDestination.configurationLabels["ns_st_ad"])
        val contentPlaying = TrackEvent(
            event = "Video Content Playing",
            properties = buildJsonObject {
                put("assetId", 123214)
                put("title", "Look Who's Purging Now")
                put("season", 2)
                put("episode", 9)
                put("genre", "cartoon")
                put("program", "Rick and Morty")
                put("channel", "cartoon network")
                put("publisher", "Turner Broadcasting System")
                put("fullEpisode", true)
                put("airdate", "2015-09-27")
                put("podId", "segment A")
                put("playbackPosition", 70)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(contentPlaying)
        verify { mockedStreamingAnalytics.startFromPosition(70) }
        verify { mockedStreamingAnalytics.notifyPlay() }
    }

    @Test
    fun videoContentPlayingWithAdType() {
        trackVideoPlaybackStarted()
        assertNotNull(comscoreDestination.configurationLabels.get("ns_st_ad"))
        val contentPlaying = TrackEvent(
            event = "Video Content Playing",
            properties = buildJsonObject {
                put("assetId", 123214)
                put("title", "Look Who's Purging Now")
                put("season", 2)
                put("episode", 9)
                put("genre", "cartoon")
                put("program", "Rick and Morty")
                put("channel", "cartoon network")
                put("publisher", "Turner Broadcasting System")
                put("fullEpisode", true)
                put("podId", "segment A")
                put("playbackPosition", 70)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(contentPlaying)
        val expected: LinkedHashMap<String, String> = LinkedHashMap()
        expected["ns_st_ci"] = "123214"
        expected["ns_st_ep"] = "Look Who's Purging Now"
        expected["ns_st_sn"] = "2"
        expected["ns_st_en"] = "9"
        expected["ns_st_ge"] = "cartoon"
        expected["ns_st_pr"] = "Rick and Morty"
        expected["ns_st_st"] = "cartoon network"
        expected["ns_st_pu"] = "Turner Broadcasting System"
        expected["ns_st_ce"] = "true"
        expected["ns_st_pn"] = "segment A"
        expected["ns_st_ct"] = "vc00"
        expected["c3"] = "*null"
        expected["c4"] = "*null"
        expected["c6"] = "*null"
//        verify {mockedStreamingAnalytics, atLeast(1))
//            .setMetadata(refEq(getContentMetadata(expected)))
        verify { mockedStreamingAnalytics.startFromPosition(70) }
        verify { mockedStreamingAnalytics.notifyPlay() }
    }

    @Test
    fun videoContentCompleted() {
        trackVideoPlaybackStarted()
        val contentCompleted = TrackEvent(
            event = "Video Content Completed",
            properties =
            buildJsonObject {
                put("assetId", 9324)
                put("title", "Raising Gazorpazorp")
                put("season", 1)
                put("episode", 7)
                put("genre", "cartoon")
                put("program", "Rick and Morty")
                put("channel", "cartoon network")
                put("publisher", "Turner Broadcasting System")
                put("fullEpisode", true)
                put("airdate", "2014-10-20")
                put("podId", "segment A")
                put("playbackPosition", 80)
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(contentCompleted)
        verify { mockedStreamingAnalytics.notifyEnd() }
    }

    @Test
    fun videoAdStarted() {
        trackVideoPlaybackStarted()
        assertNotNull(comscoreDestination.configurationLabels["ns_st_ad"])
        val adStarted = TrackEvent(
            event = "Video Ad Started",
            properties =
            buildJsonObject {
                put("asset_id", 4311)
                put("pod_id", "adSegmentA")
                put("type", "pre-roll")
                put("total_length", 120)
                put("position", 0)
                put("title", "Helmet Ad")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(adStarted)
        val expected: LinkedHashMap<String, String> = LinkedHashMap()
        expected["ns_st_ami"] = "4311"
        expected["ns_st_ad"] = "pre-roll"
        expected["ns_st_cl"] = "120000"
        expected["ns_st_amt"] = "Helmet Ad"
        expected["ns_st_ct"] = "va00"
        expected["c3"] = "*null"
        expected["c4"] = "*null"
        expected["c6"] = "*null"
        verify { mockedStreamingAnalytics.startFromPosition(0) }
        verify { mockedStreamingAnalytics.notifyPlay() }
//        verify {streamingAnalytics).setMetadata(refEq(getAdvertisementMetadata(expected)))
    }

    @Test
    fun videoAdStartedWithContentId() {
        trackVideoPlaybackStarted()
        val adStarted = TrackEvent(
            event = "Video Ad Started",
            properties =
            buildJsonObject {
                put("assetId", 4311)
                put("podId", "adSegmentA")
                put("type", "pre-roll")
                put("totalLength", 120)
                put("playbackPosition", 0)
                put("title", "Helmet Ad")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(adStarted)
        val expected: LinkedHashMap<String, String> = LinkedHashMap()
        expected["ns_st_ami"] = "4311"
        expected["ns_st_ad"] = "pre-roll"
        expected["ns_st_cl"] = "120000"
        expected["ns_st_amt"] = "Helmet Ad"
        expected["ns_st_ct"] = "va00"
        expected["c3"] = "*null"
        expected["c4"] = "*null"
        expected["c6"] = "*null"
        expected["ns_st_ci"] = "1234"
        verify { mockedStreamingAnalytics.startFromPosition(0) }
        verify { mockedStreamingAnalytics.notifyPlay() }
//        verify {streamingAnalytics).setMetadata(refEq(getAdvertisementMetadata(expected)))
    }

    @Test
    fun videoAdStartedWithAdClassificationType() {
        TODO()
//        trackVideoPlaybackStarted()
//        val comScoreOptions: MutableMap<String, Any> = LinkedHashMap()
//        comScoreOptions["adClassificationType"] = "va14"
//        integration.track(
//            TrackEvent(
//                event = "Video Ad Started"
//            )
//                    properties =
//                    buildJsonObject {
//                put("asset_id", 4311)
//                put("pod_id", "adSegmentA")
//                put("type", "pre-roll")
//                put("total_length", 120)
//                put("position", 0)
//                put("title", "Helmet Ad")
//                )
//                .integration("comScore", comScoreOptions
//            }
//        )
//        val expected: LinkedHashMap<String, String> = LinkedHashMap()
//        expected["ns_st_ami"] = "4311"
//        expected["ns_st_ad"] = "pre-roll"
//        expected["ns_st_cl"] = "120000"
//        expected["ns_st_amt"] = "Helmet Ad"
//        expected["ns_st_ct"] = "va14"
//        expected["c3"] = "*null"
//        expected["c4"] = "*null"
//        expected["c6"] = "*null"
//        streamingAnalytics.startFromPosition(0)
//        verify {streamingAnalytics).notifyPlay()
//        verify {streamingAnalytics).setMetadata(refEq(getAdvertisementMetadata(expected)))
    }

    @Test
    fun videoAdStartedWithoutVideoPlaybackStarted() {
        comscoreDestination.track(
            TrackEvent(
                event = "Video Ad Started",
                properties = buildJsonObject { put("assetId", 4324) }
            ).apply {
                messageId = "qwerty-1234"
                anonymousId = "foo"
                integrations = emptyJsonObject
                context = emptyJsonObject
                timestamp = "2021-07-13T00:59:09"
            }
        )
        assertNull(comscoreDestination.streamingAnalytics)
    }

    @Test
    fun videoAdPlaying() {
        trackVideoPlaybackStarted()
        assertNotNull(comscoreDestination.configurationLabels["ns_st_ad"])
        val adPlaying = TrackEvent(
            event = "Video Ad Playing",
            properties =
            buildJsonObject {
                put("assetId", 4311)
                put("podId", "adSegmentA")
                put("adType", "pre-roll")
                put("totalLength", 120)
                put("playbackPosition", 20)
                put("title", "Helmet Ad")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        comscoreDestination.track(adPlaying)
        verify { mockedStreamingAnalytics.startFromPosition(20) }
        verify { mockedStreamingAnalytics.notifyPlay() }
    }

    @Test
    fun videoAdCompleted() {
        trackVideoPlaybackStarted()
        val adCompleted = TrackEvent(
            event = "Video Ad Completed",
            properties =
            buildJsonObject {
                put("assetId", 3425)
                put("podId", "adSegmentb")
                put("type", "mid-roll")
                put("totalLength", 100)
                put("playbackPosition", 100)
                put("title", "Helmet Ad")
            }
        ).apply {
            messageId = "qwerty-1234"
            anonymousId = "foo"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = "2021-07-13T00:59:09"
        }
        mockedStreamingAnalytics
        comscoreDestination.track(adCompleted)
        verify { mockedStreamingAnalytics.notifyEnd() }
    }
}