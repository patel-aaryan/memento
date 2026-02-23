package com.example.mementoandroid.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject

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

    /**
     * Returns the current user's ID from the JWT token (sub claim), or null if no token or invalid.
     */
    fun getUserId(): Int? {
        val token = get() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP))
            val json = JSONObject(payload)
            json.optString("sub", "").toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs?.edit(commit = true) {
            remove(KEY_ACCESS_TOKEN)
        }
    }
}
