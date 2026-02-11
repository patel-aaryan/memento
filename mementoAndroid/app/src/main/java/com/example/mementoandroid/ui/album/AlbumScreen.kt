package com.example.mementoandroid.ui.album

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.mementoandroid.ui.album.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumName: String,
    photos: List<AlbumPhotoUi>,
    friends: List<FriendUi>,
    onBack: () -> Unit,
    onEditAlbumName: () -> Unit,
    onDeleteAlbum: () -> Unit,
    onAddFriend: () -> Unit,
    onPhotoClick: (photoId: String) -> Unit,
    onAddPhoto: (source: AddPhotoSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var addPhotoSheetOpen by remember { mutableStateOf(false) }

    if (addPhotoSheetOpen) {
        AddPhotoBottomSheet(
            onDismiss = { addPhotoSheetOpen = false },
            onPick = { source ->
                addPhotoSheetOpen = false
                onAddPhoto(source)
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AlbumTopBar(
                albumName = albumName,
                onBack = onBack,
                onEditAlbumName = onEditAlbumName,
                onDeleteAlbum = onDeleteAlbum
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {

            FriendsRow(
                friends = friends,
                onAddFriend = onAddFriend
            )

            PhotoGrid(
                photos = photos,
                onPhotoClick = onPhotoClick,
                onAddClick = { addPhotoSheetOpen = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}


