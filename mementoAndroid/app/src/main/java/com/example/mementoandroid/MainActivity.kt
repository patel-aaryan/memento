package com.example.mementoandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import com.example.mementoandroid.ui.album.AlbumScreen
import com.example.mementoandroid.ui.album.FriendUi
import com.example.mementoandroid.ui.album.AlbumPhotoUi



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MementoAndroidTheme {
                MementoAndroidApp()
            }
        }
    }
}

//@Composable
//fun MementoAndroidApp() {
//    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//        HomeScreen(modifier = Modifier.padding(innerPadding))
//    }
//}
@Composable
fun MementoAndroidApp() {
    val demoPhotos = remember {
        listOf(
            AlbumPhotoUi("1", R.drawable.photo_1),
            AlbumPhotoUi("2", R.drawable.photo_2),
        )
    }

    val demoFriends = remember {
        listOf(
            FriendUi("1", "isla"),
            FriendUi("2", "blair"),
            FriendUi("3", "shannon"),
            FriendUi("4", "nick")
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        AlbumScreen(
            modifier = Modifier.padding(innerPadding),
            albumName = "Grad Trip",
            photos = demoPhotos,
            friends = demoFriends,
            onBack = {},
            onEditAlbumName = {},
            onDeleteAlbum = {},
            onAddFriend = {},
            onPhotoClick = { /* later */ },
            onAddPhoto = { /* later */ }
        )
    }
}


data class ListItemData(
    val title: String,
    val detail: String,
    val icon: ImageVector,
)

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val sampleItems = remember {
        listOf(
            ListItemData("Sweet Dreams Bubble Tea", "Jan 2025", Icons.Default.Person),
            ListItemData("Grad Trip", "May 2025", Icons.Default.Group),
            ListItemData("Ken's Sushi", "Feb 2025", Icons.Default.Person),
            ListItemData("Square One Shopping", "Feb 2025", Icons.Default.Group),
            ListItemData("Niagara Falls Adventure", "Feb 2025", Icons.Default.Group),
            ListItemData("Cancun Fam Trip", "Dec 2024", Icons.Default.FamilyRestroom),
            ListItemData("2nd Anniversary", "Oct 2025", Icons.Default.Favorite),
            ListItemData("Sister's Euro Trip", "Feb 2024", Icons.Default.Group),
        )
    }
    val filteredItems = remember(searchQuery, sampleItems) {
        if (searchQuery.isBlank()) sampleItems
        else sampleItems.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.detail.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingActionButton(
                onClick = { /* profile */ },
                modifier = Modifier.size(48.dp),
                content = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        Modifier.size(24.dp),
                    )
                },
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        Modifier.size(24.dp),
                    )
                },
                singleLine = true,
            )
            FloatingActionButton(
                onClick = { /* add */ },
                modifier = Modifier.size(48.dp),
                content = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        Modifier.size(24.dp),
                    )
                },
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(
                items = filteredItems,
                key = { it.title },
            ) { item ->
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(item.detail) },
                )
                HorizontalDivider()
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MementoAndroidTheme {
        HomeScreen()
    }
}