package com.example.mementoandroid.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mementoandroid.ui.album.AlbumUi

@Composable
fun AlbumLogo(
    album: AlbumUi,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    fillWidth: Boolean = false
) {
    val urls = album.coverImageUrls
    val sizeModifier = if (fillWidth) {
        Modifier.fillMaxWidth().aspectRatio(1f)
    } else {
        Modifier.size(size)
    }
    Box(
        modifier = modifier
            .then(sizeModifier)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when {
            urls.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = if (fillWidth) Modifier.fillMaxSize(0.6f) else Modifier.size(size * 0.6f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            urls.size < 4 -> {
                AsyncImage(
                    model = urls.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                AlbumCollageGrid(
                    urls = urls.take(4),
                    cornerRadius = 8.dp
                )
            }
        }
    }
}

@Composable
private fun AlbumCollageGrid(
    urls: List<String>,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    require(urls.size == 4)
    Box(modifier = modifier.fillMaxSize()) {
        // Top-left
        AsyncImage(
            model = urls[0],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .clip(RoundedCornerShape(topStart = cornerRadius))
        )
        // Top-right
        AsyncImage(
            model = urls[1],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .clip(RoundedCornerShape(topEnd = cornerRadius))
        )
        // Bottom-left
        AsyncImage(
            model = urls[2],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .clip(RoundedCornerShape(bottomStart = cornerRadius))
        )
        // Bottom-right
        AsyncImage(
            model = urls[3],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .clip(RoundedCornerShape(bottomEnd = cornerRadius))
        )
    }
}
