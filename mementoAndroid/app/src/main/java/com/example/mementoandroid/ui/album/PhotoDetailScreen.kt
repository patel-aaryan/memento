package com.example.mementoandroid.ui.album

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import com.example.mementoandroid.util.buildIsoFromDateAndTime
import com.example.mementoandroid.util.extractPhotoMetadata
import com.example.mementoandroid.util.formatBackendDateTime
import com.example.mementoandroid.util.formatDateMillis
import com.example.mementoandroid.util.formatPhotoMetadataDateTime
import com.example.mementoandroid.util.formatPhotoMetadataLocation
import com.example.mementoandroid.util.formatTime
import com.example.mementoandroid.util.datePickerMillisToLocalMidnight
import com.example.mementoandroid.util.instantToLocalDateMillis
import com.example.mementoandroid.util.parseIsoToHourMinute
import com.example.mementoandroid.util.parseIsoToMillis
import com.example.mementoandroid.util.recordAudioToCache
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import android.util.Log
import com.example.mementoandroid.api.BackendClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import androidx.compose.ui.text.style.TextOverflow

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
    canDeletePhoto: Boolean = false,
    canEditPhoto: Boolean = canDeletePhoto,
    allPhotos: List<AlbumPhotoUi>? = null,
    currentPhotoIndex: Int = 0,
    onPhotoIndexChange: (Int) -> Unit = {},
    uploaderName: String? = null,
    uploaderProfilePicUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var notesText by remember(photo.id) { mutableStateOf(photo.caption?.takeIf { it.isNotBlank() } ?: mock.caption) }
    var displayedDateTime by remember(photo.id) { mutableStateOf(mock.dateTime) }
    var displayedLocation by remember(photo.id) { mutableStateOf(mock.location) }
    var metadataReady by remember(photo.id) { mutableStateOf(false) }
    var imageAspectRatio by remember(photo.id) { mutableStateOf(1f) }
    var isEditMode by remember(photo.id) { mutableStateOf(false) }
    var editedTakenAt by remember(photo.id) { mutableStateOf(photo.takenAt ?: "") }
    // Location search text; autocomplete suggestions and selected lat/lng for save
    var editedLocationText by remember(photo.id) { mutableStateOf("") }
    var locationSuggestions by remember(photo.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var editedLatitude by remember(photo.id) { mutableStateOf<Double?>(null) }
    var editedLongitude by remember(photo.id) { mutableStateOf<Double?>(null) }
    var isEditingLocation by remember(photo.id) { mutableStateOf(false) }
    var isEditingTimestamp by remember(photo.id) { mutableStateOf(false) }
    var showDatePickerDialog by remember(photo.id) { mutableStateOf(false) }
    var showTimePickerDialog by remember(photo.id) { mutableStateOf(false) }
    var pendingDateMillis by remember(photo.id) { mutableStateOf<Long?>(null) }
    var pendingHour by remember(photo.id) { mutableStateOf<Int?>(null) }
    var pendingMinute by remember(photo.id) { mutableStateOf<Int?>(null) }
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
        metadataReady = true
    }

    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            val instant = parseIsoToMillis(photo.takenAt) ?: System.currentTimeMillis()
            pendingDateMillis = instantToLocalDateMillis(instant)
            val hm = parseIsoToHourMinute(photo.takenAt)
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
                title = {
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canEditPhoto || canDeletePhoto) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                if (canEditPhoto && !isEditMode) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            menuExpanded = false
                                            editedTakenAt = photo.takenAt ?: ""
                                            editedLocationText = displayedLocation?.takeIf { it.isNotBlank() }
                                                ?: if (photo.latitude != null && photo.longitude != null)
                                                    "${photo.latitude}, ${photo.longitude}"
                                                else ""
                                            isEditingTimestamp = false
                                            isEditingLocation = false
                                            isEditMode = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Edit, contentDescription = null)
                                        }
                                    )
                                }
                                if (canDeletePhoto) {
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
                    }
                }
            )
        }
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 56.dp,
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = paddingValues.calculateBottomPadding()
                )
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            // Up arrow: above photo (when multiple photos; hidden in edit mode)
            if (!isEditMode && allPhotos != null && allPhotos.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            val prev = (currentPhotoIndex - 1).coerceAtLeast(0)
                            if (prev != currentPhotoIndex) onPhotoIndexChange(prev)
                        },
                        enabled = currentPhotoIndex > 0
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Previous photo",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // Main image — full width; keep original aspect ratio (no crop); swipe up/down for next/prev when multiple photos (disabled in edit mode)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!isEditMode && allPhotos != null && allPhotos.size > 1) {
                            Modifier.pointerInput(isEditMode, allPhotos.size, currentPhotoIndex) {
                                var totalDrag = 0f
                                detectVerticalDragGestures { _, dragAmount ->
                                    totalDrag += dragAmount
                                    // Swipe up (negative drag) → next photo; swipe down (positive drag) → previous photo
                                    if (totalDrag < -120f) {
                                        totalDrag = 0f
                                        val next = (currentPhotoIndex + 1).coerceAtMost(allPhotos.size - 1)
                                        if (next != currentPhotoIndex) onPhotoIndexChange(next)
                                    } else if (totalDrag > 120f) {
                                        totalDrag = 0f
                                        val prev = (currentPhotoIndex - 1).coerceAtLeast(0)
                                        if (prev != currentPhotoIndex) onPhotoIndexChange(prev)
                                    }
                                }
                            }
                        } else Modifier
                    )
            ) {
                when {
                    photo.imageUrl != null -> {
                        val imageModel = remember(photo.id, photo.imageUrl) {
                            ImageRequest.Builder(context).data(photo.imageUrl).listener(
                                onSuccess = { _, result ->
                                    if (result is SuccessResult) {
                                        val w = result.drawable.intrinsicWidth
                                        val h = result.drawable.intrinsicHeight
                                        if (w > 0 && h > 0) imageAspectRatio = w.toFloat() / h
                                    }
                                }
                            ).build()
                        }
                        AsyncImage(
                            model = imageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(imageAspectRatio)
                        )
                    }
                    photo.uri != null -> {
                        val imageModel = remember(photo.id, photo.uri) {
                            ImageRequest.Builder(context).data(photo.uri).listener(
                                onSuccess = { _, result ->
                                    if (result is SuccessResult) {
                                        val w = result.drawable.intrinsicWidth
                                        val h = result.drawable.intrinsicHeight
                                        if (w > 0 && h > 0) imageAspectRatio = w.toFloat() / h
                                    }
                                }
                            ).build()
                        }
                        AsyncImage(
                            model = imageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(imageAspectRatio)
                        )
                    }
                    else -> {
                        LaunchedEffect(photo.id, photo.imageRes) {
                            photo.imageRes?.let { resId ->
                                val d = context.getDrawable(resId)
                                if (d != null && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                                    imageAspectRatio = d.intrinsicWidth.toFloat() / d.intrinsicHeight
                                }
                            }
                        }
                        Image(
                            painter = painterResource(id = photo.imageRes!!),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(imageAspectRatio)
                        )
                    }
                }
            }

            // Date picker and time picker dialogs (edit mode)
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

            // Metadata section: same structure as Add to Memento (Location → Date & time → Voice → Caption)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isEditMode) {
                    // Edit mode: Location card (Add-to-Memento style)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (isEditingLocation) {
                                // Expanded edit view - keep as is
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
                                                        val detailsPath = "/location/place-details?place_id=${URLEncoder.encode(placeId, StandardCharsets.UTF_8.name())}"
                                                        BackendClient.get(detailsPath).onSuccess { details ->
                                                            val lat = details.optDouble("latitude", Double.NaN)
                                                            val lng = details.optDouble("longitude", Double.NaN)
                                                            val name = details.optString("display_name", "").takeIf { it.isNotBlank() } ?: description
                                                            if (!lat.isNaN() && !lng.isNaN()) {
                                                                editedLatitude = lat
                                                                editedLongitude = lng
                                                                editedLocationText = name
                                                                locationSuggestions = emptyList()
                                                                isEditingLocation = false
                                                            }
                                                        }.onFailure { e ->
                                                            Log.w("PhotoDetailScreen", "Place details failed", e)
                                                            locationSuggestions = emptyList()
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
                                // Collapsed view - make entire row clickable, not just the text
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isEditingLocation = true
                                            if (editedLocationText.isBlank()) {
                                                editedLocationText = displayedLocation ?: ""
                                                editedLatitude = photo.latitude
                                                editedLongitude = photo.longitude
                                            }
                                        }
                                        .padding(vertical = 4.dp), // Add some padding for better touch target
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
                                            text = editedLocationText.ifBlank { "Location not available" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (editedLocationText.isNotBlank())
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

                    // Edit mode: Date & time card — collapsed by default ("Timestamp not quite right? Click to edit"), expand to pickers on click
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (isEditingTimestamp) {
                                // Expanded edit view - keep as is
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
                                    isEditingTimestamp = false
                                }) {
                                    Text("Done")
                                }
                            } else {
                                // Collapsed view - make entire row clickable
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isEditingTimestamp = true }
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
                                        val timestampText = pendingDateMillis?.let { millis ->
                                            val h = pendingHour ?: 0
                                            val m = pendingMinute ?: 0
                                            "${formatDateMillis(millis)} · ${formatTime(h, m)}"
                                        } ?: "Timestamp not available"
                                        Text(
                                            text = timestampText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (pendingDateMillis != null)
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
                } else {
                    // View mode: simple rows (no cards); show placeholders until metadata is ready to avoid flashing previous photo's info
                    if (metadataReady) {
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
                    } else {
                        // Placeholder blocks so layout doesn't jump; same approximate height as one row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // 👇 NEW: Uploader info (shared albums only)
                if (metadataReady && uploaderName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!uploaderProfilePicUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = uploaderProfilePicUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Added by $uploaderName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Audio: view mode = show only when has recording (play only); edit mode = always show, record/play/delete
                val hasLocalRecording = !recordedAudioPath.isNullOrBlank()
                val hasPersistedAudio = !photo.audioUrl.isNullOrBlank() && photo.audioUrl != "null"
                val hasRecording = hasLocalRecording || hasPersistedAudio
                if (isEditMode || hasRecording) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isEditMode) {
                                    Modifier.clickable {
                                        when {
                                            isRecording -> scope.launch { stopChannel.send(Unit) }
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
                                    }
                                } else {
                                    Modifier.clickable {
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
                                }
                            ),
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
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        // Single-line "Tap to play" / "Playing…": match icon height so text is vertically centered
                                        if (hasRecording && !isEditMode) Modifier.height(32.dp) else Modifier
                                    ),
                                verticalArrangement = if (hasRecording && !isEditMode) Arrangement.Center else Arrangement.spacedBy(0.dp)
                            ) {
                                Text(
                                    text = when {
                                        isRecording -> "Recording…"
                                        hasRecording -> if (isPlaying) "Playing…" else "Tap to play"
                                        else -> "Tap to record"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!hasRecording) {
                                    Text(
                                        text = when {
                                            isRecording -> "Tap to stop"
                                            else -> "Record an audio for this Memento"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (isEditMode && hasRecording) {
                                    Text(
                                        text = "Delete voice note",
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
                }

                // Caption: view = read-only text; edit = same as Add to Memento (label + placeholder)
                if (isEditMode) {
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Add a caption") },
                        placeholder = { Text("E.g. Bubble tea run with Lily!") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            scope.launch {
                                delay(150)
                                val dateMillis = pendingDateMillis ?: System.currentTimeMillis()
                                val hour = pendingHour ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                val minute = pendingMinute ?: Calendar.getInstance().get(Calendar.MINUTE)
                                val takenAt = buildIsoFromDateAndTime(dateMillis, hour, minute)
                                val lat = editedLatitude ?: photo.latitude
                                val lng = editedLongitude ?: photo.longitude
                                withContext(Dispatchers.Main) {
                                    onSave(notesText, takenAt, lat, lng, recordedAudioPath)
                                    isEditMode = false
                                    displayedDateTime = formatBackendDateTime(takenAt) ?: formatDateMillis(dateMillis) + " " + formatTime(hour, minute)
                                    if (lat != null && lng != null) {
                                        displayedLocation = editedLocationText.takeIf { it.isNotBlank() }
                                            ?: formatPhotoMetadataLocation(lat, lng)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save changes")
                    }
                } else {
                    // View mode: only show caption area when there is content (skip blank or "null"); hide until metadata ready
                    if (metadataReady && notesText.isNotBlank() && notesText != "null") {
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = notesText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Down arrow: below photo/details (when multiple photos; hidden in edit mode)
            if (!isEditMode && allPhotos != null && allPhotos.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            val next = (currentPhotoIndex + 1).coerceAtMost(allPhotos.size - 1)
                            if (next != currentPhotoIndex) onPhotoIndexChange(next)
                        },
                        enabled = currentPhotoIndex < allPhotos.size - 1
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Next photo",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}
