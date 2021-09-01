@file:JvmName("EventTransformer")

package com.segment.analytics.kotlin.core.utilities

import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer

// Mark integration as enabled, for this event
fun BaseEvent.enableIntegration(integrationName: String): BaseEvent {
    return putIntegrations(integrationName, true)
}

// Mark integration as disabled, for this event
fun BaseEvent.disableIntegration(integrationName: String): BaseEvent {
    return putIntegrations(integrationName, false)
}

fun BaseEvent.putIntegrations(key: String, jsonElement: JsonElement): BaseEvent {
    integrations = buildJsonObject {
        putAll(integrations)
        put(key, jsonElement)
    }
    return this
}

// insert a non-null key-value pair into the integrations object
fun <T : Any> BaseEvent.putIntegrations(
    key: String,
    value: T,
    serializationStrategy: SerializationStrategy<T>
) = putIntegrations(key, Json.encodeToJsonElement(serializationStrategy, value))

inline fun <reified T : Any> BaseEvent.putIntegrations(key: String, value: T): BaseEvent {
    return putIntegrations(key, value, Json.serializersModule.serializer())
}

fun BaseEvent.putInContext(key: String, jsonElement: JsonElement): BaseEvent {
    context = buildJsonObject {
        putAll(context)
        put(key, jsonElement)
    }
    return this
}

// insert a non-null key-value pair into the context object
fun <T : Any> BaseEvent.putInContext(
    key: String,
    value: T,
    serializationStrategy: SerializationStrategy<T>
) = putInContext(key, Json.encodeToJsonElement(serializationStrategy, value))

inline fun <reified T : Any> BaseEvent.putInContext(key: String, value: T): BaseEvent {
    return putInContext(key, value, Json.serializersModule.serializer())
}

fun BaseEvent.putInContextUnderKey(
    parentKey: String,
    key: String,
    jsonElement: JsonElement
): BaseEvent {
    val parent = context[parentKey]?.jsonObject ?: emptyJsonObject
    context = buildJsonObject {
        putAll(context)
        val newParent = buildJsonObject {
            putAll(parent)
            put(key, jsonElement)
        }
        put(parentKey, newParent)
    }
    return this
}

// insert a non-null key-value pair inside an underlying parentKey inside the context object
fun <T : Any> BaseEvent.putInContextUnderKey(
    parentKey: String,
    key: String,
    value: T,
    serializationStrategy: SerializationStrategy<T>
) = putInContextUnderKey(parentKey, key, Json.encodeToJsonElement(serializationStrategy, value))

inline fun <reified T : Any> BaseEvent.putInContextUnderKey(
    parentKey: String,
    key: String,
    value: T
): BaseEvent {
    return putInContextUnderKey(parentKey, key, value, Json.serializersModule.serializer())
}

fun BaseEvent.removeFromContext(key: String): BaseEvent {
    context = JsonObject(context.filterNot { (k, _) -> k == key })
    return this
}