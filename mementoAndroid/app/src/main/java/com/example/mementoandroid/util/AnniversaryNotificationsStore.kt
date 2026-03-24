package com.example.mementoandroid.util

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "memento_anniversary_notifications"
private const val KEY_ENABLED = "anniversary_notifications_enabled"

object AnniversaryNotificationsStore {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** When false, silent anniversary triggers and visible anniversary pushes are ignored. */
    fun isEnabled(context: Context): Boolean {
        init(context)
        return prefs?.getBoolean(KEY_ENABLED, false) ?: false
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.apply {
            putBoolean(KEY_ENABLED, enabled)
            apply()
        }
    }
}
