package com.example.mementoandroid.ui.massupload

import androidx.compose.runtime.LaunchedEffect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

data class SelectedImage(
    val uri: Uri,
    val file: File
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPhotoGalleryScreen(
    onBack: () -> Unit,
    onImagesSelected: (List<SelectedImage>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedUris = remember { mutableStateListOf<Uri>() }
    val selectedImages = remember { mutableStateListOf<SelectedImage>() }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        // Convert URIs to files and store in cache
        val selected = uris.mapNotNull { uri ->
            try {
                val file = File(context.cacheDir, "selected_${System.currentTimeMillis()}_${uri.hashCode()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                if (file.exists()) SelectedImage(uri, file) else null
            } catch (e: Exception) {
                null
            }
        }
        onImagesSelected(selected)
    }

    LaunchedEffect(Unit) {
        galleryLauncher.launch("image/*")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "Loading gallery...",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
