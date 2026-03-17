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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.UiSettings
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import com.google.maps.android.compose.MapEffect
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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

private fun haversineKm(a: LatLng, b: LatLng): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

private fun boundsOf(points: List<LatLng>): LatLngBounds? {
    if (points.isEmpty()) return null
    val b = LatLngBounds.builder()
    points.forEach { b.include(it) }
    return b.build()
}

/**
 * If photos are globally very spread out (e.g., multiple continents), centering on the full bounds
 * can start the user on "empty ocean" at a very low zoom. This picks the largest geographic group.
 */
private fun pickLargestGeoGroup(points: List<LatLng>): List<LatLng> {
    if (points.size <= 1) return points

    // Rough spatial hashing so we don't do O(n^2) comparisons.
    val latStepDeg = 5.0
    val lngStepDeg = 5.0
    fun cellKey(p: LatLng): Pair<Int, Int> =
        floor(p.latitude / latStepDeg).toInt() to floor(p.longitude / lngStepDeg).toInt()

    val buckets = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    points.forEachIndexed { idx, p ->
        buckets.getOrPut(cellKey(p)) { mutableListOf() }.add(idx)
    }

    class UnionFind(n: Int) {
        private val parent = IntArray(n) { it }
        private val rank = IntArray(n)
        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            var ra = find(a)
            var rb = find(b)
            if (ra == rb) return
            if (rank[ra] < rank[rb]) {
                val t = ra; ra = rb; rb = t
            }
            parent[rb] = ra
            if (rank[ra] == rank[rb]) rank[ra]++
        }
    }

    val uf = UnionFind(points.size)

    // Consider points "same group" if within this distance.
    // Large enough to group a city/region, small enough to keep continents separate.
    val epsKm = 5000.0

    val neighborDeltas = listOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0, 0 to 0, 1 to 0,
        -1 to 1, 0 to 1, 1 to 1
    )

    buckets.forEach { (cell, idxs) ->
        neighborDeltas.forEach { (dx, dy) ->
            val neighbor = (cell.first + dx) to (cell.second + dy)
            val jdxs = buckets[neighbor] ?: return@forEach
            if (dx < 0 || (dx == 0 && dy < 0)) return@forEach

            if (neighbor == cell) {
                for (a in 0 until idxs.size) {
                    val i = idxs[a]
                    for (b in a + 1 until idxs.size) {
                        val j = idxs[b]
                        if (haversineKm(points[i], points[j]) <= epsKm) uf.union(i, j)
                    }
                }
            } else {
                for (i in idxs) {
                    for (j in jdxs) {
                        // Quick reject by degrees (very rough but fast).
                        val pi = points[i]
                        val pj = points[j]
                        if (abs(pi.latitude - pj.latitude) > 15 || abs(pi.longitude - pj.longitude) > 15) continue
                        if (haversineKm(pi, pj) <= epsKm) uf.union(i, j)
                    }
                }
            }
        }
    }

    val groups = mutableMapOf<Int, MutableList<LatLng>>()
    points.forEachIndexed { idx, p ->
        groups.getOrPut(uf.find(idx)) { mutableListOf() }.add(p)
    }

    // Pick the largest group; tie-break by smallest geographic span.
    return groups.values
        .sortedWith(
            compareByDescending<List<LatLng>> { it.size }.thenBy {
                val b = boundsOf(it)!!
                haversineKm(b.southwest, b.northeast)
            }
        )
        .first()
}

