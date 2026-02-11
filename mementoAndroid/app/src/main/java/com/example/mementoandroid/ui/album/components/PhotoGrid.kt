package com.example.mementoandroid.ui.album.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.ui.album.AlbumPhotoUi

@Composable
fun PhotoGrid(
    photos: List<AlbumPhotoUi>,
    modifier: Modifier = Modifier,
    onPhotoClick: (String) -> Unit,
    onAddClick: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "add-title") {
            AddPhotoTile(onClick = onAddClick)
        }

        items(photos, key = {it.id}) { photo ->
            PhotoTile(
                photo = photo,
                onClick = { onPhotoClick(photo.id) }
            )
        }
    }
}