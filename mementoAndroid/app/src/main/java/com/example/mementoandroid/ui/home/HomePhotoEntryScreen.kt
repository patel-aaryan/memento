package com.example.mementoandroid.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.util.AuthTokenStore
import com.example.mementoandroid.util.CloudinaryHelper
import com.example.mementoandroid.util.buildIsoFromDateAndTime
import com.example.mementoandroid.util.formatBackendDateTime
import com.example.mementoandroid.util.formatDateMillis
import com.example.mementoandroid.util.formatPhotoMetadataLocation
import com.example.mementoandroid.util.formatTime
import com.example.mementoandroid.util.datePickerMillisToLocalMidnight
import com.example.mementoandroid.util.instantToLocalDateMillis
import com.example.mementoandroid.util.parseIsoToHourMinute
import com.example.mementoandroid.util.parseIsoToMillis
import com.example.mementoandroid.util.recordAudioToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState

data class HomePhotoMetadata(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: String? = null,
    val imageUri: Uri,
    val imageFile: File
)

private const val NEW_PHOTO_AUDIO_ID = "new_photo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePhotoEntryScreen(
    metadata: HomePhotoMetadata,
    onBack: () -> Unit,
    onPhotoSaved: (addedToAlbumId: Int?) -> Unit,
    targetAlbumId: Int? = null,
    albumName: String? = null, // Add album name parameter
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var caption by remember(metadata.imageUri) { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // Date & location from metadata, with optional manual override
    var displayedLocation by remember(metadata.imageUri) { mutableStateOf<String?>(null) }
    var displayedDateTime by remember(metadata.imageUri) { mutableStateOf<String?>(null) }
    var isEditingLocation by remember(metadata.imageUri) { mutableStateOf(false) }
    var isEditingTimestamp by remember(metadata.imageUri) { mutableStateOf(false) }
    var editedLocationText by remember(metadata.imageUri) { mutableStateOf("") }
    var locationSuggestions by remember(metadata.imageUri) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var editedLatitude by remember(metadata.imageUri) { mutableStateOf<Double?>(null) }
    var editedLongitude by remember(metadata.imageUri) { mutableStateOf<Double?>(null) }
    var editedTakenAt by remember(metadata.imageUri) { mutableStateOf("") }
    var showDatePickerDialog by remember(metadata.imageUri) { mutableStateOf(false) }
    var showTimePickerDialog by remember(metadata.imageUri) { mutableStateOf(false) }
    var pendingDateMillis by remember(metadata.imageUri) { mutableStateOf<Long?>(null) }
    var pendingHour by remember(metadata.imageUri) { mutableStateOf<Int?>(null) }
    var pendingMinute by remember(metadata.imageUri) { mutableStateOf<Int?>(null) }

    // Voice note (reuse PhotoDetailScreen pattern: record to cache, play, delete, re-record)
    var recordedAudioPath by remember(metadata.imageUri) { mutableStateOf<String?>(null) }
    var isRecording by remember(metadata.imageUri) { mutableStateOf(false) }
    var isPlaying by remember(metadata.imageUri) { mutableStateOf(false) }
    var mediaPlayer by remember(metadata.imageUri) { mutableStateOf<MediaPlayer?>(null) }
    val mediaPlayerHolder = remember(metadata.imageUri) { object { var current: MediaPlayer? = null } }
    val stopChannel = remember(metadata.imageUri) { Channel<Unit>(Channel.CONFLATED) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isRecording = true
            scope.launch {
                val path = recordAudioToCache(context, NEW_PHOTO_AUDIO_ID, stopChannel)
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
        }
    }

    fun startRecording() {
        isRecording = true
        scope.launch {
            val path = recordAudioToCache(context, NEW_PHOTO_AUDIO_ID, stopChannel)
            withContext(Dispatchers.Main) {
                isRecording = false
                recordedAudioPath = path
            }
        }
    }

    // Initial display: from metadata; also try to resolve a human-readable
    // location from the backend using coordinates (with city fallback).
    LaunchedEffect(metadata.latitude, metadata.longitude, metadata.takenAt) {
        val lat = metadata.latitude
        val lng = metadata.longitude
        if (lat != null && lng != null) {
            withContext(Dispatchers.Main) {
                displayedLocation = formatPhotoMetadataLocation(lat, lng)
            }
            // Try to resolve an automatic city/place name from coordinates.
            val result = BackendClient.get(
                path = "/location/reverse-geocode?lat=$lat&lng=$lng"
            )
            result.onSuccess { json ->
                val city = json.optString("city", "")
                if (city.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        displayedLocation = city
                    }
                }
            }.onFailure { e ->
                Log.w("HomePhotoEntryScreen", "Failed to reverse-geocode city", e)
            }
        } else {
            withContext(Dispatchers.Main) {
                displayedLocation = null
            }
        }
        displayedDateTime = metadata.takenAt?.let {
            formatBackendDateTime(it) ?: run {
                try {
                    val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(it)
                    date?.let { d -> SimpleDateFormat("MMM dd, yyyy · h:mm a", Locale.US).format(d) }
                } catch (_: Exception) { null }
            } ?: it
        } ?: null
    }

    // When entering timestamp edit mode, init pending date/time from current value
    LaunchedEffect(isEditingTimestamp) {
        if (isEditingTimestamp) {
            val source = editedTakenAt.takeIf { it.isNotBlank() } ?: metadata.takenAt
            val instant = parseIsoToMillis(source) ?: System.currentTimeMillis()
            pendingDateMillis = instantToLocalDateMillis(instant)
            val hm = parseIsoToHourMinute(source)
            if (hm != null) {
                pendingHour = hm.first
                pendingMinute = hm.second
            } else {
                val cal = Calendar.getInstance()
                pendingHour = cal.get(Calendar.HOUR_OF_DAY)
                pendingMinute = cal.get(Calendar.MINUTE)
            }
        }
    }

    // Debounced location autocomplete when user types
    LaunchedEffect(isEditingLocation, editedLocationText) {
        if (!isEditingLocation || editedLocationText.isBlank()) {
            withContext(Dispatchers.Main) { locationSuggestions = emptyList() }
            return@LaunchedEffect
        }
        delay(300)
        val query = editedLocationText
        val path = "/location/autocomplete?q=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}"
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
                if (editedLocationText == query) locationSuggestions = list
            }.onFailure {
                if (editedLocationText == query) locationSuggestions = emptyList()
            }
        }
    }

    val bitmap = remember(metadata.imageUri) {
        try {
            context.contentResolver.openInputStream(metadata.imageUri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadAndSave() {
        val token = AuthTokenStore.get() ?: return
        val lat = editedLatitude ?: metadata.latitude
        val lng = editedLongitude ?: metadata.longitude
        val takenAt = editedTakenAt.takeIf { it.isNotBlank() } ?: metadata.takenAt

        val imageUrl = withContext(Dispatchers.IO) {
            CloudinaryHelper.uploadImage(context, metadata.imageFile, token)
        }
        if (imageUrl == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to upload image", Toast.LENGTH_SHORT).show()
            }
            return
        }

        var audioUrl: String? = null
        if (!recordedAudioPath.isNullOrBlank()) {
            val file = File(recordedAudioPath)
            if (file.exists()) {
                audioUrl = withContext(Dispatchers.IO) {
                    CloudinaryHelper.uploadAudio(context, file, token)
                }
            }
        }

        val body = JSONObject().apply {
            put("image_url", imageUrl)
            put("caption", caption)
            targetAlbumId?.let { put("album_id", it) }
            lat?.let { put("latitude", it) }
            lng?.let { put("longitude", it) }
            takenAt?.let { put("taken_at", it) }
            audioUrl?.let { put("audio_url", it) }
            // Send the resolved place name so the backend can persist it
            displayedLocation?.let { name ->
                if (name.isNotBlank()) {
                    put("location_name", name)
                }
            }
        }
        Log.d("HomePhotoEntryScreen", "POST /images body: ${body.toString()}")
        val result = BackendClient.post("/images", body, token = token)

        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                Toast.makeText(context, "Photo saved!", Toast.LENGTH_SHORT).show()
                onPhotoSaved(targetAlbumId)
            } else {
                Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            targetAlbumId != null && !albumName.isNullOrBlank() -> "Add to $albumName"
                            targetAlbumId != null -> "Add to album"
                            else -> "Add to My Photos"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (isSaving) return@TextButton
                            isSaving = true
                            scope.launch {
                                uploadAndSave()
                                isSaving = false
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showDatePickerDialog) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = pendingDateMillis ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            pendingDateMillis = datePickerMillisToLocalMidnight(it)
                        }
                        showDatePickerDialog = false
                    }) {
                        Text("OK")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        if (showTimePickerDialog) {
            val timePickerState = rememberTimePickerState(
                initialHour = pendingHour ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                initialMinute = pendingMinute ?: Calendar.getInstance().get(Calendar.MINUTE),
                is24Hour = false
            )
            AlertDialog(
                onDismissRequest = { showTimePickerDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        pendingHour = timePickerState.hour
                        pendingMinute = timePickerState.minute
                        showTimePickerDialog = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePickerDialog = false }) {
                        Text("Cancel")
                    }
                },
                text = {
                    TimePicker(state = timePickerState)
                }
            )
        }
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Selected photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Location card - make entire row clickable
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (isEditingLocation) {
                            // Expanded edit view
                            Text(
                                text = "Location",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editedLocationText,
                                onValueChange = { editedLocationText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Search for a location (e.g. city or address)") }
                            )
                            if (locationSuggestions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                locationSuggestions.forEach { (description, placeId) ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    val path = "/location/place-details?place_id=${URLEncoder.encode(placeId, StandardCharsets.UTF_8.name())}"
                                                    BackendClient.get(path).onSuccess { details ->
                                                        val lat = details.optDouble("latitude", Double.NaN)
                                                        val lng = details.optDouble("longitude", Double.NaN)
                                                        val name = details.optString("display_name", "").takeIf { it.isNotBlank() } ?: description
                                                        if (!lat.isNaN() && !lng.isNaN()) {
                                                            editedLatitude = lat
                                                            editedLongitude = lng
                                                            displayedLocation = name
                                                            editedLocationText = name
                                                            locationSuggestions = emptyList()
                                                            isEditingLocation = false
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(vertical = 8.dp)
                                    )
                                }
                            }
                            TextButton(onClick = { isEditingLocation = false }) {
                                Text("Done")
                            }
                        } else {
                            // Collapsed view - entire row clickable
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isEditingLocation = true
                                        editedLocationText = displayedLocation ?: ""
                                        editedLatitude = metadata.latitude
                                        editedLongitude = metadata.longitude
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayedLocation ?: "Location not available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (displayedLocation != null)
                                            "Location not quite right? Click to edit"
                                        else
                                            "Manually add location",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Date & time card - make entire row clickable
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (isEditingTimestamp) {
                            // Expanded edit view
                            Text(
                                text = "Date & time",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = pendingDateMillis?.let { formatDateMillis(it) } ?: "No date set",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = { showDatePickerDialog = true }) {
                                    Text("Change date")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val h = pendingHour ?: 0
                                val m = pendingMinute ?: 0
                                Text(
                                    text = formatTime(h, m),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = { showTimePickerDialog = true }) {
                                    Text("Change time")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                val dateMillis = pendingDateMillis ?: System.currentTimeMillis()
                                val hour = pendingHour ?: 0
                                val minute = pendingMinute ?: 0
                                editedTakenAt = buildIsoFromDateAndTime(dateMillis, hour, minute)
                                displayedDateTime = formatBackendDateTime(editedTakenAt) ?: editedTakenAt
                                isEditingTimestamp = false
                            }) {
                                Text("Done")
                            }
                        } else {
                            // Collapsed view - entire row clickable
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isEditingTimestamp = true
                                        editedTakenAt = metadata.takenAt ?: ""
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayedDateTime ?: "Timestamp not available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (displayedDateTime != null)
                                            "Timestamp not quite right? Click to edit"
                                        else
                                            "Manually add timestamp",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Voice note card
            item {
                val hasRecording = !recordedAudioPath.isNullOrBlank()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when {
                                isRecording -> scope.launch { stopChannel.send(Unit) }
                                hasRecording -> {
                                    if (mediaPlayer?.isPlaying == true) {
                                        mediaPlayerHolder.current?.release()
                                        mediaPlayerHolder.current = null
                                        mediaPlayer = null
                                        isPlaying = false
                                    } else {
                                        val path = recordedAudioPath!!
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
                                    hasRecording -> if (isPlaying) "Playing…" else "Tap to play"
                                    else -> "Tap to record"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Record an audio for this Memento",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (hasRecording) {
                                Text(
                                    text = "Delete voice note",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable {
                                        mediaPlayerHolder.current?.release()
                                        mediaPlayerHolder.current = null
                                        mediaPlayer = null
                                        isPlaying = false
                                        recordedAudioPath = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Caption field
            item {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Add a caption") },
                    placeholder = { Text("E.g. Bubble tea run with Lily!") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    minLines = 3
                )
            }
        }
    }
}