package com.segment.analytics.kotlin.core.compat

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import sovran.kotlin.Store
import java.util.function.Consumer

/**
 * This class is merely a wrapper of {@link Analytics com.segment.analytics.kotlin.core.Analytics}
 * for Java compatibility purpose.
 */
class JavaAnalytics private constructor() {

    /**
     * A constructor that takes a configuration
     * @param configuration an instance of configuration that can be build
     *          through {@link ConfigurationBuilder com.segment.analytics.kotlin.core.compat.ConfigurationBuilder}
     */
    constructor(configuration: Configuration): this() {
        analytics = Analytics(configuration)
        setup(analytics)
    }

    /**
     * A constructor takes an instance of {@link Analytics com.segment.analytics.kotlin.core.Analytics}
     * @param analytics an instance of Analytics object.
     *          This constructor wrappers it and provides a JavaAnalytics for Java compatibility.
     */
    constructor(analytics: Analytics): this() {
        this.analytics = analytics
        setup(analytics)
    }

    internal lateinit var analytics: Analytics
        private set

    lateinit var store: Store
        private set

    lateinit var storage: Storage
        private set

    lateinit var analyticsScope: CoroutineScope
        private set

    lateinit var ioDispatcher: CoroutineDispatcher
        private set

    /**
     * The track method is how you record any actions your users perform. Each action is known by a
     * name, like 'Purchased a T-Shirt'. You can also record properties specific to those actions.
     * For example a 'Purchased a Shirt' event might have properties like revenue or size.
     *
     * @param name Name of the action
     * @param properties [Properties] to describe the action in JsonObject form. can be built by
     *          {@link Builder com.segment.analytics.kotlin.core.compat.Builder}
     * @see <a href="https://segment.com/docs/spec/track/">Track Documentation</a>
     */
    @JvmOverloads
    fun track(name: String, properties: JsonObject = emptyJsonObject) = analytics.track(name, properties)

    /**
     * The track method is how you record any actions your users perform. Each action is known by a
     * name, like 'Purchased a T-Shirt'. You can also record properties specific to those actions.
     * For example a 'Purchased a Shirt' event might have properties like revenue or size.
     *
     * @param name Name of the action
     * @param serializable an object that implements {@link JsonSerializable com.segment.analytics.kotlin.core.compat.JsonSerializable}
     * @see <a href="https://segment.com/docs/spec/track/">Track Documentation</a>
     */
    fun track(name: String, serializable: JsonSerializable) = analytics.track(name, serializable.serialize())

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
     * @param traits [Traits] about the user in JsonObject form. can be built by
     *          {@link Builder com.segment.analytics.kotlin.core.compat.Builder}
     * @see <a href="https://segment.com/docs/spec/identify/">Identify Documentation</a>
     */
    @JvmOverloads
    fun identify(userId: String, traits: JsonObject = emptyJsonObject) = analytics.identify(userId, traits)

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
     * @param serializable an object that implements {@link JsonSerializable com.segment.analytics.kotlin.core.compat.JsonSerializable}
     * @see <a href="https://segment.com/docs/spec/identify/">Identify Documentation</a>
     */
    fun identify(userId: String, serializable: JsonSerializable) = analytics.identify(userId, serializable.serialize())

    /**
     * The screen methods let your record whenever a user sees a screen of your mobile app, and
     * attach a name, category or properties to the screen. Either category or name must be
     * provided.
     *
     * @param title A name for the screen.
     * @param category A category to describe the screen.
     * @param properties [Properties] to add extra information to this call. can be built by
     *          {@link Builder com.segment.analytics.kotlin.core.compat.Builder}
     * @see <a href="https://segment.com/docs/spec/screen/">Screen Documentation</a>
     */
    @JvmOverloads
    fun screen(
        title: String,
        properties: JsonObject = emptyJsonObject,
        category: String = ""
    ) = analytics.screen(title, properties, category)

    /**
     * The screen methods let your record whenever a user sees a screen of your mobile app, and
     * attach a name, category or properties to the screen. Either category or name must be
     * provided.
     *
     * @param title A name for the screen.
     * @param category A category to describe the screen.
     * @param serializable an object that implements {@link JsonSerializable com.segment.analytics.kotlin.core.compat.JsonSerializable}
     * @see <a href="https://segment.com/docs/spec/screen/">Screen Documentation</a>
     */
    fun screen(
        title: String,
        serializable: JsonSerializable,
        category: String = ""
    ) = analytics.screen(title, serializable.serialize(), category)

    /**
     * The group method lets you associate a user with a group. It also lets you record custom
     * traits about the group, like industry or number of employees.
     *
     * <p>If you've called {@link #identify(String, Traits, Options)} before, this will
     * automatically remember the userId. If not, it will fall back to use the anonymousId instead.
     *
     * @param groupId Unique identifier which you recognize a group by in your own database
     * @param traits [Traits] about the group. can be built by
     *          {@link Builder com.segment.analytics.kotlin.core.compat.Builder}
     * @see <a href="https://segment.com/docs/spec/group/">Group Documentation</a>
     */
    @JvmOverloads
    fun group(groupId: String, traits: JsonObject = emptyJsonObject) = analytics.group(groupId, traits)

    /**
     * The group method lets you associate a user with a group. It also lets you record custom
     * traits about the group, like industry or number of employees.
     *
     * <p>If you've called {@link #identify(String, Traits, Options)} before, this will
     * automatically remember the userId. If not, it will fall back to use the anonymousId instead.
     *
     * @param groupId Unique identifier which you recognize a group by in your own database
     * @param serializable an object that implements {@link JsonSerializable com.segment.analytics.kotlin.core.compat.JsonSerializable}
     * @see <a href="https://segment.com/docs/spec/group/">Group Documentation</a>
     */
    fun group(groupId: String, serializable: JsonSerializable) = analytics.group(groupId, serializable.serialize())

    fun process(event: BaseEvent) = analytics.process(event)

    /**
     * Register a plugin to the analytics timeline
     * @param plugin [Plugin] to be added
     */
    fun add(plugin: Plugin) = apply { analytics.add(plugin) }

    /**
     * Retrieve a registered plugin by reference
     * @param plugin [Plugin] in Java Class
     */
    fun <T: Plugin> find(plugin: Class<T>) = analytics.find(plugin.kotlin)

    /**
     * Remove a plugin from the analytics timeline using its name
     * @param plugin [Plugin] to be remove
     */
    fun remove(plugin: Plugin) = apply { analytics.remove(plugin) }

    /**
     * Apply a closure to all plugins registered to the analytics client. Ideal for invoking
     * functions for Utility plugins
     * @param closure a lambda or Function in Java
     */
    fun applyClosureToPlugins(closure: (Plugin) -> Unit) = analytics.applyClosureToPlugins(closure)

    /**
     * Apply a closure to all plugins registered to the analytics client. Ideal for invoking
     * functions for Utility plugins
     * @param closure Consumer in Java
     */
    @Suppress("NewApi")
    fun applyClosureToPlugins(closure: Consumer<in Plugin>) = analytics.applyClosureToPlugins(closure::accept)

    fun flush() = analytics.flush()

    /**
     * Retrieve the userId registered by a previous `identify` call
     */
    fun userId() = analytics.userId()

    /**
     * Retrieve the traits registered by a previous `identify` call
     */
    fun traits() = analytics.traits()

    private fun setup(analytics: Analytics) {
        store = analytics.store
        storage = analytics.storage
        analyticsScope = analytics.analyticsScope
        ioDispatcher = analytics.ioDispatcher
    }
}