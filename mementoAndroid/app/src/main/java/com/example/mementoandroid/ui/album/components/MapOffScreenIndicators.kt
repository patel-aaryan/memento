package com.example.mementoandroid.ui.album.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMap
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

data class OffScreenHudItem(
    val xPx: Float,
    val yPx: Float,
    val rotationDeg: Float,
    val cluster: PhotoCluster,
)

/**
 * First exit point of the ray from map center [cx,cy] toward screen point [tx,ty] through the
 * inset rectangle. [cx,cy] must lie inside the box. Returns null if the direction is degenerate
 * or the ray does not exit (avoids falling back to center, which caused HUD flashes while panning).
 */
internal fun pointTowardTargetOnEdge(
    cx: Float,
    cy: Float,
    tx: Float,
    ty: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): Pair<Float, Float>? {
    val vx = tx - cx
    val vy = ty - cy
    if (vx * vx + vy * vy < 1e-4f) return null

    val uX = when {
        vx > 1e-6f -> (right - cx) / vx
        vx < -1e-6f -> (left - cx) / vx
        else -> Float.POSITIVE_INFINITY
    }
    val uY = when {
        vy > 1e-6f -> (bottom - cy) / vy
        vy < -1e-6f -> (top - cy) / vy
        else -> Float.POSITIVE_INFINITY
    }
    val u = minOf(uX, uY)
    if (!u.isFinite() || u <= 0f) return null

    val ex = (cx + u * vx).coerceIn(left, right)
    val ey = (cy + u * vy).coerceIn(top, bottom)
    return ex to ey
}

/**
 * True when any part of the circle (marker footprint) still overlaps the map view [0,w]×[0,h].
 */
internal fun markerCircleIntersectsMapView(
    centerX: Float,
    centerY: Float,
    radiusPx: Float,
    mapWidthPx: Int,
    mapHeightPx: Int,
): Boolean {
    val w = mapWidthPx.toFloat()
    val h = mapHeightPx.toFloat()
    val closestX = centerX.coerceIn(0f, w)
    val closestY = centerY.coerceIn(0f, h)
    val dx = centerX - closestX
    val dy = centerY - closestY
    return dx * dx + dy * dy <= radiusPx * radiusPx
}

/**
 * Edge HUD chips only when the marker circle is fully outside the map (no overlap with the view).
 * [markerRadiusPx] should match on-screen pin radius (half of marker diameter in px).
 * Chips are placed along the [hudPlacementInsetPx] inset.
 */
fun computeOffScreenHudItems(
    map: GoogleMap,
    clusters: List<PhotoCluster>,
    mapWidthPx: Int,
    mapHeightPx: Int,
    markerRadiusPx: Float,
    hudPlacementInsetPx: Float,
): List<OffScreenHudItem> {
    if (mapWidthPx <= 0 || mapHeightPx <= 0 || clusters.isEmpty()) return emptyList()
    val proj = try {
        map.projection
    } catch (_: Exception) {
        return emptyList()
    }
    val cx = mapWidthPx / 2f
    val cy = mapHeightPx / 2f
    val hudLeft = hudPlacementInsetPx
    val hudTop = hudPlacementInsetPx
    val hudRight = mapWidthPx - hudPlacementInsetPx
    val hudBottom = mapHeightPx - hudPlacementInsetPx
    if (hudRight <= hudLeft || hudBottom <= hudTop) return emptyList()

    val out = ArrayList<OffScreenHudItem>(clusters.size)
    clusters.forEachIndexed { index, cluster ->
        val pt = try {
            proj.toScreenLocation(cluster.position)
        } catch (_: Exception) {
            return@forEachIndexed
        }
        val mx = pt.x.toFloat()
        val my = pt.y.toFloat()
        if (!mx.isFinite() || !my.isFinite()) return@forEachIndexed
        if (markerCircleIntersectsMapView(mx, my, markerRadiusPx, mapWidthPx, mapHeightPx)) {
            return@forEachIndexed
        }

        val edge = pointTowardTargetOnEdge(cx, cy, mx, my, hudLeft, hudTop, hudRight, hudBottom)
            ?: return@forEachIndexed
        var (ex, ey) = edge

        // Reject bogus "center" placements (e.g. bad projection while the map is moving).
        val minHudRadius = minOf(mapWidthPx, mapHeightPx) * 0.08f
        if (hypot((ex - cx).toDouble(), (ey - cy).toDouble()).toFloat() < minHudRadius) {
            return@forEachIndexed
        }

        // Spread overlapping chips slightly along the perpendicular.
        val perpX = -(my - cy)
        val perpY = (mx - cx)
        val plen = hypot(perpX.toDouble(), perpY.toDouble()).toFloat().coerceAtLeast(1f)
        val spread = (index % 7 - 3) * 10f
        ex += (perpX / plen) * spread
        ey += (perpY / plen) * spread
        ex = ex.coerceIn(hudLeft, hudRight)
        ey = ey.coerceIn(hudTop, hudBottom)

        val rot = Math.toDegrees(
            atan2(
                (my - ey).toDouble(),
                (mx - ex).toDouble(),
            ),
        ).toFloat()
        out.add(OffScreenHudItem(ex, ey, rot, cluster))
    }
    return out
}

@Composable
fun BoxScope.MapOffScreenIndicatorLayer(
    items: List<OffScreenHudItem>,
    indicatorSizeDp: Float,
    density: Density,
    onIndicatorClick: (PhotoCluster) -> Unit,
) {
    val halfPx = with(density) { (indicatorSizeDp / 2f).dp.toPx() }
    items.forEach { item ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        (item.xPx - halfPx).roundToInt(),
                        (item.yPx - halfPx).roundToInt(),
                    )
                }
                .size(indicatorSizeDp.dp)
                .shadow(4.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
                .clickable { onIndicatorClick(item.cluster) },
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size((indicatorSizeDp * 0.55f).dp)
                    .rotate(item.rotationDeg),
            )
        }
    }
}
