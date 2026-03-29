package com.example.mementoandroid.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.mementoandroid.util.AlbumViewStore
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mementoandroid.util.orDefaultAvatar
import com.example.mementoandroid.ui.album.AlbumUi
import com.example.mementoandroid.ui.album.AlbumPhotoUi
import com.example.mementoandroid.ui.album.standaloneMementoTitle
import com.example.mementoandroid.ui.album.titleForHome
import com.example.mementoandroid.ui.home.components.AlbumLogo
import com.example.mementoandroid.ui.home.components.SortAndFilterRow
import com.example.mementoandroid.ui.home.components.HomeSort
import com.example.mementoandroid.ui.home.components.HomeSortKind
import com.example.mementoandroid.ui.home.components.HomeFilterKind

/** Home list item: either a standalone photo (click opens photo detail) or an album (click opens album). */
sealed class HomeItem {
    data class StandalonePhoto(val photo: AlbumPhotoUi, val albumId: Int) : HomeItem()
    data class Album(val album: AlbumUi) : HomeItem()
}

private fun HomeItem.displayTitle(): String = when (this) {
    is HomeItem.StandalonePhoto -> photo.standaloneMementoTitle()
    is HomeItem.Album -> album.titleForHome()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    albums: List<AlbumUi>,
    standalonePhotos: List<AlbumPhotoUi> = emptyList(),
    myPhotosAlbumId: Int? = null,
    /** True while the first albums + mementos fetch is in progress; shows skeleton instead of empty state. */
    isHomeDataLoading: Boolean = false,
    modifier: Modifier = Modifier,
    profilePictureUrl: String? = null,
    albumSuggestion: SuggestedAlbumUi? = null,
    albumSuggestionBusy: Boolean = false,
    onAcceptAlbumSuggestion: () -> Unit = {},
    onRejectAlbumSuggestion: () -> Unit = {},
    onProfileClick: () -> Unit,
    onAlbumClick: (Int) -> Unit = {},
    onStandalonePhotoClick: (AlbumPhotoUi) -> Unit = {},
    onAction: (HomeAddAction) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val initialGridView = remember { AlbumViewStore.getIsGridView() }
    var showTileView by remember(initialGridView) { mutableStateOf(initialGridView) }
    var homeSort by remember { mutableStateOf(HomeSort(HomeSortKind.NEWEST_FIRST)) }
    var homeFilter by remember { mutableStateOf(HomeFilterKind.ALL) }

    LaunchedEffect(showTileView) {
        AlbumViewStore.saveIsGridView(showTileView)
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val albumsWithoutMyPhotos = remember(albums, myPhotosAlbumId) {
        if (myPhotosAlbumId == null) albums else albums.filter { it.id != myPhotosAlbumId }
    }
    /** True if there is at least one standalone memento or album to potentially show (sort/filter only then). */
    val hasHomeContent = remember(standalonePhotos, albumsWithoutMyPhotos) {
        standalonePhotos.isNotEmpty() || albumsWithoutMyPhotos.isNotEmpty()
    }
    val homeItems = remember(standalonePhotos, myPhotosAlbumId, albumsWithoutMyPhotos, homeSort, homeFilter) {
        val aid = myPhotosAlbumId ?: 0
        val sortedPhotos = standalonePhotos.sortedPhotosForHome(homeSort)
        val sortedAlbums = albumsWithoutMyPhotos.sortedAlbumsForHome(homeSort)
        val photos = sortedPhotos.map { HomeItem.StandalonePhoto(it, aid) }
        val albumItems = sortedAlbums.map { HomeItem.Album(it) }
        when (homeFilter) {
            HomeFilterKind.ALL -> photos + albumItems
            HomeFilterKind.MY_PHOTOS -> photos
            HomeFilterKind.SHARED_ALBUMS -> albumItems
        }
    }

    val filteredItems = remember(searchQuery, homeItems) {
        if (searchQuery.isBlank()) homeItems
        else homeItems.filter { it.displayTitle().contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Profile avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onProfileClick)
                ) {
                    AsyncImage(
                        model = profilePictureUrl.orDefaultAvatar(),
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

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

            if (hasHomeContent) {
                SortAndFilterRow(
                    sort = homeSort,
                    onSortChange = { homeSort = it },
                    filter = homeFilter,
                    onFilterChange = { homeFilter = it },
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 2.dp)
                )
            }

            if (albumSuggestion != null) {
                AlbumSuggestionBanner(
                    suggestion = albumSuggestion,
                    busy = albumSuggestionBusy,
                    onAccept = onAcceptAlbumSuggestion,
                    onReject = onRejectAlbumSuggestion,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isHomeDataLoading -> {
                        HomeFeedLoadingPlaceholder(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    filteredItems.isEmpty() && searchQuery.isBlank() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Tap the + button to start adding mementos and albums!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    filteredItems.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No matches for your search",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp),
                            )
                        }
                    }
                    showTileView -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(
                                items = filteredItems,
                                key = { item -> when (item) { is HomeItem.StandalonePhoto -> "photo-${item.photo.id}"; is HomeItem.Album -> "album-${item.album.id}" } },
                            ) { item ->
                                when (item) {
                                    is HomeItem.StandalonePhoto -> StandalonePhotoTile(
                                        photo = item.photo,
                                        title = item.displayTitle(),
                                        onClick = { onStandalonePhotoClick(item.photo) },
                                    )
                                    is HomeItem.Album -> AlbumTile(
                                        album = item.album,
                                        onClick = { onAlbumClick(item.album.id) },
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(
                                items = filteredItems,
                                key = { item -> when (item) { is HomeItem.StandalonePhoto -> "photo-${item.photo.id}"; is HomeItem.Album -> "album-${item.album.id}" } },
                            ) { item ->
                                when (item) {
                                    is HomeItem.StandalonePhoto -> ListItem(
                                        modifier = Modifier.clickable { onStandalonePhotoClick(item.photo) },
                                        colors = ListItemDefaults.colors(
                                            containerColor = Color.Transparent,
                                        ),
                                        leadingContent = {
                                            StandalonePhotoThumbnail(photo = item.photo, size = 32.dp)
                                        },
                                        headlineContent = {
                                            Text(
                                                item.displayTitle(),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                    is HomeItem.Album -> ListItem(
                                        modifier = Modifier.clickable { onAlbumClick(item.album.id) },
                                        colors = ListItemDefaults.colors(
                                            containerColor = Color.Transparent,
                                        ),
                                        leadingContent = {
                                            AlbumLogo(
                                                album = item.album,
                                                size = 32.dp,
                                            )
                                        },
                                        headlineContent = {
                                            Text(
                                                item.album.titleForHome(),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        },
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        // List/tile view toggle FAB (bottom right)
        FloatingActionButton(
            onClick = { showTileView = !showTileView },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = if (showTileView) Icons.Default.ViewList else Icons.Default.GridView,
                contentDescription = if (showTileView) "Show list view" else "Show tile view"
            )
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
                        color = MaterialTheme.colorScheme.onSurface,
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

/** Shown while albums + mementos are loading so the empty-state hint does not flash. */
@Composable
private fun HomeFeedLoadingPlaceholder(modifier: Modifier = Modifier) {
    val skeletonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.padding(bottom = 20.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(skeletonColor),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(skeletonColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun StandalonePhotoTile(
    photo: AlbumPhotoUi,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            photo.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun StandalonePhotoThumbnail(
    photo: AlbumPhotoUi,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        photo.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AlbumTile(
    album: AlbumUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlbumLogo(
            album = album,
            modifier = Modifier.fillMaxWidth(),
            fillWidth = true,
        )
        Text(
            text = album.titleForHome(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
        )
    }
}

fun List<AlbumUi>.sortedAlbumsForHome(sort: HomeSort): List<AlbumUi> {
    return when (sort.kind) {
        HomeSortKind.NEWEST_FIRST -> sortedByDescending { it.id }
        HomeSortKind.OLDEST_FIRST -> sortedBy { it.id }
    }
}

fun List<AlbumPhotoUi>.sortedPhotosForHome(sort: HomeSort): List<AlbumPhotoUi> {
    return when (sort.kind) {
        HomeSortKind.NEWEST_FIRST -> sortedByDescending { it.dateAdded ?: "" }
        HomeSortKind.OLDEST_FIRST -> sortedBy { it.dateAdded ?: "" }
    }
}