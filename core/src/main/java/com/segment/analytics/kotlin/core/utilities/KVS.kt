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

    fun get(key: String, defaultVal: Int): Int
    fun put(key: String, value: Int): Boolean
    fun get(key: String, defaultVal: String?): String?
    fun put(key: String, value: String): Boolean
    fun remove(key: String): Boolean
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

}