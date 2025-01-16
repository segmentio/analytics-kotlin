package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.utilities.putAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import sovran.kotlin.Action
import sovran.kotlin.State
import java.util.*

/**
 * Stores state related to the analytics system
 * - configuration used to initialize the client
 * - segment settings as a json map
 * - running state indicating the system has received settings
 */
data class System(
    var configuration: Configuration = Configuration(""),
    var settings: Settings?,
    var running: Boolean,
    var initializedPlugins: Set<Int>,
    var enabled: Boolean
) : State {

    companion object {
        fun defaultState(configuration: Configuration, storage: Storage): System {
            val settings = try {
                Json.decodeFromString(
                    Settings.serializer(),
                    storage.read(Storage.Constants.Settings) ?: ""
                )
            } catch (ignored: Exception) {

                // This could be an empty Settings...
                if (configuration.defaultSettings.integrations.contains("Segment.io")) {
                    configuration.defaultSettings
                } else {
                    Settings(
                        integrations = buildJsonObject {
                            put(
                                "Segment.io",
                                buildJsonObject {
                                    put(
                                        "apiKey",
                                        configuration.writeKey
                                    )
                                    put("apiHost", Constants.DEFAULT_API_HOST)
                                })
                        },
                        plan = emptyJsonObject,
                        edgeFunction = emptyJsonObject,
                        middlewareSettings = emptyJsonObject
                    )
                }


            }
            return System(
                configuration = configuration,
                settings = settings,
                running = false,
                initializedPlugins = setOf(),
                enabled = true
            )
        }
    }

    class UpdateSettingsAction(var settings: Settings) : Action<System> {
        override fun reduce(state: System): System {
            return System(
                state.configuration,
                settings,
                state.running,
                state.initializedPlugins,
                state.enabled
            )
        }
    }

    class ToggleRunningAction(var running: Boolean) : Action<System> {
        override fun reduce(state: System): System {
            return System(
                state.configuration,
                state.settings,
                running,
                state.initializedPlugins,
                state.enabled
            )
        }
    }

    class AddDestinationToSettingsAction(
        var destinationKey: String,
    ) : Action<System> {
        override fun reduce(state: System): System {
            val newIntegrations = buildJsonObject {
                state.settings?.integrations?.let { putAll(it) }
                put(destinationKey, true)
            }
            val newSettings = state.settings?.copy(integrations = newIntegrations)
            return System(
                state.configuration,
                newSettings,
                state.running,
                state.initializedPlugins,
                state.enabled
            )
        }
    }

    class AddInitializedPlugins(
        var dispatched: Set<Int>,
    ) : Action<System> {
        override fun reduce(state: System): System {
            val initializedPlugins = state.initializedPlugins + dispatched
            return System(
                state.configuration,
                state.settings,
                state.running,
                initializedPlugins,
                state.enabled
            )
        }
    }

    class ToggleEnabledAction(val enabled: Boolean): Action<System> {
        override fun reduce(state: System): System {
            return System(
                state.configuration,
                state.settings,
                state.running,
                state.initializedPlugins,
                enabled
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

    class ResetAction(var anonymousId: String = UUID.randomUUID().toString()) : Action<UserInfo> {
        override fun reduce(state: UserInfo): UserInfo {
            return UserInfo(anonymousId, null, null)
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
