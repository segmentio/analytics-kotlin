package com.segment.analytics.destinations.plugins

import android.content.Context
import com.comscore.streaming.StreamingAnalytics
import com.comscore.streaming.StreamingConfiguration
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.System
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.log
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.kotlin.core.utilities.getString
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import sovran.kotlin.Store
import com.segment.analytics.kotlin.core.Analytics as SegmentAnalytics

/**
 * Note: Some of the behaviour requires the use of the enrichment plugin ComscoreOptionsPlugin
 * which is usually inserted into the analytics timeline, but for the purpose of these tests, will
 * be inserted into the destination timeline.
 */
class ComscoreDestinationTests {

    @MockK(relaxUnitFun = true)
    lateinit var mockedComscoreAnalytics: ComscoreAnalytics

    @MockK(relaxUnitFun = true)
    lateinit var mockedStreamingAnalytics: StreamingAnalytics

    @MockK(relaxUnitFun = true)
    lateinit var mockedStreamingConfiguration: StreamingConfiguration


    lateinit var comscoreDestination: ComscoreDestination

    @MockK(relaxUnitFun = true)
    lateinit var mockedAnalytics: SegmentAnalytics

    @MockK
    lateinit var mockedStore: Store

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
        every { mockedAnalytics.store } returns mockedStore
        every { mockedStore.currentState(System::class) } returns null
        comscoreDestination.setup(mockedAnalytics)
        every { mockedComscoreAnalytics.createStreamingAnalytics() } returns mockedStreamingAnalytics
        every { mockedStreamingAnalytics.configuration } returns mockedStreamingConfiguration

        mockkStatic(Class.forName("com.segment.analytics.kotlin.core.platform.plugins.LoggerKt").kotlin)
        every { mockedAnalytics.log(any(), any(), any()) } just Runs

        val comscoreOptionsPlugin = ComscoreOptionsPlugin()
        comscoreDestination.comscoreOptionsPlugin = comscoreOptionsPlugin
        comscoreDestination.add(comscoreOptionsPlugin)
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

        comscoreDestination = ComscoreDestination(mockedComscoreAnalytics)
        comscoreDestination.setup(mockedAnalytics)
        val partnerId = slot<String>()
        every { mockedComscoreAnalytics.start(any(), capture(partnerId), any()) } just Runs
        every { mockedAnalytics.add(any()) } returns mockedAnalytics

        comscoreDestination.update(settingsBlob, Plugin.UpdateType.Initial)

