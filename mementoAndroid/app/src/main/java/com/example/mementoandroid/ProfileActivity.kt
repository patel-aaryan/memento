package com.example.mementoandroid

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import coil.compose.AsyncImage
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.api.BackendException
import com.example.mementoandroid.api.getFcmToken
import com.example.mementoandroid.ui.album.AddPhotoSource
import com.example.mementoandroid.ui.album.components.AddPhotoBottomSheet
import com.example.mementoandroid.util.AuthTokenStore
import com.example.mementoandroid.util.DarkModeStore
import com.example.mementoandroid.util.orDefaultAvatar
import com.example.mementoandroid.util.CloudinaryHelper
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "ProfileActivity"

data class UserProfile(
    val name: String,
    val email: String,
    val profilePictureUrl: String?
)

/** Pending friend request where current user is the recipient (can accept or decline). */
data class IncomingFriendRequest(
    val id: Int,
    val requester: UserProfile
)

private fun parseIncomingFriendRequests(arr: JSONArray): List<IncomingFriendRequest> {
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optInt("id", -1)
        if (id < 0) return@mapNotNull null
        val r = o.optJSONObject("requester") ?: return@mapNotNull null
        IncomingFriendRequest(
            id = id,
            requester = UserProfile(
                name = r.optString("name", ""),
                email = r.optString("email", ""),
                profilePictureUrl = r.optString("profile_picture_url", "").takeIf { it.isNotBlank() }
            )
        )
    }
}

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
        DarkModeStore.init(applicationContext)
        setContent {
            var darkMode by remember { mutableStateOf(DarkModeStore.get(applicationContext)) }
            MementoAndroidTheme(darkTheme = darkMode) {
                val context = LocalContext.current as ComponentActivity
                val token = AuthTokenStore.get()
                var user by remember { mutableStateOf<UserProfile?>(null) }
                var friends by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
                var incomingFriendRequests by remember { mutableStateOf<List<IncomingFriendRequest>>(emptyList()) }
                var addPhotoSheetOpen by remember { mutableStateOf(false) }
                var pendingProfileFile by remember { mutableStateOf<File?>(null) }
                var pendingProfileUri by remember { mutableStateOf<Uri?>(null) }
                val scope = rememberCoroutineScope()

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

                fun refetchFriends() {
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        BackendClient.getArray("/friends", t)
                            .onSuccess { arr ->
                                val list = (0 until arr.length()).map { i ->
                                    val o = arr.getJSONObject(i)
                                    UserProfile(
                                        name = o.optString("name", ""),
                                        email = o.optString("email", ""),
                                        profilePictureUrl = o.optString("profile_picture_url", "").takeIf { it.isNotBlank() }
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    friends = list
                                }
                            }
                            .onFailure { withContext(Dispatchers.Main) { handle401(context, it) } }
                    }
                }

                fun refetchIncomingFriendRequests() {
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        BackendClient.getArray("/friends/requests/incoming", t)
                            .onSuccess { arr ->
                                val list = parseIncomingFriendRequests(arr)
                                withContext(Dispatchers.Main) {
                                    incomingFriendRequests = list
                                }
                            }
                            .onFailure { withContext(Dispatchers.Main) { handle401(context, it) } }
                    }
                }

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

                    // Fetch initial friends list and incoming requests
                    refetchFriends()
                    refetchIncomingFriendRequests()
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

                fun addFriendByEmail(email: String) {
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        val body = JSONObject().apply { put("email", email) }
                        BackendClient.post("/friends/add_friend", body, token = t)
                            .onSuccess { json ->
                                val pending = json.optBoolean("pending", true)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (pending) "Friend request sent" else "Friend added",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                refetchFriends()
                                refetchIncomingFriendRequests()
                            }
                            .onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    if (e is BackendException && e.statusCode == 401) {
                                        handle401(context, e)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            e.message ?: "Failed to send friend request",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                    }
                }

                fun acceptFriendRequest(requestId: Int) {
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        BackendClient.post("/friends/requests/$requestId/accept", JSONObject(), token = t)
                            .onSuccess {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Friend added", Toast.LENGTH_SHORT).show()
                                }
                                refetchFriends()
                                refetchIncomingFriendRequests()
                            }
                            .onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    if (e is BackendException && e.statusCode == 401) {
                                        handle401(context, e)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            e.message ?: "Failed to accept request",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                    }
                }

                fun declineFriendRequest(requestId: Int) {
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        BackendClient.post("/friends/requests/$requestId/decline", null, token = t)
                            .onSuccess {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Request declined", Toast.LENGTH_SHORT).show()
                                }
                                refetchIncomingFriendRequests()
                            }
                            .onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    if (e is BackendException && e.statusCode == 401) {
                                        handle401(context, e)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            e.message ?: "Failed to decline request",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                    }
                }

                fun getFriendLink() {
                    val t = AuthTokenStore.get() ?: return
                    scope.launch {
                        BackendClient.get("/friends/get_friend_link", t)
                            .onSuccess { j ->
                                val link = j.optString("link", "")
                                withContext(Dispatchers.Main) {
                                    if (link.isNotBlank()) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, link)
                                            putExtra(Intent.EXTRA_SUBJECT, "Add me on Memento")
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Share friend link")
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Failed to get friend link",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            .onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    if (e is BackendException && e.statusCode == 401) {
                                        handle401(context, e)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            e.message ?: "Failed to get friend link",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                    }
                }

                fun logout() {
                    scope.launch {
                        val t = AuthTokenStore.get()
                        if (t != null) {
                            val fcmToken = getFcmToken()
                            if (fcmToken != null) {
                                BackendClient.post(
                                    "/notifications/unregister-device",
                                    JSONObject().put("fcm_token", fcmToken),
                                    t
                                ).onFailure { Log.w(TAG, "Failed to unregister device: $it") }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            AuthTokenStore.clear()
                            startActivity(Intent(context, LoginActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            })
                            finish()
                        }
                    }
                }

                ProfileScreen(
                    user = user,
                    friends = friends,
                    incomingFriendRequests = incomingFriendRequests,
                    darkModeEnabled = darkMode,
                    onDarkModeChange = { darkMode = it; DarkModeStore.set(applicationContext, it) },
                    onBack = { finish() },
                    onProfilePictureClick = { addPhotoSheetOpen = true },
                    onSaveName = ::saveName,
                    onGetFriendLink = ::getFriendLink,
                    onAddFriendByEmail = ::addFriendByEmail,
                    onAcceptFriendRequest = ::acceptFriendRequest,
                    onDeclineFriendRequest = ::declineFriendRequest,
                    onLogout = ::logout
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: UserProfile?,
    friends: List<UserProfile> = emptyList(),
    incomingFriendRequests: List<IncomingFriendRequest> = emptyList(),
    darkModeEnabled: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onProfilePictureClick: () -> Unit,
    onSaveName: ((String) -> Unit)? = null,
    onGetFriendLink: (() -> Unit)? = null,
    onAddFriendByEmail: ((String) -> Unit)? = null,
    onAcceptFriendRequest: ((Int) -> Unit)? = null,
    onDeclineFriendRequest: ((Int) -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val profileName = user?.name ?: "Loading..."
    val profileEmail = user?.email ?: ""
    val profilePictureUrl = user?.profilePictureUrl
    var showNameDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var friendEmail by remember { mutableStateOf("") }

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
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile picture (from GET /auth/me URL, or default placeholder)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onProfilePictureClick),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = profilePictureUrl.orDefaultAvatar(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
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
                Button(
                    onClick = {
                        val intent = Intent().apply {
                            action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Manage Notifications")
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
                        onCheckedChange = onDarkModeChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Incoming friend requests (accept / decline)
            if (incomingFriendRequests.isNotEmpty() &&
                onAcceptFriendRequest != null &&
                onDeclineFriendRequest != null
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Friend requests",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    incomingFriendRequests.forEach { req ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = req.requester.profilePictureUrl.orDefaultAvatar(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = req.requester.name.ifBlank { "Unnamed" },
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = req.requester.email,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { onDeclineFriendRequest(req.id) }) {
                                        Text("Decline", color = MaterialTheme.colorScheme.error)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = { onAcceptFriendRequest(req.id) }) {
                                        Text("Accept")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Friends section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Friends",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (onGetFriendLink != null) {
                    Button(
                        onClick = onGetFriendLink,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get friend link")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (onAddFriendByEmail != null) {
                    OutlinedTextField(
                        value = friendEmail,
                        onValueChange = { friendEmail = it },
                        label = { Text("Friend email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val trimmed = friendEmail.trim()
                            if (trimmed.isNotEmpty()) {
                                onAddFriendByEmail(trimmed)
                                friendEmail = ""
                            }
                        },
                        enabled = friendEmail.isNotBlank(),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Send friend request")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (friends.isEmpty()) {
                    Text(
                        text = "No friends yet",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        friends.forEach { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = friend.profilePictureUrl.orDefaultAvatar(),
                                        contentDescription = "Friend avatar",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = friend.name.ifBlank { "Unnamed" },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = friend.email,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (onLogout != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Log out")
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout?.invoke()
                    }
                ) {
                    Text("Yes", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
        friends = listOf(
            UserProfile("Alex", "alex@example.com", null),
            UserProfile("Sam", "sam@example.com", null)
        ),
        onBack = {},
        onProfilePictureClick = {}
    )
}

