package com.example.mementoandroid.ui.album.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.util.AuthTokenStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.example.mementoandroid.ui.album.FriendUi
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendPickerScreen(
    albumId: Int,
    currentFriends: List<FriendUi>,
    onBack: () -> Unit,
    onFriendsAdded: (List<FriendUi>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var friends by remember { mutableStateOf<List<FriendUi>>(emptyList()) }
    var selectedFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var isAdding by remember { mutableStateOf(false) }

    val currentFriendIds = remember(currentFriends) {
        currentFriends.map { it.id }.toSet()
    }

    suspend fun loadFriends() {
        val token = AuthTokenStore.get() ?: return

        val result = BackendClient.getArray("/friends", token)

        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                val arr = result.getOrNull()
                val friendList = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                    val o = arr?.getJSONObject(i) ?: return@mapNotNull null
                    FriendUi(
                        id = o.getInt("id").toString(),
                        username = o.getString("name"),
                        profilePictureUrl = o.optString("profile_picture_url", "").takeIf { it.isNotBlank() }
                    )
                }.filter { it.id !in currentFriendIds } // Don't show already added friends

                friends = friendList
            } else {
                Toast.makeText(context, "Failed to load friends", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadFriends()
    }

    val filteredFriends = remember(searchQuery, friends) {
        if (searchQuery.isBlank()) friends
        else friends.filter {
            it.username.contains(searchQuery, ignoreCase = true)
        }
    }

    suspend fun addSelectedFriends() {
        if (selectedFriends.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Select at least one friend", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val token = AuthTokenStore.get() ?: return
        isAdding = true

        var successCount = 0
        val addedFriends = mutableListOf<FriendUi>()

        for (friendId in selectedFriends) {
            val body = JSONObject().put("user_id", friendId.toInt())
            val result = BackendClient.post("/albums/$albumId/members", body, token = token)

            if (result.isSuccess) {
                successCount++
                // Find the friend details
                friends.find { it.id == friendId }?.let { addedFriends.add(it) }
            }
        }

        withContext(Dispatchers.Main) {
            isAdding = false
            if (successCount > 0) {
                Toast.makeText(
                    context,
                    "Added $successCount friend${if (successCount > 1) "s" else ""} to album",
                    Toast.LENGTH_SHORT
                ).show()
                onFriendsAdded(addedFriends)
            }
            if (successCount < selectedFriends.size) {
                val failedCount = selectedFriends.size - successCount
                Toast.makeText(
                    context,
                    "Failed to add $failedCount friend${if (failedCount > 1) "s" else ""}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Friends to Album") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (selectedFriends.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch { addSelectedFriends() }
                            },
                            enabled = !isAdding
                        ) {
                            if (isAdding) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Text("Add (${selectedFriends.size})")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search friends by username") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredFriends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank())
                            "No friends yet. Add friends from Profile first!"
                        else
                            "No friends match '$searchQuery'",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredFriends) { friend ->
                        FriendPickerItem(
                            friend = friend,
                            isSelected = friend.id in selectedFriends,
                            onToggle = { friendId ->
                                selectedFriends = if (friendId in selectedFriends) {
                                    selectedFriends - friendId
                                } else {
                                    selectedFriends + friendId
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendPickerItem(
    friend: FriendUi,
    isSelected: Boolean,
    onToggle: (String) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onToggle(friend.id) },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (friend.profilePictureUrl != null) {
                    // You can add Coil/Glide here later for profile pics
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        headlineContent = {
            Text(text = friend.username)
        },
        trailingContent = {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}