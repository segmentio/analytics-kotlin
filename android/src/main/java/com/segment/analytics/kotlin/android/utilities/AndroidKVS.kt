package com.segment.analytics.kotlin.android.utilities

import android.content.SharedPreferences
import com.segment.analytics.kotlin.core.utilities.KVS

/**
 * A key-value store wrapper for sharedPreferences on Android
 */
class AndroidKVS(val sharedPreferences: SharedPreferences) : KVS {
    override fun getInt(key: String, defaultVal: Int): Int =
        sharedPreferences.getInt(key, defaultVal)

    override fun putInt(key: String, value: Int): Boolean =
        sharedPreferences.edit().putInt(key, value).commit()
}