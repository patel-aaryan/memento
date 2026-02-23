package com.example.mementoandroid.ui.album

import android.net.Uri

data class AlbumPhotoUi(
    val id: String,
    val imageRes: Int? = null,
    val uri: Uri? = null,
    val imageUrl: String? = null,
    val caption: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val dateAdded: String? = null,
    val takenAt: String? = null
) {
    init {
        require(listOf(imageRes, uri, imageUrl).any { it != null }) {
            "One of imageRes, uri, or imageUrl must be set"
        }
    }
}

data class AlbumUi(
    val id: Int,
    val name: String
)

data class FriendUi(
    val id: String,
    val username: String,
    val profilePictureUrl: String? = null
)

enum class AddPhotoSource {Camera, Photos}