package com.example.mementoandroid.ui.album.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumTopBar(
    albumName: String,
    onBack: () -> Unit,
    onEditAlbumName: () -> Unit,
    onDeleteAlbum: () -> Unit,
) {
    TopAppBar(
        title = {Text(albumName)},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onEditAlbumName) {
                Icon(Icons.Default.Edit, contentDescription = "Edit album name")
            }
            IconButton(onClick = onDeleteAlbum) {
                Icon(Icons.Default.Delete, contentDescription = "Delete album")
            }
        }
    )
}
