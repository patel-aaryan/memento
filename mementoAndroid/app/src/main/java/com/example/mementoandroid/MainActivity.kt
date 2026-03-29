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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.api.BackendException
import com.example.mementoandroid.ui.album.AddPhotoSource
import com.example.mementoandroid.ui.album.AlbumPhotoUi
import com.example.mementoandroid.ui.album.AlbumScreen
import com.example.mementoandroid.ui.album.AlbumSort
import com.example.mementoandroid.ui.album.AlbumSortKind
import com.example.mementoandroid.ui.album.AlbumUi
import com.example.mementoandroid.ui.album.FriendUi
import com.example.mementoandroid.ui.album.parseAlbumUi
import com.example.mementoandroid.ui.album.titleForHome
import com.example.mementoandroid.ui.album.PhotoDetailScreen
import com.example.mementoandroid.ui.album.getPhotoDetailMock
import com.example.mementoandroid.ui.album.components.FriendPickerScreen
import com.example.mementoandroid.ui.album.replaceWithAlbumGridSkeletons
import com.example.mementoandroid.ui.album.sortedAndFiltered
import com.example.mementoandroid.ui.home.HomeAddAction
import com.example.mementoandroid.ui.home.HomePhotoEntryScreen
import com.example.mementoandroid.ui.home.HomePhotoMetadata
import com.example.mementoandroid.ui.home.HomeScreen
import com.example.mementoandroid.ui.home.SuggestedAlbumUi
import com.example.mementoandroid.ui.massupload.SelectedImage
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import com.example.mementoandroid.util.AlbumSortStore
import com.example.mementoandroid.util.AlbumViewStore
import com.example.mementoandroid.util.AuthTokenStore
import com.example.mementoandroid.util.DarkModeStore
import com.example.mementoandroid.util.CloudinaryHelper
import com.example.mementoandroid.util.PendingFriendTokenStore
import com.example.mementoandroid.util.exifDateTimeToIso
import com.example.mementoandroid.util.extractPhotoMetadata
import com.example.mementoandroid.util.logPhotoMetadata
import com.example.mementoandroid.util.sharePhotos
import com.example.mementoandroid.util.verifyAndLogLocationStrippingCause
import com.example.mementoandroid.util.DeviceLocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val TAG = "MainActivity"

private fun handle401(context: ComponentActivity, e: Throwable) {
    if (e is BackendException && e.statusCode == 401) {
        AuthTokenStore.clear()
        context.startActivity(Intent(context, LoginActivity::class.java))
        context.finish()
    }
}

class MainActivity : ComponentActivity() {

    private val darkModeState = mutableStateOf(false)

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

