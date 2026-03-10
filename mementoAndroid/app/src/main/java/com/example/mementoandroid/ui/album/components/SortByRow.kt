package com.example.mementoandroid.ui.album.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.ui.album.AlbumSort
import com.example.mementoandroid.ui.album.AlbumSortKind
import com.example.mementoandroid.ui.album.FriendUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortByRow(
    sort: AlbumSort,
    onSortChange: (AlbumSort) -> Unit,
    friends: List<FriendUi>,
    currentUserId: Int?,
    modifier: Modifier = Modifier
) {
    var uploadedByExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Sort by",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )
        FilterChip(
            selected = sort.kind == AlbumSortKind.TIME_NEWEST_FIRST,
            onClick = { onSortChange(AlbumSort(AlbumSortKind.TIME_NEWEST_FIRST)) },
            label = { Text("Newest first") }
        )
        FilterChip(
            selected = sort.kind == AlbumSortKind.TIME_OLDEST_FIRST,
            onClick = { onSortChange(AlbumSort(AlbumSortKind.TIME_OLDEST_FIRST)) },
            label = { Text("Oldest first") }
        )
        FilterChip(
            selected = sort.kind == AlbumSortKind.BY_LOCATION,
            onClick = { onSortChange(AlbumSort(AlbumSortKind.BY_LOCATION)) },
            label = { Text("Location") }
        )
        Box {
            val uploadedByLabel = when {
                sort.kind == AlbumSortKind.UPLOADED_BY && sort.uploadedByUserId != null -> {
                    if (sort.uploadedByUserId == currentUserId) "Me"
                    else friends.find { it.id.toIntOrNull() == sort.uploadedByUserId }?.username ?: "User"
                }
                else -> "Uploaded by"
            }
            FilterChip(
                selected = sort.kind == AlbumSortKind.UPLOADED_BY,
                onClick = { uploadedByExpanded = true },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(uploadedByLabel)
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                }
            )
            DropdownMenu(
                expanded = uploadedByExpanded,
                onDismissRequest = { uploadedByExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("All") },
                    onClick = {
                        uploadedByExpanded = false
                        onSortChange(AlbumSort(AlbumSortKind.TIME_NEWEST_FIRST))
                    }
                )
                if (currentUserId != null) {
                    DropdownMenuItem(
                        text = { Text("Me") },
                        onClick = {
                            uploadedByExpanded = false
                            onSortChange(AlbumSort(AlbumSortKind.UPLOADED_BY, currentUserId))
                        }
                    )
                }
                friends.forEach { friend ->
                    val uid = friend.id.toIntOrNull() ?: return@forEach
                    DropdownMenuItem(
                        text = { Text(friend.username) },
                        onClick = {
                            uploadedByExpanded = false
                            onSortChange(AlbumSort(AlbumSortKind.UPLOADED_BY, uid))
                        }
                    )
                }
            }
        }
    }
}
