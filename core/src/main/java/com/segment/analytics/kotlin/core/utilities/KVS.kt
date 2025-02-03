package com.segment.analytics.kotlin.core.utilities

import java.util.concurrent.ConcurrentHashMap


/**
 * Key-value store interface used by eventsFile
 */
interface KVS {
    @Deprecated("Deprecated in favor of `get`", ReplaceWith("get(key, defaultVal)"))
    fun getInt(key: String, defaultVal: Int): Int = get(key, defaultVal)
    @Deprecated("Deprecated in favor of `put`", ReplaceWith("put(key, value)"))
    fun putInt(key: String, value: Int): Boolean = put(key, value)

    /**
     * Read the value of a given key as integer
     * @param key Key
     * @param defaultVal Fallback value to use
     * @return Value
     */
    fun get(key: String, defaultVal: Int): Int

    /**
     * Store the key value pair
     * @param key Key
     * @param value Fallback value to use
     * @return Status of the operation
     */
    fun put(key: String, value: Int): Boolean

    /**
     * Read the value of a given key as integer
     * @param key Key
     * @param defaultVal Fallback value to use
     * @return Value
     */
    fun get(key: String, defaultVal: String?): String?

    /**
     * Store the key value pair
     * @param key Key
     * @param value Fallback value to use
     * @return Status of the operation
     */
    fun put(key: String, value: String): Boolean

    /**
     * Remove a key/value pair by key
     *
     * @param key Key
     * @return Status of the operation
     */
    fun remove(key: String): Boolean

    /**
     * checks if a given key exists
     *
     * @param Key
     * @return Status of the operation
     */
    fun contains(key: String): Boolean
}

class InMemoryPrefs: KVS {

    private val cache = ConcurrentHashMap<String, Any>()
    override fun get(key: String, defaultVal: Int): Int {
        return if (cache[key] is Int) cache[key] as Int else defaultVal
    }

    override fun get(key: String, defaultVal: String?): String? {
        return if (cache[key] is String) cache[key] as String else defaultVal
    }

    override fun put(key: String, value: Int): Boolean {
        cache[key] = value
        return true
    }

    override fun put(key: String, value: String): Boolean {
        cache[key] = value
        return true
    }

    override fun remove(key: String): Boolean {
        cache.remove(key)
        return true
    }

    override fun contains(key: String) = cache.containsKey(key)

}