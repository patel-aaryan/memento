package com.example.mementoandroid.ui.album.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumTopBar(
    albumName: String,
    onBack: () -> Unit,
    onEditAlbumName: () -> Unit,
    onDeleteAlbum: () -> Unit,
    isEditMode: Boolean = false,
    isOwner: Boolean = true,
    editedTitle: String = "",
    onEditedTitleChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onCancel: () -> Unit = {},
    selectedCount: Int = 0,
    onShareSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
) {
    TopAppBar(
        title = {
            if (isEditMode && isOwner) {
                TextField(
                    value = editedTitle,
                    onValueChange = onEditedTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Album name") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    )
                )
            } else {
                Text(albumName)
            }
        },
        modifier = Modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (isEditMode) {
                if (selectedCount > 0) {
                    IconButton(onClick = onShareSelected) {
                        Icon(Icons.Default.Share, contentDescription = "Share selected")
                    }
                    IconButton(onClick = onDeleteSelected) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                    }
                }
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Check, contentDescription = "Save")
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            } else {
                IconButton(onClick = onEditAlbumName) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDeleteAlbum) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete album")
                }
            }
        }
    )
}
