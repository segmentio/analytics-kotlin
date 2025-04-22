package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.utilities.SegmentInstant
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import sovran.kotlin.Store
import java.util.*

typealias AnalyticsContext = JsonObject
typealias Integrations = JsonObject
typealias Properties = JsonObject
typealias Traits = JsonObject
typealias EnrichmentClosure = (event: BaseEvent?) -> BaseEvent?

val emptyJsonObject = JsonObject(emptyMap())
val emptyJsonArray = JsonArray(emptyList())

@Serializable
data class DestinationMetadata(
    var bundled: List<String>? = emptyList(),
    var unbundled: List<String>? = emptyList(),
    var bundledIds: List<String>? = emptyList(),
)

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

@Serializable(with = BaseEventSerializer::class)
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

    abstract var _metadata: DestinationMetadata

    var enrichment: EnrichmentClosure? = null

    companion object {
        internal const val ALL_INTEGRATIONS_KEY = "All"
    }

    internal fun applyBaseData(enrichment: EnrichmentClosure?) {
        this.enrichment = enrichment
        this.timestamp = SegmentInstant.now()
        this.context = emptyJsonObject
        this.messageId = UUID.randomUUID().toString()
    }

    internal suspend fun applyBaseEventData(store: Store) {
        val userInfo = store.currentState(UserInfo::class) ?: return

        this.anonymousId = userInfo.anonymousId
        this.integrations = emptyJsonObject

        if (this.userId.isBlank()) {
            // attach system userId if present
            this.userId = userInfo.userId ?: ""
        }
    }

    // Create a shallow copy of this event payload
    fun <T : BaseEvent> copy(): T {
        val original = this
        val copy = when (this) {
            is AliasEvent -> AliasEvent(userId = this.userId, previousId = this.previousId)
            is GroupEvent -> GroupEvent(groupId = this.groupId, traits = this.traits)
            is IdentifyEvent -> IdentifyEvent(userId = this.userId, traits = this.traits)
            is ScreenEvent -> ScreenEvent(
                name = this.name,
                category = this.category,
                properties = this.properties
            )
            is TrackEvent -> TrackEvent(event = this.event, properties = this.properties)
        }.apply {
//            type = original.type
            anonymousId = original.anonymousId
            messageId = original.messageId
            timestamp = original.timestamp
            context = original.context
            integrations = original.integrations
            userId = original.userId
            _metadata = original._metadata
            enrichment = original.enrichment
        }
        @Suppress("UNCHECKED_CAST")
        return copy as T // This is ok because resultant type will be same as input type
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaseEvent

        if (type != other.type) return false
        if (anonymousId != other.anonymousId) return false
        if (messageId != other.messageId) return false
        if (timestamp != other.timestamp) return false
        if (context != other.context) return false
        if (integrations != other.integrations) return false
        if (userId != other.userId) return false
        if (_metadata != other._metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + anonymousId.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + context.hashCode()
        result = 31 * result + integrations.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + _metadata.hashCode()
        return result
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
    override var _metadata: DestinationMetadata = DestinationMetadata()

    override lateinit var timestamp: String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as TrackEvent

        if (properties != other.properties) return false
        if (event != other.event) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + event.hashCode()
        return result
    }

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
    override var _metadata: DestinationMetadata = DestinationMetadata()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as IdentifyEvent

        if (traits != other.traits) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + traits.hashCode()
        return result
    }
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
    override var _metadata: DestinationMetadata = DestinationMetadata()

    override lateinit var timestamp: String
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as GroupEvent

        if (groupId != other.groupId) return false
        if (traits != other.traits) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + traits.hashCode()
        return result
    }
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
    override var _metadata: DestinationMetadata = DestinationMetadata()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as AliasEvent

        if (previousId != other.previousId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + previousId.hashCode()
        return result
    }
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
    override var _metadata: DestinationMetadata = DestinationMetadata()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ScreenEvent

        if (name != other.name) return false
        if (category != other.category) return false
        if (properties != other.properties) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + properties.hashCode()
        return result
    }
}

object BaseEventSerializer : JsonContentPolymorphicSerializer<BaseEvent>(BaseEvent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out BaseEvent> {
        return when (element.jsonObject["type"]?.jsonPrimitive?.content) {
            "track" -> TrackEvent.serializer()
            "screen" -> ScreenEvent.serializer()
            "alias" -> AliasEvent.serializer()
            "identify" -> IdentifyEvent.serializer()
            "group" -> GroupEvent.serializer()
            else -> throw Exception("Unknown Event: key 'type' not found or does not matches any event type")
        }
    }
}
