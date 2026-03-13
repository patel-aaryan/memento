package com.example.mementoandroid.ui.album.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.ui.album.AlbumPhotoUi

@Composable
fun PhotoGrid(
    photos: List<AlbumPhotoUi>,
    modifier: Modifier = Modifier,
    onPhotoClick: (String) -> Unit,
    onAddClick: () -> Unit,
    isEditMode: Boolean = false,
    isOwner: Boolean = true,
    currentUserId: Int? = null,
    selectedIds: Set<String> = emptySet(),
    onSelectionChange: (Set<String>) -> Unit = {},
) {
    fun isSelectable(photo: AlbumPhotoUi): Boolean {
        if (!isEditMode) return false
        return if (isOwner) true
        else currentUserId != null && photo.userId == currentUserId
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "add-title") {
            AddPhotoTile(
                onClick = if (isEditMode) {{}} else onAddClick,
                modifier = if (isEditMode) Modifier.alpha(0.5f) else Modifier
            )
        }

        items(photos, key = { it.id }) { photo ->
            val selectable = isSelectable(photo)
            val selected = photo.id in selectedIds
            PhotoTile(
                photo = photo,
                onClick = { onPhotoClick(photo.id) },
                isEditMode = isEditMode,
                isSelectable = selectable,
                isSelected = selected,
                onToggleSelect = {
                    onSelectionChange(
                        if (selected) selectedIds - photo.id
                        else selectedIds + photo.id
                    )
                }
            )
        }
    }
}
