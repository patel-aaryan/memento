package com.example.mementoandroid.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.ui.home.HomeAddAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAddBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPick: (HomeAddAction) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = { onPick(HomeAddAction.Camera) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Take a Photo")
            }

            Button(
                onClick = { onPick(HomeAddAction.Photos) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose from Gallery")
            }

            Button(
                onClick = { onPick(HomeAddAction.MakeAlbum) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create New Album")
            }
        }
    }
}