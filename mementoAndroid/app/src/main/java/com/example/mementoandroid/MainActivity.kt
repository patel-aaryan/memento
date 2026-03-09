package com.example.mementoandroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.api.BackendException
import com.example.mementoandroid.ui.album.AddPhotoSource
import com.example.mementoandroid.ui.album.AlbumPhotoUi
import com.example.mementoandroid.ui.album.AlbumScreen
import com.example.mementoandroid.ui.album.AlbumUi
import com.example.mementoandroid.ui.album.CreateAlbumScreen
import com.example.mementoandroid.ui.album.FriendUi
import com.example.mementoandroid.ui.album.PhotoDetailScreen
import com.example.mementoandroid.ui.album.getPhotoDetailMock
import com.example.mementoandroid.ui.album.components.FriendPickerScreen
import com.example.mementoandroid.ui.home.HomeAddAction
import com.example.mementoandroid.ui.home.HomePhotoEntryScreen
import com.example.mementoandroid.ui.home.HomePhotoMetadata
import com.example.mementoandroid.ui.home.HomeScreen
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import com.example.mementoandroid.util.AuthTokenStore
import com.example.mementoandroid.util.CloudinaryHelper
import com.example.mementoandroid.util.PendingFriendTokenStore
import com.example.mementoandroid.util.exifDateTimeToIso
import com.example.mementoandroid.util.extractPhotoMetadata
import com.example.mementoandroid.util.logPhotoMetadata
import com.example.mementoandroid.util.verifyAndLogLocationStrippingCause
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Switch
import com.example.mementoandroid.reminder.AnniversaryLocationWorker



private const val TAG = "MainActivity"

private fun handle401(context: ComponentActivity, e: Throwable) {
    if (e is BackendException && e.statusCode == 401) {
        AuthTokenStore.clear()
        context.startActivity(Intent(context, LoginActivity::class.java))
        context.finish()
    }
}

class MainActivity : ComponentActivity() {

