package com.example.mementoandroid.ui.album.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import com.example.mementoandroid.R
import com.example.mementoandroid.ui.album.AlbumPhotoUi
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.UiSettings
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.*

fun interpolateColor(colorStart: Int, colorEnd: Int, fraction: Float): Int {
    val startA = Color.alpha(colorStart)
    val startR = Color.red(colorStart)
    val startG = Color.green(colorStart)
    val startB = Color.blue(colorStart)

    val endA = Color.alpha(colorEnd)
    val endR = Color.red(colorEnd)
    val endG = Color.green(colorEnd)
    val endB = Color.blue(colorEnd)

    val a = (startA + ((endA - startA) * fraction)).toInt()
    val r = (startR + ((endR - startR) * fraction)).toInt()
    val g = (startG + ((endG - startG) * fraction)).toInt()
    val b = (startB + ((endB - startB) * fraction)).toInt()

    return Color.argb(a, r, g, b)
}
fun createAvatarMarker(
    context: Context,
    bitmap: Bitmap,
    borderColor: Int = Color.WHITE,
    sizeDp: Int = 70,
    borderDp: Int = 5
): BitmapDescriptor {

    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()
    val borderPx = (borderDp * density)

    val scaled = bitmap.scale(sizePx, sizePx)

    val output = createBitmap(sizePx, sizePx)
    val canvas = Canvas(output)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    paint.shader = shader

    val radius = sizePx / 2f

    canvas.drawCircle(radius, radius, radius - borderPx, paint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    borderPaint.color = borderColor
    borderPaint.style = Paint.Style.STROKE
    borderPaint.strokeWidth = borderPx

    canvas.drawCircle(radius, radius, radius - borderPx / 2, borderPaint)

    return BitmapDescriptorFactory.fromBitmap(output)
}

@RequiresApi(Build.VERSION_CODES.O)
fun extractDate(takenAt: String?): LocalDate? {
    if (takenAt.isNullOrBlank() || takenAt == "null") return null
    return try {
        LocalDate.parse(takenAt.substring(0, 10))
    } catch (e: Exception) {
        null
    }
}

suspend fun loadPhotoBitmap(context: Context, photo: AlbumPhotoUi): Bitmap? {

    return when {
        photo.imageRes != null -> {
            BitmapFactory.decodeResource(context.resources, photo.imageRes)
        }

        photo.uri != null -> {
            context.contentResolver.openInputStream(photo.uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        }

        photo.imageUrl != null -> {
            // Load remote image using Coil
            withContext(Dispatchers.IO) {
                try {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(photo.imageUrl)
                        .allowHardware(false) // required to get Bitmap
                        .build()

                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        result.drawable.toBitmap()
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        else -> null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MapScreen(
    photos: List<AlbumPhotoUi>,
    onPhotoClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val photosWithLocation = remember(photos) {
        photos.filter { it.latitude != null && it.longitude != null }
    }
    val cameraPositionState = rememberCameraPositionState()

    // Calculate window to include all photos
    LaunchedEffect(photosWithLocation) {
        if (photosWithLocation.isNotEmpty()) {
            val builder = LatLngBounds.builder()
            photosWithLocation.forEach { photo ->
                builder.include(LatLng(photo.latitude!!, photo.longitude!!))
            }
            val bounds = builder.build()

            // Move camera to show all markers with some padding
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngBounds(bounds, 200) // 100 px padding
            )
        } else {
            // Default location if no photos
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
        }
    }

    val mapProperties = MapProperties(
        isMyLocationEnabled = false,
        mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
            context,
            R.raw.map_style
        )
    )

    // Find first and last times
    val photoDates = photosWithLocation.mapNotNull { extractDate(it.dateAdded) }

    val firstDate = photoDates.minOrNull()
    val lastDate = photoDates.maxOrNull()

    val totalDays = if (firstDate != null && lastDate != null) {
        ChronoUnit.DAYS.between(firstDate, lastDate).coerceAtLeast(1)
    } else 1

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
//        modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = com.google.maps.android.compose.MapUiSettings(zoomControlsEnabled = false)
        ) {

            // DEBUG
//        photosWithLocation.forEach { photo ->
//            val takenAt = photo.dateAdded
//            Log.d("MapDebug", "$takenAt")
//        }
//        Log.d("MapDebug", "$firstDate, $lastDate, $totalDays")

            photosWithLocation.forEach { photo ->
                val lat = photo.latitude!!
                val lng = photo.longitude!!

                // Load bitmap asynchronously
                val markerBitmap by produceState<Bitmap?>(initialValue = null, photo) {
                    value = loadPhotoBitmap(context, photo) // suspend function
                }

                // Compute fraction for border color
                val date = extractDate(photo.dateAdded)
                val daysSinceStart = if (date != null && firstDate != null) {
                    ChronoUnit.DAYS.between(firstDate, date).toFloat()
                } else 0f
                val fraction = (daysSinceStart / totalDays).coerceIn(0f, 1f)
                val borderColor = interpolateColor(
                    Color.RED,   // earliest
                    Color.BLUE,  // latest
                    fraction
                )
                // Create map marker
                val markerIcon = remember(markerBitmap) {
                    markerBitmap?.let { createAvatarMarker(context, it, borderColor) }
                }

                Marker(
                    state = MarkerState(position = LatLng(lat, lng)),
                    icon = markerIcon,
                    onClick = {
                        onPhotoClick(photo.id)
                        true
                    }
                )
            }
        }
        TimeGradientLegend(
            startDate = firstDate,
            endDate = lastDate,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
    }
}