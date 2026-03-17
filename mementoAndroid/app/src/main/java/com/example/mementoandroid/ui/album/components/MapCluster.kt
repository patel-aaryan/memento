package com.example.mementoandroid.ui.album.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.mementoandroid.ui.album.AlbumPhotoUi
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import androidx.core.graphics.createBitmap
import kotlin.math.abs

data class PhotoCluster(
    val position: LatLng,
    val photos: List<AlbumPhotoUi>
)

fun clusterPhotosFast(
    map: GoogleMap,
    photos: List<AlbumPhotoUi>,
    clusterSizePx: Int = 120
): List<PhotoCluster> {

    val projection = map.projection
    if (photos.isEmpty()) return emptyList()

    // Interpret `clusterSizePx` as an "overlap radius" in screen pixels.
    // Anything within this radius is treated as overlapping and clustered together.
    val thresholdPx = clusterSizePx.coerceAtLeast(1)
    val thresholdSq = thresholdPx.toLong() * thresholdPx.toLong()

    data class ScreenItem(
        val photo: AlbumPhotoUi,
        val latLng: LatLng,
        val x: Int,
        val y: Int
    )

    val items = photos.mapNotNull { photo ->
        val lat = photo.latitude
        val lng = photo.longitude
        if (lat == null || lng == null) return@mapNotNull null
        val ll = LatLng(lat, lng)
        val pt = projection.toScreenLocation(ll)
        ScreenItem(photo = photo, latLng = ll, x = pt.x, y = pt.y)
    }

    if (items.isEmpty()) return emptyList()

    // Spatial hash: bucket points into grid cells, then only compare with 8 neighbor cells.
    val cellSize = thresholdPx
    fun cellKey(x: Int, y: Int): Pair<Int, Int> = (x / cellSize) to (y / cellSize)

    val buckets = mutableMapOf<Pair<Int, Int>, MutableList<Int>>() // cell -> indices
    items.forEachIndexed { idx, it ->
        buckets.getOrPut(cellKey(it.x, it.y)) { mutableListOf() }.add(idx)
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
                val tmp = ra
                ra = rb
                rb = tmp
            }
            parent[rb] = ra
            if (rank[ra] == rank[rb]) rank[ra]++
        }
    }

    val uf = UnionFind(items.size)

    // Compare within each bucket + neighboring buckets.
    val neighborDeltas = listOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0, 0 to 0, 1 to 0,
        -1 to 1, 0 to 1, 1 to 1
    )

    buckets.forEach { (cell, idxs) ->
        neighborDeltas.forEach { (dx, dy) ->
            val neighbor = (cell.first + dx) to (cell.second + dy)
            val jdxs = buckets[neighbor] ?: return@forEach

            // Avoid doing each pair twice across cells by ordering.
            // If it's the same cell, we still do pairwise i<j.
            if (dx < 0 || (dx == 0 && dy < 0)) return@forEach

            if (neighbor == cell) {
                for (a in 0 until idxs.size) {
                    val i = idxs[a]
                    val pi = items[i]
                    for (b in a + 1 until idxs.size) {
                        val j = idxs[b]
                        val pj = items[j]
                        val dxp = (pi.x - pj.x).toLong()
                        val dyp = (pi.y - pj.y).toLong()
                        if (dxp * dxp + dyp * dyp <= thresholdSq) uf.union(i, j)
                    }
                }
            } else {
                for (i in idxs) {
                    val pi = items[i]
                    for (j in jdxs) {
                        val pj = items[j]
                        // Cheap reject for big deltas before squaring (helps a bit on dense maps)
                        if (abs(pi.x - pj.x) > thresholdPx || abs(pi.y - pj.y) > thresholdPx) continue
                        val dxp = (pi.x - pj.x).toLong()
                        val dyp = (pi.y - pj.y).toLong()
                        if (dxp * dxp + dyp * dyp <= thresholdSq) uf.union(i, j)
                    }
                }
            }
        }
    }

    // Materialize clusters by union-find root.
    val byRoot = mutableMapOf<Int, MutableList<ScreenItem>>()
    items.forEachIndexed { idx, it ->
        byRoot.getOrPut(uf.find(idx)) { mutableListOf() }.add(it)
    }

    return byRoot.values.map { grouped ->
        val avgLat = grouped.map { it.photo.latitude!! }.average()
        val avgLng = grouped.map { it.photo.longitude!! }.average()
        PhotoCluster(
            position = LatLng(avgLat, avgLng),
            photos = grouped.map { it.photo }
        )
    }
}

fun createClusterIcon(context: Context, count: Int): BitmapDescriptor {

    val size = 120
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.BLACK
    canvas.drawCircle(size/2f, size/2f, size/2f, paint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    textPaint.color = Color.WHITE
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.textSize = 40f

    val y = size/2f - (textPaint.descent() + textPaint.ascent())/2
    canvas.drawText(count.toString(), size/2f, y, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

fun createSegmentedClusterIcon(
    context: Context,
    count: Int,
    segmentColors: List<Int>,
    sizeDp: Int = 70,
    borderDp: Int = 5,
    fillColor: Int = Color.BLACK,
    textColor: Int = Color.WHITE,
): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    val borderPx = (borderDp * density).coerceAtLeast(1f)

    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)

    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = sizePx / 2f

    // Fill circle.
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    canvas.drawCircle(cx, cy, radius - borderPx, fillPaint)

    // Segmented border ring.
    val colors = if (segmentColors.isNotEmpty()) segmentColors else listOf(Color.WHITE)
    val n = colors.size.coerceAtLeast(1)
    val sweep = 360f / n
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderPx
        strokeCap = Paint.Cap.BUTT
    }

    val inset = borderPx / 2f
    val oval = RectF(inset, inset, sizePx - inset, sizePx - inset)
    for (i in 0 until n) {
        ringPaint.color = colors[i]
        // Start at top (12 o'clock) and go clockwise.
        val start = -90f + (i * sweep)
        // Tiny gap between segments for clarity.
        val gap = (0.8f).coerceAtMost(sweep * 0.15f)
        val segSweep = (sweep - gap).coerceAtLeast(1f)
        canvas.drawArc(oval, start + gap / 2f, segSweep, false, ringPaint)
    }

    // Count text.
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        // Scale font with marker size.
        textSize = (sizePx * 0.42f).coerceAtMost(56f)
    }
    val y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(count.toString(), cx, y, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}