package com.example.mementoandroid.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.ui.album.AlbumUi

// Define the HomeAddAction sealed class here if it's not in a separate file
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    albums: List<AlbumUi>,
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit,
    onAlbumClick: (Int) -> Unit = {},
    onAction: (HomeAddAction) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val filteredItems = remember(searchQuery, albums) {
        if (searchQuery.isBlank()) albums
        else albums.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar with FABs and search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Profile FAB
                FloatingActionButton(
                    onClick = onProfileClick,
                    modifier = Modifier.size(48.dp),
                    content = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    singleLine = true,
                )

                // Add FAB
                FloatingActionButton(
                    onClick = {
                        showAddSheet = true
                    },
                    modifier = Modifier.size(48.dp),
                    content = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
            }

            // Albums list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(
                    items = filteredItems,
                    key = { it.id },
                ) { item ->
                    ListItem(
                        modifier = Modifier.clickable { onAlbumClick(item.id) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        headlineContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(item.name)
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }

        // Bottom sheet - now defined inline
        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Button(
                        onClick = {
                            showAddSheet = false
                            onAction(HomeAddAction.Camera)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Camera")
                    }

                    Button(
                        onClick = {
                            showAddSheet = false
                            onAction(HomeAddAction.Photos)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Photos")
                    }

                    Button(
                        onClick = {
                            showAddSheet = false
                            onAction(HomeAddAction.MakeAlbum)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Make an Album")
                    }
                }
            }
        }
    }
}
