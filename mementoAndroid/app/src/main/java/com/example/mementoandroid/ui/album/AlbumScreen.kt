package com.example.mementoandroid.ui.album

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.mementoandroid.ui.album.components.*

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
    isAlbumOwner: Boolean,
    onBack: () -> Unit,
    onEditAlbumName: () -> Unit,
    onSaveAlbumName: (newName: String) -> Unit = {},
    onDeleteAlbum: () -> Unit,
    onAddFriend: () -> Unit,
    onPhotoClick: (photoId: String) -> Unit,
    /** Map: inseparable cluster (same coordinates) — sort by location and open first photo. */
    onSameLocationClusterClick: (photoId: String) -> Unit,
    onAddPhoto: (source: AddPhotoSource) -> Unit,
    onSaveEdits: (newName: String?, imageIdsToDelete: List<String>) -> Unit = { _, _ -> },
    onSharePhotos: (List<AlbumPhotoUi>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var addPhotoSheetOpen by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var editedTitle by remember(albumName, isEditMode) { mutableStateOf(albumName) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var idsToDelete by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(albumName) {
        if (!isEditMode) editedTitle = albumName
    }

    if (addPhotoSheetOpen) {
        AddPhotoBottomSheet(
            onDismiss = { addPhotoSheetOpen = false },
            onPick = { source ->
                addPhotoSheetOpen = false
                onAddPhoto(source)
            }
        )
    }

    val displayPhotos = remember(photos, idsToDelete) {
        photos.filter { it.id !in idsToDelete }
    }
    val selectedPhotos = remember(displayPhotos, selectedIds) {
        displayPhotos.filter { it.id in selectedIds }
    }
    val canDeleteSelected = remember(isAlbumOwner, selectedIds, selectedPhotos, currentUserId) {
        if (selectedIds.isEmpty()) false
        else if (isAlbumOwner) true
        else selectedPhotos.all { it.userId == currentUserId }
    }

    fun exitEditMode() {
        isEditMode = false
        selectedIds = emptySet()
        idsToDelete = emptySet()
        editedTitle = albumName
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AlbumTopBar(
                albumName = albumName,
                onBack = onBack,
                onEditAlbumName = {
                    isEditMode = true
                    editedTitle = albumName
                },
                onDeleteAlbum = onDeleteAlbum,
                isEditMode = isEditMode,
                isOwner = isAlbumOwner,
                editedTitle = editedTitle,
                onEditedTitleChange = { editedTitle = it },
                onSave = {
                    val newName = if (isAlbumOwner && editedTitle.trim() != albumName) editedTitle.trim().takeIf { it.isNotBlank() } else null
                    val toDelete = idsToDelete.toList()
                    onSaveEdits(newName, toDelete)
                    exitEditMode()
                },
                onCancel = { exitEditMode() },
                selectedCount = selectedIds.size,
                canDeleteSelected = canDeleteSelected,
                onShareSelected = { onSharePhotos(selectedPhotos) },
                onDeleteSelected = {
                    idsToDelete = idsToDelete + selectedIds
                    selectedIds = emptySet()
                }
            )
        },
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
            if (!showMap) {
                SortByRow(
                    sort = sort,
                    onSortChange = onSortChange,
                    friends = friends,
                    currentUserId = currentUserId,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
            if (showMap) {
                MapScreen(
                    photos = displayPhotos,
                    onPhotoClick = onPhotoClick,
                    onSameLocationClusterClick = onSameLocationClusterClick
                )
            } else {
                PhotoGrid(
                    photos = displayPhotos,
                    onPhotoClick = onPhotoClick,
                    onAddClick = { addPhotoSheetOpen = true },
                    modifier = Modifier.fillMaxSize(),
                    isEditMode = isEditMode,
                    isOwner = isAlbumOwner,
                    currentUserId = currentUserId,
                    selectedIds = selectedIds,
                    onSelectionChange = { selectedIds = it }
                )
            }
        }
    }
}
