package com.segment.analytics.kotlin.core.utilities

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

object AnySerializer: KSerializer<Any> {
    override fun deserialize(decoder: Decoder): Any {
        // Stub function; should not be called.
        return "not-implemented";
    }

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = ContextualSerializer(Any::class, null, emptyArray()).descriptor


    override fun serialize(encoder: Encoder, value: Any) {
        val toJsonElement = value.toJsonElement()
        encoder.encodeSerializableValue(Json.serializersModule.serializer(), toJsonElement)
    }
}

/**
 * A pre-configured Json Implementation with an Any type serializer.
 */
val JsonSerializer = Json { serializersModule = SerializersModule {
    contextual(Any::class) { AnySerializer }
} }
