package com.example.mementoandroid.ui.album

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.mementoandroid.util.extractPhotoMetadata
import com.example.mementoandroid.util.recordAudioToCache
import com.example.mementoandroid.util.formatBackendDateTime
import com.example.mementoandroid.util.formatPhotoMetadataDateTime
import com.example.mementoandroid.util.formatPhotoMetadataLocation
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.Image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: AlbumPhotoUi,
    albumName: String,
    mock: PhotoDetailMock,
    onBack: () -> Unit,
    onSave: (caption: String) -> Unit = {},
    onDeletePhoto: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var notesText by remember(photo.id) { mutableStateOf(photo.caption?.takeIf { it.isNotBlank() } ?: mock.caption) }
    var displayedDateTime by remember(photo.id) { mutableStateOf(mock.dateTime) }
    var displayedLocation by remember(photo.id) { mutableStateOf(mock.location) }
    var recordedAudioPath by remember(photo.id) { mutableStateOf<String?>(null) }
    var isRecording by remember(photo.id) { mutableStateOf(false) }
    var isPlaying by remember(photo.id) { mutableStateOf(false) }
    var mediaPlayer by remember(photo.id) { mutableStateOf<MediaPlayer?>(null) }
    val mediaPlayerHolder = remember(photo.id) { object { var current: MediaPlayer? = null } }
    val stopChannel = remember(photo.id) { Channel<Unit>(Channel.CONFLATED) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isRecording = true
            scope.launch {
                val path = recordAudioToCache(context, photo.id, stopChannel)
                withContext(Dispatchers.Main) {
                    isRecording = false
                    recordedAudioPath = path
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerHolder.current?.release()
            mediaPlayerHolder.current = null
            mediaPlayer = null
        }
    }

    fun startRecording() {
        isRecording = true
        scope.launch {
            val path = recordAudioToCache(context, photo.id, stopChannel)
            withContext(Dispatchers.Main) {
                isRecording = false
                recordedAudioPath = path
            }
        }
    }

    LaunchedEffect(photo.id, photo.uri, photo.imageUrl, photo.dateAdded, photo.takenAt) {
        displayedDateTime = mock.dateTime
        displayedLocation = mock.location
        val uri = photo.uri
        if (uri != null && photo.imageUrl == null) {
            val md = withContext(Dispatchers.IO) { extractPhotoMetadata(context, uri) }
            md?.let {
                formatPhotoMetadataDateTime(it.dateTimeOriginal ?: it.dateTaken)?.let { displayedDateTime = it }
                if (it.latitude != null && it.longitude != null) {
                    displayedLocation = formatPhotoMetadataLocation(it.latitude, it.longitude)
                }
            }
        }
        if (displayedDateTime == mock.dateTime) {
            formatBackendDateTime(photo.takenAt)?.let { displayedDateTime = it }
                ?: formatBackendDateTime(photo.dateAdded)?.let { displayedDateTime = it }
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
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menuExpanded = false
                                    onDeletePhoto()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Top padding so image and content sit centered / just below center
            Spacer(modifier = Modifier.height(32.dp))

            // Main image — full width
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                when {
                    photo.imageUrl != null -> AsyncImage(
                        model = photo.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    )
                    photo.uri != null -> AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    )
                    else -> Image(
                        painter = painterResource(id = photo.imageRes!!),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    )
                }
            }

            // Metadata section: Date → Location → Audio → Notes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Date & time
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                // Location (from metadata if available, else mock)
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

                // Uploader (group albums only) — between location and audio
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

                // Audio recording (underneath location)
                val hasRecording = recordedAudioPath != null
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when {
                                isRecording -> {
                                    scope.launch { stopChannel.send(Unit) }
                                }
                                hasRecording -> {
                                    val path = recordedAudioPath ?: return@clickable
                                    if (mediaPlayer?.isPlaying == true) {
                                        mediaPlayerHolder.current?.release()
                                        mediaPlayerHolder.current = null
                                        mediaPlayer = null
                                        isPlaying = false
                                    } else {
                                        mediaPlayerHolder.current?.release()
                                        mediaPlayerHolder.current = null
                                        val player = MediaPlayer().apply {
                                            setAudioAttributes(
                                                AudioAttributes.Builder()
                                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                    .build()
                                            )
                                            setDataSource(path)
                                            prepareAsync()
                                            setOnPreparedListener { start(); isPlaying = true }
                                            setOnCompletionListener {
                                                release()
                                                mediaPlayerHolder.current = null
                                                mediaPlayer = null
                                                isPlaying = false
                                            }
                                        }
                                        mediaPlayerHolder.current = player
                                        mediaPlayer = player
                                    }
                                }
                                else -> {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        startRecording()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isRecording -> Icons.Default.Stop
                                hasRecording -> Icons.Default.PlayCircle
                                else -> Icons.Default.Mic
                            },
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    isRecording -> "Recording…"
                                    hasRecording -> "Voice note"
                                    else -> "No voice note"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    isRecording -> "Tap to stop"
                                    hasRecording -> if (isPlaying) "Playing…" else "Tap to play"
                                    else -> "Tap to record"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Notes (underneath audio — larger textbox for associated notes)
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    minLines = 4,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text("Add notes for this photo...") }
                )
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        scope.launch {
                            delay(150)
                            withContext(Dispatchers.Main) { onSave(notesText) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }
        }
    }
}
