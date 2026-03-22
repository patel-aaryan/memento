package com.example.mementoandroid.ui.album

import android.net.Uri

data class AlbumPhotoUi(
    val id: String,
    val imageRes: Int? = null,
    val uri: Uri? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val caption: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
    val dateAdded: String? = null,
    val takenAt: String? = null,
    val userId: Int? = null
) {
    init {
        require(listOf(imageRes, uri, imageUrl).any { it != null }) {
            "One of imageRes, uri, or imageUrl must be set"
        }
    }
}

data class AlbumUi(
    val id: Int,
    val name: String,
    val ownerId: Int? = null,
    val coverImageUrls: List<String> = emptyList()
)

data class FriendUi(
    val id: String,
    val username: String,
    val profilePictureUrl: String? = null
)

enum class AddPhotoSource {Camera, Photos}

/** How album photos are sorted or filtered in the grid and feed. */
enum class AlbumSortKind {
    TIME_NEWEST_FIRST,
    TIME_OLDEST_FIRST,
    BY_LOCATION,
    UPLOADED_BY
}

/** Applied sort/filter: kind + optional user id for UPLOADED_BY. */
data class AlbumSort(
    val kind: AlbumSortKind,
    val uploadedByUserId: Int? = null
)

fun List<AlbumPhotoUi>.sortedAndFiltered(sort: AlbumSort): List<AlbumPhotoUi> {
    var list = this
    if (sort.kind == AlbumSortKind.UPLOADED_BY && sort.uploadedByUserId != null) {
        list = list.filter { it.userId == sort.uploadedByUserId }
    }
    return when (sort.kind) {
        AlbumSortKind.TIME_NEWEST_FIRST -> list.sortedByDescending { it.takenAt ?: it.dateAdded ?: "" }
        AlbumSortKind.TIME_OLDEST_FIRST -> list.sortedBy { it.takenAt ?: it.dateAdded ?: "" }
        AlbumSortKind.BY_LOCATION -> {
            list.groupBy { p ->
                when {
                    p.latitude != null && p.longitude != null -> "%.4f,%.4f".format(p.latitude, p.longitude)
                    else -> "_no_location"
                }
            }.toSortedMap().values.flatten()
        }
        AlbumSortKind.UPLOADED_BY -> list.sortedByDescending { it.takenAt ?: it.dateAdded ?: "" }
    }
}