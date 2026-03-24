package com.example.mementoandroid.ui.album.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.mementoandroid.R
import com.example.mementoandroid.ui.album.AlbumPhotoUi
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.graphics.Color as ComposeColor
import android.util.Log
import androidx.compose.ui.graphics.StrokeCap

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

@RequiresApi(Build.VERSION_CODES.O)
fun extractTimelineDate(photo: AlbumPhotoUi): LocalDate? {
    // Keep this in sync with how album sorting / detail screen pick the primary time:
    // prefer takenAt, then fall back to dateAdded.
    val candidates = listOfNotNull(photo.takenAt, photo.dateAdded)
    for (raw in candidates) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") continue

        // Most robust: try to parse full offset datetime (backend-style), then fall back to date-only prefix.
        try {
            val normalized = trimmed.replace(' ', 'T')
            return OffsetDateTime.parse(normalized).toLocalDate()
        } catch (_: Exception) {
        }

        if (trimmed.length >= 10) {
            try {
                return LocalDate.parse(trimmed.substring(0, 10))
            } catch (_: Exception) {
            }
        }
    }
    return null
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
    /** Multi-photo cluster at a single map point: open first photo and sort album by location. */
    onSameLocationClusterClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val photosWithLocation = remember(photos) {
        photos.filter { it.latitude != null && it.longitude != null }
    }
    val cameraPositionState = rememberCameraPositionState()
    val scope = rememberCoroutineScope()

    data class MissingInfoEntry(
        val photo: AlbumPhotoUi,
        val description: String
    )

    val missingInfoEntries = remember(photos) {
        photos.flatMap { photo ->
            val list = mutableListOf<MissingInfoEntry>()
            val hasLocation = photo.latitude != null && photo.longitude != null
            if (!hasLocation) {
                list.add(MissingInfoEntry(photo, "Missing location"))
            }
            val hasTime = extractTimelineDate(photo) != null
            if (!hasTime) {
                list.add(MissingInfoEntry(photo, "Missing time"))
            }
            list
        }
    }
    val missingImagesCount = remember(missingInfoEntries) {
        missingInfoEntries.map { it.photo.id }.toSet().size
    }
    var showMissingInfoDialog by remember { mutableStateOf(false) }

    // Debug log: all image times in this album (raw + parsed as used for coloring).
    LaunchedEffect(photos) {
        Log.d("MapScreenDebug", "----- Album photo times (${photos.size}) -----")
        photos.forEach { photo ->
            val parsedDate = extractTimelineDate(photo)
            Log.d(
                "MapScreenDebug",
                "id=${photo.id}, takenAt=${photo.takenAt}, dateAdded=${photo.dateAdded}, timelineDate=$parsedDate, lat=${photo.latitude}, lng=${photo.longitude}"
            )
        }
        Log.d("MapScreenDebug", "----------------------------------------------")
    }

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

    // Find first and last times over the whole album (not just photos with location),
    // using the same precedence as sorting/detail screens (takenAt, then dateAdded).
    val photoDates = remember(photos) { photos.mapNotNull { extractTimelineDate(it) } }

    val firstDate = photoDates.minOrNull()
    val lastDate = photoDates.maxOrNull()

    val totalDays = if (firstDate != null && lastDate != null) {
        ChronoUnit.DAYS.between(firstDate, lastDate).coerceAtLeast(1)
    } else 1

    var clusters by remember { mutableStateOf<List<PhotoCluster>>(emptyList()) }
    var mapRef by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    var mapViewSize by remember { mutableStateOf(IntSize.Zero) }
    var offScreenHudItems by remember { mutableStateOf<List<OffScreenHudItem>>(emptyList()) }
    val density = LocalDensity.current
    // Match avatar/cluster markers (~70.dp diameter) so HUD hides until the pin is fully off-screen.
    val markerRadiusPx = remember(density) { with(density) { 35.dp.toPx() } }
    val hudPlacementInsetPx = remember(density) { with(density) { 20.dp.toPx() } }

    LaunchedEffect(mapRef, mapViewSize, clusters, markerRadiusPx, hudPlacementInsetPx) {
        val map = mapRef ?: return@LaunchedEffect
        val w = mapViewSize.width
        val h = mapViewSize.height
        if (w <= 0 || h <= 0) return@LaunchedEffect
        fun recomputeHud() {
            offScreenHudItems = computeOffScreenHudItems(
                map = map,
                clusters = clusters,
                mapWidthPx = w,
                mapHeightPx = h,
                markerRadiusPx = markerRadiusPx,
                hudPlacementInsetPx = hudPlacementInsetPx,
            )
        }
        recomputeHud()
        snapshotFlow { cameraPositionState.position }
            .collect { recomputeHud() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .onGloballyPositioned { coords -> mapViewSize = coords.size }
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
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

                        // Compute border color: grey if no time, otherwise gradient.
                        val date = extractTimelineDate(photo)
                        val borderColor = if (date == null || firstDate == null) {
                            Color.GRAY
                        } else {
                            val daysSinceStart = ChronoUnit.DAYS.between(firstDate, date).toFloat()
                            val fraction = (daysSinceStart / totalDays).coerceIn(0f, 1f)
                            interpolateColor(
                                Color.RED,   // earliest
                                Color.BLUE,  // latest
                                fraction
                            )
                        }

                        // Create map marker (recreate if bitmap or border color changes)
                        val markerIcon = remember(markerBitmap, borderColor) {
                            markerBitmap?.let { createAvatarMarker(context, it, borderColor) }
                        }

                        Marker(
                            state = MarkerState(cluster.position),
                            icon = markerIcon,
                            // Single pins sit "below" clusters when overlapping.
                            zIndex = 0f,
                            onClick = {
                                runClusterMarkerAction(
                                    cluster,
                                    cameraPositionState,
                                    scope,
                                    onPhotoClick,
                                    onSameLocationClusterClick,
                                )
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
                                val date = extractTimelineDate(p)
                                if (date == null || firstDate == null) {
                                    Color.GRAY
                                } else {
                                    val daysSinceStart = ChronoUnit.DAYS.between(firstDate, date).toFloat()
                                    val fraction = (daysSinceStart / totalDays).coerceIn(0f, 1f)
                                    interpolateColor(Color.RED, Color.BLUE, fraction)
                                }
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
                            // Make clusters sit above individual pins so taps prefer clusters.
                            zIndex = 1f,
                            onClick = {
                                runClusterMarkerAction(
                                    cluster,
                                    cameraPositionState,
                                    scope,
                                    onPhotoClick,
                                    onSameLocationClusterClick,
                                )
                                true
                            }
                        )
                    }
                }
            }

            MapOffScreenIndicatorLayer(
                items = offScreenHudItems,
                indicatorSizeDp = 40f,
                density = density,
                onIndicatorClick = { cluster ->
                    runOffScreenIndicatorClick(
                        cluster,
                        cameraPositionState,
                        scope,
                        onPhotoClick,
                        onSameLocationClusterClick,
                    )
                },
            )

            TimeGradientLegend(
                startDate = firstDate,
                endDate = lastDate,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }

        if (missingImagesCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$missingImagesCount images missing information",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { showMissingInfoDialog = true },
                color = ComposeColor(0xFF1976D2)
            )
        }

        if (showMissingInfoDialog) {
            AlertDialog(
                onDismissRequest = { showMissingInfoDialog = false },
                title = {
                    Text(text = "Missing information")
                },
                text = {
                    Column(
                        modifier = Modifier
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        missingInfoEntries.forEach { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showMissingInfoDialog = false
                                        onPhotoClick(entry.photo.id)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                val thumbBitmap by produceState<Bitmap?>(initialValue = null, entry.photo) {
                                    value = loadPhotoBitmap(context, entry.photo)
                                }
                                if (thumbBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = thumbBitmap!!.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(text = entry.description)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showMissingInfoDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}