    // Request notification permission (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "✓ Notification permission granted")
            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "✗ Notification permission denied")
            Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthTokenStore.init(applicationContext)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "✓ Notification permission already granted")
                }
                else -> {
                    Log.d(TAG, "Requesting notification permission...")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        // Get Firebase Cloud Messaging token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(TAG, "========================================")
                Log.d(TAG, "FCM Device Token: $token")
                Log.d(TAG, "========================================")
                Toast.makeText(this, "FCM Token copied to logs", Toast.LENGTH_LONG).show()
                // TODO: Send this token to your backend to store with user profile
            } else {
                Log.e(TAG, "Failed to get FCM token", task.exception)
                Toast.makeText(this, "Failed to get FCM token", Toast.LENGTH_SHORT).show()
            }
        }

        enableEdgeToEdge()
        // Kick off the first anniversary location check; subsequent runs reschedule themselves
        Log.d(TAG, "Scheduling first AnniversaryLocationWorker")
        AnniversaryLocationWorker.scheduleNext(applicationContext)
        setContent {
            MementoAndroidTheme {
                val context = LocalContext.current as ComponentActivity
                var selectedAlbumId by rememberSaveable { mutableStateOf<Int?>(null) }
                var selectedPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
                var albums by remember { mutableStateOf<List<AlbumUi>>(emptyList()) }
                var currentUserProfilePictureUrl by remember { mutableStateOf<String?>(null) }
                val photos = remember { mutableStateListOf<AlbumPhotoUi>() }
                var albumMembers by remember { mutableStateOf<List<FriendUi>>(emptyList()) }

                var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
                var pendingCameraFile by remember { mutableStateOf<File?>(null) }
                var pendingHomePhotoEntry by rememberSaveable { mutableStateOf<HomePhotoMetadata?>(null) }

                var showCreateAlbum by rememberSaveable { mutableStateOf(false) }
                var showFriendPicker by remember { mutableStateOf(false) }
                var pendingAlbumIdForFriend by remember { mutableStateOf<Int?>(null) }
                var albumShowMap by rememberSaveable { mutableStateOf(false) }

                val scope = rememberCoroutineScope()

                val profileLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { _ ->
                    scope.launch {
                        val t = AuthTokenStore.get() ?: return@launch
                        BackendClient.get("/auth/me", t)
                            .onSuccess { j ->
                                val url = j.optString("profile_picture_url", "").takeIf { it.isNotBlank() }
                                withContext(Dispatchers.Main) { currentUserProfilePictureUrl = url }
                            }
                            .onFailure { handle401(context, it) }
                    }
                }

                LaunchedEffect(Unit) {
                    val t = AuthTokenStore.get() ?: return@LaunchedEffect
                    val pendingToken = PendingFriendTokenStore.get(context)
                    if (pendingToken != null) {
                        PendingFriendTokenStore.clear(context)
                        val body = JSONObject().put("token", pendingToken)
                        BackendClient.post("/friends/add_friend_by_link", body, token = t)
                            .onSuccess { Toast.makeText(context, "Friend added!", Toast.LENGTH_SHORT).show() }
                            .onFailure { handle401(context, it) }
                    }
                    BackendClient.get("/auth/me", t)
                        .onSuccess { j ->
                            val url = j.optString("profile_picture_url", "").takeIf { it.isNotBlank() }
                            withContext(Dispatchers.Main) { currentUserProfilePictureUrl = url }
                        }
                        .onFailure { handle401(context, it) }
                    BackendClient.getArray("/albums", t)
                        .onSuccess { arr ->
                            val list = (0 until arr.length()).map { i ->
                                val o = arr.getJSONObject(i)
                                val coverUrls = if (o.has("cover_image_urls")) {
                                    val a = o.getJSONArray("cover_image_urls")
                                    (0 until a.length()).map { j -> a.getString(j) }
                                } else emptyList()
                                AlbumUi(o.getInt("id"), o.getString("name"), coverUrls)
                            }
                            withContext(Dispatchers.Main) { albums = list }
                        }
                        .onFailure { handle401(context, it) }
                }

                LaunchedEffect(selectedAlbumId) {
                    if (selectedAlbumId == null) {
                        withContext(Dispatchers.Main) { albumMembers = emptyList() }
                        return@LaunchedEffect
                    }
                    val t = AuthTokenStore.get() ?: return@LaunchedEffect
                    val aid = selectedAlbumId!!

                    BackendClient.getArray("/albums/$aid/members", t).onSuccess { membersArr ->
                        val currentUserId = AuthTokenStore.getUserId()?.toString()
                        val list = (0 until membersArr.length())
                            .map { i ->
                                val o = membersArr.getJSONObject(i)
                                FriendUi(
                                    id = o.getInt("id").toString(),
                                    username = o.getString("name"),
                                    profilePictureUrl = o.optString("profile_picture_url", "").takeIf { it.isNotBlank() }
                                )
                            }
                            .filter { it.id != currentUserId }
                        withContext(Dispatchers.Main) { albumMembers = list }
                    }.onFailure { withContext(Dispatchers.Main) { albumMembers = emptyList() } }

                    BackendClient.getArray("/images/album/$aid", t).onSuccess { arr ->
                        val list = (0 until arr.length()).map { i ->
                            val o = arr.getJSONObject(i)
                            val lat = if (o.isNull("latitude")) null else o.getDouble("latitude")
                            val lon = if (o.isNull("longitude")) null else o.getDouble("longitude")
                            AlbumPhotoUi(
                                id = o.getInt("id").toString(),
                                imageUrl = o.getString("image_url"),
                                audioUrl = o.optString("audio_url", "").takeIf { it.isNotBlank() },
                                caption = o.optString("caption", "").takeIf { it.isNotBlank() },
                                latitude = lat,
                                longitude = lon,
                                dateAdded = o.optString("date_added", "").takeIf { it.isNotBlank() },
                                takenAt = o.optString("taken_at", "").takeIf { it.isNotBlank() }
                            )
                        }
                        withContext(Dispatchers.Main) {
                            photos.clear()
                            photos.addAll(list)
                        }
                    }.onFailure { e ->
                        withContext(Dispatchers.Main) {
                            handle401(context, e)
                            photos.clear()
                        }
                    }
                }

                fun loadAlbumImages() {
                    val aid = selectedAlbumId ?: return
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        BackendClient.getArray("/images/album/$aid", t)
                            .onSuccess { arr ->
                                val list = (0 until arr.length()).map { i ->
                                    val o = arr.getJSONObject(i)
                                    val lat = if (o.isNull("latitude")) null else o.getDouble("latitude")
                                    val lon = if (o.isNull("longitude")) null else o.getDouble("longitude")
                                    AlbumPhotoUi(
                                        id = o.getInt("id").toString(),
                                        imageUrl = o.getString("image_url"),
                                        audioUrl = o.optString("audio_url", "").takeIf { it.isNotBlank() && it != "null" },
                                        caption = o.optString("caption", "").takeIf { it.isNotBlank() },
                                        latitude = lat,
                                        longitude = lon,
                                        dateAdded = o.optString("date_added", "").takeIf { it.isNotBlank() },
                                        takenAt = o.optString("taken_at", "").takeIf { it.isNotBlank() }
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    photos.clear()
                                    photos.addAll(list)
                                }
                            }
                            .onFailure { e -> withContext(Dispatchers.Main) { handle401(context, e) } }
                    }
                }

                fun addPhotoWithUpload(
                    photoFile: File,
                    fallbackUri: Uri?,
                    albumId: Int,
                    latitude: Double?,
                    longitude: Double?,
                    takenAt: String? = null
                ) {
                    val t = AuthTokenStore.get()
                    if (t == null) {
                        Log.w(TAG, "Upload skipped: no auth token. Add photo from Login first.")
                        return
                    }
                    scope.launch {
                        val url = CloudinaryHelper.uploadImage(context, photoFile, t)
                        withContext(Dispatchers.Main) {
                            if (url == null) return@withContext
                        }
                        val body = JSONObject().apply {
                            put("album_id", albumId)
                            put("image_url", url)
                            if (latitude != null) put("latitude", latitude)
                            if (longitude != null) put("longitude", longitude)
                            if (takenAt != null) put("taken_at", takenAt)
                        }
                        BackendClient.post("/images", body, token = t)
                            .onSuccess { loadAlbumImages() }
                            .onFailure { e -> withContext(Dispatchers.Main) { handle401(context, e) } }
                    }
                }

                val takePictureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success) {
                        val file = pendingCameraFile
                        val uri = pendingCameraUri
                        val albumId = selectedAlbumId
                        pendingCameraFile = null
                        pendingCameraUri = null
                        if (file != null && file.exists()) {
                            scope.launch(Dispatchers.IO) {
                                val md = extractPhotoMetadata(context, uri ?: Uri.fromFile(file))
                                val takenAt = md?.dateTimeOriginal?.let { exifDateTimeToIso(it) }
                                    ?: md?.dateTaken?.let { exifDateTimeToIso(it) }

                                withContext(Dispatchers.Main) {
                                    if (albumId != null) {
                                        addPhotoWithUpload(file, uri, albumId, null, null, takenAt)
                                    } else {
                                        pendingHomePhotoEntry = HomePhotoMetadata(
                                            latitude = md?.latitude,
                                            longitude = md?.longitude,
                                            takenAt = takenAt,
                                            imageUri = uri ?: Uri.fromFile(file),
                                            imageFile = file
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val pickPhotoLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val albumId = selectedAlbumId
                        scope.launch(Dispatchers.IO) {
                            verifyAndLogLocationStrippingCause(uri)
                            val metadata = extractPhotoMetadata(context, uri)
                            metadata?.let { logPhotoMetadata(it) }
                            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                file.outputStream().use { input.copyTo(it) }
                            }

                            if (file.exists()) {
                                val takenAt = metadata?.dateTimeOriginal?.let { exifDateTimeToIso(it) }
                                    ?: metadata?.dateTaken?.let { exifDateTimeToIso(it) }

                                withContext(Dispatchers.Main) {
                                    if (albumId != null) {
                                        addPhotoWithUpload(
                                            file,
                                            uri,
                                            albumId,
                                            metadata?.latitude,
                                            metadata?.longitude,
                                            takenAt
                                        )
                                    } else {
                                        pendingHomePhotoEntry = HomePhotoMetadata(
                                            latitude = metadata?.latitude,
                                            longitude = metadata?.longitude,
                                            takenAt = takenAt,
                                            imageUri = uri,
                                            imageFile = file
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                fun onAddPhoto(source: AddPhotoSource) {
                    when (source) {
                        AddPhotoSource.Camera -> {
                            val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                            pendingCameraFile = file
                            pendingCameraUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            pendingCameraUri?.let { takePictureLauncher.launch(it) }
                        }
                        AddPhotoSource.Photos -> {
                            pickPhotoLauncher.launch(arrayOf("image/*"))
                        }
                    }
                }

                fun onHomeAction(action: HomeAddAction) {
                    when (action) {
                        HomeAddAction.Camera -> {
                            val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                            pendingCameraFile = file
                            pendingCameraUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            pendingCameraUri?.let { takePictureLauncher.launch(it) }
                        }
                        HomeAddAction.Photos -> {
                            pickPhotoLauncher.launch(arrayOf("image/*"))
                        }
                        HomeAddAction.MakeAlbum -> showCreateAlbum = true
                    }
                }

                val selectedAlbumName = selectedAlbumId?.let { id -> albums.find { it.id == id }?.name }

                when {
                    showFriendPicker && pendingAlbumIdForFriend != null -> {
                        BackHandler {
                            showFriendPicker = false
                            pendingAlbumIdForFriend = null
                        }
                        FriendPickerScreen(
                            albumId = pendingAlbumIdForFriend!!,
                            currentFriends = if (selectedAlbumId != null) albumMembers else emptyList(),
                            onBack = {
                                showFriendPicker = false
                                pendingAlbumIdForFriend = null
                            },
                            onFriendsAdded = { newFriends ->
                                if (selectedAlbumId != null) {
                                    // Update existing album's friends list
                                    albumMembers = albumMembers + newFriends
                                }
                                showFriendPicker = false
                                pendingAlbumIdForFriend = null
                            }
                        )
                    }

                    showCreateAlbum -> {
                        BackHandler { showCreateAlbum = false }
                        CreateAlbumScreen(
                            onBack = { showCreateAlbum = false },
                            onAlbumCreated = { albumId, albumName ->
                                scope.launch {
                                    val t = AuthTokenStore.get()
                                    if (t != null) {
                                        BackendClient.getArray("/albums", t)
                                            .onSuccess { arr ->
                                                val list = (0 until arr.length()).map { i ->
                                                    val o = arr.getJSONObject(i)
                                                    val coverUrls = if (o.has("cover_image_urls")) {
                                                        val a = o.getJSONArray("cover_image_urls")
                                                        (0 until a.length()).map { j -> a.getString(j) }
                                                    } else emptyList()
                                                    AlbumUi(o.getInt("id"), o.getString("name"), coverUrls)
                                                }
                                                withContext(Dispatchers.Main) {
                                                    albums = list
                                                }
                                            }
                                    }
                                }
                            },
                            onAddFriend = { albumId ->
                                pendingAlbumIdForFriend = albumId
                                showFriendPicker = true
                            },
                            onAddPhoto = ::onAddPhoto,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    pendingHomePhotoEntry != null -> {
                        BackHandler { pendingHomePhotoEntry = null }
                        HomePhotoEntryScreen(
                            metadata = pendingHomePhotoEntry!!,
                            onBack = { pendingHomePhotoEntry = null },
                            onPhotoSaved = {
                                pendingHomePhotoEntry = null
                                scope.launch {
                                    val t = AuthTokenStore.get()
                                    if (t != null) {
                                        BackendClient.getArray("/albums", t)
                                            .onSuccess { arr ->
                                                val list = (0 until arr.length()).map { i ->
                                                    val o = arr.getJSONObject(i)
                                                    val coverUrls = if (o.has("cover_image_urls")) {
                                                        val a = o.getJSONArray("cover_image_urls")
                                                        (0 until a.length()).map { j -> a.getString(j) }
                                                    } else emptyList()
                                                    AlbumUi(o.getInt("id"), o.getString("name"), coverUrls)
                                                }
                                                withContext(Dispatchers.Main) { albums = list }
                                            }
                                    }
                                }
                            }
                        )
                    }

                    selectedAlbumId != null && selectedPhotoId != null -> {
                        BackHandler { selectedPhotoId = null }
                        val albumName = selectedAlbumName ?: ""
                        val photo = photos.find { it.id == selectedPhotoId }
                        if (photo != null) {
                            val photoToDelete = photo
                            PhotoDetailScreen(
                                photo = photoToDelete,
                                albumName = albumName,
                                mock = getPhotoDetailMock(albumName, photoToDelete.id),
                                onBack = { selectedPhotoId = null },
                                onDeleteAudio = {
                                    scope.launch {
                                        val t = AuthTokenStore.get() ?: return@launch
                                        val body = JSONObject().put("audio_url", JSONObject.NULL)
                                        val result = BackendClient.put("/images/${photoToDelete.id}", body, token = t)
                                        withContext(Dispatchers.Main) {
                                            result.onFailure { handle401(context, it) }
                                            if (result.isSuccess) loadAlbumImages()
                                            Toast.makeText(
                                                context,
                                                if (result.isSuccess) "Audio removed" else result.exceptionOrNull()?.message ?: "Failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onSave = { caption, takenAt, latitude, longitude, audioFilePath ->
                                    scope.launch {
                                        val t = AuthTokenStore.get() ?: return@launch
                                        var audioUrl: String? = null
                                        if (!audioFilePath.isNullOrBlank()) {
                                            val file = java.io.File(audioFilePath)
                                            if (file.exists()) {
                                                audioUrl = CloudinaryHelper.uploadAudio(context, file, t)
                                            }
                                        }
                                        val body = JSONObject().apply {
                                            put("caption", caption)
                                            takenAt?.takeIf { it.isNotBlank() }?.let { put("taken_at", it) }
                                            latitude?.let { put("latitude", it) }
                                            longitude?.let { put("longitude", it) }
                                            audioUrl?.let { put("audio_url", it) }
                                        }
                                        val result = BackendClient.put("/images/${photoToDelete.id}", body, token = t)
                                        withContext(Dispatchers.Main) {
                                            result.onFailure { handle401(context, it) }
                                            if (result.isSuccess) loadAlbumImages()
                                            Toast.makeText(
                                                context,
                                                if (result.isSuccess) "Saved" else result.exceptionOrNull()?.message ?: "Failed to save",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onDeletePhoto = {
                                    scope.launch {
                                        val t = AuthTokenStore.get()
                                        withContext(Dispatchers.Main) {
                                            if (t == null) {
                                                Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show()
                                                return@withContext
                                            }
                                        }
                                        val result = BackendClient.delete("/images/${photoToDelete.id}", token = t)
                                        withContext(Dispatchers.Main) {
                                            result.onFailure { e ->
                                                handle401(context, e)
                                                Toast.makeText(
                                                    context,
                                                    (e as? BackendException)?.message ?: "Failed to delete",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            if (result.isSuccess) {
                                                photos.removeAll { it.id == photoToDelete.id }
                                                selectedPhotoId = null
                                                Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        } else {
                            selectedPhotoId = null
                        }
                    }

                    selectedAlbumId != null -> {
                        val albumId = selectedAlbumId!!
                        AlbumScreen(
                            modifier = Modifier.fillMaxSize(),
                            albumName = selectedAlbumName ?: "",
                            photos = photos,
                            friends = albumMembers,
                            isSharedAlbum = albumMembers.isNotEmpty(),
                            showMap = albumShowMap,
                            onShowMapChange = { albumShowMap = it },
                            onBack = { selectedAlbumId = null },
                            onEditAlbumName = {},
                            onSaveAlbumName = { newName ->
                                scope.launch {
                                    val t = AuthTokenStore.get() ?: return@launch
                                    val body = JSONObject().put("name", newName)
                                    val result = BackendClient.put("/albums/$albumId", body, token = t)
                                    withContext(Dispatchers.Main) {
                                        result.onFailure { handle401(context, it) }
                                        if (result.isSuccess) {
                                            albums = albums.map { if (it.id == albumId) it.copy(name = newName) else it }
                                            Toast.makeText(context, "Album name updated", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                (result.exceptionOrNull() as? BackendException)?.message ?: "Failed to update",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onDeleteAlbum = {},
                            onAddFriend = {
                                pendingAlbumIdForFriend = albumId
                                showFriendPicker = true
                            },
                            onPhotoClick = { selectedPhotoId = it },
                            onAddPhoto = ::onAddPhoto
                        )
                    }

                    else -> {
                        HomeScreen(
                            albums = albums,
                            profilePictureUrl = currentUserProfilePictureUrl,
                            onProfileClick = { profileLauncher.launch(Intent(context, ProfileActivity::class.java)) },
                            onAlbumClick = { selectedAlbumId = it },
                            onAction = ::onHomeAction
                        )
                    }
                }
            }
        }
    }
}