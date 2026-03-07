package com.example.mementoandroid.util

import android.content.Context
import android.content.SharedPreferences

private const val ANNIV_PREFS_NAME = "memento_anniversary_notifications"
private const val KEY_PREFIX = "anniv_seen_"

object AnniversaryNotificationStore {
    private var prefs: SharedPreferences? = null

    private fun ensureInit(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(ANNIV_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun hasSeen(context: Context, key: String): Boolean {
        ensureInit(context)
        return prefs?.getBoolean(KEY_PREFIX + key, false) == true
    }

    fun markSeen(context: Context, key: String) {
        ensureInit(context)
        prefs?.edit()?.putBoolean(KEY_PREFIX + key, true)?.apply()
    }
}

