package com.segment.analytics.utilities

import android.content.SharedPreferences
import com.segment.analytics.kotlin.core.utilities.KVS
import com.segment.analytics.kotlin.core.utilities.createDirectory
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

class AndroidKVS(val sharedPreferences: SharedPreferences): KVS {
    override fun getInt(key: String, defaultVal: Int): Int =
        sharedPreferences.getInt(key, defaultVal)

    override fun putInt(key: String, value: Int): Boolean =
        sharedPreferences.edit().putInt(key, value).commit()
}