        /* assertions about config */
        assertNotNull(comscoreDestination.settings)
        assertNotNull(comscoreDestination.comscoreOptionsPlugin)
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
        ).applyBaseEventData()
        val identifyEvent = comscoreDestination.process(sampleEvent)

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
        ).applyBaseEventData()
        val screenEvent = comscoreDestination.process(sampleEvent)

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
        ).applyBaseEventData()
        val trackEvent = comscoreDestination.process(sampleEvent)

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
        }.applyBaseEventData()

        comscoreDestination.process(playbackStarted)

        verify { mockedStreamingAnalytics.createPlaybackSession() }
        verify { mockedStreamingConfiguration.addLabels(expected) }

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
        ).applyBaseEventData()

        comscoreDestination.process(playbackStarted)
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
        verify { mockedStreamingConfiguration.addLabels(expected) }

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
        ).applyBaseEventData()
        comscoreDestination.process(playbackPaused)

        assertNull(comscoreDestination.streamingAnalytics)
    }

    @Test
    fun `track video playback paused with options`() {
        trackVideoPlaybackStarted()
        val playbackPaused = TrackEvent(
            event = "Video Playback Paused",
            properties = buildJsonObject {
                put("assetId", 1234)
                put("adType", "mid-roll")
                put("totalLength", 100)
                put("videoPlayer", "vimeo")
                put("playbackPosition", 10)
                put("fullScreen", true)
                put("bitrate", 50)
                put("sound", 80)
                put(ComscoreOptionsPlugin.TARGET_KEY, buildJsonObject {
                    put("c3", "abc")
                })
            }
        ).applyBaseEventData()
        comscoreDestination.process(playbackPaused)

        val expected = mapOf(
            "ns_st_mp" to "vimeo",
            "ns_st_vo" to "80",
            "ns_st_ws" to "full",
            "ns_st_br" to "50000",
            "c3" to "abc",
            "c4" to "*null",
            "c6" to "*null"
        )

        verify { mockedStreamingAnalytics.notifyPause() }
        verify { mockedStreamingConfiguration.addLabels(expected) }
    }


    @Test
    fun `track video playback buffer started after playback started`() {
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
        ).applyBaseEventData()
        comscoreDestination.process(playbackBufferStarted)

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
        verify { mockedStreamingConfiguration.addLabels(expected) }
    }

    @Test
    fun `track video playback buffer completed after playback started`() {
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
        ).applyBaseEventData()
        comscoreDestination.process(playbackBufferCompleted)

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
        verify { mockedStreamingConfiguration.addLabels(expected) }
    }

    @Test
    fun `track video playback seek started after playback started`() {
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
        ).applyBaseEventData()
        comscoreDestination.process(playbackSeekStarted)

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
        verify { mockedStreamingConfiguration.addLabels(expected) }
    }

    @Test
    fun `track video playback seek completed after playback started`() {
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
        ).applyBaseEventData()
        comscoreDestination.process(playbackSeekCompleted)

        val expected = mapOf(
            "ns_st_mp" to "vimeo",
            "ns_st_vo" to "80",
            "ns_st_ws" to "full",
            "ns_st_br" to "500000",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null"
        )
        verify { mockedStreamingAnalytics.startFromPosition(50) }
        verify { mockedStreamingAnalytics.notifyPlay() }
        verify { mockedStreamingConfiguration.addLabels(expected) }
    }

    @Test
    fun `track video playback resumed after playback started`() {
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
        ).applyBaseEventData()
        comscoreDestination.process(playbackResumed)

        val expected = mapOf(
            "ns_st_mp" to "youtube",
            "ns_st_vo" to "80",
            "ns_st_ws" to "full",
            "ns_st_br" to "500000",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null"
        )
        verify { mockedStreamingAnalytics.startFromPosition(60) }
        verify { mockedStreamingAnalytics.notifyPlay() }
        verify { mockedStreamingConfiguration.addLabels(expected) }
    }

    @Test
    fun `track video content started with digitalAirdate`() {
        trackVideoPlaybackStarted()
        val videoContentStarted = TrackEvent(
            event = "Video Content Started",
            properties = buildJsonObject {
                put("assetId", 9324)
                put("title", "Meeseeks and Destroy")
                put("season", 1)
                put("episode", 5)
                put("genre", "cartoon")
                put("program", "Rick and Morty")
                put("channel", "cartoon network")
                put("publisher", "Turner Broadcasting System")
                put("fullEpisode", true)
                put("podId", "segment A")
                put("totalLength", "120")
                put("playbackPosition", 70)
                put(ComscoreOptionsPlugin.TARGET_KEY, buildJsonObject {
                    put("digitalAirdate", "2014-01-20")
                    put("contentClassificationType", "vc12")
                })
            }
        ).applyBaseEventData()
        comscoreDestination.process(videoContentStarted)

        val expected = mapOf(
            "ns_st_ci" to "9324",
            "ns_st_ep" to "Meeseeks and Destroy",
            "ns_st_sn" to "1",
            "ns_st_en" to "5",
            "ns_st_ge" to "cartoon",
            "ns_st_pr" to "Rick and Morty",
            "ns_st_st" to "cartoon network",
            "ns_st_pu" to "Turner Broadcasting System",
            "ns_st_ce" to "true",
            "ns_st_ddt" to "2014-01-20",
            "ns_st_pn" to "segment A",
            "ns_st_cl" to "120000",
            "ns_st_ct" to "vc12",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null"
        )

        verify { mockedStreamingAnalytics.startFromPosition(70) }
        verify { mockedStreamingAnalytics.notifyPlay() }

        // This is hack-y way to test the map but bcos ContentMetadata is not comparable, we have to resort to this
        val logEvent = mutableListOf<String>()
        verify { mockedAnalytics.log(capture(logEvent)) }
        val actualProps = logEvent.fetchPropertiesFromLogs("streamingAnalytics.setMetadata(")
        assertEquals(expected, actualProps)

        // ideally we would write this
        // verify { streamingAnalytics.setMetadata(getContentMetadata(expected)) }
    }

    @Test
    fun `track video content started with tvAirdate`() {
        trackVideoPlaybackStarted()
        val trackEvent =
            TrackEvent(
                event = "Video Content Started",
                properties = buildJsonObject {
                    put("title", "Meeseeks and Destroy")
                    put("season", 1)
                    put("episode", 5)
                    put("genre", "cartoon")
                    put("program", "Rick and Morty")
                    put("channel", "cartoon network")
                    put("publisher", "Turner Broadcasting System")
                    put("full_episode", true)
                    put("pod_id", "segment A")
                    put("total_length", "120")
                    put("position", 70)
                    put(ComscoreOptionsPlugin.TARGET_KEY, buildJsonObject {
                        put("tvAirdate", "2017-05-14")
                        put("contentClassificationType", "vc12")
                    })
                }
            ).applyBaseEventData()

        comscoreDestination.process(trackEvent)

        val expected = mapOf(
            "ns_st_ci" to "0",
            "ns_st_ep" to "Meeseeks and Destroy",
            "ns_st_sn" to "1",
            "ns_st_en" to "5",
            "ns_st_ge" to "cartoon",
            "ns_st_pr" to "Rick and Morty",
            "ns_st_st" to "cartoon network",
            "ns_st_pu" to "Turner Broadcasting System",
            "ns_st_ce" to "true",
            "ns_st_tdt" to "2017-05-14",
            "ns_st_pn" to "segment A",
            "ns_st_cl" to "120000",
            "ns_st_ct" to "vc12",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null"
        )
        verify { mockedStreamingAnalytics.startFromPosition(70) }
        verify { mockedStreamingAnalytics.notifyPlay() }

        // This is hack-y way to test the map but bcos ContentMetadata is not comparable, we have to resort to this
        val logEvent = mutableListOf<String>()
        verify { mockedAnalytics.log(capture(logEvent)) }
        val actualProps = logEvent.fetchPropertiesFromLogs("streamingAnalytics.setMetadata(")
        assertEquals(expected, actualProps)
    }

    @Test
    fun `track video content started without playback started`() {
        comscoreDestination.process(
            TrackEvent(
                event = "Video Content Started",
                properties = buildJsonObject { put("assetId", 5678) }
            ).applyBaseEventData())
        assertNull(comscoreDestination.streamingAnalytics)
    }

    @Test
    fun `track video content playing`() {
        val playbackStarted = TrackEvent(
            event = "Video Playback Started",
            properties = buildJsonObject {
                put("asset_id", 1234)
                put("total_length", 120)
                put("video_player", "youtube")
                put("sound", 80)
                put("bitrate", 40)
                put("full_screen", true)
            }
        ).applyBaseEventData()
        comscoreDestination.process(playbackStarted) // No ad-type with this event
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
        ).applyBaseEventData()
        comscoreDestination.process(contentPlaying)
        verify { mockedStreamingAnalytics.startFromPosition(70) }
        verify { mockedStreamingAnalytics.notifyPlay() }
        assertNull(comscoreDestination.configurationLabels["ns_st_ad"])
    }

    @Test
    fun `track video content playing with adType`() {
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
                put("podId", "segment A")
                put("playbackPosition", 70)
            }
        ).applyBaseEventData()
        comscoreDestination.process(contentPlaying)
        val expected = mapOf(
            "ns_st_ci" to "123214",
            "ns_st_ep" to "Look Who's Purging Now",
            "ns_st_sn" to "2",
            "ns_st_en" to "9",
            "ns_st_ge" to "cartoon",
            "ns_st_pr" to "Rick and Morty",
            "ns_st_st" to "cartoon network",
            "ns_st_pu" to "Turner Broadcasting System",
            "ns_st_ce" to "true",
            "ns_st_pn" to "segment A",
            "ns_st_ct" to "vc00",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null"
        )

        verify { mockedStreamingAnalytics.startFromPosition(70) }
        verify { mockedStreamingAnalytics.notifyPlay() }

        // This is hack-y way to test the map but bcos ContentMetadata is not comparable, we have to resort to this
        val logEvent = mutableListOf<String>()
        verify { mockedAnalytics.log(capture(logEvent)) }
        val actualProps = logEvent.fetchPropertiesFromLogs("streamingAnalytics.setMetadata(")
        assertEquals(expected, actualProps)
    }

    @Test
    fun `track video content completed`() {
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
        ).applyBaseEventData()
        comscoreDestination.process(contentCompleted)
        verify { mockedStreamingAnalytics.notifyEnd() }
    }

    @Test
    fun `track video ad started`() {
        val playbackStarted = TrackEvent(
            event = "Video Playback Started",
            properties = buildJsonObject {
                put("total_length", 120)
                put("video_player", "youtube")
                put("sound", 80)
                put("bitrate", 40)
                put("full_screen", true)
            }
        ).applyBaseEventData()
        comscoreDestination.process(playbackStarted) // No content_id with this event
        assertNull(comscoreDestination.configurationLabels["ns_st_ci"])
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
        ).applyBaseEventData()
        comscoreDestination.process(adStarted)
        val expected = mapOf(
            "ns_st_ami" to "4311",
            "ns_st_ad" to "pre-roll",
            "ns_st_cl" to "120000",
            "ns_st_amt" to "Helmet Ad",
            "ns_st_ct" to "va00",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null",
        )
        verify { mockedStreamingAnalytics.startFromPosition(0) }
        verify { mockedStreamingAnalytics.notifyPlay() }

        // This is hack-y way to test the map but bcos ContentMetadata is not comparable, we have to resort to this
        val logEvent = mutableListOf<String>()
        verify { mockedAnalytics.log(capture(logEvent)) }
        val actualProps = logEvent.fetchPropertiesFromLogs("streamingAnalytics.setMetadata(")
        assertEquals(expected, actualProps)
    }

    @Test
    fun `track video ad started with content id`() {
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
        ).applyBaseEventData()
        comscoreDestination.process(adStarted)

        val expected = mapOf(
            "ns_st_ci" to "1234", // from initial video playback started
            "ns_st_ami" to "4311",
            "ns_st_ad" to "pre-roll",
            "ns_st_cl" to "120000",
            "ns_st_amt" to "Helmet Ad",
            "ns_st_ct" to "va00",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null"
        )

        verify { mockedStreamingAnalytics.startFromPosition(0) }
        verify { mockedStreamingAnalytics.notifyPlay() }

        // This is hack-y way to test the map but bcos ContentMetadata is not comparable, we have to resort to this
        val logEvent = mutableListOf<String>()
        verify { mockedAnalytics.log(capture(logEvent)) }
        val actualProps = logEvent.fetchPropertiesFromLogs("streamingAnalytics.setMetadata(")
        assertEquals(expected, actualProps)
    }

    @Test
    fun `track video ad started with ad classification type`() {
        trackVideoPlaybackStarted()

        val track = TrackEvent(
            event = "Video Ad Started",
            properties = buildJsonObject {
                put("asset_id", 4311)
                put("pod_id", "adSegmentA")
                put("type", "pre-roll")
                put("total_length", 120)
                put("position", 0)
                put("title", "Helmet Ad")
                put(ComscoreOptionsPlugin.TARGET_KEY, buildJsonObject {
                    put("adClassificationType", "va14")
                })
            }
        ).applyBaseEventData()

        comscoreDestination.process(track)

        val expected = mapOf(
            "ns_st_ci" to "1234", // from initial video playback started
            "ns_st_ami" to "4311",
            "ns_st_ad" to "pre-roll",
            "ns_st_cl" to "120000",
            "ns_st_amt" to "Helmet Ad",
            "ns_st_ct" to "va14",
            "c3" to "*null",
            "c4" to "*null",
            "c6" to "*null"
        )
        verify { mockedStreamingAnalytics.startFromPosition(0) }
        verify { mockedStreamingAnalytics.notifyPlay() }

        // This is hack-y way to test the map but bcos ContentMetadata is not comparable, we have to resort to this
        val logEvent = mutableListOf<String>()
        verify { mockedAnalytics.log(capture(logEvent)) }
        val actualProps = logEvent.fetchPropertiesFromLogs("streamingAnalytics.setMetadata(")
        assertEquals(expected, actualProps)
    }

    @Test
    fun `track video ad started without playback started`() {
        comscoreDestination.process(
            TrackEvent(
                event = "Video Ad Started",
                properties = buildJsonObject { put("assetId", 4324) }
            ).applyBaseEventData()
        )
        assertNull(comscoreDestination.streamingAnalytics)
    }

    @Test
    fun `track video ad playing`() {
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
        ).applyBaseEventData()
        comscoreDestination.process(adPlaying)
        verify { mockedStreamingAnalytics.startFromPosition(20) }
        verify { mockedStreamingAnalytics.notifyPlay() }
    }

    @Test
    fun `track video ad completed`() {
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
        ).applyBaseEventData()
        comscoreDestination.process(adCompleted)
        verify { mockedStreamingAnalytics.notifyEnd() }
    }

    private fun MutableList<String>.fetchPropertiesFromLogs(targetLog: String) =
        first { it.startsWith(targetLog) }
            .let { it.substring(it.indexOf("{") + 1, it.indexOf("}")) }
            .split(",")
            .map { it.split("=") }
            .map { it.first().trim() to it.last().trim() }
            .toMap()

    private fun BaseEvent.applyBaseEventData() = apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = "2021-07-13T00:59:09"
    }
}