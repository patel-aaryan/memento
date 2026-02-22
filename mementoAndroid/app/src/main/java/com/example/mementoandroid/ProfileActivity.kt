package com.example.mementoandroid

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.api.BackendException
import com.example.mementoandroid.ui.album.AddPhotoSource
import com.example.mementoandroid.ui.album.components.AddPhotoBottomSheet
import com.example.mementoandroid.util.AuthTokenStore
import com.example.mementoandroid.util.CloudinaryHelper
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val TAG = "ProfileActivity"

data class UserProfile(
    val name: String,
    val email: String,
    val profilePictureUrl: String?
)

private fun handle401(context: ComponentActivity, e: Throwable) {
    if (e is BackendException && e.statusCode == 401) {
        AuthTokenStore.clear()
        context.startActivity(Intent(context, LoginActivity::class.java))
        context.finish()
    }
}

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthTokenStore.init(applicationContext)
        setContent {
            MementoAndroidTheme {
                val context = LocalContext.current as ComponentActivity
                val token = AuthTokenStore.get()
                var user by remember { mutableStateOf<UserProfile?>(null) }
                var addPhotoSheetOpen by remember { mutableStateOf(false) }
                var pendingProfileFile by remember { mutableStateOf<File?>(null) }
                var pendingProfileUri by remember { mutableStateOf<Uri?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    token ?: return@LaunchedEffect
                    BackendClient.get("/auth/me", token)
                        .onSuccess { j ->
                            withContext(Dispatchers.Main) {
                                user = UserProfile(
                                    name = j.optString("name", ""),
                                    email = j.optString("email", ""),
                                    profilePictureUrl = j.optString("profile_picture_url", "").takeIf { it.isNotBlank() }
                                )
                            }
                        }
                        .onFailure { handle401(context, it) }
                }

                fun refetchUser() {
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        BackendClient.get("/auth/me", t)
                            .onSuccess { j ->
                                withContext(Dispatchers.Main) {
                                    user = UserProfile(
                                        name = j.optString("name", ""),
                                        email = j.optString("email", ""),
                                        profilePictureUrl = j.optString("profile_picture_url", "").takeIf { it.isNotBlank() }
                                    )
                                }
                            }
                            .onFailure { withContext(Dispatchers.Main) { handle401(context, it) } }
                    }
                }

                fun uploadProfilePhoto(photoFile: File) {
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        val url = CloudinaryHelper.uploadImage(context, photoFile, t)
                        withContext(Dispatchers.Main) {
                            if (url == null) {
                                Log.e(TAG, "Profile photo upload failed")
                                return@withContext
                            }
                        }
                        val body = JSONObject().apply { put("profile_picture_url", url) }
                        BackendClient.patch("/auth/me", body, token = t)
                            .onSuccess { refetchUser() }
                            .onFailure { withContext(Dispatchers.Main) { handle401(context, it) } }
                    }
                }

                val takePictureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success) {
                        val file = pendingProfileFile
                        pendingProfileFile = null
                        pendingProfileUri = null
                        if (file != null && file.exists()) {
                            uploadProfilePhoto(file)
                        }
                    }
                }

                val pickPhotoLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        scope.launch(Dispatchers.IO) {
                            val file = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                file.outputStream().use { input.copyTo(it) }
                            }
                            if (file.exists()) {
                                uploadProfilePhoto(file)
                            }
                        }
                    }
                }

                fun onPickProfilePhoto(source: AddPhotoSource) {
                    addPhotoSheetOpen = false
                    when (source) {
                        AddPhotoSource.Camera -> {
                            val file = File(context.cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")
                            pendingProfileFile = file
                            pendingProfileUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            pendingProfileUri?.let { takePictureLauncher.launch(it) }
                        }
                        AddPhotoSource.Photos -> pickPhotoLauncher.launch(arrayOf("image/*"))
                    }
                }

                if (addPhotoSheetOpen) {
                    AddPhotoBottomSheet(
                        onDismiss = { addPhotoSheetOpen = false },
                        onPick = ::onPickProfilePhoto
                    )
                }

                fun saveName(newName: String) {
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        val body = JSONObject().apply { put("name", newName) }
                        BackendClient.patch("/auth/me", body, token = t)
                            .onSuccess { refetchUser() }
                            .onFailure { withContext(Dispatchers.Main) { handle401(context, it) } }
                    }
                }

                ProfileScreen(
                    user = user,
                    onBack = { finish() },
                    onProfilePictureClick = { addPhotoSheetOpen = true },
                    onSaveName = ::saveName
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: UserProfile?,
    onBack: () -> Unit,
    onProfilePictureClick: () -> Unit,
    onSaveName: ((String) -> Unit)? = null
) {
    val profileName = user?.name ?: "Loading..."
    val profileEmail = user?.email ?: ""
    val profilePictureUrl = user?.profilePictureUrl
    var showNameDialog by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile picture (from GET /auth/me URL, or placeholder)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onProfilePictureClick),
                contentAlignment = Alignment.Center
            ) {
                if (!profilePictureUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profilePictureUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                if (onSaveName != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Name",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { showNameDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = profileEmail,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
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
    }

    if (showNameDialog && user != null) {
        var newName by remember(user.name) { mutableStateOf(user.name) }
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
                        onSaveName?.invoke(newName)
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
    ProfileScreen(
        user = UserProfile("Jane", "jane@example.com", null),
        onBack = {},
        onProfilePictureClick = {}
    )
}