private fun pickLargestFittableSubset(points: List<LatLng>, fitDiagonalKm: Double): List<LatLng> {
    if (points.size <= 2) return points

    fun diagonalKmOf(pts: List<LatLng>): Double {
        val b = boundsOf(pts) ?: return 0.0
        return haversineKm(b.southwest, b.northeast)
    }

    // If everything already fits, keep it simple.
    if (diagonalKmOf(points) <= fitDiagonalKm) return points

    // Greedy per-seed: grow a set while the bounding diagonal stays <= fitDiagonalKm.
    // This favors "fit as many as possible" without going exponential.
    var best: List<LatLng> = listOf(points.first())
    var bestDiag = Double.POSITIVE_INFINITY

    points.forEach { seed ->
        val others = points
            .filter { it != seed }
            .sortedBy { haversineKm(seed, it) }

        val current = mutableListOf(seed)
        var currentDiag = 0.0

        for (p in others) {
            val candidate = current + p
            val diag = diagonalKmOf(candidate)
            if (diag <= fitDiagonalKm) {
                current.add(p)
                currentDiag = diag
            }
        }

        val diagForCurrent = if (current.size == 1) Double.POSITIVE_INFINITY else currentDiag
        if (current.size > best.size || (current.size == best.size && diagForCurrent < bestDiag)) {
            best = current.toList()
            bestDiag = diagForCurrent
        }
    }

    return best
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
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }

    // Calculate window to include all photos
    LaunchedEffect(photosWithLocation) {
        if (photosWithLocation.isNotEmpty()) {
            val points = photosWithLocation.map { LatLng(it.latitude!!, it.longitude!!) }
            if (points.size == 1) {
                // Single point: don't zoom all the way in.
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(points.first(), 10f)
                )
                return@LaunchedEffect
            }

            val allBounds = boundsOf(points)

            // If the overall span is enormous, don't zoom out to "empty ocean".
            // Instead, fit the largest geographic group so the user starts with photos on-screen.
            val usePoints = if (allBounds != null) {
                val diagonalKm = haversineKm(allBounds.southwest, allBounds.northeast)
                // First try to fit as many as possible within a "reasonable" initial view.
                // (e.g., California + Toronto can fit, but China + North America cannot)
                val fittable = if (diagonalKm > 4500.0) {
                    pickLargestFittableSubset(points, fitDiagonalKm = 4500.0)
                } else points

                if (fittable.size >= 2) fittable
                else if (diagonalKm > 6000.0) pickLargestGeoGroup(points)
                else points
            } else points

            val bounds = boundsOf(usePoints)
            if (bounds != null) {
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngBounds(bounds, 200)
                )
            }
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

    var clusters by remember { mutableStateOf<List<PhotoCluster>>(emptyList()) }
    var mapRef by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { mapSizePx = it }
    ) {
        GoogleMap(
//        modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = com.google.maps.android.compose.MapUiSettings(zoomControlsEnabled = false)
        ) {
            MapEffect(photosWithLocation) { map ->
                mapRef = map

                fun updateClusters() {
                    clusters = clusterPhotosFast(map, photosWithLocation)
                }

                map.setOnCameraIdleListener {
                    updateClusters()
                }

                updateClusters()
            }

            // Also recompute clusters when zoom changes (some devices/paths don't reliably hit idle).
            LaunchedEffect(mapRef, photosWithLocation) {
                val map = mapRef ?: return@LaunchedEffect
                snapshotFlow { cameraPositionState.position }
                    .map { it.zoom }
                    .distinctUntilChanged()
                    .collect {
                        clusters = clusterPhotosFast(map, photosWithLocation)
                    }
            }

            // DEBUG
//        photosWithLocation.forEach { photo ->
//            val takenAt = photo.dateAdded
//            Log.d("MapDebug", "$takenAt")
//        }
//        Log.d("MapDebug", "$firstDate, $lastDate, $totalDays")

            clusters.forEach { cluster ->
                if (cluster.photos.size == 1) {
                    val photo = cluster.photos.first()
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
                        state = MarkerState(cluster.position),
                        icon = markerIcon,
                        onClick = {
                            onPhotoClick(photo.id)
                            true
                        }
                    )
                } else {
                    // Compute per-photo border colors (same as single-pin logic),
                    // then use them to draw a segmented cluster ring.
                    val segmentColors = remember(cluster.photos, firstDate, totalDays) {
                        // Too many segments becomes unreadable; sample evenly for large clusters.
                        val maxSegments = 24
                        val src = cluster.photos
                        val sampled =
                            if (src.size <= maxSegments) src
                            else {
                                val step = src.size.toFloat() / maxSegments
                                (0 until maxSegments).map { idx -> src[(idx * step).toInt().coerceIn(0, src.lastIndex)] }
                            }

                        sampled.map { p ->
                            val date = extractDate(p.dateAdded)
                            val daysSinceStart = if (date != null && firstDate != null) {
                                ChronoUnit.DAYS.between(firstDate, date).toFloat()
                            } else 0f
                            val fraction = (daysSinceStart / totalDays).coerceIn(0f, 1f)
                            interpolateColor(Color.RED, Color.BLUE, fraction)
                        }
                    }

                    Marker(
                        state = MarkerState(cluster.position),
                        icon = createSegmentedClusterIcon(
                            context = context,
                            count = cluster.photos.size,
                            segmentColors = segmentColors,
                            sizeDp = 70,
                            borderDp = 5,
                        ),
                        onClick = {
                            val oldZoom = cameraPositionState.position.zoom
                            val latLngs = cluster.photos.mapNotNull { p ->
                                val lat = p.latitude
                                val lng = p.longitude
                                if (lat == null || lng == null) null else LatLng(lat, lng)
                            }

                            // Fit clicked cluster to view with padding. This is much more reliable
                            // than "zoom + N" because it adapts to how spread out the cluster is.
                            val moved = runCatching {
                                if (latLngs.isEmpty()) return@runCatching false

                                val unique = latLngs.distinct()
                                if (unique.size == 1) {
                                    // Degenerate bounds (all points same). Just zoom in.
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
                                // Fallback: at least attempt a zoom-in centered on cluster.
                                val targetZoom = (oldZoom + 2f).coerceAtMost(20f)
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(cluster.position, targetZoom)
                                    )
                                }
                            }

                            true
                        }
                    )
                }
            }
        }

        // Yellow off-screen indicators for clusters/photos outside the viewport.
        // Each tick is drawn on the edge pointing toward the off-screen cluster.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val map = mapRef ?: return@Canvas
            val w = mapSizePx.width.toFloat()
            val h = mapSizePx.height.toFloat()
            if (w <= 0f || h <= 0f) return@Canvas

            val insetPx = with(density) { 5.dp.toPx() }
            val lineLen = with(density) { 18.dp.toPx() }
            val strokeW = with(density) { 3.dp.toPx() }
            val binPx = with(density) { 14.dp.toPx() }.coerceAtLeast(6f)

            val left = insetPx
            val right = w - insetPx
            val top = insetPx
            val bottom = h - insetPx

            val cx = w / 2f
            val cy = h / 2f

            val proj = map.projection
            val seen = HashSet<String>()
            val yellow = ComposeColor(0xFFFFEB3B)

            clusters.forEach { cluster ->
                val pt = proj.toScreenLocation(cluster.position)
                val x = pt.x.toFloat()
                val y = pt.y.toFloat()

                val inside = x in left..right && y in top..bottom
                if (inside) return@forEach

                val dx = x - cx
                val dy = y - cy
                if (dx == 0f && dy == 0f) return@forEach

                // Intersect ray from center -> point with the inset rectangle.
                var t = Float.POSITIVE_INFINITY
                if (dx > 0f) t = min(t, (right - cx) / dx)
                if (dx < 0f) t = min(t, (left - cx) / dx)
                if (dy > 0f) t = min(t, (bottom - cy) / dy)
                if (dy < 0f) t = min(t, (top - cy) / dy)
                if (!t.isFinite() || t <= 0f) return@forEach

                val ix = (cx + dx * t).coerceIn(left, right)
                val iy = (cy + dy * t).coerceIn(top, bottom)

                val edge = when {
                    abs(ix - left) < 1.5f -> "L"
                    abs(ix - right) < 1.5f -> "R"
                    abs(iy - top) < 1.5f -> "T"
                    abs(iy - bottom) < 1.5f -> "B"
                    else -> {
                        // Fallback: pick nearest edge.
                        val dl = abs(ix - left)
                        val dr = abs(ix - right)
                        val dt = abs(iy - top)
                        val db = abs(iy - bottom)
                        val m = min(min(dl, dr), min(dt, db))
                        when (m) {
                            dl -> "L"
                            dr -> "R"
                            dt -> "T"
                            else -> "B"
                        }
                    }
                }

                val key = when (edge) {
                    "L", "R" -> "$edge:${(iy / binPx).toInt()}"
                    else -> "$edge:${(ix / binPx).toInt()}"
                }
                if (!seen.add(key)) return@forEach

                when (edge) {
                    "L", "R" -> {
                        val xEdge = if (edge == "L") left else right
                        val y0 = (iy - lineLen / 2f).coerceIn(top, bottom)
                        val y1 = (iy + lineLen / 2f).coerceIn(top, bottom)
                        drawLine(
                            color = yellow,
                            start = Offset(xEdge, y0),
                            end = Offset(xEdge, y1),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                    "T", "B" -> {
                        val yEdge = if (edge == "T") top else bottom
                        val x0 = (ix - lineLen / 2f).coerceIn(left, right)
                        val x1 = (ix + lineLen / 2f).coerceIn(left, right)
                        drawLine(
                            color = yellow,
                            start = Offset(x0, yEdge),
                            end = Offset(x1, yEdge),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                }
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