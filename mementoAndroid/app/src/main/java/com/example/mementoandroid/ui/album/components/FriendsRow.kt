package com.example.mementoandroid.ui.album.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mementoandroid.ui.album.FriendUi
import com.example.mementoandroid.util.orDefaultAvatar

@Composable
fun FriendsRow(
    friends: List<FriendUi>,
    onAddFriend: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(friends, key = { it.id }) { friend ->
            FriendChip(
                username = friend.username,
                profilePictureUrl = friend.profilePictureUrl
            )
        }
        item {
            AddFriendChip(onClick = onAddFriend)
        }
    }
}

@Composable
private fun FriendChip(
    username: String,
    profilePictureUrl: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        AsyncImage(
            model = profilePictureUrl.orDefaultAvatar(),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = username,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddFriendChip(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = "Add friend")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Add friend",
            style = MaterialTheme.typography.labelMedium
        )
    }
}