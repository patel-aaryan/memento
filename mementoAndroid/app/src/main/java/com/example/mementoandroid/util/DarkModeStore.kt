package com.example.mementoandroid.util

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "memento_dark_mode"
private const val KEY_DARK_MODE = "dark_mode"

object DarkModeStore {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun get(context: Context): Boolean {
        init(context)
        return prefs?.getBoolean(KEY_DARK_MODE, false) ?: false
    }

    fun set(context: Context, darkMode: Boolean) {
        init(context)
        prefs?.edit()?.apply {
            putBoolean(KEY_DARK_MODE, darkMode)
            apply()
        }
    }
}
