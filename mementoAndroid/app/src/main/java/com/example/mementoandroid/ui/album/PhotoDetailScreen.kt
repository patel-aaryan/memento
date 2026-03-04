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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
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
import android.util.Log
import com.example.mementoandroid.api.BackendClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: AlbumPhotoUi,
    albumName: String,
    mock: PhotoDetailMock,
    onBack: () -> Unit,
    onSave: (caption: String, takenAt: String?, latitude: Double?, longitude: Double?, audioFilePath: String?) -> Unit = { _, _, _, _, _ -> },
    onDeletePhoto: () -> Unit = {},
    onDeleteAudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var notesText by remember(photo.id) { mutableStateOf(photo.caption?.takeIf { it.isNotBlank() } ?: mock.caption) }
    var displayedDateTime by remember(photo.id) { mutableStateOf(mock.dateTime) }
    var displayedLocation by remember(photo.id) { mutableStateOf(mock.location) }
    var isEditMode by remember(photo.id) { mutableStateOf(false) }
    var editedTakenAt by remember(photo.id) { mutableStateOf(photo.takenAt ?: "") }
    // Location search text; autocomplete suggestions and selected lat/lng for save
    var editedLocationText by remember(photo.id) { mutableStateOf("") }
    var locationSuggestions by remember(photo.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var editedLatitude by remember(photo.id) { mutableStateOf<Double?>(null) }
    var editedLongitude by remember(photo.id) { mutableStateOf<Double?>(null) }
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

    LaunchedEffect(photo.id, photo.uri, photo.imageUrl, photo.latitude, photo.longitude, photo.dateAdded, photo.takenAt) {
        displayedDateTime = mock.dateTime
        displayedLocation = mock.location
        val uri = photo.uri
        if (uri != null && photo.imageUrl == null) {
            val md = withContext(Dispatchers.IO) { extractPhotoMetadata(context, uri) }
            md?.let {
                formatPhotoMetadataDateTime(it.dateTimeOriginal ?: it.dateTaken)?.let { displayedDateTime = it }
                if (it.latitude != null && it.longitude != null) {
                    // First show coordinate-based location as a quick fallback
                    displayedLocation = formatPhotoMetadataLocation(it.latitude, it.longitude)

                    // Then try to resolve a human-readable place name from the backend
                    val result = BackendClient.get(
                        path = "/location/nearest-place?lat=${it.latitude}&lng=${it.longitude}"
                    )
                    result.onSuccess { json ->
                        val placeName = json.optString("place_name", "")
                        if (placeName.isNotBlank()) {
                            displayedLocation = placeName
                        }
                    }.onFailure { e ->
                        Log.w("PhotoDetailScreen", "Failed to fetch place name", e)
                    }
                }
            }
        } else if (photo.imageUrl != null && photo.latitude != null && photo.longitude != null) {
            // For backend images that already have stored coordinates, use them to look up a place name.
            // Show a simple coordinate-based location as an immediate fallback.
            displayedLocation = formatPhotoMetadataLocation(photo.latitude, photo.longitude)

            val result = BackendClient.get(
                path = "/location/nearest-place?lat=${photo.latitude}&lng=${photo.longitude}"
            )
            result.onSuccess { json ->
                val placeName = json.optString("place_name", "")
                if (placeName.isNotBlank()) {
                    displayedLocation = placeName
                }
            }.onFailure { e ->
                Log.w("PhotoDetailScreen", "Failed to fetch place name for backend image", e)
            }
        }
        if (displayedDateTime == mock.dateTime) {
            formatBackendDateTime(photo.takenAt)?.let { displayedDateTime = it }
                ?: formatBackendDateTime(photo.dateAdded)?.let { displayedDateTime = it }
        }
    }

    // Debounced autocomplete when user types in location search (edit mode)
    LaunchedEffect(isEditMode, editedLocationText) {
        if (!isEditMode || editedLocationText.isBlank()) {
            withContext(Dispatchers.Main) { locationSuggestions = emptyList() }
            return@LaunchedEffect
        }
        delay(300)
        val query = editedLocationText
        val path = "/location/autocomplete?q=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}"
        Log.d("PhotoDetailScreen", "Calling autocomplete: $path")
        val result = BackendClient.get(path)
        withContext(Dispatchers.Main) {
            result.onSuccess { json ->
                val arr = json.optJSONArray("predictions") ?: JSONArray()
                val list = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val desc = o.optString("description", "")
                    val placeId = o.optString("place_id", "")
                    if (desc.isNotBlank() && placeId.isNotBlank()) Pair(desc, placeId) else null
                }
                if (editedLocationText == query) {
                    Log.d("PhotoDetailScreen", "Autocomplete returned ${list.size} suggestions")
                    locationSuggestions = list
                }
            }.onFailure { e ->
                Log.w("PhotoDetailScreen", "Autocomplete failed", e)
                if (editedLocationText == query) locationSuggestions = emptyList()
            }
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
                                text = { Text(if (isEditMode) "Done" else "Edit") },
                                onClick = {
                                    menuExpanded = false
                                    if (isEditMode) {
                                        isEditMode = false
                                    } else {
                                        editedTakenAt = photo.takenAt ?: ""
                                        editedLocationText = displayedLocation ?: ""
                                        isEditMode = true
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = null
                                    )
                                }
                            )
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
                // Date & time (editable in edit mode)
                if (isEditMode) {
                    Text(
                        text = "Date & time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editedTakenAt,
                        onValueChange = { editedTakenAt = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. 2025-01-15T14:30:00") }
                    )
                } else {
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
                }

                // Location (editable in edit mode — search with Google Places autocomplete dropdown)
                if (isEditMode) {
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editedLocationText,
                        onValueChange = { editedLocationText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search for a location (e.g. city or address)") }
                    )
                    if (locationSuggestions.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                locationSuggestions.forEach { (description, placeId) ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    val detailsPath = "/location/place-details?place_id=${URLEncoder.encode(placeId, StandardCharsets.UTF_8.name())}"
                                                    BackendClient.get(detailsPath).onSuccess { details ->
                                                        val lat = details.optDouble("latitude", Double.NaN)
                                                        val lng = details.optDouble("longitude", Double.NaN)
                                                        val name = details.optString("display_name", "").takeIf { it.isNotBlank() } ?: description
                                                        if (!lat.isNaN() && !lng.isNaN()) {
                                                            editedLatitude = lat
                                                            editedLongitude = lng
                                                            displayedLocation = name
                                                            editedLocationText = name
                                                            locationSuggestions = emptyList()
                                                        }
                                                    }.onFailure { e ->
                                                        Log.w("PhotoDetailScreen", "Place details failed", e)
                                                        locationSuggestions = emptyList()
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (displayedLocation != null) {
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

                // Audio: only "has recording" when we have real local or persisted URL (not blank/"null")
                val hasLocalRecording = !recordedAudioPath.isNullOrBlank()
                val hasPersistedAudio = !photo.audioUrl.isNullOrBlank() && photo.audioUrl != "null"
                val hasRecording = hasLocalRecording || hasPersistedAudio
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when {
                                isRecording -> {
                                    scope.launch { stopChannel.send(Unit) }
                                }
                                hasRecording -> {
                                    if (mediaPlayer?.isPlaying == true) {
                                        mediaPlayerHolder.current?.release()
                                        mediaPlayerHolder.current = null
                                        mediaPlayer = null
                                        isPlaying = false
                                    } else {
                                        val dataSource = when {
                                            hasLocalRecording -> recordedAudioPath!!
                                            hasPersistedAudio -> photo.audioUrl!!
                                            else -> null
                                        }
                                        if (dataSource.isNullOrBlank()) return@clickable
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
                            // In edit mode, allow deleting audio when there is one
                            if (isEditMode && hasRecording) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Delete audio",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable {
                                        recordedAudioPath = null
                                        onDeleteAudio()
                                    }
                                )
                            }
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
                            val takenAt = editedTakenAt.takeIf { it.isNotBlank() }
                            val lat = editedLatitude ?: photo.latitude
                            val lng = editedLongitude ?: photo.longitude
                            withContext(Dispatchers.Main) {
                                onSave(notesText, takenAt, lat, lng, recordedAudioPath)
                                if (isEditMode) {
                                    isEditMode = false
                                    if (takenAt != null) displayedDateTime = formatBackendDateTime(takenAt) ?: takenAt
                                    if (lat != null && lng != null) displayedLocation = displayedLocation ?: formatPhotoMetadataLocation(lat, lng)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isEditMode) "Save changes" else "Save")
                }
            }
        }
    }
}
