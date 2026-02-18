package com.example.mementoandroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color



class ProfileActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MementoAndroidTheme {
                ProfileScreen()
            }
        }

    }
}

@Composable
fun ProfileScreen(
    profileEmail: String = "bchilling@gmail.com",
    defaultImage: Int = R.drawable.photo_1
) {
    var profileName by remember { mutableStateOf("John Cena") }
    var showImageDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableIntStateOf(defaultImage) }
    val images = listOf(
        R.drawable.photo_1,
        R.drawable.photo_2,
        R.drawable.photo_3,
        R.drawable.photo_4,
        R.drawable.photo_5,
        R.drawable.photo_6,
        R.drawable.photo_7,
        R.drawable.photo_8,
        R.drawable.photo_9,
        R.drawable.photo_10,
        R.drawable.photo_11,
        R.drawable.photo_12,
        R.drawable.photo_13,
        R.drawable.photo_14,
        R.drawable.photo_15,
        R.drawable.photo_16,

    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile picture
        Image(
            painter = painterResource(id = selectedImage),
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(120.dp)
                .clickable { showImageDialog = true }
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Name with edit icon
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = profileName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Name",
                modifier = Modifier
                    .size(20.dp)
                    .clickable { showNameDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Email
        Text(
            text = profileEmail,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Settings toggles
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Enable Notifications", fontSize = 16.sp)
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Dark Mode", fontSize = 16.sp)
                Switch(
                    checked = darkModeEnabled,
                    onCheckedChange = { darkModeEnabled = it }
                )
            }
        }
    }
    // Edit profile image Dialog
    if (showImageDialog){
        ImagePickerPopup(
            images = images,
            selectedImage = selectedImage,
            onImageSelected = { selected -> selectedImage = selected },
            onDismiss = {showImageDialog = false}
        )
    }

    // Edit Name Dialog
    if (showNameDialog) {
        var newName by remember { mutableStateOf(profileName) }

        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Edit Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        profileName = newName
                        showNameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ImagePickerPopup(
    images: List<Int>, // list of drawable resource IDs
    selectedImage: Int,
    onImageSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var highlightedImage by remember { mutableIntStateOf(selectedImage) }
    Dialog(onDismissRequest = onDismiss) {

        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {

                Text(
                    text = "Select an Image",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(images) { imageRes ->

                        val isSelected = imageRes == highlightedImage

                        Image(
                            painter = painterResource(id = imageRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
//                                    onImageSelected(imageRes)
                                    highlightedImage = imageRes
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

//                Button(
//                    onClick = onDismiss,
//                    modifier = Modifier.align(Alignment.End)
//                ) {
//                    Text("Close")
//                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onDismiss() }
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onImageSelected(highlightedImage)
                            onDismiss()
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    ProfileScreen()
}

