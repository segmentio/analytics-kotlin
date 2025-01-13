package com.segment.analytics.kotlin.android.utilities

import android.content.SharedPreferences
import com.segment.analytics.kotlin.core.utilities.KVS

/**
 * A key-value store wrapper for sharedPreferences on Android
 */
class AndroidKVS(val sharedPreferences: SharedPreferences): KVS  {


    override fun get(key: String, defaultVal: Int) =
        sharedPreferences.getInt(key, defaultVal)

    override fun get(key: String, defaultVal: String?) =
        sharedPreferences.getString(key, defaultVal) ?: defaultVal

    override fun put(key: String, value: Int) =
        sharedPreferences.edit().putInt(key, value).commit()

    override fun put(key: String, value: String) =
        sharedPreferences.edit().putString(key, value).commit()

    override fun remove(key: String): Boolean =
        sharedPreferences.edit().remove(key).commit()
}