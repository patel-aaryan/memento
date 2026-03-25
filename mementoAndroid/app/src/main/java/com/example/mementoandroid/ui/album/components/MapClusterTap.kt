package com.example.mementoandroid.ui.album.components

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Off-screen edge chip: single pin only pans the map; clusters keep marker tap behavior.
 */
fun runOffScreenIndicatorClick(
    cluster: PhotoCluster,
    cameraPositionState: CameraPositionState,
    scope: CoroutineScope,
    onPhotoClick: (String) -> Unit,
    onSameLocationClusterClick: (String) -> Unit,
    mapMaxZoom: () -> Float = { 20f },
) {
    if (cluster.photos.size == 1) {
        scope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(cluster.position)
            )
        }
        return
    }
    runClusterMarkerAction(
        cluster,
        cameraPositionState,
        scope,
        onPhotoClick,
        onSameLocationClusterClick,
        mapMaxZoom,
    )
}

private const val SAME_LOCATION_MAX_ZOOM_EPSILON = 0.08f

/**
 * Same behavior as map markers: single photo opens detail; multi-cluster zooms / same-location shortcut.
 * Same-coordinate clusters: first tap zooms to map max; second tap (already max-zoomed) opens + sorts by location.
 */
fun runClusterMarkerAction(
    cluster: PhotoCluster,
    cameraPositionState: CameraPositionState,
    scope: CoroutineScope,
    onPhotoClick: (String) -> Unit,
    onSameLocationClusterClick: (String) -> Unit,
    mapMaxZoom: () -> Float = { 20f },
) {
    if (cluster.photos.size == 1) {
        onPhotoClick(cluster.photos.first().id)
        return
    }
    val oldZoom = cameraPositionState.position.zoom
    val latLngs = cluster.photos.mapNotNull { p ->
        val lat = p.latitude
        val lng = p.longitude
        if (lat == null || lng == null) null else LatLng(lat, lng)
    }
    if (cluster.photos.size > 1 && latLngs.distinct().size == 1) {
        val maxZ = mapMaxZoom().coerceIn(2f, 22f)
        val pos = latLngs.first()
        if (oldZoom >= maxZ - SAME_LOCATION_MAX_ZOOM_EPSILON) {
            onSameLocationClusterClick(cluster.photos.first().id)
        } else {
            scope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(pos, maxZ)
                )
            }
        }
        return
    }
    val moved = runCatching {
        if (latLngs.isEmpty()) return@runCatching false

        val unique = latLngs.distinct()
        if (unique.size == 1) {
            val targetZoom = (oldZoom + 2f).coerceAtMost(20f)
            scope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(unique.first(), targetZoom)
                )
            }
            true
        } else {
            val b = LatLngBounds.builder()
            unique.forEach { b.include(it) }
            val bounds = b.build()
            val paddingPx = 220
            scope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)
                )
            }
            true
        }
    }.getOrElse { false }

    if (!moved) {
        val targetZoom = (oldZoom + 2f).coerceAtMost(20f)
        scope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(cluster.position, targetZoom)
            )
        }
    }
}
