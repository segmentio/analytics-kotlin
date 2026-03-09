package com.segment.analytics.kotlin.core.retry

import com.segment.analytics.kotlin.core.Storage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Extension functions for persisting and loading RetryState from Storage.
 *
 * Uses kotlinx.serialization to serialize RetryState to/from JSON.
 * Provides defensive error handling - returns default state on any parse errors.
 */

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Save RetryState to storage.
 *
 * Serializes the state to JSON and writes it to Storage.Constants.RetryState key.
 * Returns true if successful, false on any error.
 */
suspend fun Storage.saveRetryState(state: RetryState): Boolean {
    return try {
        val serialized = json.encodeToString(state)
        write(Storage.Constants.RetryState, serialized)
        true
    } catch (e: Exception) {
        // Log or handle serialization errors
        false
    }
}

/**
 * Load RetryState from storage.
 *
 * Reads from Storage.Constants.RetryState key and deserializes JSON.
 * Returns default RetryState() on any error (missing data, corrupt JSON, etc.).
 * Never throws exceptions.
 */
fun Storage.loadRetryState(): RetryState {
    return try {
        val serialized = read(Storage.Constants.RetryState)
        if (serialized.isNullOrEmpty()) {
            RetryState()
        } else {
            json.decodeFromString<RetryState>(serialized)
        }
    } catch (e: SerializationException) {
        // Corrupt data → return defaults
        RetryState()
    } catch (e: Exception) {
        // Any other error → return defaults
        RetryState()
    }
}

/**
 * Clear RetryState from storage.
 *
 * Removes the persisted retry state.
 * Returns true if successful, false otherwise.
 */
fun Storage.clearRetryState(): Boolean {
    return try {
        remove(Storage.Constants.RetryState)
    } catch (e: Exception) {
        false
    }
}
