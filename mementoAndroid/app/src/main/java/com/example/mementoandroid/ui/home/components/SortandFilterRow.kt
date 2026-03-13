package com.example.mementoandroid.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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

enum class HomeSortKind {
    NEWEST_FIRST,
    OLDEST_FIRST
}

data class HomeSort(
    val kind: HomeSortKind
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortAndFilterRow(
    sort: HomeSort,
    onSortChange: (HomeSort) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Get the current sort label
    val sortLabel = when (sort.kind) {
        HomeSortKind.NEWEST_FIRST -> "Newest first"
        HomeSortKind.OLDEST_FIRST -> "Oldest first"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Sort by",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            FilterChip(
                selected = false,
                onClick = { sortMenuExpanded = true },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(sortLabel)
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Open sort options",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                }
            )

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Newest first") },
                    onClick = {
                        sortMenuExpanded = false
                        onSortChange(HomeSort(HomeSortKind.NEWEST_FIRST))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Oldest first") },
                    onClick = {
                        sortMenuExpanded = false
                        onSortChange(HomeSort(HomeSortKind.OLDEST_FIRST))
                    }
                )
            }
        }
    }
}