    override fun onResume() {
        super.onResume()
        darkModeState.value = DarkModeStore.get(applicationContext)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { newIntent ->
            val albumId = newIntent.getIntExtra("album_id", -1).takeIf { it > 0 }
            if (albumId != null) {
                setIntent(newIntent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthTokenStore.init(applicationContext)
        AlbumSortStore.init(applicationContext)
        AlbumViewStore.init(applicationContext)
        DarkModeStore.init(applicationContext)

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

        enableEdgeToEdge()
        setContent {
            val darkMode by darkModeState
            MementoAndroidTheme(darkTheme = darkMode) {
                val context = LocalContext.current as ComponentActivity
                val initialAlbumId = intent.getIntExtra("album_id", -1).takeIf { it > 0 }
                var selectedAlbumId by rememberSaveable { mutableStateOf(initialAlbumId) }
                var selectedPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
                var currentPhotoIndex by rememberSaveable { mutableStateOf(0) }
                var albums by remember { mutableStateOf<List<AlbumUi>>(emptyList()) }
                var currentUserProfilePictureUrl by remember { mutableStateOf<String?>(null) }
                val photos = remember {
                    mutableStateListOf<AlbumPhotoUi>().apply {
                        addAll(AlbumPhotoUi.defaultGridSkeletons())
                    }
                }
                var albumMembers by remember { mutableStateOf<List<FriendUi>>(emptyList()) }
                var standalonePhotos by remember { mutableStateOf<List<AlbumPhotoUi>>(emptyList()) }
                var myPhotosAlbumId by remember { mutableStateOf<Int?>(null) }
                var photoDetailFromStandalone by remember { mutableStateOf(false) }
                var isRefreshingAlbumImages by remember { mutableStateOf(false) }
                /** When set, open photo detail for this (albumId, photoId) without showing album first. Cleared when state is synced or on back. */
                var pendingStandalonePhotoDetail by remember { mutableStateOf<Pair<Int, String>?>(null) }
                /** After refresh from save/delete-audio, restore selection to this photo id. Cleared by LaunchedEffect once index is set. */
                var photoIdToSelectAfterRefresh by remember { mutableStateOf<String?>(null) }

                var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
                var pendingCameraFile by remember { mutableStateOf<File?>(null) }
                val cameraBuffer = remember { mutableStateListOf<SelectedImage>() }
                var showCameraBufferOverlay by remember { mutableStateOf(false) }
                var cameraTargetAlbumId by remember { mutableStateOf<Int?>(null) }
                var pendingCameraPermissionTargetAlbumId by remember { mutableStateOf<Int?>(null) }
                var cameraErrorMessage by remember { mutableStateOf<String?>(null) }
                var pendingHomePhotoEntry by rememberSaveable { mutableStateOf<HomePhotoMetadata?>(null) }
                var pendingAlbumPhotoEntry by remember { mutableStateOf<Pair<HomePhotoMetadata, Int>?>(null) }
                var pendingMultiplePhotos by remember { mutableStateOf<List<SelectedImage>?>(null) }
                var currentMultiPhotoIndex by remember { mutableStateOf(0) }

                // States for album creation dialog
                var showCreateAlbumDialog by rememberSaveable { mutableStateOf(false) }
                var newAlbumName by rememberSaveable { mutableStateOf("") }
                var isCreatingAlbum by remember { mutableStateOf(false) }

                var albumSuggestion by remember { mutableStateOf<SuggestedAlbumUi?>(null) }
                var albumSuggestionBusy by remember { mutableStateOf(false) }
                /** False after first home feed load (albums + My Photos images) finishes or fails. */
                var isHomeDataLoading by remember { mutableStateOf(true) }

                var showFriendPicker by remember { mutableStateOf(false) }
                var pendingAlbumIdForFriend by remember { mutableStateOf<Int?>(null) }
                var albumShowMap by rememberSaveable { mutableStateOf(false) }
                var showDeleteAlbumDialog by remember { mutableStateOf(false) }
                var albumSort by remember { mutableStateOf(AlbumSort(AlbumSortKind.TIME_NEWEST_FIRST)) }

                val scope = rememberCoroutineScope()
                suspend fun getCurrentLocationOrNull(): android.location.Location? =
                    DeviceLocationHelper.getLastKnownOrNull(context)


                fun loadAlbumSuggestion() {
                    scope.launch {
                        val t = AuthTokenStore.get() ?: return@launch
                        withContext(Dispatchers.IO) {
                            BackendClient.get("/album-suggestions/current", t)
                                .onSuccess { j ->
                                    val sugObj = j.optJSONObject("suggestion")
                                    val ui = if (sugObj != null && sugObj.has("id")) {
                                        val arr = sugObj.optJSONArray("preview_image_urls")
                                        val previews = mutableListOf<String>()
                                        if (arr != null) {
                                            for (i in 0 until arr.length()) {
                                                previews.add(arr.getString(i))
                                            }
                                        }
                                        SuggestedAlbumUi(
                                            id = sugObj.getInt("id"),
                                            albumName = sugObj.optString("album_name", ""),
                                            previewUrls = previews,
                                            imageCount = sugObj.optInt("image_count", 0),
                                        )
                                    } else {
                                        null
                                    }
                                    withContext(Dispatchers.Main) { albumSuggestion = ui }
                                }
                                .onFailure {
                                    withContext(Dispatchers.Main) { albumSuggestion = null }
                                }
                        }
                    }
                }

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
                    val t = AuthTokenStore.get()
                    if (t == null) {
                        isHomeDataLoading = false
                        return@LaunchedEffect
                    }
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
                                arr.getJSONObject(i).parseAlbumUi()
                            }
                            withContext(Dispatchers.Main) { albums = list }
                            val currentUserId = AuthTokenStore.getUserId()
                            val myPhotos = list.find { it.name == "My Photos" && it.ownerId == currentUserId }
                            if (myPhotos != null) {
                                BackendClient.getArray("/images/album/${myPhotos.id}", t)
                                    .onSuccess { imgArr ->
                                        val photoList = (0 until imgArr.length()).map { i ->
                                            val o = imgArr.getJSONObject(i)
                                            val lat = if (o.isNull("latitude")) null else o.getDouble("latitude")
                                            val lon = if (o.isNull("longitude")) null else o.getDouble("longitude")
                                            AlbumPhotoUi(
                                                id = o.getInt("id").toString(),
                                                imageUrl = o.getString("image_url"),
                                                audioUrl = o.optString("audio_url", "").takeIf { it.isNotBlank() },
                                                caption = o.optString("caption", "").takeIf { it.isNotBlank() },
                                                latitude = lat,
                                                longitude = lon,
                                                locationName = o.optString("location_name", "").takeIf { it.isNotBlank() },
                                                dateAdded = o.optString("date_added", "").takeIf { it.isNotBlank() },
                                                takenAt = o.optString("taken_at", "").takeIf { it.isNotBlank() },
                                                userId = if (o.has("user_id")) o.getInt("user_id") else null
                                            )
                                        }
                                        withContext(Dispatchers.Main) {
                                            standalonePhotos = photoList
                                            myPhotosAlbumId = myPhotos.id
                                            isHomeDataLoading = false
                                        }
                                        loadAlbumSuggestion()
                                    }
                                    .onFailure { e ->
                                        withContext(Dispatchers.Main) {
                                            standalonePhotos = emptyList()
                                            myPhotosAlbumId = myPhotos.id
                                            isHomeDataLoading = false
                                        }
                                        handle401(context, e)
                                    }
                            } else {
                                withContext(Dispatchers.Main) {
                                    standalonePhotos = emptyList()
                                    myPhotosAlbumId = null
                                    isHomeDataLoading = false
                                }
                                loadAlbumSuggestion()
                            }
                        }
                        .onFailure { e ->
                            withContext(Dispatchers.Main) { isHomeDataLoading = false }
                            handle401(context, e)
                        }
                    // Clear the album_id intent extra after consuming it
                    intent.removeExtra("album_id")
                }

                LaunchedEffect(selectedAlbumId) {
                    val aid = selectedAlbumId
                    if (aid != null) {
                        AlbumSortStore.get(aid)?.let { saved ->
                            withContext(Dispatchers.Main) { albumSort = saved }
                        }
                    }
                    if (aid == null) {
                        withContext(Dispatchers.Main) {
                            albumMembers = emptyList()
                            photos.replaceWithAlbumGridSkeletons()
                        }
                        loadAlbumSuggestion()
                        return@LaunchedEffect
                    }
                    val t = AuthTokenStore.get() ?: return@LaunchedEffect

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
                                        locationName = o.optString("location_name", "").takeIf { it.isNotBlank() },
                                        dateAdded = o.optString("date_added", "").takeIf { it.isNotBlank() },
                                        takenAt = o.optString("taken_at", "").takeIf { it.isNotBlank() },
                                        userId = if (o.has("user_id")) o.getInt("user_id") else null
                                    )
                                }
                        withContext(Dispatchers.Main) {
                            photos.clear()
                            photos.addAll(list)
                        }
                    }.onFailure { e ->
                        withContext(Dispatchers.Main) {
                            handle401(context, e)
                            photos.replaceWithAlbumGridSkeletons()
                        }
                    }
                }

