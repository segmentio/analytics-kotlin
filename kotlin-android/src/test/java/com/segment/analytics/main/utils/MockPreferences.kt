package com.segment.analytics.main.utils

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.annotation.Nullable


/**
 * Mock implementation of shared preference, which just saves data in memory using map.
 */
class MemorySharedPreferences : SharedPreferences {
    internal val preferenceMap: HashMap<String, Any?> = HashMap()
    private val preferenceEditor: MockSharedPreferenceEditor
    override fun getAll(): Map<String, *> {
        return preferenceMap
    }

    @Nullable
    override fun getString(s: String, @Nullable s1: String?): String? {
        return try {
            preferenceMap[s] as String?
        } catch(ex: Exception) {
            s1
        }
    }

    @Nullable
    override fun getStringSet(s: String, @Nullable set: Set<String>?): Set<String>? {
        return try {
            preferenceMap[s] as Set<String>?
        } catch(ex: Exception) {
            set
        }
    }

    override fun getInt(s: String, i: Int): Int {
        return try {
            preferenceMap[s] as Int
        } catch(ex: Exception) {
            i
        }
    }

    override fun getLong(s: String, l: Long): Long {
        return try {
            preferenceMap[s] as Long
        } catch(ex: Exception) {
            l
        }
    }

    override fun getFloat(s: String, v: Float): Float {
        return try {
            preferenceMap[s] as Float
        } catch(ex: Exception) {
            v
        }
    }

    override fun getBoolean(s: String, b: Boolean): Boolean {
        return try {
            preferenceMap[s] as Boolean
        } catch(ex: Exception) {
            b
        }
    }

    override fun contains(s: String): Boolean {
        return preferenceMap.containsKey(s)
    }

    override fun edit(): SharedPreferences.Editor {
        return preferenceEditor
    }

    override fun registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {}
    class MockSharedPreferenceEditor(private val preferenceMap: HashMap<String, Any?>) :
        SharedPreferences.Editor {
        override fun putString(s: String, @Nullable s1: String?): SharedPreferences.Editor {
            preferenceMap[s] = s1
            return this
        }

        override fun putStringSet(
            s: String,
            @Nullable set: Set<String>?
        ): SharedPreferences.Editor {
            preferenceMap[s] = set
            return this
        }

        override fun putInt(s: String, i: Int): SharedPreferences.Editor {
            preferenceMap[s] = i
            return this
        }

        override fun putLong(s: String, l: Long): SharedPreferences.Editor {
            preferenceMap[s] = l
            return this
        }

        override fun putFloat(s: String, v: Float): SharedPreferences.Editor {
            preferenceMap[s] = v
            return this
        }

        override fun putBoolean(s: String, b: Boolean): SharedPreferences.Editor {
            preferenceMap[s] = b
            return this
        }

        override fun remove(s: String): SharedPreferences.Editor {
            preferenceMap.remove(s)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            preferenceMap.clear()
            return this
        }

        override fun commit(): Boolean {
            return true
        }

        override fun apply() {
            // Nothing to do, everything is saved in memory.
        }
    }

    init {
        preferenceEditor = MockSharedPreferenceEditor(preferenceMap)
    }
}