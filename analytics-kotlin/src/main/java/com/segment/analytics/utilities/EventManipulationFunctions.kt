package com.segment.analytics.utilities

import com.segment.analytics.BaseEvent
import com.segment.analytics.emptyJsonObject
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

// insert a non-null key-value pair into the integrations object
fun <T : Any> BaseEvent.putIntegrations(
    key: String,
    value: T,
    serializationStrategy: SerializationStrategy<T>
): BaseEvent {
    integrations = buildJsonObject {
        putAll(integrations)
        put(key, Json.encodeToJsonElement(serializationStrategy, value))
    }
    return this
}

inline fun <reified T : Any> BaseEvent.putIntegrations(key: String, value: T): BaseEvent {
    return putIntegrations(key, value, Json.serializersModule.serializer())
}

// insert a non-null key-value pair into the context object
fun <T : Any> BaseEvent.putInContext(
    key: String,
    value: T,
    serializationStrategy: SerializationStrategy<T>
): BaseEvent {
    context = buildJsonObject {
        putAll(context)
        put(key, Json.encodeToJsonElement(serializationStrategy, value))
    }
    return this
}

inline fun <reified T : Any> BaseEvent.putInContext(key: String, value: T): BaseEvent {
    return putInContext(key, value, Json.serializersModule.serializer())
}

// insert a non-null key-value pair inside an underlying parentKey inside the context object
fun <T : Any> BaseEvent.putInContextUnderKey(
    parentKey: String,
    key: String,
    value: T,
    serializationStrategy: SerializationStrategy<T>
): BaseEvent {
    val parent = context[parentKey]?.jsonObject ?: emptyJsonObject
    context = buildJsonObject {
        putAll(context)
        val newParent = buildJsonObject {
            putAll(parent)
            put(key, Json.encodeToJsonElement(serializationStrategy, value))
        }
        put(parentKey, newParent)
    }
    return this
}

inline fun <reified T : Any> BaseEvent.putInContextUnderKey(parentKey: String,key: String, value: T): BaseEvent {
    return putInContextUnderKey(parentKey, key, value, Json.serializersModule.serializer())
}

fun BaseEvent.removeFromContext(key: String): BaseEvent {
    context = JsonObject(context.filterNot { (k, _) -> k == key })
    return this
}