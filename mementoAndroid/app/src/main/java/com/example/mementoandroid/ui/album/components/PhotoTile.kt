package com.example.mementoandroid.ui.album.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mementoandroid.ui.album.AlbumPhotoUi

@Composable
fun PhotoTile(
    photo: AlbumPhotoUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isSelectable: Boolean = true,
    isSelected: Boolean = false,
    isOwnedByCurrentUser: Boolean = false,
    onToggleSelect: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (isEditMode && isOwnedByCurrentUser) Modifier.border(3.dp, androidx.compose.material3.MaterialTheme.colorScheme.tertiary, RoundedCornerShape(16.dp))
                else Modifier
            )
            .then(
                if (isSelected) Modifier.border(4.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(
                enabled = (!isEditMode || isSelectable) && !photo.isPlaceholder,
                onClick = {
                    if (isEditMode && isSelectable) onToggleSelect()
                    else if (!isEditMode) onClick()
                }
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box {
            when {
                photo.isPlaceholder -> AlbumGridSkeletonShimmer(modifier = Modifier.fillMaxSize())
                photo.imageUrl != null -> AsyncImage(
                    model = photo.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                photo.uri != null -> AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> Image(
                    painter = painterResource(id = photo.imageRes!!),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isEditMode && isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(50)
                        )
                        .padding(4.dp)
                        .size(20.dp),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun AlbumGridSkeletonShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "albumGridShimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "albumGridShimmerShift",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val brush = Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.35f + 0.45f * shift),
            base.copy(alpha = 0.55f + 0.4f * (1f - shift)),
            base.copy(alpha = 0.35f + 0.45f * shift),
        ),
        start = Offset(0f, 0f),
        end = Offset(120f, 180f),
    )
    Box(modifier = modifier.background(brush))
}
