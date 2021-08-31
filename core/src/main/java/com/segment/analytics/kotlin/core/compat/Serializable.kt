package com.segment.analytics.kotlin.core.compat

import kotlinx.serialization.json.JsonObject

interface Serializable {
    fun serialize() : JsonObject
}