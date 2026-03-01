package com.example.mementoandroid.util

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "pending_friend_token"
private const val KEY_TOKEN = "token"

object PendingFriendTokenStore {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun save(context: Context, token: String) {
        init(context)
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }

    fun get(context: Context): String? {
        init(context)
        return prefs?.getString(KEY_TOKEN, null)
    }

    fun clear(context: Context) {
        init(context)
        prefs?.edit()?.remove(KEY_TOKEN)?.apply()
    }
}
