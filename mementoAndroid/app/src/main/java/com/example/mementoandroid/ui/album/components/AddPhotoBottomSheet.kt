package com.example.mementoandroid.ui.album.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.ui.album.AddPhotoSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPhotoBottomSheet(
    onDismiss: () -> Unit,
    onPick: (AddPhotoSource) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add photo", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { onPick(AddPhotoSource.Camera) }, modifier = Modifier.fillMaxWidth()) {
                Text("Camera")
            }
            Button(onClick = { onPick(AddPhotoSource.Photos) }, modifier = Modifier.fillMaxWidth()) {
                Text("Photos")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}