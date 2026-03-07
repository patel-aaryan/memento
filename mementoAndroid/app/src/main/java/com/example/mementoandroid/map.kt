package com.example.mementoandroid

import android.content.Context
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


fun createAvatarMarker(
    context: Context,
    drawableId: Int,
    sizeDp: Int = 48,
    borderDp: Int = 4
): BitmapDescriptor {

    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()
    val borderPx = (borderDp * density)

    val original = BitmapFactory.decodeResource(context.resources, drawableId)

    val scaled = original.scale(sizePx, sizePx)

    val output = createBitmap(sizePx, sizePx)
    val canvas = Canvas(output)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    paint.shader = shader

    val radius = sizePx / 2f

    // draw avatar
    canvas.drawCircle(radius, radius, radius - borderPx, paint)

    // draw white border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    borderPaint.color = Color.WHITE
    borderPaint.style = Paint.Style.STROKE
    borderPaint.strokeWidth = borderPx

    canvas.drawCircle(radius, radius, radius - borderPx / 2, borderPaint)

    return BitmapDescriptorFactory.fromBitmap(output)
}

@Composable
fun MapScreen() {

    val context = LocalContext.current

    val nyc = LatLng(40.7128, -74.0060)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(nyc, 12f)
    }

    val mapProperties = MapProperties(
        isMyLocationEnabled = false,
        mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
            context,
            R.raw.map_style
        )
    )

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties
    ) {
        val markerIcon = remember {
            createAvatarMarker(context, R.drawable.photo_1)
        }

        Marker(
            state = MarkerState(position = nyc),
            icon = markerIcon,
        )

    }
}