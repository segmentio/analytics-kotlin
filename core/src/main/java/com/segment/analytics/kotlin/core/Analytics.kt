package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.Timeline
import com.segment.analytics.kotlin.core.platform.plugins.ContextPlugin
import com.segment.analytics.kotlin.core.platform.plugins.SegmentDestination
import com.segment.analytics.kotlin.core.platform.plugins.StartupQueue
import com.segment.analytics.kotlin.core.platform.plugins.UserInfoPlugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
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
import java.util.*
import java.util.concurrent.Executors
import kotlin.reflect.KClass

/**
 * Internal constructor of Analytics. Used for internal unit tests injection
 * @property configuration configuration that analytics can use
 * @property store sovran state management instance
 * @property analyticsScope coroutine scope where analytics tasks run in
 * @property analyticsDispatcher coroutine dispatcher that runs the analytics tasks
 * @property networkIODispatcher coroutine dispatcher that runs the network tasks
 * @property fileIODispatcher coroutine dispatcher that runs the file related tasks
 */
open class Analytics protected constructor(
    val configuration: Configuration,
    coroutineConfig: CoroutineConfiguration,
) : Subscriber, CoroutineConfiguration by coroutineConfig {

    // use lazy to avoid the instance being leak before fully initialized
    internal val timeline: Timeline by lazy {
        Timeline().also { it.analytics = this }
    }

    // use lazy to avoid the instance being leak before fully initialized
    val storage: Storage by lazy {
        configuration.storageProvider.getStorage(
            analytics = this,
            writeKey = configuration.writeKey,
            ioDispatcher = fileIODispatcher,
            store = store,
            application = configuration.application!!
        )
    }

    internal var userInfo: UserInfo = UserInfo.defaultState(storage)

    companion object {
        var debugLogsEnabled: Boolean = false

        // A Static Log Target that can be used from anywhere without an analytics reference.
        internal var logger: Logger = ConsoleLogger()

        fun setLogger(logger: Logger) {
            Analytics.logger = logger
        }

        /**
         * Retrieve the version of this library in use.
         * - Returns: A string representing the version in "BREAKING.FEATURE.FIX" format.
         */
        fun version(): String = Constants.LIBRARY_VERSION
    }

    init {
        require(configuration.isValid()) { "invalid configuration" }
        build()
    }

    /**
     * Public constructor of Analytics.
     * @property configuration configuration that analytics can use
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    constructor(configuration: Configuration) : this(configuration,
        object : CoroutineConfiguration {
            override val store = Store()
            val exceptionHandler = CoroutineExceptionHandler { _, t ->
                Analytics.segmentLog(
                    "Caught Exception in Analytics Scope: ${t}"
                )
            }
            override val analyticsScope = CoroutineScope(SupervisorJob() + exceptionHandler)
            override val analyticsDispatcher : CloseableCoroutineDispatcher =
                Executors.newCachedThreadPool().asCoroutineDispatcher()
            override val networkIODispatcher : CloseableCoroutineDispatcher =
                Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            override val fileIODispatcher : CloseableCoroutineDispatcher =
                Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        })

    // This function provides a default state to the store & attaches the storage and store instances
    // Initiates the initial call to settings and adds default system plugins
    internal fun build() {
        try {
            // because startup queue doesn't depend on a state, we can add it first
            add(StartupQueue())
            add(ContextPlugin())
            add(UserInfoPlugin())

            // Setup store
            analyticsScope.launch(analyticsDispatcher) {
                store.also {
                    // load memory with initial value
                    it.provide(userInfo)
                    it.provide(System.defaultState(configuration, storage))

                    // subscribe to store after state is provided
                    storage.subscribeToStore()
                }

                if (configuration.autoAddSegmentDestination) {
                    add(SegmentDestination())
                }

                checkSettings()
            }
        } catch (t: Throwable) {
            Analytics.segmentLog("Caught Exception in build(): $t")
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
        serializationStrategy: SerializationStrategy<T>,
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
        properties: T,
    ) {
        track(name, properties, Json.serializersModule.serializer())
    }

    /**
     * Identify lets you tie one of your users and their actions to a recognizable {@code userId}.
     * It also lets you record {@code traits} about the user, like their email, name, account type,
     * etc.
     *
     * <p>Traits and userId will be automatically cached and available on future sessions for the
     * same user. To update a trait on the server, call identify with the same user id.
     * You can also use {@link #identify(Traits)} for this purpose.
     *
     * In the case when user logs out, make sure to call {@link #reset()} to clear user's identity
     * info.
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
     * same user. To update a trait on the server, call identify with the same user id.
     * You can also use {@link #identify(Traits)} for this purpose.
     *
     * In the case when user logs out, make sure to call {@link #reset()} to clear user's identity
     * info.
     *
     * @param userId Unique identifier which you recognize a user by in your own database
     * @param traits [Traits] about the user. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @param serializationStrategy strategy to serialize [traits]
     * @see <a href="https://segment.com/docs/spec/identify/">Identify Documentation</a>
     */
    fun <T : Any> identify(
        userId: String,
        traits: T,
        serializationStrategy: SerializationStrategy<T>,
    ) {
        identify(userId, Json.encodeToJsonElement(serializationStrategy, traits).jsonObject)
    }

    /**
     * Identify lets you tie one of your users and their actions to a recognizable {@code userId}.
     * It also lets you record {@code traits} about the user, like their email, name, account type,
     * etc.
     *
     * <p>Traits and userId will be automatically cached and available on future sessions for the
     * same user. To update a trait on the server, call identify with the same user id.
     * You can also use {@link #identify(Traits)} for this purpose.
     *
     * In the case when user logs out, make sure to call {@link #reset()} to clear user's identity
     * info.
     *
     * @param traits [Traits] about the user. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @see <a href="https://segment.com/docs/spec/identify/">Identify Documentation</a>
     */
    inline fun <reified T : Any> identify(
        traits: T,
    ) {
        identify(traits, Json.serializersModule.serializer())
    }

    /**
     * Identify lets you record {@code traits} about the user, like their email, name, account type,
     * etc.
     *
     * <p>Traits and userId will be automatically cached and available on future sessions for the
     * same user. To update a trait on the server, call identify with the same user id.
     * You can also use {@link #identify(Traits)} for this purpose.
     *
     * In the case when user logs out, make sure to call {@link #reset()} to clear user's identity
     * info.
     *
     * @param traits [Traits] about the user.
     * @see <a href="https://segment.com/docs/spec/identify/">Identify Documentation</a>
     */
    @JvmOverloads
    fun identify(traits: JsonObject = emptyJsonObject) {
        analyticsScope.launch(analyticsDispatcher) {
            store.dispatch(UserInfo.SetTraitsAction(traits), UserInfo::class)
        }
        val event = IdentifyEvent(
            userId = "", // using "" for userId, which will get filled down the pipe
            traits = traits
        )
        process(event)
    }

    /**
     * Identify lets you tie one of your users and their actions to a recognizable {@code userId}.
     * It also lets you record {@code traits} about the user, like their email, name, account type,
     * etc.
     *
     * <p>Traits and userId will be automatically cached and available on future sessions for the
     * same user. To update a trait on the server, call identify with the same user id.
     * You can also use {@link #identify(Traits)} for this purpose.
     *
     * In the case when user logs out, make sure to call {@link #reset()} to clear user's identity
     * info.
     *
     * @param traits [Traits] about the user. Needs to be [serializable](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md)
     * @param serializationStrategy strategy to serialize [traits]
     * @see <a href="https://segment.com/docs/spec/identify/">Identify Documentation</a>
     */
    fun <T : Any> identify(
        traits: T,
        serializationStrategy: SerializationStrategy<T>,
    ) {
        identify(Json.encodeToJsonElement(serializationStrategy, traits).jsonObject)
    }

    /**
     * Identify lets you tie one of your users and their actions to a recognizable {@code userId}.
     * It also lets you record {@code traits} about the user, like their email, name, account type,
     * etc.
     *
     * <p>Traits and userId will be automatically cached and available on future sessions for the
     * same user. To update a trait on the server, call identify with the same user id.
     * You can also use {@link #identify(Traits)} for this purpose.
     *
     * In the case when user logs out, make sure to call {@link #reset()} to clear user's identity
     * info.
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
        category: String = "",
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
        category: String = "",
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
        serializationStrategy: SerializationStrategy<T>,
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
        event.applyBaseData()

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
        return this
    }

    /**
     * Retrieve the first match of registered plugin. It finds
     *      1. the first instance of the given class/interface
     *      2. or the first instance of subclass of the given class/interface
     * @param plugin [KClass]
     */
    fun <T : Plugin> find(plugin: KClass<T>): T? = this.timeline.find(plugin)

    /**
     * Retrieve the first match of registered destination plugin by key. It finds
     * @param destinationKey [String]
     */
    fun find(destinationKey: String): DestinationPlugin? = this.timeline.find(destinationKey)

    /**
     * Retrieve the first match of registered plugin. It finds
     *      1. all instances of the given class/interface
     *      2. and all instances of subclass of the given class/interface
     * @param plugin [KClass]
     */
    fun <T : Plugin> findAll(plugin: KClass<T>): List<T> = this.timeline.findAll(plugin)

    /**
     * Remove a plugin from the analytics timeline using its name
     * @param plugin [Plugin] to be remove
     */
    fun remove(plugin: Plugin): Analytics {
        this.timeline.remove(plugin)
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
            (it as? EventPlugin)?.flush()
        }
    }

    /**
     * Reset the user identity info and all the event plugins. Should be invoked when
     * user logs out
     */
    fun reset() {
        val newAnonymousId = UUID.randomUUID().toString()
        userInfo = UserInfo(newAnonymousId, null, null)

        analyticsScope.launch(analyticsDispatcher) {
            store.dispatch(UserInfo.ResetAction(newAnonymousId), UserInfo::class)
            timeline.applyClosure {
                (it as? EventPlugin)?.reset()
            }
        }
    }

    /**
     * Shuts down the library by freeing up resources includes queues and Threads. This is a
     * non-reversible operation. This instance of Analytics will be shutdown and no longer process
     * events.
     *
     * Should only be called in containerized environments where you need to free resources like
     * CoroutineDispatchers and ExecutorService instances so they allow the container to shutdown
     * properly.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun shutdown() {
        (analyticsDispatcher as CloseableCoroutineDispatcher).close()
        (networkIODispatcher as CloseableCoroutineDispatcher).close()
        (fileIODispatcher as CloseableCoroutineDispatcher).close()

        store.shutdown();
    }

    /**
     * Retrieve the userId registered by a previous `identify` call.
     */
    fun userId(): String? {
        return userInfo.userId
    }

    /**
     * Retrieve the userId registered by a previous `identify` call
     */
    @Deprecated(
        "This function no longer serves a purpose and internally calls `userId()`.",
        ReplaceWith("userId()")
    )
    fun userIdAsync(): String? {
        return userId()
    }

    /**
     * Retrieve the traits registered by a previous `identify` call.
     */
    fun traits(): JsonObject? {
        return userInfo.traits
    }

    /**
     * Retrieve the traits registered by a previous `identify` call
     */
    @Deprecated(
        "This function no longer serves a purpose and internally calls `traits()`.",
        ReplaceWith("traits()")
    )
    fun traitsAsync(): JsonObject? {
        return traits()
    }

    /**
     * Retrieve the traits registered by a previous `identify` call in a blocking way.
     */
    inline fun <reified T : Any> traits(deserializationStrategy: DeserializationStrategy<T> = Json.serializersModule.serializer()): T? {
        return traits()?.let {
            decodeFromJsonElement(deserializationStrategy, it)
        }
    }

    /**
     * Retrieve the traits registered by a previous `identify` call
     */
    @Deprecated(
        "This function no longer serves a purpose and internally calls `traits(deserializationStrategy: DeserializationStrategy<T>)`.",
        ReplaceWith("traits(deserializationStrategy: DeserializationStrategy<T>)")
    )
    inline fun <reified T : Any> traitsAsync(deserializationStrategy: DeserializationStrategy<T> = Json.serializersModule.serializer()): T? {
        return traits(deserializationStrategy)
    }

    /**
     * Retrieve the settings  in a blocking way.
     * Note: this method invokes `runBlocking` internal, it's not recommended to be used
     * in coroutines.
     */
    @BlockingApi
    fun settings(): Settings? = runBlocking {
        settingsAsync()
    }

    /**
     * Retrieve the settings
     */
    suspend fun settingsAsync(): Settings? {
        val system = store.currentState(System::class)
        return system?.settings
    }

    /**
     * Retrieve the anonymousId.
     */
    fun anonymousId(): String {
        return userInfo.anonymousId
    }

    /**
     * Retrieve the anonymousId
     */
    @Deprecated(
        "This function no longer serves a purpose and internally calls `anonymousId()`.",
        ReplaceWith("anonymousId()")
    )
    fun anonymousIdAsync(): String {
        return anonymousId()
    }

    /**
     * Retrieve the version of this library in use.
     * - Returns: A string representing the version in "BREAKING.FEATURE.FIX" format.
     */
    fun version() = Analytics.version()
}


/**
 * constructor function to build analytics in dsl format with config options
 * Usage: Analytics("123") {
 *            this.application = "n/a"
 *            this.collectDeviceId = false
 *            this.flushAt = 10
 *        }
 *
 * NOTE: this method should only be used for JVM application. for Android, there is
 * another set of extension functions that requires a context as the second parameter:
 *      * Analytics(writeKey: String, context: Context)
 *      * Analytics(writeKey: String, context: Context, configs: Configuration.() -> Unit)
 */
fun Analytics(writeKey: String, configs: Configuration.() -> Unit): Analytics {
    if (isAndroid()) {
        error("Using JVM Analytics initializer in Android platform. Context is required in constructor!")
    }

    val config = Configuration(writeKey)
    configs.invoke(config)
    return Analytics(config)
}

internal fun isAndroid(): Boolean {
    return try {
        Class.forName("com.segment.analytics.kotlin.android.AndroidStorage")
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}
