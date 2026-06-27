package com.tachiup.data

import android.content.Context

/** Lightweight persisted settings backed by SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("tachiup", Context.MODE_PRIVATE)

    var useShizuku: Boolean
        get() = prefs.getBoolean(KEY_SHIZUKU, false)
        set(value) = prefs.edit().putBoolean(KEY_SHIZUKU, value).apply()

    var includeNsfw: Boolean
        get() = prefs.getBoolean(KEY_NSFW, true)
        set(value) = prefs.edit().putBoolean(KEY_NSFW, value).apply()

    companion object {
        private const val KEY_SHIZUKU = "use_shizuku"
        private const val KEY_NSFW = "include_nsfw"
    }
}
