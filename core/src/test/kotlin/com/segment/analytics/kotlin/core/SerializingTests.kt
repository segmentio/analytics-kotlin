package com.segment.analytics.kotlin.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.lang.Exception

class SerializingTests {
    val analytics = Analytics(Configuration(writeKey = "123", application = "Test"))

    @Serializable
    data class Foo(val name: String)

    data class Bar(val count: Int)

    @Serializable(with = FooBarSerializer::class)
    data class FooBar(val switch: Boolean)

    object FooBarSerializer : KSerializer<FooBar> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("fooBar") {
            element<Boolean>("switch")
        }

        override fun serialize(encoder: Encoder, value: FooBar) =
            encoder.encodeStructure(descriptor) {
                encodeBooleanElement(descriptor, 0, value.switch)
            }

        override fun deserialize(decoder: Decoder): FooBar =
            decoder.decodeStructure(descriptor) {
                var switch = false
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> switch = decodeBooleanElement(descriptor, 0)
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
                FooBar(switch)
            }
    }

    @Test
    fun `test whether api compiles and doesnt throw any runtime exceptions`() {
        analytics.track("foo", buildJsonObject {
            put("name", "123")
        })

        analytics.track("foo", Foo("123"))
        val barStrat = object : KSerializer<Bar> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("bar") {
                element<Int>("count")
            }

            override fun serialize(encoder: Encoder, value: Bar) =
                encoder.encodeStructure(descriptor) {
                    encodeIntElement(descriptor, 0, value.count)
                }

            override fun deserialize(decoder: Decoder): Bar =
                decoder.decodeStructure(descriptor) {
                    var count = -1
                    while (true) {
                        when (val index = decodeElementIndex(descriptor)) {
                            0 -> count = decodeIntElement(descriptor, 0)
                            CompositeDecoder.DECODE_DONE -> break
                            else -> error("Unexpected index: $index")
                        }
                    }
                    Bar(count)
                }

        }
        analytics.track("bar", Bar(123), barStrat)

        analytics.track("foobar", FooBar(false))
    }


    @Test
    fun `test whether api fails`() {
        try {
            analytics.track("bar", Bar(123))
            Assertions.fail()
        } catch (ex: SerializationException) {

        } catch (ex: Exception) {
            Assertions.fail()
        }
    }

}