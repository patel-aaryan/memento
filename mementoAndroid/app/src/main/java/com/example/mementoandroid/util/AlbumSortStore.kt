package com.example.mementoandroid.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.mementoandroid.ui.album.AlbumSort
import com.example.mementoandroid.ui.album.AlbumSortKind
import org.json.JSONObject

private const val PREFS_NAME = "memento_album_sort"
private const val KEY_PREFIX = "sort_"

object AlbumSortStore {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun save(albumId: Int, sort: AlbumSort) {
        prefs?.edit(commit = true) {
            putString(KEY_PREFIX + albumId, sortToJson(sort))
        }
    }

    fun get(albumId: Int): AlbumSort? {
        val json = prefs?.getString(KEY_PREFIX + albumId, null) ?: return null
        return try {
            jsonToSort(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun sortToJson(sort: AlbumSort): String {
        return JSONObject().apply {
            put("kind", sort.kind.name)
            put("uploadedByUserId", sort.uploadedByUserId ?: JSONObject.NULL)
        }.toString()
    }

    private fun jsonToSort(json: String): AlbumSort {
        val obj = JSONObject(json)
        val kind = AlbumSortKind.valueOf(obj.getString("kind"))
        val uploadedByUserId = if (obj.isNull("uploadedByUserId")) null else obj.getInt("uploadedByUserId")
        return AlbumSort(kind = kind, uploadedByUserId = uploadedByUserId)
    }
}
