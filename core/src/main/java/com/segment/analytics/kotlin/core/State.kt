package com.segment.analytics.kotlin.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sovran.kotlin.Action
import sovran.kotlin.State
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

/**
 * Stores state related to the analytics system
 * - configuration used to initialize the client
 * - list of destinations that are enabled / disabled
 * - segment settings as a json map
 * - running state indicating the system has received settings
 */
data class System(
    var configuration: Configuration = Configuration(""),
    var integrations: Integrations?,
    var settings: Settings?,
    var running: Boolean
) : State {

    companion object {
        fun defaultState(configuration: Configuration, storage: Storage): System {
            val settings = try {
                Json.decodeFromString(
                    Settings.serializer(),
                    storage.read(Storage.Constants.Settings) ?: ""
                )
            } catch (ex: Exception) {
                configuration.defaultSettings
            }
            return System(
                configuration = configuration,
                integrations = emptyJsonObject,
                settings = settings,
                running = false
            )
        }
    }

    class UpdateSettingsAction(var settings: Settings) : Action<System> {
        override fun reduce(state: System): System {
            return System(
                state.configuration,
                state.integrations,
                settings,
                state.running
            )
        }
    }

    class AddIntegrationAction(var pluginName: String) : Action<System> {
        override fun reduce(state: System): System {
            // we need to set any destination plugins to false in the
            // integrations payload. this prevents them from being sent
            // by segment.com once an event reaches Segment.
            state.integrations?.let {
                val newIntegrations =
                    it.filter { (k, _) -> (k != pluginName) }.toMap(LinkedHashMap())
                newIntegrations[pluginName] = JsonPrimitive(false)
                return System(
                    state.configuration,
                    JsonObject(newIntegrations),
                    state.settings,
                    state.running
                )
            }
            return state
        }
    }

    class RemoveIntegrationAction(var pluginName: String) : Action<System> {
        override fun reduce(state: System): System {
            state.integrations?.let {
                val newIntegrations =
                    it.filter { (k, _) -> (k != pluginName) }.toMap(LinkedHashMap())
                return System(
                    state.configuration,
                    JsonObject(newIntegrations),
                    state.settings,
                    state.running
                )
            }
            return state
        }
    }

    class ToggleRunningAction(var running: Boolean) : Action<System> {
        override fun reduce(state: System): System {
            return System(
                state.configuration,
                state.integrations,
                state.settings,
                running
            )
        }
    }
}

/**
 * Stores state related to the user
 * - anonymousId (String)
 * - userId (String)
 * - traits (Map)
 */
data class UserInfo(
    var anonymousId: String,
    var userId: String?,
    var traits: JsonObject?
) : State {

    companion object {
        fun defaultState(storage: Storage): UserInfo {
            val userId: String? = storage.read(Storage.Constants.UserId)
            val traits: JsonObject? =
                Json.decodeFromString(
                    storage.read(Storage.Constants.Traits) ?: "{}"
                )
            val anonymousId: String =
                storage.read(Storage.Constants.AnonymousId) ?: UUID.randomUUID().toString()

            return UserInfo(anonymousId, userId, traits)
        }
    }

    class ResetAction : Action<UserInfo> {
        override fun reduce(state: UserInfo): UserInfo {
            return UserInfo(UUID.randomUUID().toString(), null, null)
        }
    }

    class SetUserIdAction(var userId: String) : Action<UserInfo> {
        override fun reduce(state: UserInfo): UserInfo {
            return UserInfo(state.anonymousId, userId, state.traits)
        }
    }

    class SetAnonymousIdAction(var anonymousId: String) : Action<UserInfo> {
        override fun reduce(state: UserInfo): UserInfo {
            return UserInfo(anonymousId, state.userId, state.traits)
        }
    }

    class SetTraitsAction(var traits: JsonObject) : Action<UserInfo> {
        override fun reduce(state: UserInfo): UserInfo {
            return UserInfo(state.anonymousId, state.userId, traits)
        }
    }

    class SetUserIdAndTraitsAction(var userId: String, var traits: JsonObject) : Action<UserInfo> {
        override fun reduce(state: UserInfo): UserInfo {
            return UserInfo(state.anonymousId, userId, traits)
        }
    }
}
