package com.segment.analytics.kotlin.core.utilities

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

object AnySerializer: KSerializer<Any> {
    override fun deserialize(decoder: Decoder): Any {
        // Stub function; should not be called.
        return "not-implemented";
    }

    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("My Any Serializer", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: Any) {
        val toJsonElement = value.toJsonElement()
        encoder.encodeSerializableValue(Json.serializersModule.serializer(), toJsonElement)
    }
}