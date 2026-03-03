package com.example.mementoandroid.ui.album

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.api.BackendException
import com.example.mementoandroid.ui.album.components.AddPhotoBottomSheet
import com.example.mementoandroid.ui.album.components.FriendsRow
import com.example.mementoandroid.ui.album.components.PhotoGrid
import com.example.mementoandroid.util.AuthTokenStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import android.util.Log

private const val TAG = "CreateAlbumScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAlbumScreen(
    onBack: () -> Unit,
    onAlbumCreated: (Int, String) -> Unit,
    onAddPhoto: (AddPhotoSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var albumName by rememberSaveable { mutableStateOf("") }
    var albumId by rememberSaveable { mutableStateOf<Int?>(null) }

    var friends by remember { mutableStateOf(emptyList<FriendUi>()) }
    var photos by remember { mutableStateOf(emptyList<AlbumPhotoUi>()) }

    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var addPhotoSheetOpen by remember { mutableStateOf(false) }

    fun showError(msg: String) {
        errorMsg = msg
    }

    suspend fun createAlbum(name: String): Int? {
        val token = AuthTokenStore.get() ?: run {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show()
            }
            return null
        }

        val body = JSONObject().put("name", name)
        val result = BackendClient.post("/albums", body, token = token)

        return if (result.isSuccess) {
            val id = result.getOrNull()?.getInt("id")
            id
        } else {
            val exception = result.exceptionOrNull()
            if (exception is BackendException && exception.statusCode == 401) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
                }
                // Handle logout here if needed
            }
            null
        }
    }

    suspend fun addMember(albumId: Int, userId: Int): Boolean {
        val token = AuthTokenStore.get() ?: return false

        val body = JSONObject().put("user_id", userId)
        val result = BackendClient.post("/albums/$albumId/members", body, token = token)

        return result.isSuccess
    }

    suspend fun loadAlbumPhotos(albumId: Int): List<AlbumPhotoUi> {
        val token = AuthTokenStore.get() ?: return emptyList()

        val result = BackendClient.getArray("/images/album/$albumId", token)
        return if (result.isSuccess) {
            val arr = result.getOrNull() ?: return emptyList()
            (0 until arr.length()).map { i ->
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
        } else {
            emptyList()
        }
    }

    if (addPhotoSheetOpen) {
        AddPhotoBottomSheet(
            onDismiss = { addPhotoSheetOpen = false },
            onPick = { source ->
                addPhotoSheetOpen = false
                val id = albumId
                if (id == null) {
                    showError("Create the album first.")
                    return@AddPhotoBottomSheet
                }
                onAddPhoto(source)
                // Refresh photos after adding
                scope.launch {
                    photos = loadAlbumPhotos(id)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (albumId == null) "Create album" else "Album") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (albumId == null) {
                        TextButton(
                            enabled = !busy && albumName.trim().isNotEmpty(),
                            onClick = {
                                if (busy) return@TextButton
                                busy = true
                                errorMsg = null

                                scope.launch {
                                    try {
                                        val createdId = createAlbum(albumName.trim())
                                        if (createdId != null) {
                                            albumId = createdId
                                            onAlbumCreated(createdId, albumName.trim())
                                            Toast.makeText(context, "Album created!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            showError("Failed to create album")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error creating album", e)
                                        showError(e.message ?: "Failed to create album")
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        ) { Text("Create") }
                    } else {
                        // Show additional actions when album is created
                        TextButton(
                            onClick = { addPhotoSheetOpen = true }
                        ) { Text("Add Photo") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = albumName,
                onValueChange = { albumName = it },
                label = { Text("Album name") },
                singleLine = true,
                enabled = albumId == null && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (albumId != null) {
                FriendsRow(
                    friends = friends,
                    onAddFriend = {
                        if (busy) return@FriendsRow
                        busy = true
                        errorMsg = null

                        scope.launch {
                            try {
                                // For now, just show a toast - you'll implement friend picker later
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Friend picker coming soon", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                showError(e.message ?: "Failed to add member")
                            } finally {
                                busy = false
                            }
                        }
                    }
                )

                PhotoGrid(
                    photos = photos,
                    onPhotoClick = { /* TODO: Navigate to photo detail */ },
                    onAddClick = { addPhotoSheetOpen = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Working...") },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        )
    }

    if (errorMsg != null) {
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            confirmButton = {
                TextButton(onClick = { errorMsg = null }) { Text("OK") }
            },
            title = { Text("Error") },
            text = { Text(errorMsg!!) }
        )
    }
}