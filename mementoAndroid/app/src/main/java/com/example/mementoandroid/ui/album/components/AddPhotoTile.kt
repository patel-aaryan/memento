package com.example.mementoandroid.ui.album.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddPhotoTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Add, contentDescription = "Add photo",
                    modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(6.dp))
                Text("Add", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}