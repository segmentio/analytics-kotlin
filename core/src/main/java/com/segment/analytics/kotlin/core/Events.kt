package com.segment.analytics.kotlin.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import sovran.kotlin.Store
import java.time.Instant
import java.util.UUID

typealias AnalyticsContext = JsonObject
typealias Integrations = JsonObject
typealias Properties = JsonObject
typealias Traits = JsonObject

val emptyJsonObject = JsonObject(emptyMap())

class DateSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}

@Serializable
enum class EventType {
    @SerialName("track")
    Track,

    @SerialName("screen")
    Screen,

    @SerialName("alias")
    Alias,

    @SerialName("identify")
    Identify,

    @SerialName("group")
    Group
}

/**
 * Principal type class for any event type and will be one of
 * @see TrackEvent
 * @see IdentifyEvent
 * @see ScreenEvent
 * @see GroupEvent
 * @see AliasEvent
 */
sealed class BaseEvent {
    // The type of event
    abstract var type: EventType

    // the anonymousId tied to the event
    abstract var anonymousId: String

    // the UUID for the event
    abstract var messageId: String

    // timestamp of when the event was generated
    abstract var timestamp: String

    // a JsonObject describing additional values related to the event
    abstract var context: AnalyticsContext

    // a JsonObject describing the integrations that the event will interact with
    abstract var integrations: Integrations

    // the userId tied to the event
    abstract var userId: String

    internal suspend fun applyBaseEventData(store: Store) {
        this.timestamp = Instant.now().toString()
        val system = store.currentState(System::class) ?: return
        val userInfo = store.currentState(UserInfo::class) ?: return

        this.anonymousId = userInfo.anonymousId
        this.messageId = UUID.randomUUID().toString()
        this.context = emptyJsonObject
        this.integrations = system.integrations ?: emptyJsonObject

        if (this.userId.isBlank()) {
            // attach system userId if present
            this.userId = userInfo.userId ?: ""
        }
    }
}

@Serializable
@SerialName("track")
data class TrackEvent(
    var properties: Properties,
    var event: String
) : BaseEvent() {
    override var type: EventType = EventType.Track
    override lateinit var messageId: String
    override lateinit var anonymousId: String
    override lateinit var integrations: Integrations
    override lateinit var context: AnalyticsContext
    override var userId: String = ""

    override lateinit var timestamp: String
}

@Serializable
@SerialName("identify")
data class IdentifyEvent(
    override var userId: String,
    var traits: Traits
) : BaseEvent() {
    override var type: EventType = EventType.Identify
    override lateinit var messageId: String
    override lateinit var anonymousId: String
    override lateinit var integrations: Integrations
    override lateinit var context: AnalyticsContext

    override lateinit var timestamp: String
}

@Serializable
@SerialName("group")
data class GroupEvent(
    var groupId: String,
    var traits: Traits
) : BaseEvent() {
    override var type: EventType = EventType.Group
    override lateinit var messageId: String
    override lateinit var anonymousId: String
    override lateinit var integrations: Integrations
    override lateinit var context: AnalyticsContext
    override var userId: String = ""

    override lateinit var timestamp: String
}

@Serializable
@SerialName("alias")
data class AliasEvent(
    override var userId: String,
    var previousId: String
) : BaseEvent() {
    override var type: EventType = EventType.Alias
    override lateinit var messageId: String
    override lateinit var anonymousId: String
    override lateinit var integrations: Integrations
    override lateinit var context: AnalyticsContext

    override lateinit var timestamp: String
}

@Serializable
@SerialName("screen")
data class ScreenEvent(
    var name: String,
    var category: String,
    var properties: Properties
) : BaseEvent() {
    override var type: EventType = EventType.Screen
    override lateinit var messageId: String
    override lateinit var anonymousId: String
    override lateinit var context: AnalyticsContext
    override lateinit var integrations: Integrations
    override var userId: String = ""

    override lateinit var timestamp: String
}