                fun loadAlbumImages(onImagesLoaded: ((List<AlbumPhotoUi>) -> Unit)? = null) {
                    val aid = selectedAlbumId ?: return
                    val t = AuthTokenStore.get() ?: return
                    isRefreshingAlbumImages = true
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
                                        locationName = o.optString("location_name", "").takeIf { it.isNotBlank() },
                                        dateAdded = o.optString("date_added", "").takeIf { it.isNotBlank() },
                                        takenAt = o.optString("taken_at", "").takeIf { it.isNotBlank() },
                                        userId = if (o.has("user_id")) o.getInt("user_id") else null
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    photos.clear()
                                    photos.addAll(list)
                                    isRefreshingAlbumImages = false
                                    onImagesLoaded?.invoke(list)
                                }
                            }
                            .onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    isRefreshingAlbumImages = false
                                    handle401(context, e)
                                }
                            }
                    }
                }

                fun refreshHomeData() {
                    scope.launch {
                        val t = AuthTokenStore.get() ?: return@launch
                        withContext(Dispatchers.IO) {
                            BackendClient.getArray("/albums", t)
                                .onSuccess { arr ->
                                    val list = (0 until arr.length()).map { i ->
                                        arr.getJSONObject(i).parseAlbumUi()
                                    }
                                    withContext(Dispatchers.Main) { albums = list }
                                    val currentUserId = AuthTokenStore.getUserId()
                                    val myPhotos = list.find { it.name == "My Photos" && it.ownerId == currentUserId }
                                    if (myPhotos != null) {
                                        BackendClient.getArray("/images/album/${myPhotos.id}", t)
                                            .onSuccess { imgArr ->
                                                val photoList = (0 until imgArr.length()).map { i ->
                                                    val o = imgArr.getJSONObject(i)
                                                    val lat = if (o.isNull("latitude")) null else o.getDouble("latitude")
                                                    val lon = if (o.isNull("longitude")) null else o.getDouble("longitude")
                                            AlbumPhotoUi(
                                                id = o.getInt("id").toString(),
                                                imageUrl = o.getString("image_url"),
                                                audioUrl = o.optString("audio_url", "").takeIf { it.isNotBlank() },
                                                caption = o.optString("caption", "").takeIf { it.isNotBlank() },
                                                latitude = lat,
                                                longitude = lon,
                                                locationName = o.optString("location_name", "").takeIf { it.isNotBlank() },
                                                dateAdded = o.optString("date_added", "").takeIf { it.isNotBlank() },
                                                takenAt = o.optString("taken_at", "").takeIf { it.isNotBlank() },
                                                userId = if (o.has("user_id")) o.getInt("user_id") else null
                                            )
                                                }
                                                withContext(Dispatchers.Main) {
                                                    standalonePhotos = photoList
                                                    myPhotosAlbumId = myPhotos.id
                                                }
                                                loadAlbumSuggestion()
                                            }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            standalonePhotos = emptyList()
                                            myPhotosAlbumId = null
                                        }
                                        loadAlbumSuggestion()
                                    }
                                }
                        }
                    }
                }

                fun processNextPhoto(photos: List<SelectedImage>, index: Int, targetAlbumId: Int? = null) {
                    if (index >= photos.size) {
                        // All photos processed
                        pendingMultiplePhotos = null
                        currentMultiPhotoIndex = 0
                        if (targetAlbumId != null) {
                            loadAlbumImages() // Refresh album view
                        } else {
                            refreshHomeData() // Refresh home view
                        }
                        return
                    }

                    val photo = photos[index]
                    scope.launch(Dispatchers.IO) {
                        val metadata = extractPhotoMetadata(context, photo.uri)
                        val takenAt = metadata?.dateTimeOriginal?.let { exifDateTimeToIso(it) }
                            ?: metadata?.dateTaken?.let { exifDateTimeToIso(it) }
                        var lat = metadata?.latitude
                        var lon = metadata?.longitude
                        if (lat == null || lon == null) {
                            val currentLoc = getCurrentLocationOrNull()
                            if (currentLoc != null) {
                                lat = currentLoc.latitude
                                lon = currentLoc.longitude
                            }
                        }

                        withContext(Dispatchers.Main) {
                            val meta = HomePhotoMetadata(
                                latitude = lat,
                                longitude = lon,
                                takenAt = takenAt,
                                imageUri = photo.uri,
                                imageFile = photo.file
                            )

                            if (targetAlbumId != null) {
                                pendingAlbumPhotoEntry = Pair(meta, targetAlbumId)
                            } else {
                                pendingHomePhotoEntry = meta
                            }
                            currentMultiPhotoIndex = index
                        }
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
                    val file = pendingCameraFile
                    val uri = pendingCameraUri
                    pendingCameraFile = null
                    pendingCameraUri = null
                    if (success && file != null && file.exists() && uri != null) {
                        cameraBuffer.add(SelectedImage(uri, file))
                    }
                    if (cameraBuffer.isNotEmpty()) {
                        showCameraBufferOverlay = true
                    }
                }

                lateinit var launchCamera: (Int?) -> Unit
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    val targetAlbumId = pendingCameraPermissionTargetAlbumId
                    pendingCameraPermissionTargetAlbumId = null
                    if (granted) {
                        launchCamera(targetAlbumId)
                    } else {
                        cameraErrorMessage = "Camera permission is required to take photos. Please allow camera access and try again."
                    }
                }

                launchCamera = { targetAlbumId ->
                    cameraTargetAlbumId = targetAlbumId
                    try {
                        val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                        pendingCameraFile = file
                        pendingCameraUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        pendingCameraUri?.let { takePictureLauncher.launch(it) }
                    } catch (e: SecurityException) {
                        pendingCameraFile = null
                        pendingCameraUri = null
                        cameraErrorMessage = "Unable to open camera because permission was denied."
                    } catch (e: Exception) {
                        pendingCameraFile = null
                        pendingCameraUri = null
                        cameraErrorMessage = "Unable to open camera right now. Please try again."
                    }
                }

                fun requestCameraAndLaunch(targetAlbumId: Int?) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        launchCamera(targetAlbumId)
                    } else {
                        pendingCameraPermissionTargetAlbumId = targetAlbumId
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                                var lat = metadata?.latitude
                                var lon = metadata?.longitude
                                if (lat == null || lon == null) {
                                    val currentLoc = getCurrentLocationOrNull()
                                    if (currentLoc != null) {
                                        lat = currentLoc.latitude
                                        lon = currentLoc.longitude
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    val meta = HomePhotoMetadata(
                                        latitude = lat,
                                        longitude = lon,
                                        takenAt = takenAt,
                                        imageUri = uri,
                                        imageFile = file
                                    )
                                    if (albumId != null) {
                                        pendingAlbumPhotoEntry = Pair(meta, albumId)
                                    } else {
                                        pendingHomePhotoEntry = meta
                                    }
                                }
                            }
                        }
                    }
                }

                val pickMultiplePhotosLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetMultipleContents()
                ) { uris: List<Uri> ->
                    if (uris.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            val selectedImages = uris.mapNotNull { uri ->
                                try {
                                    val file = File(context.cacheDir, "selected_${System.currentTimeMillis()}_${uri.hashCode()}.jpg")
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        file.outputStream().use { input.copyTo(it) }
                                    }
                                    if (file.exists()) SelectedImage(uri, file) else null
                                } catch (e: Exception) {
                                    null
                                }
                            }.take(10) // Limit to 10 images

                            withContext(Dispatchers.Main) {
                                if (selectedImages.isNotEmpty()) {
                                    pendingMultiplePhotos = selectedImages
                                    currentMultiPhotoIndex = 0
                                    // Pass the selectedAlbumId as target if we're in an album
                                    processNextPhoto(selectedImages, 0, selectedAlbumId)
                                }
                            }
                        }
                    }
                }

                fun onAddPhoto(source: AddPhotoSource) {
                    when (source) {
                        AddPhotoSource.Camera -> requestCameraAndLaunch(selectedAlbumId)
                        AddPhotoSource.Photos -> {
                            if (selectedAlbumId != null) {
                                pickMultiplePhotosLauncher.launch("image/*")
                            } else {
                                pickPhotoLauncher.launch(arrayOf("image/*"))
                            }
                        }
                    }
                }

                fun onHomeAction(action: HomeAddAction) {
                    when (action) {
                        HomeAddAction.Camera -> requestCameraAndLaunch(null)
                        HomeAddAction.Photos -> pickMultiplePhotosLauncher.launch("image/*")
                        HomeAddAction.MakeAlbum -> {
                            showCreateAlbumDialog = true
                            newAlbumName = ""
                        }
                    }
                }

                val selectedAlbumName = selectedAlbumId?.let { id -> albums.find { it.id == id }?.titleForHome() }

                @OptIn(ExperimentalMaterial3Api::class)
                Box(Modifier.fillMaxSize()) {
                    if (!cameraErrorMessage.isNullOrBlank()) {
                        AlertDialog(
                            onDismissRequest = { cameraErrorMessage = null },
                            title = { Text("Camera unavailable") },
                            text = { Text(cameraErrorMessage ?: "") },
                            confirmButton = {
                                TextButton(onClick = { cameraErrorMessage = null }) {
                                    Text("OK")
                                }
                            }
                        )
                    }

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

                        pendingAlbumPhotoEntry != null -> {
                            BackHandler {
                                // Skip current photo and move to next
                                pendingMultiplePhotos?.let { photos ->
                                    val nextIndex = currentMultiPhotoIndex + 1
                                    if (nextIndex < photos.size) {
                                        val nextPhoto = photos[nextIndex]
                                        scope.launch(Dispatchers.IO) {
                                            val metadata = extractPhotoMetadata(context, nextPhoto.uri)
                                            val takenAt = metadata?.dateTimeOriginal?.let { exifDateTimeToIso(it) }
                                                ?: metadata?.dateTaken?.let { exifDateTimeToIso(it) }
                                            withContext(Dispatchers.Main) {
                                                val meta = HomePhotoMetadata(
                                                    latitude = metadata?.latitude,
                                                    longitude = metadata?.longitude,
                                                    takenAt = takenAt,
                                                    imageUri = nextPhoto.uri,
                                                    imageFile = nextPhoto.file
                                                )
                                                pendingAlbumPhotoEntry = Pair(meta, selectedAlbumId!!)
                                                currentMultiPhotoIndex = nextIndex
                                            }
                                        }
                                    } else {
                                        pendingMultiplePhotos = null
                                        currentMultiPhotoIndex = 0
                                        pendingAlbumPhotoEntry = null
                                        loadAlbumImages()
                                    }
                                } ?: run {
                                    pendingAlbumPhotoEntry = null
                                }
                            }
                            HomePhotoEntryScreen(
                                metadata = pendingAlbumPhotoEntry!!.first,
                                targetAlbumId = pendingAlbumPhotoEntry!!.second,
                                albumName = selectedAlbumName,
                                onBack = {
                                    pendingMultiplePhotos?.let { photos ->
                                        val nextIndex = currentMultiPhotoIndex + 1
                                        if (nextIndex < photos.size) {
                                            val nextPhoto = photos[nextIndex]
                                            scope.launch(Dispatchers.IO) {
                                                val metadata = extractPhotoMetadata(context, nextPhoto.uri)
                                                val takenAt = metadata?.dateTimeOriginal?.let { exifDateTimeToIso(it) }
                                                    ?: metadata?.dateTaken?.let { exifDateTimeToIso(it) }
                                                withContext(Dispatchers.Main) {
                                                    val meta = HomePhotoMetadata(
                                                        latitude = metadata?.latitude,
                                                        longitude = metadata?.longitude,
                                                        takenAt = takenAt,
                                                        imageUri = nextPhoto.uri,
                                                        imageFile = nextPhoto.file
                                                    )
                                                    pendingAlbumPhotoEntry = Pair(meta, selectedAlbumId!!)
                                                    currentMultiPhotoIndex = nextIndex
                                                }
                                            }
                                        } else {
                                            pendingMultiplePhotos = null
                                            currentMultiPhotoIndex = 0
                                            pendingAlbumPhotoEntry = null
                                            loadAlbumImages()
                                        }
                                    } ?: run {
                                        pendingAlbumPhotoEntry = null
                                    }
                                },
                                onPhotoSaved = { addedToAlbumId ->
                                    pendingMultiplePhotos?.let { photos ->
                                        val nextIndex = currentMultiPhotoIndex + 1
                                        if (nextIndex < photos.size) {
                                            val nextPhoto = photos[nextIndex]
                                            scope.launch(Dispatchers.IO) {
                                                val metadata = extractPhotoMetadata(context, nextPhoto.uri)
                                                val takenAt = metadata?.dateTimeOriginal?.let { exifDateTimeToIso(it) }
                                                    ?: metadata?.dateTaken?.let { exifDateTimeToIso(it) }
                                                withContext(Dispatchers.Main) {
                                                    val meta = HomePhotoMetadata(
                                                        latitude = metadata?.latitude,
                                                        longitude = metadata?.longitude,
                                                        takenAt = takenAt,
                                                        imageUri = nextPhoto.uri,
                                                        imageFile = nextPhoto.file
                                                    )
                                                    pendingAlbumPhotoEntry = Pair(meta, selectedAlbumId!!)
                                                    currentMultiPhotoIndex = nextIndex
                                                }
                                            }
                                        } else {
                                            pendingMultiplePhotos = null
                                            currentMultiPhotoIndex = 0
                                            pendingAlbumPhotoEntry = null
                                            loadAlbumImages()
                                        }
                                    } ?: run {
                                        pendingAlbumPhotoEntry = null
                                        if (addedToAlbumId != null) loadAlbumImages()
                                    }
                                }
                            )
                        }

                        pendingHomePhotoEntry != null -> {
                            BackHandler {
                                pendingMultiplePhotos?.let { photos ->
                                    val nextIndex = currentMultiPhotoIndex + 1
                                    if (nextIndex < photos.size) {
                                        val nextPhoto = photos[nextIndex]
                                        scope.launch(Dispatchers.IO) {
                                            val metadata = extractPhotoMetadata(context, nextPhoto.uri)
                                            val takenAt = metadata?.dateTimeOriginal?.let { exifDateTimeToIso(it) }
                                                ?: metadata?.dateTaken?.let { exifDateTimeToIso(it) }
                                            withContext(Dispatchers.Main) {
                                                val meta = HomePhotoMetadata(
                                                    latitude = metadata?.latitude,
                                                    longitude = metadata?.longitude,
                                                    takenAt = takenAt,
                                                    imageUri = nextPhoto.uri,
                                                    imageFile = nextPhoto.file
                                                )
                                                pendingHomePhotoEntry = meta
                                                currentMultiPhotoIndex = nextIndex
                                            }
                                        }
                                    } else {
                                        pendingMultiplePhotos = null
                                        currentMultiPhotoIndex = 0
                                        pendingHomePhotoEntry = null
                                        refreshHomeData()
                                    }
                                } ?: run {
                                    pendingHomePhotoEntry = null
                                }
                            }
                            HomePhotoEntryScreen(
                                metadata = pendingHomePhotoEntry!!,
                                onBack = {
                                    pendingMultiplePhotos?.let { photos ->
                                        val nextIndex = currentMultiPhotoIndex + 1
                                        if (nextIndex < photos.size) {
                                            val nextPhoto = photos[nextIndex]
                                            scope.launch(Dispatchers.IO) {
                                                val metadata = extractPhotoMetadata(context, nextPhoto.uri)
                                                val takenAt = metadata?.dateTimeOriginal?.let { exifDateTimeToIso(it) }
                                                    ?: metadata?.dateTaken?.let { exifDateTimeToIso(it) }
                                                withContext(Dispatchers.Main) {
                                                    val meta = HomePhotoMetadata(
                                                        latitude = metadata?.latitude,
                                                        longitude = metadata?.longitude,
                                                        takenAt = takenAt,
                                                        imageUri = nextPhoto.uri,
                                                        imageFile = nextPhoto.file
                                                    )
                                                    pendingHomePhotoEntry = meta
                                                    currentMultiPhotoIndex = nextIndex
                                                }
                                            }
                                        } else {
                                            pendingMultiplePhotos = null
                                            currentMultiPhotoIndex = 0
                                            pendingHomePhotoEntry = null
                                            refreshHomeData()
                                        }
                                    } ?: run {
                                        pendingHomePhotoEntry = null
                                    }
                                },
                                onPhotoSaved = { addedToAlbumId ->
                                    pendingMultiplePhotos?.let { photos ->
                                        val nextIndex = currentMultiPhotoIndex + 1
                                        if (nextIndex < photos.size) {
                                            val nextPhoto = photos[nextIndex]
                                            scope.launch(Dispatchers.IO) {
                                                val metadata = extractPhotoMetadata(context, nextPhoto.uri)
                                                val takenAt = metadata?.dateTimeOriginal?.let { exifDateTimeToIso(it) }
                                                    ?: metadata?.dateTaken?.let { exifDateTimeToIso(it) }
                                                withContext(Dispatchers.Main) {
                                                    val meta = HomePhotoMetadata(
                                                        latitude = metadata?.latitude,
                                                        longitude = metadata?.longitude,
                                                        takenAt = takenAt,
                                                        imageUri = nextPhoto.uri,
                                                        imageFile = nextPhoto.file
                                                    )
                                                    pendingHomePhotoEntry = meta
                                                    currentMultiPhotoIndex = nextIndex
                                                }
                                            }
                                        } else {
                                            pendingMultiplePhotos = null
                                            currentMultiPhotoIndex = 0
                                            pendingHomePhotoEntry = null
                                            refreshHomeData()
                                        }
                                    } ?: run {
                                        pendingHomePhotoEntry = null
                                        if (addedToAlbumId == null) refreshHomeData()
                                    }
                                }
                            )
                        }

                        pendingStandalonePhotoDetail != null -> {
                            val (aid, photoId) = pendingStandalonePhotoDetail!!
                            val list = standalonePhotos
                            val displayIdx = currentPhotoIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                            val photoToShow = list.getOrNull(displayIdx) ?: list.find { it.id == photoId }
                            if (photoToShow != null) {
                                LaunchedEffect(Unit) {
                                    selectedAlbumId = aid
                                    selectedPhotoId = list.getOrNull(displayIdx)?.id ?: photoId
                                    photos.clear()
                                    photos.addAll(list)
                                    photoDetailFromStandalone = true
                                    pendingStandalonePhotoDetail = null
                                }
                                val albumName = albums.find { it.id == aid }?.titleForHome() ?: ""
                                val albumOwnerId = albums.find { it.id == aid }?.ownerId
                                val currentUserId = AuthTokenStore.getUserId()
                                val canDeletePhoto = (currentUserId != null && albumOwnerId != null && currentUserId == albumOwnerId) ||
                                        (photoToShow.userId != null && currentUserId != null && currentUserId == photoToShow.userId)
                                val canEditPhoto = currentUserId != null && currentUserId == photoToShow.userId

                                val uploaderName = null
                                val uploaderProfilePic = null

                                PhotoDetailScreen(
                                    photo = photoToShow,
                                    albumName = albumName,
                                    mock = getPhotoDetailMock(albumName, photoToShow.id),
                                    onBack = {
                                        pendingStandalonePhotoDetail = null
                                        selectedAlbumId = null
                                        selectedPhotoId = null
                                        photoDetailFromStandalone = false
                                    },
                                    canDeletePhoto = canDeletePhoto,
                                    canEditPhoto = canEditPhoto,
                                    allPhotos = if (list.size > 1) list else null,
                                    currentPhotoIndex = displayIdx,
                                    onPhotoIndexChange = { newIdx ->
                                        currentPhotoIndex = newIdx.coerceIn(0, list.size - 1)
                                        selectedPhotoId = list.getOrNull(newIdx)?.id
                                    },
                                    onDeleteAudio = {
                                        scope.launch {
                                            val t = AuthTokenStore.get() ?: return@launch
                                            val body = JSONObject().put("audio_url", JSONObject.NULL)
                                            val result = BackendClient.put("/images/${photoToShow.id}", body, token = t)
                                            withContext(Dispatchers.Main) {
                                                result.onFailure { handle401(context, it) }
                                                if (result.isSuccess) {
                                                    photoIdToSelectAfterRefresh = photoToShow.id
                                                    loadAlbumImages()
                                                    selectedAlbumId = aid
                                                    selectedPhotoId = photoToShow.id
                                                    photos.clear()
                                                    photos.addAll(standalonePhotos)
                                                    currentPhotoIndex = list.indexOfFirst { it.id == photoToShow.id }.coerceAtLeast(0)
                                                    photoDetailFromStandalone = true
                                                    pendingStandalonePhotoDetail = null
                                                }
                                                Toast.makeText(context, if (result.isSuccess) "Audio removed" else result.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onSave = { caption, takenAt, latitude, longitude, audioFilePath ->
                                        scope.launch {
                                            val t = AuthTokenStore.get() ?: return@launch
                                            var audioUrl: String? = null
                                            if (!audioFilePath.isNullOrBlank()) {
                                                val file = java.io.File(audioFilePath)
                                                if (file.exists()) audioUrl = CloudinaryHelper.uploadAudio(context, file, t)
                                            }
                                            val body = JSONObject().apply {
                                                put("caption", caption)
                                                takenAt?.takeIf { it.isNotBlank() }?.let { put("taken_at", it) }
                                                latitude?.let { put("latitude", it) }
                                                longitude?.let { put("longitude", it) }
                                                audioUrl?.let { put("audio_url", it) }
                                            }
                                            val result = BackendClient.put("/images/${photoToShow.id}", body, token = t)
                                            withContext(Dispatchers.Main) {
                                                result.onFailure { handle401(context, it) }
                                                if (result.isSuccess) {
                                                    photoIdToSelectAfterRefresh = photoToShow.id
                                                    loadAlbumImages()
                                                    selectedAlbumId = aid
                                                    selectedPhotoId = photoToShow.id
                                                    photos.clear()
                                                    photos.addAll(standalonePhotos)
                                                    currentPhotoIndex = list.indexOfFirst { it.id == photoToShow.id }.coerceAtLeast(0)
                                                    photoDetailFromStandalone = true
                                                    pendingStandalonePhotoDetail = null
                                                }
                                                Toast.makeText(context, if (result.isSuccess) "Saved" else result.exceptionOrNull()?.message ?: "Failed to save", Toast.LENGTH_SHORT).show()
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
                                            val result = BackendClient.delete("/images/${photoToShow.id}", token = t)
                                            withContext(Dispatchers.Main) {
                                                result.onFailure { e ->
                                                    handle401(context, e)
                                                    Toast.makeText(context, (e as? BackendException)?.message ?: "Failed to delete", Toast.LENGTH_SHORT).show()
                                                }
                                                if (result.isSuccess) {
                                                    val newList = list.filter { it.id != photoToShow.id }
                                                    photos.clear()
                                                    if (newList.isEmpty()) photos.replaceWithAlbumGridSkeletons()
                                                    else photos.addAll(newList)
                                                    standalonePhotos = newList
                                                    pendingStandalonePhotoDetail = null
                                                    selectedAlbumId = null
                                                    selectedPhotoId = null
                                                    photoDetailFromStandalone = false
                                                    Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    uploaderName = uploaderName,
                                    uploaderProfilePicUrl = uploaderProfilePic
                                )
                            } else {
                                pendingStandalonePhotoDetail = null
                            }
                        }

                        selectedAlbumId != null && selectedPhotoId != null -> {
                            val filteredAlbumPhotos = photos.sortedAndFiltered(albumSort)
                            LaunchedEffect(photoIdToSelectAfterRefresh, filteredAlbumPhotos) {
                                photoIdToSelectAfterRefresh?.let { id ->
                                    val idx = filteredAlbumPhotos.indexOfFirst { it.id == id }
                                    if (idx >= 0) {
                                        currentPhotoIndex = idx
                                        selectedPhotoId = id
                                    }
                                    photoIdToSelectAfterRefresh = null
                                }
                            }
                            LaunchedEffect(selectedPhotoId, filteredAlbumPhotos) {
                                if (photoIdToSelectAfterRefresh == null) {
                                    selectedPhotoId?.let { id ->
                                        val idx = filteredAlbumPhotos.indexOfFirst { it.id == id }
                                        if (idx >= 0) currentPhotoIndex = idx
                                    }
                                }
                            }
                            BackHandler {
                                if (photoDetailFromStandalone) {
                                    selectedPhotoId = null
                                    selectedAlbumId = null
                                    photoDetailFromStandalone = false
                                } else {
                                    selectedPhotoId = null
                                }
                            }
                            val albumName = selectedAlbumName ?: ""
                            val photo = filteredAlbumPhotos.getOrNull(currentPhotoIndex) ?: filteredAlbumPhotos.find { it.id == selectedPhotoId }
                            if (photo != null || isRefreshingAlbumImages) {
                                if (photo != null) {
                                    val photoToDelete = photo
                                    val albumOwnerId = selectedAlbumId?.let { aid -> albums.find { it.id == aid }?.ownerId }
                                    val currentUserId = AuthTokenStore.getUserId()
                                    val canDeletePhoto = (currentUserId != null && albumOwnerId != null && currentUserId == albumOwnerId) ||
                                            (photoToDelete.userId != null && currentUserId != null && currentUserId == photoToDelete.userId)
                                    val canEditPhoto = currentUserId != null && currentUserId == photoToDelete.userId

                                    // 👇 NEW: Look up uploader info from albumMembers
                                    val uploader = albumMembers.find { it.id == photoToDelete.userId?.toString() }
                                    val uploaderName = uploader?.username
                                    val uploaderProfilePic = uploader?.profilePictureUrl

                                    PhotoDetailScreen(
                                        photo = photoToDelete,
                                        albumName = albumName,
                                        mock = getPhotoDetailMock(albumName, photoToDelete.id),
                                        onBack = {
                                            if (photoDetailFromStandalone) {
                                                selectedPhotoId = null
                                                selectedAlbumId = null
                                                photoDetailFromStandalone = false
                                            } else {
                                                selectedPhotoId = null
                                            }
                                        },
                                        canDeletePhoto = canDeletePhoto,
                                        canEditPhoto = canEditPhoto,
                                        allPhotos = if (filteredAlbumPhotos.size > 1) filteredAlbumPhotos else null,
                                        currentPhotoIndex = currentPhotoIndex,
                                        onPhotoIndexChange = { currentPhotoIndex = it.coerceIn(0, filteredAlbumPhotos.size - 1) },
                                        onDeleteAudio = {
                                            scope.launch {
                                                val t = AuthTokenStore.get() ?: return@launch
                                                val body = JSONObject().put("audio_url", JSONObject.NULL)
                                                val result = BackendClient.put("/images/${photoToDelete.id}", body, token = t)
                                                withContext(Dispatchers.Main) {
                                                    result.onFailure { handle401(context, it) }
                                                    if (result.isSuccess) {
                                                        photoIdToSelectAfterRefresh = selectedPhotoId
                                                        loadAlbumImages()
                                                    }
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
                                                    if (result.isSuccess) {
                                                        photoIdToSelectAfterRefresh = selectedPhotoId
                                                        loadAlbumImages()
                                                    }
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
                                                        if (photoDetailFromStandalone) {
                                                            selectedAlbumId = null
                                                            photoDetailFromStandalone = false
                                                        }
                                                        Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        // 👇 NEW: Pass uploader info
                                        uploaderName = uploaderName,
                                        uploaderProfilePicUrl = uploaderProfilePic
                                    )
                                } else {
                                    if (!isRefreshingAlbumImages) selectedPhotoId = null
                                }
                            } else {
                                selectedPhotoId = null
                            }
                        }

                        selectedAlbumId != null -> {
                            val albumId = selectedAlbumId!!
                            val currentUserId = AuthTokenStore.getUserId()
                            val isAlbumOwner = albums.find { it.id == albumId }?.ownerId?.let { it == currentUserId } == true
                            if (showDeleteAlbumDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteAlbumDialog = false },
                                    title = { Text(if (isAlbumOwner) "Delete album?" else "Remove album?") },
                                    text = {
                                        Text(
                                            if (isAlbumOwner)
                                                "This album will be deleted for everyone. This cannot be undone."
                                            else
                                                "This will remove the album from your list only. Others will still see it."
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showDeleteAlbumDialog = false
                                            scope.launch {
                                                val t = AuthTokenStore.get() ?: return@launch
                                                val path = if (isAlbumOwner) "/albums/$albumId" else "/albums/$albumId/leave"
                                                val result = BackendClient.delete(path, token = t)
                                                withContext(Dispatchers.Main) {
                                                    result.onFailure { handle401(context, it); return@withContext }
                                                    selectedAlbumId = null
                                                    BackendClient.getArray("/albums", t)
                                                        .onSuccess { arr ->
                                                            val list = (0 until arr.length()).map { i ->
                                                                arr.getJSONObject(i).parseAlbumUi()
                                                            }
                                                            albums = list
                                                        }
                                                    Toast.makeText(
                                                        context,
                                                        if (isAlbumOwner) "Album deleted" else "Album removed from your list",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }) {
                                            Text(if (isAlbumOwner) "Delete" else "Remove", color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteAlbumDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                            val filteredAlbumPhotos = photos.sortedAndFiltered(albumSort)
                            AlbumScreen(
                                modifier = Modifier.fillMaxSize(),
                                albumName = selectedAlbumName ?: "",
                                photos = filteredAlbumPhotos,
                                friends = albumMembers,
                                isSharedAlbum = albumMembers.isNotEmpty(),
                                showMap = albumShowMap,
                                onShowMapChange = { albumShowMap = it },
                                sort = albumSort,
                                onSortChange = { newSort ->
                                    albumSort = newSort
                                    AlbumSortStore.save(albumId, newSort)
                                },
                                currentUserId = AuthTokenStore.getUserId(),
                                isAlbumOwner = isAlbumOwner,
                                onBack = { selectedAlbumId = null },
                                onEditAlbumName = {},
                                onSaveAlbumName = { },
                                onDeleteAlbum = { showDeleteAlbumDialog = true },
                                onAddFriend = {
                                    pendingAlbumIdForFriend = albumId
                                    showFriendPicker = true
                                },
                                onPhotoClick = { selectedPhotoId = it },
                                onSameLocationClusterClick = { photoId ->
                                    albumSort = AlbumSort(AlbumSortKind.BY_LOCATION)
                                    AlbumSortStore.save(albumId, albumSort)
                                    selectedPhotoId = photoId
                                },
                                onAddPhoto = ::onAddPhoto,
                                onSaveEdits = { newName, imageIdsToDelete ->
                                    if (newName != null || imageIdsToDelete.isNotEmpty()) {
                                        scope.launch {
                                            val t = AuthTokenStore.get() ?: return@launch
                                            if (newName != null && newName.isNotBlank()) {
                                                val body = JSONObject().put("name", newName)
                                                BackendClient.put("/albums/$albumId", body, token = t)
                                                    .onSuccess {
                                                        withContext(Dispatchers.Main) {
                                                            albums = albums.map { if (it.id == albumId) it.copy(name = newName) else it }
                                                        }
                                                    }
                                            }
                                            imageIdsToDelete.forEach { photoId ->
                                                BackendClient.delete("/images/$photoId", token = t)
                                            }
                                            withContext(Dispatchers.Main) {
                                                loadAlbumImages()
                                                Toast.makeText(context, "Changes saved", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onSharePhotos = { selectedPhotos ->
                                    scope.launch {
                                        sharePhotos(context, selectedPhotos)
                                    }
                                }
                            )
                        }

                        else -> {
                            HomeScreen(
                                albums = albums,
                                standalonePhotos = standalonePhotos,
                                myPhotosAlbumId = myPhotosAlbumId,
                                isHomeDataLoading = isHomeDataLoading,
                                profilePictureUrl = currentUserProfilePictureUrl,
                                albumSuggestion = albumSuggestion,
                                albumSuggestionBusy = albumSuggestionBusy,
                                onAcceptAlbumSuggestion = acceptSuggestion@{
                                    val sid = albumSuggestion?.id ?: return@acceptSuggestion
                                    scope.launch {
                                        val t = AuthTokenStore.get() ?: return@launch
                                        albumSuggestionBusy = true
                                        withContext(Dispatchers.IO) {
                                            BackendClient.post(
                                                "/album-suggestions/$sid/accept",
                                                body = null,
                                                token = t,
                                            )
                                                .onSuccess { j ->
                                                    val newAlbumId = j.getInt("album_id")
                                                    withContext(Dispatchers.Main) {
                                                        albumSuggestionBusy = false
                                                        albumSuggestion = null
                                                        photoDetailFromStandalone = false
                                                        selectedAlbumId = newAlbumId
                                                        refreshHomeData()
                                                    }
                                                }
                                                .onFailure { e ->
                                                    withContext(Dispatchers.Main) {
                                                        albumSuggestionBusy = false
                                                        handle401(context, e)
                                                        Toast.makeText(
                                                            context,
                                                            (e as? BackendException)?.message
                                                                ?: e.message
                                                                ?: "Could not create album",
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                }
                                        }
                                    }
                                },
                                onRejectAlbumSuggestion = rejectSuggestion@{
                                    val sid = albumSuggestion?.id ?: return@rejectSuggestion
                                    scope.launch {
                                        val t = AuthTokenStore.get() ?: return@launch
                                        albumSuggestionBusy = true
                                        withContext(Dispatchers.IO) {
                                            BackendClient.post(
                                                "/album-suggestions/$sid/reject",
                                                body = null,
                                                token = t,
                                            )
                                                .onSuccess {
                                                    withContext(Dispatchers.Main) {
                                                        albumSuggestionBusy = false
                                                        albumSuggestion = null
                                                    }
                                                    loadAlbumSuggestion()
                                                }
                                                .onFailure { e ->
                                                    withContext(Dispatchers.Main) {
                                                        albumSuggestionBusy = false
                                                        handle401(context, e)
                                                        Toast.makeText(
                                                            context,
                                                            (e as? BackendException)?.message
                                                                ?: e.message
                                                                ?: "Could not dismiss",
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                }
                                        }
                                    }
                                },
                                onProfileClick = { profileLauncher.launch(Intent(context, ProfileActivity::class.java)) },
                                onAlbumClick = {
                                    selectedAlbumId = it
                                    photoDetailFromStandalone = false
                                },
                                onStandalonePhotoClick = { photo ->
                                    val aid = myPhotosAlbumId
                                    if (aid != null) {
                                        currentPhotoIndex = standalonePhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
                                        pendingStandalonePhotoDetail = Pair(aid, photo.id)
                                    }
                                },
                                onAction = ::onHomeAction
                            )
                        }
                    }

                    // New album creation dialog
                    if (showCreateAlbumDialog) {
                        AlertDialog(
                            onDismissRequest = { showCreateAlbumDialog = false },
                            title = { Text("Create new album") },
                            text = {
                                OutlinedTextField(
                                    value = newAlbumName,
                                    onValueChange = { newAlbumName = it },
                                    label = { Text("Album name") },
                                    singleLine = true,
                                    enabled = !isCreatingAlbum
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (newAlbumName.isBlank()) return@TextButton
                                        isCreatingAlbum = true
                                        scope.launch {
                                            val token = AuthTokenStore.get()
                                            if (token == null) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show()
                                                    isCreatingAlbum = false
                                                    showCreateAlbumDialog = false
                                                }
                                                return@launch
                                            }
                                            val body = JSONObject().put("name", newAlbumName.trim())
                                            val result = BackendClient.post("/albums", body, token = token)
                                            withContext(Dispatchers.Main) {
                                                isCreatingAlbum = false
                                                if (result.isSuccess) {
                                                    val newId = result.getOrNull()?.getInt("id")
                                                    if (newId != null) {
                                                        // Add to local list immediately so the name appears
                                                        val newAlbum = AlbumUi(
                                                            id = newId,
                                                            name = newAlbumName.trim(),
                                                            ownerId = AuthTokenStore.getUserId(),
                                                            coverImageUrls = emptyList()
                                                        )
                                                        albums = albums + newAlbum
                                                        selectedAlbumId = newId
                                                        showCreateAlbumDialog = false
                                                        // Refresh in background to get accurate data (cover images etc.)
                                                        refreshHomeData()
                                                    } else {
                                                        Toast.makeText(context, "Failed to create album", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to create album"
                                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = newAlbumName.isNotBlank() && !isCreatingAlbum
                                ) {
                                    if (isCreatingAlbum) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else {
                                        Text("Create")
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showCreateAlbumDialog = false },
                                    enabled = !isCreatingAlbum
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (showCameraBufferOverlay && cameraBuffer.isNotEmpty()) {
                        ModalBottomSheet(
                            onDismissRequest = {
                                showCameraBufferOverlay = false
                                cameraBuffer.clear()
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "${cameraBuffer.size} photo${if (cameraBuffer.size == 1) "" else "s"} captured",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Button(
                                    onClick = {
                                        showCameraBufferOverlay = false
                                        requestCameraAndLaunch(cameraTargetAlbumId)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Take another")
                                }
                                Button(
                                    onClick = {
                                        val toUpload = cameraBuffer.toList()
                                        cameraBuffer.clear()
                                        showCameraBufferOverlay = false
                                        pendingMultiplePhotos = toUpload
                                        currentMultiPhotoIndex = 0
                                        processNextPhoto(toUpload, 0, cameraTargetAlbumId)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Upload ${cameraBuffer.size} photo${if (cameraBuffer.size == 1) "" else "s"}")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
