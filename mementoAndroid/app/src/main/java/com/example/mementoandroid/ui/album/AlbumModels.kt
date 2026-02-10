package com.example.mementoandroid.ui.album

data class AlbumPhotoUi(
    val id: String,
    val imageRes: Int
)

data class FriendUi(
    val id: String,
    val username: String
)

enum class AddPhotoSource {Camera, Photos}