package com.example.mementoandroid.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

private const val PREFS_NAME = "memento_auth"
private const val KEY_ACCESS_TOKEN = "access_token"

object AuthTokenStore {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun save(token: String) {
        prefs?.edit(commit = true) {
            putString(KEY_ACCESS_TOKEN, token)
        }
    }

    fun get(): String? = prefs?.getString(KEY_ACCESS_TOKEN, null)

    fun clear() {
        prefs?.edit(commit = true) {
            remove(KEY_ACCESS_TOKEN)
        }
    }
}
