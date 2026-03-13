package com.example.mementoandroid.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.mementoandroid.ui.album.AlbumPhotoUi
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Prepares and launches Android native share for multiple photos.
 * Downloads remote images to cache and creates content URIs for sharing.
 */
suspend fun sharePhotos(context: Context, photos: List<AlbumPhotoUi>) {
    if (photos.isEmpty()) return
    val uris = withContext(Dispatchers.IO) {
        photos.mapNotNull { photo -> photoToShareUri(context, photo) }
    }
    if (uris.isEmpty()) return
    withContext(Dispatchers.Main) {
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share images"))
    }
}

private suspend fun photoToShareUri(context: Context, photo: AlbumPhotoUi): Uri? {
    return when {
        photo.imageUrl != null -> {
            val file = File(context.cacheDir, "share_${photo.id}_${System.currentTimeMillis()}.jpg")
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(photo.imageUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap()
                    file.outputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
        photo.uri != null -> photo.uri
        photo.imageRes != null -> null
        else -> null
    }
}
