package com.example.mementoandroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.api.BackendException
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import com.example.mementoandroid.ui.album.AddPhotoSource
import com.example.mementoandroid.ui.album.AlbumPhotoUi
import com.example.mementoandroid.ui.album.AlbumScreen
import com.example.mementoandroid.ui.album.AlbumUi
import com.example.mementoandroid.ui.album.FriendUi
import com.example.mementoandroid.ui.album.PhotoDetailScreen
import com.example.mementoandroid.ui.album.getPhotoDetailMock
import org.json.JSONObject
import com.example.mementoandroid.util.AuthTokenStore
import com.example.mementoandroid.util.CloudinaryHelper
import com.example.mementoandroid.util.extractPhotoMetadata
import com.example.mementoandroid.util.logPhotoMetadata
import com.example.mementoandroid.util.verifyAndLogLocationStrippingCause
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



private const val TAG = "MainActivity"

private fun handle401(context: ComponentActivity, e: Throwable) {
    if (e is BackendException && e.statusCode == 401) {
        AuthTokenStore.clear()
        context.startActivity(Intent(context, LoginActivity::class.java))
        context.finish()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthTokenStore.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MementoAndroidTheme {
                val context = LocalContext.current as ComponentActivity
                var selectedAlbumId by rememberSaveable { mutableStateOf<Int?>(null) }
                var selectedPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
                var albums by remember { mutableStateOf<List<AlbumUi>>(emptyList()) }
                val photos = remember { mutableStateListOf<AlbumPhotoUi>() }
                val demoFriends = remember {
                    listOf(
                        FriendUi("1", "isla"),
                        FriendUi("2", "blair"),
                        FriendUi("3", "shannon"),
                        FriendUi("4", "nick")
                    )
                }
                var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
                var pendingCameraFile by remember { mutableStateOf<File?>(null) }

                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    val t = AuthTokenStore.get() ?: return@LaunchedEffect
                    BackendClient.getArray("/albums", t)
                        .onSuccess { arr ->
                            val list = (0 until arr.length()).map { i ->
                                val o = arr.getJSONObject(i)
                                AlbumUi(o.getInt("id"), o.getString("name"))
                            }
                            withContext(Dispatchers.Main) { albums = list }
                        }
                        .onFailure { handle401(context, it) }
                }

                LaunchedEffect(selectedAlbumId) {
                    if (selectedAlbumId == null) return@LaunchedEffect
                    val t = AuthTokenStore.get() ?: return@LaunchedEffect
                    BackendClient.getArray("/images/album/$selectedAlbumId", t).onSuccess { arr ->
                        val list = (0 until arr.length()).map { i ->
                            val o = arr.getJSONObject(i)
                            val lat = if (o.isNull("latitude")) null else o.getDouble("latitude")
                            val lon = if (o.isNull("longitude")) null else o.getDouble("longitude")
                            AlbumPhotoUi(
                                id = o.getInt("id").toString(),
                                imageUrl = o.getString("image_url"),
                                caption = o.optString("caption", "").takeIf { it.isNotBlank() },
                                latitude = lat,
                                longitude = lon,
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
                                        caption = o.optString("caption", "").takeIf { it.isNotBlank() },
                                        latitude = lat,
                                        longitude = lon,
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
                    longitude: Double?
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
                        if (file != null && file.exists() && albumId != null) {
                            addPhotoWithUpload(file, uri, albumId, null, null)
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
                            if (file.exists() && albumId != null) {
                                addPhotoWithUpload(
                                    file,
                                    uri,
                                    albumId,
                                    metadata?.latitude,
                                    metadata?.longitude
                                )
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

                val selectedAlbumName = selectedAlbumId?.let { id -> albums.find { it.id == id }?.name }

                when {
                    selectedAlbumId != null && selectedPhotoId != null -> {
                        BackHandler { selectedPhotoId = null }
                        val albumName = selectedAlbumName ?: ""
                        val photo = photos.find { it.id == selectedPhotoId }
                        if (photo != null) {
                            PhotoDetailScreen(
                                photo = photo,
                                albumName = albumName,
                                mock = getPhotoDetailMock(albumName, photo.id),
                                onBack = { selectedPhotoId = null },
                                onSave = { caption ->
                                    scope.launch {
                                        val t = AuthTokenStore.get() ?: return@launch
                                        val body = JSONObject().put("caption", caption)
                                        val result = BackendClient.put("/images/${photo.id}", body, token = t)
                                        withContext(Dispatchers.Main) {
                                            result.onFailure { handle401(context, it) }
                                            if (result.isSuccess) loadAlbumImages()
                                            Toast.makeText(
                                                context,
                                                if (result.isSuccess) "Notes saved" else result.exceptionOrNull()?.message ?: "Failed to save",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                        } else {
                            selectedPhotoId = null
                        }
                    }
                    selectedAlbumId != null -> {
                        AlbumScreen(
                            modifier = Modifier.fillMaxSize(),
                            albumName = selectedAlbumName ?: "",
                            photos = photos,
                            friends = demoFriends,
                            isSharedAlbum = false,
                            onBack = { selectedAlbumId = null },
                            onEditAlbumName = {},
                            onDeleteAlbum = {},
                            onAddFriend = {},
                            onPhotoClick = { selectedPhotoId = it },
                            onAddPhoto = ::onAddPhoto
                        )
                    }
                    else -> {
                        HomeScreen(
                            albums = albums,
                            onProfileClick = {
                                startActivity(Intent(context, ProfileActivity::class.java))
                            },
                            onAlbumClick = { selectedAlbumId = it }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun HomeScreen(
    albums: List<AlbumUi>,
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit,
    onAlbumClick: (Int) -> Unit = {}
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredItems = remember(searchQuery, albums) {
        if (searchQuery.isBlank()) albums
        else albums.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingActionButton(
                onClick = {onProfileClick()},
                modifier = Modifier.size(48.dp),
                content = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        Modifier.size(24.dp),
                    )
                },
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        Modifier.size(24.dp),
                    )
                },
                singleLine = true,
            )
            FloatingActionButton(
                onClick = { /* add */ },
                modifier = Modifier.size(48.dp),
                content = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        Modifier.size(24.dp),
                    )
                },
            )
        }
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
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text("") },
                )
                HorizontalDivider()
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MementoAndroidTheme {
        HomeScreen(
            albums = emptyList(),
            onProfileClick = {}
        )
    }
}