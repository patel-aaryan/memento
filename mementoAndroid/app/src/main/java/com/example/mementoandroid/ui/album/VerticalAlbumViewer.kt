package com.example.mementoandroid.ui.album


import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VerticalAlbumViewer(
    photos: List<AlbumPhotoUi>,
    initialPhotoId: String,
    onBack: () -> Unit,
    onLikePhoto: (String) -> Unit = {},
    onSharePhoto: (String) -> Unit = {},
    onMoreOptions: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val initialIndex = photos.indexOfFirst { it.id == initialPhotoId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { photos.size }
    )

    var showControls by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val currentPhoto by remember {
        derivedStateOf { photos.getOrNull(pagerState.currentPage) }
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Vertical Pager for vertical scrolling
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos[page]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                                // Reset auto-hide timer
                                if (showControls) {
                                    scope.launch {
                                        delay(3000)
                                        showControls = false
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Display image from any source (imageRes, uri, or imageUrl)
                PhotoImage(
                    photo = photo,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Photo counter
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color.Black.copy(alpha = 0.5f),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${photos.size}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 16.sp
                    )
                }

                // More options
                IconButton(
                    onClick = { currentPhoto?.let { onMoreOptions(it.id) } },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
                }
            }
        }

        // Bottom Info - Animated visibility
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            currentPhoto?.let { photo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Photo info
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = photo.caption ?: "Untitled",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontSize = 20.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (photo.takenAt != null) {
                            Text(
                                text = "Taken: ${photo.takenAt}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        } else if (photo.dateAdded != null) {
                            Text(
                                text = "Added: ${photo.dateAdded}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        }

                        if (photo.latitude != null && photo.longitude != null) {
                            Text(
                                text = "📍 Location available",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }

                        if (photo.audioUrl != null) {
                            Text(
                                text = "🎵 Audio attached",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Action buttons
                    Row {
                        // Like button (you can add isLiked to your data class if needed)
                        IconButton(
                            onClick = { onLikePhoto(photo.id) },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { onSharePhoto(photo.id) },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoImage(
    photo: AlbumPhotoUi,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current

    when {
        photo.imageRes != null -> {
            // Load from drawable resource
            Image(
                painter = painterResource(id = photo.imageRes),
                contentDescription = photo.caption,
                contentScale = contentScale,
                modifier = modifier
            )
        }
        photo.uri != null -> {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photo.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.caption,
                contentScale = contentScale,
                modifier = modifier
            )
        }
        photo.imageUrl != null -> {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photo.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.caption,
                contentScale = contentScale,
                modifier = modifier
            )
        }
        else -> {
            Box(
                modifier = modifier
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No image",
                    color = Color.White
                )
            }
        }
    }
}