//package com.example.mementoandroid.ui.home.components
//
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.ArrowDropDown
//import androidx.compose.material3.DropdownMenu
//import androidx.compose.material3.DropdownMenuItem
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.FilterChip
//import androidx.compose.material3.FilterChipDefaults
//import androidx.compose.material3.Icon
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//
//enum class HomeSortKind {
//    NEWEST_FIRST,
//    OLDEST_FIRST
//}
//
//data class HomeSort(
//    val kind: HomeSortKind
//)
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun SortAndFilterRow(
//    sort: HomeSort,
//    onSortChange: (HomeSort) -> Unit,
//    modifier: Modifier = Modifier
//) {
//    var sortMenuExpanded by remember { mutableStateOf(false) }
//
//    // Get the current sort label
//    val sortLabel = when (sort.kind) {
//        HomeSortKind.NEWEST_FIRST -> "Newest first"
//        HomeSortKind.OLDEST_FIRST -> "Oldest first"
//    }
//
//    Row(
//        modifier = modifier
//            .fillMaxWidth()
//            .padding(horizontal = 16.dp, vertical = 8.dp),
//        verticalAlignment = Alignment.CenterVertically,
//        horizontalArrangement = Arrangement.SpaceBetween
//    ) {
//        Text(
//            text = "Sort by",
//            style = MaterialTheme.typography.labelMedium,
//            color = MaterialTheme.colorScheme.onSurfaceVariant
//        )
//
//        Box {
//            FilterChip(
//                selected = false,
//                onClick = { sortMenuExpanded = true },
//                label = {
//                    Row(
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.spacedBy(4.dp)
//                    ) {
//                        Text(sortLabel)
//                        Icon(
//                            Icons.Default.ArrowDropDown,
//                            contentDescription = "Open sort options",
//                            modifier = Modifier.size(FilterChipDefaults.IconSize)
//                        )
//                    }
//                }
//            )
//
//            DropdownMenu(
//                expanded = sortMenuExpanded,
//                onDismissRequest = { sortMenuExpanded = false }
//            ) {
//                DropdownMenuItem(
//                    text = { Text("Newest first") },
//                    onClick = {
//                        sortMenuExpanded = false
//                        onSortChange(HomeSort(HomeSortKind.NEWEST_FIRST))
//                    }
//                )
//                DropdownMenuItem(
//                    text = { Text("Oldest first") },
//                    onClick = {
//                        sortMenuExpanded = false
//                        onSortChange(HomeSort(HomeSortKind.OLDEST_FIRST))
//                    }
//                )
//            }
//        }
//    }
//}

package com.example.mementoandroid.ui.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

enum class HomeFilterKind {
    ALL,
    SHARED_ALBUMS,
    MY_PHOTOS
}

data class HomeSort(
    val kind: HomeSortKind
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortAndFilterRow(
    sort: HomeSort,
    onSortChange: (HomeSort) -> Unit,
    filter: HomeFilterKind,
    onFilterChange: (HomeFilterKind) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val filterLabel = when (filter) {
        HomeFilterKind.ALL -> "All"
        HomeFilterKind.SHARED_ALBUMS -> "Shared albums"
        HomeFilterKind.MY_PHOTOS -> "My Photos"
    }
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Sort by",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        FilterChip(
            selected = sort.kind == HomeSortKind.NEWEST_FIRST,
            onClick = { onSortChange(HomeSort(HomeSortKind.NEWEST_FIRST)) },
            label = { Text("Newest first", color = MaterialTheme.colorScheme.onSurface) },
        )

        FilterChip(
            selected = sort.kind == HomeSortKind.OLDEST_FIRST,
            onClick = { onSortChange(HomeSort(HomeSortKind.OLDEST_FIRST)) },
            label = { Text("Oldest first", color = MaterialTheme.colorScheme.onSurface) },
        )

        Text(
            text = "Show",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )

        Box {
            FilterChip(
                selected = false,
                onClick = { filterMenuExpanded = true },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(filterLabel, color = MaterialTheme.colorScheme.onSurface)
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Filter by section",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
            )
            DropdownMenu(
                expanded = filterMenuExpanded,
                onDismissRequest = { filterMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("All", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        filterMenuExpanded = false
                        onFilterChange(HomeFilterKind.ALL)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Shared albums", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        filterMenuExpanded = false
                        onFilterChange(HomeFilterKind.SHARED_ALBUMS)
                    }
                )
                DropdownMenuItem(
                    text = { Text("My Photos", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        filterMenuExpanded = false
                        onFilterChange(HomeFilterKind.MY_PHOTOS)
                    }
                )
            }
        }
    }
}