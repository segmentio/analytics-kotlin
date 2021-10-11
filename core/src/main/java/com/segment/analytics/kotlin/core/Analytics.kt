package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.Timeline
import com.segment.analytics.kotlin.core.platform.plugins.ContextPlugin
import com.segment.analytics.kotlin.core.platform.plugins.SegmentDestination
import com.segment.analytics.kotlin.core.platform.plugins.StartupQueue
import com.segment.analytics.kotlin.core.platform.plugins.log
import kotlinx.coroutines.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import sovran.kotlin.Store
import sovran.kotlin.Subscriber
import java.util.concurrent.Executors
import kotlin.reflect.KClass

class Analytics internal constructor(
    val configuration: Configuration,
    val store: Store,
    val analyticsScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    val analyticsDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    val networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    val fileIODispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
) : Subscriber {

    internal val timeline: Timeline
    val storage: Storage

    init {
        require(configuration.isValid()) { "invalid configuration" }
        timeline = Timeline().also { it.analytics = this }

        storage = configuration.storageProvider.getStorage(
            analytics = this,
            writeKey = configuration.writeKey,
            ioDispatcher = fileIODispatcher,
            store = store,
            application = configuration.application!!
        )
        build()
    }

    constructor(configuration: Configuration): this(configuration, Store())

    // This function provides a default state to the store & attaches the storage and store instances
    // Initiates the initial call to settings and adds default system plugins
    internal fun build() {
        // because startup queue doesn't depend on a state, we can add it first
        add(StartupQueue())
        add(ContextPlugin())
        
        // Setup store
        analyticsScope.launch(analyticsDispatcher) {
            store.also {
                it.provide(UserInfo.defaultState(storage))
                it.provide(System.defaultState(configuration, storage))

                // subscribe to store after state is provided
                storage.subscribeToStore()
            }

            checkSettings()

            if (configuration.autoAddSegmentDestination) {
                add(
                    SegmentDestination(
                        configuration.writeKey,
                        configuration.flushAt,
                        configuration.flushInterval * 1000L,
                        configuration.apiHost
                    )
                )
            }
        }
    }

    // Analytic event specific APIs

    /**
     * The track method is how you record any actions your users perform. Each action is known by a
     * name, like 'Purchased a T-Shirt'. You can also record properties specific to those actions.
     * For example a 'Purchased a Shirt' event might have properties like revenue or size.
     *
     * @param name Name of the action
     * @param properties [Properties] to describe the action.
     * @see <a href="https://segment.com/docs/spec/track/">Track Documentation</a>
     */
    @JvmOverloads
    fun track(name: String, properties: JsonObject = emptyJsonObject) {
        val event = TrackEvent(event = name, properties = properties)
        process(event)
    }

    /**
     * The track method is how you record any actions your users perform. Each action is known by a
     * name, like 'Purchased a T-Shirt'. You can also record properties specific to those actions.
     * For example a 'Purchased a Shirt' event might have properties like revenue or size.
     *
     * @param name Name of the action
     * @param properties to describe the action. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @param serializationStrategy strategy to serialize [properties]
     * @see <a href="https://segment.com/docs/spec/track/">Track Documentation</a>
     */
    fun <T : Any> track(
        name: String,
        properties: T,
        serializationStrategy: SerializationStrategy<T>
    ) {
        track(name, Json.encodeToJsonElement(serializationStrategy, properties).jsonObject)
    }

    /**
     * The track method is how you record any actions your users perform. Each action is known by a
     * name, like 'Purchased a T-Shirt'. You can also record properties specific to those actions.
     * For example a 'Purchased a Shirt' event might have properties like revenue or size.
     *
     * @param name Name of the action
     * @param properties to describe the action. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @see <a href="https://segment.com/docs/spec/track/">Track Documentation</a>
     */
    inline fun <reified T : Any> track(
        name: String,
        properties: T
    ) {
        track(name, properties, Json.serializersModule.serializer())
    }

    /**
     * Identify lets you tie one of your users and their actions to a recognizable {@code userId}.
     * It also lets you record {@code traits} about the user, like their email, name, account type,
     * etc.
     *
     * <p>Traits and userId will be automatically cached and available on future sessions for the
     * same user. To update a trait on the server, call identify with the same user id (or null).
     * You can also use {@link #identify(Traits)} for this purpose.
     *
     * @param userId Unique identifier which you recognize a user by in your own database
     * @param traits [Traits] about the user.
     * @see <a href="https://segment.com/docs/spec/identify/">Identify Documentation</a>
     */
    @JvmOverloads
    fun identify(userId: String, traits: JsonObject = emptyJsonObject) {
        analyticsScope.launch(analyticsDispatcher) {
            store.dispatch(UserInfo.SetUserIdAndTraitsAction(userId, traits), UserInfo::class)
        }
        val event = IdentifyEvent(userId = userId, traits = traits)
        process(event)
    }

    /**
     * Identify lets you tie one of your users and their actions to a recognizable {@code userId}.
     * It also lets you record {@code traits} about the user, like their email, name, account type,
     * etc.
     *
     * <p>Traits and userId will be automatically cached and available on future sessions for the
     * same user. To update a trait on the server, call identify with the same user id (or null).
     * You can also use {@link #identify(Traits)} for this purpose.
     *
     * @param userId Unique identifier which you recognize a user by in your own database
     * @param traits [Traits] about the user. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @param serializationStrategy strategy to serialize [traits]
     * @see <a href="https://segment.com/docs/spec/identify/">Identify Documentation</a>
     */
    fun <T : Any> identify(
        userId: String,
        traits: T,
        serializationStrategy: SerializationStrategy<T>
    ) {
        identify(userId, Json.encodeToJsonElement(serializationStrategy, traits).jsonObject)
    }

    /**
     * Identify lets you tie one of your users and their actions to a recognizable {@code userId}.
     * It also lets you record {@code traits} about the user, like their email, name, account type,
     * etc.
     *
     * <p>Traits and userId will be automatically cached and available on future sessions for the
     * same user. To update a trait on the server, call identify with the same user id (or null).
     * You can also use {@link #identify(Traits)} for this purpose.
     *
     * @param userId Unique identifier which you recognize a user by in your own database
     * @param traits [Traits] about the user. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @see <a href="https://segment.com/docs/spec/identify/">Identify Documentation</a>
     */
    inline fun <reified T : Any> identify(
        userId: String,
        traits: T,
    ) {
        identify(userId, traits, Json.serializersModule.serializer())
    }

    /**
     * The screen methods let your record whenever a user sees a screen of your mobile app, and
     * attach a name, category or properties to the screen. Either category or name must be
     * provided.
     *
     * @param title A name for the screen.
     * @param category A category to describe the screen.
     * @param properties [Properties] to add extra information to this call.
     * @see <a href="https://segment.com/docs/spec/screen/">Screen Documentation</a>
     */
    @JvmOverloads
    fun screen(
        title: String,
        properties: JsonObject = emptyJsonObject,
        category: String = ""
    ) {
        val event = ScreenEvent(name = title, category = category, properties = properties)
        process(event)
    }

    /**
     * The screen methods let your record whenever a user sees a screen of your mobile app, and
     * attach a name, category or properties to the screen. Either category or name must be
     * provided.
     *
     * @param title A name for the screen.
     * @param category A category to describe the screen.
     * @param properties [Properties] to add extra information to this call. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @param serializationStrategy strategy to serialize [properties]
     * @see <a href="https://segment.com/docs/spec/screen/">Screen Documentation</a>
     */
    fun <T : Any> screen(
        title: String,
        properties: T,
        serializationStrategy: SerializationStrategy<T>,
        category: String = ""
    ) {
        screen(
            title,
            Json.encodeToJsonElement(serializationStrategy, properties).jsonObject,
            category
        )
    }

    /**
     * The screen methods let your record whenever a user sees a screen of your mobile app, and
     * attach a name, category or properties to the screen. Either category or name must be
     * provided.
     *
     * @param title A name for the screen.
     * @param category A category to describe the screen.
     * @param properties [Properties] to add extra information to this call. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @see <a href="https://segment.com/docs/spec/screen/">Screen Documentation</a>
     */
    inline fun <reified T : Any> screen(
        title: String,
        properties: T,
        category: String = "",
    ) {
        screen(title, properties, Json.serializersModule.serializer(), category)
    }

    /**
     * The group method lets you associate a user with a group. It also lets you record custom
     * traits about the group, like industry or number of employees.
     *
     * <p>If you've called {@link #identify(String, Traits, Options)} before, this will
     * automatically remember the userId. If not, it will fall back to use the anonymousId instead.
     *
     * @param groupId Unique identifier which you recognize a group by in your own database
     * @param traits [Traits] about the group
     * @see <a href="https://segment.com/docs/spec/group/">Group Documentation</a>
     */
    @JvmOverloads
    fun group(groupId: String, traits: JsonObject = emptyJsonObject) {
        val event = GroupEvent(groupId = groupId, traits = traits)
        process(event)
    }

    /**
     * The group method lets you associate a user with a group. It also lets you record custom
     * traits about the group, like industry or number of employees.
     *
     * <p>If you've called {@link #identify(String, Traits, Options)} before, this will
     * automatically remember the userId. If not, it will fall back to use the anonymousId instead.
     *
     * @param groupId Unique identifier which you recognize a group by in your own database
     * @param traits [Traits] about the group. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @param serializationStrategy strategy to serialize [traits]
     * @see <a href="https://segment.com/docs/spec/group/">Group Documentation</a>
     */
    fun <T : Any> group(
        groupId: String,
        traits: T,
        serializationStrategy: SerializationStrategy<T>
    ) {
        group(groupId, Json.encodeToJsonElement(serializationStrategy, traits).jsonObject)
    }

    /**
     * The group method lets you associate a user with a group. It also lets you record custom
     * traits about the group, like industry or number of employees.
     *
     * <p>If you've called {@link #identify(String, Traits, Options)} before, this will
     * automatically remember the userId. If not, it will fall back to use the anonymousId instead.
     *
     * @param groupId Unique identifier which you recognize a group by in your own database
     * @param traits [Traits] about the group. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @see <a href="https://segment.com/docs/spec/group/">Group Documentation</a>
     */
    inline fun <reified T : Any> group(
        groupId: String,
        traits: T,
    ) {
        group(groupId, traits, Json.serializersModule.serializer())
    }

    /**
     * The alias method is used to merge two user identities, effectively connecting two sets of
     * user data as one. This is an advanced method, but it is required to manage user identities
     * successfully in some of our integrations.
     *
     * @param newId The new ID you want to alias the existing ID to. The existing ID will be either
     *     the previousId if you have called identify, or the anonymous ID.
     * @see <a href="https://segment.com/docs/tracking-api/alias/">Alias Documentation</a>
     */
    fun alias(newId: String) {
        analyticsScope.launch(analyticsDispatcher) {
            val curUserInfo = store.currentState(UserInfo::class)
            if (curUserInfo != null) {
                val event = AliasEvent(
                    userId = newId,
                    previousId = curUserInfo.userId ?: curUserInfo.anonymousId
                )
                launch {
                    store.dispatch(UserInfo.SetUserIdAction(newId), UserInfo::class)
                }
                process(event)
            } else {
                log("failed to fetch current UserInfo state")
            }
        }
    }

    fun process(event: BaseEvent) {
        log("applying base attributes on ${Thread.currentThread().name}")
        analyticsScope.launch(analyticsDispatcher) {
            event.applyBaseEventData(store)
            log("processing event on ${Thread.currentThread().name}")
            timeline.process(event)
        }
    }

    // Platform specific APIs

    /**
     * Register a plugin to the analytics timeline
     * @param plugin [Plugin] to be added
     */
    fun add(plugin: Plugin): Analytics {
        this.timeline.add(plugin)
        if (plugin is DestinationPlugin && plugin !is SegmentDestination) {
            analyticsScope.launch(analyticsDispatcher) {
                store.dispatch(System.AddIntegrationAction(plugin.key), System::class)
            }
        }
        return this
    }

    /**
     * Retrieve a registered plugin by reference
     * @param plugin [Plugin]
     */
    fun <T: Plugin> find(plugin: KClass<T>): T? = this.timeline.find(plugin)

    /**
     * Remove a plugin from the analytics timeline using its name
     * @param plugin [Plugin] to be remove
     */
    fun remove(plugin: Plugin): Analytics {
        this.timeline.remove(plugin)
        if (plugin is DestinationPlugin && plugin !is SegmentDestination) {
            analyticsScope.launch(analyticsDispatcher) {
                store.dispatch(System.RemoveIntegrationAction(plugin.key), System::class)
            }
        }
        return this
    }

    /**
     * Apply a closure to all plugins registered to the analytics client. Ideal for invoking
     * functions for Utility plugins
     */
    fun applyClosureToPlugins(closure: (Plugin) -> Unit) {
        timeline.applyClosure(closure)
    }

    fun flush() {
        this.timeline.applyClosure {
            (it as? DestinationPlugin)?.flush()
        }
    }

    /**
     * Retrieve the userId registered by a previous `identify` call in a blocking way.
     * Note: this method invokes `runBlocking` internal, it's not recommended to be used
     * in coroutines.
     */
    fun userId(): String? = runBlocking {
        userIdAsync()
    }

    /**
     * Retrieve the userId registered by a previous `identify` call
     */
    suspend fun userIdAsync(): String? {
        val userInfo = store.currentState(UserInfo::class)
        return userInfo?.userId
    }

    /**
     * Retrieve the traits registered by a previous `identify` call
     */
    fun traits(): JsonObject? = runBlocking {
        traitsAsync()
    }

    /**
     * Retrieve the traits registered by a previous `identify` call in a blocking way.
     * Note: this method invokes `runBlocking` internal, it's not recommended to be used
     * in coroutines.
     */
    suspend fun traitsAsync(): JsonObject? {
        val userInfo = store.currentState(UserInfo::class)
        return userInfo?.traits
    }

    /**
     * Retrieve the traits registered by a previous `identify` call in a blocking way.
     * Note: this method invokes `runBlocking` internal, it's not recommended to be used
     * in coroutines.
     */
    inline fun <reified T : Any> traits(deserializationStrategy: DeserializationStrategy<T> = Json.serializersModule.serializer()): T? {
        return traits()?.let {
            decodeFromJsonElement(deserializationStrategy, it)
        }
    }

    /**
     * Retrieve the traits registered by a previous `identify` call in a blocking way.
     * Note: this method invokes `runBlocking` internal, it's not recommended to be used
     * in coroutines.
     */
    suspend inline fun <reified T : Any> traitsAsync(deserializationStrategy: DeserializationStrategy<T> = Json.serializersModule.serializer()): T? {
        return traitsAsync()?.let {
            decodeFromJsonElement(deserializationStrategy, it)
        }
    }
}

// constructor function to build analytics in dsl format with config options
// Usage: Analytics("123") {
//            this.application = "n/a"
//            this.analyticsScope = MainScope()
//            this.collectDeviceId = false
//            this.flushAt = 10
//        }
fun Analytics(writeKey: String, configs: Configuration.() -> Unit): Analytics {
    val config = Configuration(writeKey)
    configs.invoke(config)
    return Analytics(config)
}