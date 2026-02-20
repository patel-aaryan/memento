package com.example.mementoandroid.ui.album

import android.net.Uri

data class AlbumPhotoUi(
    val id: String,
    val imageRes: Int? = null,
    val uri: Uri? = null
) {
    init {
        require(imageRes != null || uri != null) { "Either imageRes or uri must be set" }
    }
}

data class FriendUi(
    val id: String,
    val username: String
)

enum class AddPhotoSource {Camera, Photos}