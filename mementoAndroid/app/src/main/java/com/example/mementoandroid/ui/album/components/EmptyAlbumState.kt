package com.example.mementoandroid.ui.album.components


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmptyAlbumState(
    onAddPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your album is empty", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAddPhotoClick) {
                Text("Add your first photo")
            }
        }
    }
}