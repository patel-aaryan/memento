package com.example.mementoandroid.ui.album

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.util.extractPhotoMetadata
import com.example.mementoandroid.util.formatBackendDateTime
import com.example.mementoandroid.util.formatPhotoMetadataDateTime
import com.example.mementoandroid.util.formatPhotoMetadataLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FEED_IMAGE_HEIGHT_DP = 320

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFeedScreen(
    photos: List<AlbumPhotoUi>,
    initialPhotoId: String,
    albumName: String,
    getMock: (String, String) -> PhotoDetailMock,
    onBack: () -> Unit,
    onEditPhoto: (AlbumPhotoUi) -> Unit,
    canEditPhoto: (AlbumPhotoUi) -> Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val initialIndex = photos.indexOfFirst { it.id == initialPhotoId }.takeIf { it >= 0 } ?: 0

    LaunchedEffect(photos.size, initialPhotoId) {
        if (photos.isNotEmpty() && initialIndex in photos.indices) {
            listState.scrollToItem(initialIndex)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(
                count = photos.size,
                key = { photos[it].id }
            ) { index ->
                val photo = photos[index]
                val mock = getMock(albumName, photo.id)
                PhotoFeedItemContent(
                    photo = photo,
                    albumName = albumName,
                    mock = mock,
                    canEditPhoto = canEditPhoto(photo),
                    onEditPhoto = { onEditPhoto(photo) }
                )
            }
        }
    }
}

@Composable
private fun PhotoFeedItemContent(
    photo: AlbumPhotoUi,
    albumName: String,
    mock: PhotoDetailMock,
    canEditPhoto: Boolean,
    onEditPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var displayedDateTime by remember(photo.id) { mutableStateOf(mock.dateTime) }
    var displayedLocation by remember(photo.id) { mutableStateOf(mock.location) }
    var notesText by remember(photo.id) {
        mutableStateOf(photo.caption?.takeIf { it.isNotBlank() } ?: mock.caption)
    }
    var mediaPlayer by remember(photo.id) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(photo.id) { mutableStateOf(false) }
    val mediaPlayerHolder = remember(photo.id) { object { var current: MediaPlayer? = null } }

    LaunchedEffect(photo.id, photo.uri, photo.imageUrl, photo.latitude, photo.longitude, photo.dateAdded, photo.takenAt) {
        displayedDateTime = mock.dateTime
        displayedLocation = mock.location
        val uri = photo.uri
        if (uri != null && photo.imageUrl == null) {
            val md = withContext(Dispatchers.IO) { extractPhotoMetadata(context, uri) }
            md?.let {
                formatPhotoMetadataDateTime(it.dateTimeOriginal ?: it.dateTaken)?.let { displayedDateTime = it }
                if (it.latitude != null && it.longitude != null) {
                    displayedLocation = formatPhotoMetadataLocation(it.latitude, it.longitude)
                    val result = BackendClient.get(
                        path = "/location/nearest-place?lat=${it.latitude}&lng=${it.longitude}"
                    )
                    result.onSuccess { json ->
                        val placeName = json.optString("place_name", "")
                        if (placeName.isNotBlank()) displayedLocation = placeName
                    }.onFailure { e -> Log.w("PhotoFeedScreen", "Failed to fetch place name", e) }
                }
            }
        } else if (photo.imageUrl != null && photo.latitude != null && photo.longitude != null) {
            displayedLocation = formatPhotoMetadataLocation(photo.latitude, photo.longitude)
            val result = BackendClient.get(
                path = "/location/nearest-place?lat=${photo.latitude}&lng=${photo.longitude}"
            )
            result.onSuccess { json ->
                val placeName = json.optString("place_name", "")
                if (placeName.isNotBlank()) displayedLocation = placeName
            }.onFailure { e -> Log.w("PhotoFeedScreen", "Failed to fetch place name", e) }
        }
        if (displayedDateTime == mock.dateTime) {
            formatBackendDateTime(photo.takenAt)?.let { displayedDateTime = it }
                ?: formatBackendDateTime(photo.dateAdded)?.let { displayedDateTime = it }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerHolder.current?.release()
            mediaPlayerHolder.current = null
            mediaPlayer = null
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(32.dp))

        // Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FEED_IMAGE_HEIGHT_DP.dp)
        ) {
            when {
                photo.imageUrl != null -> AsyncImage(
                    model = photo.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(FEED_IMAGE_HEIGHT_DP.dp)
                )
                photo.uri != null -> AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(FEED_IMAGE_HEIGHT_DP.dp)
                )
                else -> Image(
                    painter = painterResource(id = photo.imageRes!!),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(FEED_IMAGE_HEIGHT_DP.dp)
                )
            }
        }

        // Metadata (view-only): date, location, uploader, voice (if any), notes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = displayedDateTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (canEditPhoto) {
                    TextButton(onClick = onEditPhoto) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Edit")
                    }
                }
            }

            if (displayedLocation != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = displayedLocation!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (mock.uploaderName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Added by ${mock.uploaderName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val hasPersistedAudio = !photo.audioUrl.isNullOrBlank() && photo.audioUrl != "null"
            if (hasPersistedAudio) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (mediaPlayer?.isPlaying == true) {
                                mediaPlayerHolder.current?.release()
                                mediaPlayerHolder.current = null
                                mediaPlayer = null
                                isPlaying = false
                            } else {
                                val dataSource = photo.audioUrl!!
                                mediaPlayerHolder.current?.release()
                                mediaPlayerHolder.current = null
                                val player = MediaPlayer().apply {
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                            .build()
                                    )
                                    setDataSource(dataSource)
                                    prepareAsync()
                                    setOnPreparedListener { start(); isPlaying = true }
                                    setOnCompletionListener {
                                        release()
                                        mediaPlayerHolder.current = null
                                        mediaPlayer = null
                                        isPlaying = false
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        release()
                                        mediaPlayerHolder.current = null
                                        mediaPlayer = null
                                        isPlaying = false
                                        true
                                    }
                                }
                                mediaPlayerHolder.current = player
                                mediaPlayer = player
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice note",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isPlaying) "Playing…" else "Tap to play",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text(
                text = "Notes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = notesText.ifBlank { "No notes" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
