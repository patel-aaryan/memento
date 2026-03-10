package com.example.mementoandroid.ui.album

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.mementoandroid.ui.album.components.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumName: String,
    photos: List<AlbumPhotoUi>,
    friends: List<FriendUi>,
    isSharedAlbum: Boolean,
    showMap: Boolean,
    onShowMapChange: (Boolean) -> Unit,
    sort: AlbumSort,
    onSortChange: (AlbumSort) -> Unit,
    currentUserId: Int?,
    onBack: () -> Unit,
    onEditAlbumName: () -> Unit,
    onSaveAlbumName: (newName: String) -> Unit = {},
    onDeleteAlbum: () -> Unit,
    onAddFriend: () -> Unit,
    onPhotoClick: (photoId: String) -> Unit,
    onAddPhoto: (source: AddPhotoSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var addPhotoSheetOpen by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editedName by remember(albumName, showEditDialog) { mutableStateOf(albumName) }

    if (addPhotoSheetOpen) {
        AddPhotoBottomSheet(
            onDismiss = { addPhotoSheetOpen = false },
            onPick = { source ->
                addPhotoSheetOpen = false
                onAddPhoto(source)
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit album name") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Album name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = editedName.trim()
                        if (name.isNotBlank()) {
                            onSaveAlbumName(name)
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AlbumTopBar(
                albumName = albumName,
                onBack = onBack,
                onEditAlbumName = { showEditDialog = true },
                onDeleteAlbum = onDeleteAlbum
            )
        },
        // Map button
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onShowMapChange(!showMap) }
            ) {
                Icon(
                    imageVector = if (showMap) Icons.Default.GridView else Icons.Default.Map,
                    contentDescription = if (showMap) "Show grid" else "Show map"
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            FriendsRow(
                friends = friends,
                onAddFriend = onAddFriend
            )
            SortByRow(
                sort = sort,
                onSortChange = onSortChange,
                friends = friends,
                currentUserId = currentUserId,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
            if (showMap){
                MapScreen(
                    photos = photos,
                    onPhotoClick = onPhotoClick
                )
            } else {
                PhotoGrid(
                    photos = photos,
                    onPhotoClick = onPhotoClick,
                    onAddClick = { addPhotoSheetOpen = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}


