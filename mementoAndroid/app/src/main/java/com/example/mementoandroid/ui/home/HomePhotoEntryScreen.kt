package com.example.mementoandroid.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.mementoandroid.util.AuthTokenStore
import com.example.mementoandroid.util.CloudinaryHelper
import com.example.mementoandroid.api.BackendClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class HomePhotoMetadata(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: String? = null,
    val imageUri: Uri,
    val imageFile: File
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePhotoEntryScreen(
    metadata: HomePhotoMetadata,
    onBack: () -> Unit,
    onPhotoSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var caption by remember { mutableStateOf("") }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }


    val bitmap = remember(metadata.imageUri) {
        try {
            context.contentResolver.openInputStream(metadata.imageUri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    val locationString = remember(metadata.latitude, metadata.longitude) {
        when {
            metadata.latitude != null && metadata.longitude != null ->
                String.format("%.4f° N, %.4f° W", metadata.latitude, metadata.longitude)
            else -> "Location not available"
        }
    }

    val dateString = remember(metadata.takenAt) {
        metadata.takenAt?.let {
            try {
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(it)
                SimpleDateFormat("MMM dd, yyyy · h:mm a", Locale.US).format(date)
            } catch (e: Exception) {
                "Date not available"
            }
        } ?: "Date not available"
    }

    val audioRecorderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/m4a")
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Audio recording coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun uploadAndSave() {
        val token = AuthTokenStore.get() ?: return

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
        if (audioFile != null) {
            audioUrl = withContext(Dispatchers.IO) {
                CloudinaryHelper.uploadAudio(context, audioFile!!, token)
            }
        }

        val body = JSONObject().apply {
            put("image_url", imageUrl)
            put("caption", caption)
            metadata.latitude?.let { put("latitude", it) }
            metadata.longitude?.let { put("longitude", it) }
            metadata.takenAt?.let { put("taken_at", it) }
            audioUrl?.let { put("audio_url", it) }
        }
        Log.d("DEBUG", "POST /images body: ${body.toString()}")
        val result = BackendClient.post("/images", body, token = token)

        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                Toast.makeText(context, "Photo saved!", Toast.LENGTH_SHORT).show()
                onPhotoSaved()
            } else {
                Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add to Memento") },
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
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


            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = locationString,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = dateString,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (audioFile != null) "Voice note recorded" else "No voice note",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                audioRecorderLauncher.launch("voice_note_${System.currentTimeMillis()}.m4a")
                            },
                            enabled = !isSaving
                        ) {
                            Icon(Icons.Default.Mic, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (audioFile != null) "Re-record" else "Record voice note")
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    placeholder = { Text("Add a caption...") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    minLines = 3
                )
            }
        }
    }
}