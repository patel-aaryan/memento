package com.example.mementoandroid.util

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "memento_album_view"
private const val KEY_IS_GRID_VIEW = "is_grid_view"

object AlbumViewStore {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** True = grid view, false = list view. Defaults to true (grid) when cache is empty. */
    fun getIsGridView(): Boolean {
        return prefs?.getBoolean(KEY_IS_GRID_VIEW, true) ?: true
    }

    fun saveIsGridView(isGrid: Boolean) {
        prefs?.edit()?.apply {
            putBoolean(KEY_IS_GRID_VIEW, isGrid)
            apply()
        }
    }
}
