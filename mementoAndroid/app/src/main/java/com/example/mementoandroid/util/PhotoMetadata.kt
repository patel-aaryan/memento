package com.example.mementoandroid.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "PhotoMetadata"

/**
 * EXIF stores GPS as rationals: "degrees/denom, minutes/denom, seconds/denom" (e.g. "43/1, 38/1, 1234/100").
 * Converts to decimal degrees: degrees + minutes/60 + seconds/3600, negated if ref is S or W.
 */
fun parseExifGpsToDecimalDegrees(
    rationalString: String?,
    ref: String?
): Double? {
    if (rationalString.isNullOrBlank()) return null
    val parts = rationalString.split(",").map { it.trim() }
    if (parts.size != 3) return null
    fun parseRational(s: String): Double? {
        val frac = s.split("/").map { it.trim() }
        if (frac.size != 2) return null
        val num = frac[0].toDoubleOrNull() ?: return null
        val denom = frac[1].toDoubleOrNull() ?: return null
        return if (denom != 0.0) num / denom else null
    }
    val d = parseRational(parts[0]) ?: return null
    val m = parseRational(parts[1]) ?: return null
    val s = parseRational(parts[2]) ?: return null
    var decimal = d + m / 60.0 + s / 3600.0
    if (ref.equals("S", ignoreCase = true) || ref.equals("W", ignoreCase = true)) decimal = -decimal
    return decimal
}

/**
 * Metadata that can be extracted from a photo URI (from picker or camera).
 * Not all fields will be present for every image.
 *
 * Note on location: When the URI comes from the system Photo Picker
 * (content://media/picker/...), Android intentionally strips EXIF GPS data
 * for privacy. So [latitude] and [longitude] will be null for picker photos
 * even if the original image had location. The device Gallery can show
 * location because it reads the original MediaStore item. To get location
 * you would need to use MediaStore with READ_MEDIA_IMAGES + ACCESS_MEDIA_LOCATION
 * and pick from MediaStore directly (different UX and permissions).
 */
data class PhotoMetadata(
    val uri: Uri,
    val displayName: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val dateTaken: String?,
    val dateTimeOriginal: String?,
    val make: String?,
    val model: String?,
    val widthPx: Int?,
    val heightPx: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val orientation: Int?,
)

/** Issue Tracker: Photo Picker strips location by design. https://issuetracker.google.com/issues/243294058 */
private const val PHOTO_PICKER_ISSUE_URL = "https://issuetracker.google.com/issues/243294058"

/**
 * Verifies and logs why latitude/longitude are null. Call when location is missing.
 *
 * What might cause null lat/long:
 * 1. Photo Picker URI (content://media/picker/... or .../photopicker/...) – system strips location by design; permissions do not help.
 * 2. Wrong picker: GetContent is often routed to the same picker (picker_get_content). Use OpenDocument (ACTION_OPEN_DOCUMENT) to get document URIs that keep EXIF.
 * 3. Image never had location (no GPS when taken, or already stripped by another app).
 * 4. Provider redaction: some content providers strip EXIF for privacy regardless of URI type.
 *
 * Root cause for Photo Picker: Google intentionally strips location at the media provider level (Issue 243294058, Won't Fix).
 */
fun verifyAndLogLocationStrippingCause(uri: Uri) {
    val uriString = uri.toString()
    val isPickerUri = uriString.contains("picker", ignoreCase = true) ||
        uriString.contains("photopicker", ignoreCase = true)
    Log.d(TAG, "========== verifyAndLogLocationStrippingCause ==========")
    Log.d(TAG, "uri: $uriString")
    Log.d(TAG, "uri.scheme=${uri.scheme} authority=${uri.authority} path=${uri.path}")
    Log.d(TAG, "isPhotoPickerUri: $isPickerUri")
    if (isPickerUri) {
        Log.w(TAG, "CAUSE: This is a Photo Picker URI. Google INTENTIONALLY strips GPS/location metadata from picker URIs for privacy. This is not a bug in your app.")
        Log.w(TAG, "See: $PHOTO_PICKER_ISSUE_URL (Status: Won't Fix / Intended Behavior)")
        Log.w(TAG, "VERIFY: Pick the SAME image using OpenDocument (ACTION_OPEN_DOCUMENT); EXIF location will be present.")
        Log.w(TAG, "WORKAROUND: Use ActivityResultContracts.OpenDocument() and launch(arrayOf(\"image/*\")). The returned URI (e.g. content://.../documents/document/image%3A123) will have full EXIF.")
    } else {
        Log.d(TAG, "URI is not a picker URI; location may be stripped for other reasons (permissions, provider, or image has no EXIF location).")
    }
    Log.d(TAG, "=========================================================")
}

/**
 * Extracts available metadata from a photo [Uri] using ContentResolver and ExifInterface.
 * Safe to call from a background thread. Returns null if the URI cannot be read.
 */
fun extractPhotoMetadata(context: Context, uri: Uri): PhotoMetadata? {
    val resolver = context.contentResolver
    var displayName: String? = null
    var sizeBytes: Long? = null

    // OpenableColumns: display name and size (works for most content URIs)
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
            if (sizeIndex >= 0) sizeBytes = cursor.getLong(sizeIndex)
        }
    }

    val mimeType = resolver.getType(uri)

    // EXIF (requires reading the stream)
    var dateTaken: String? = null
    var dateTimeOriginal: String? = null
    var make: String? = null
    var model: String? = null
    var widthPx: Int? = null
    var heightPx: Int? = null
    var lat: Double? = null
    var lon: Double? = null
    var orientation: Int? = null

    try {
        resolver.openInputStream(uri)?.use { inputStream ->
            val exif = ExifInterface(inputStream)
            // Dump every EXIF attribute in one log line
            val allTags = ExifInterface::class.java.declaredFields
                .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
                .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
            val dump = allTags.mapNotNull { tag -> exif.getAttribute(tag)?.let { "$tag=$it" } }.joinToString(", ")
            Log.d(TAG, "EXIF dump: $dump")
            val latRaw = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
            val lonRaw = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
            val latDec = parseExifGpsToDecimalDegrees(latRaw, latRef)
            val lonDec = parseExifGpsToDecimalDegrees(lonRaw, lonRef)
            Log.d(TAG, "GPS converted: lat=$latDec (raw=$latRaw ref=$latRef), lon=$lonDec (raw=$lonRaw ref=$lonRef)")
            dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME)
            dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            make = exif.getAttribute(ExifInterface.TAG_MAKE)
            model = exif.getAttribute(ExifInterface.TAG_MODEL)
            widthPx = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
            heightPx = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            val latLong = FloatArray(2)
            if (exif.getLatLong(latLong)) {
                lat = latLong[0].toDouble()
                lon = latLong[1].toDouble()
            } else if (latDec != null && lonDec != null && !latRef.isNullOrBlank() && !lonRef.isNullOrBlank()) {
                lat = latDec
                lon = lonDec
            }
        }
    } catch (e: IOException) {
        Log.w(TAG, "Could not read EXIF from $uri", e)
    }

    return PhotoMetadata(
        uri = uri,
        displayName = displayName,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        dateTaken = dateTaken,
        dateTimeOriginal = dateTimeOriginal,
        make = make,
        model = model,
        widthPx = widthPx,
        heightPx = heightPx,
        latitude = lat,
        longitude = lon,
        orientation = orientation,
    )
}

/**
 * EXIF date/time is typically "yyyy:MM:dd HH:mm:ss" (e.g. "2025:01:15 15:42:00").
 * Returns a display string like "Jan 15, 2025 · 3:42 PM", or null if parsing fails.
 */
fun formatPhotoMetadataDateTime(exifDateTime: String?): String? {
    if (exifDateTime.isNullOrBlank()) return null
    return try {
        val inFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val outFormat = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        val date = inFormat.parse(exifDateTime.trim())
        date?.let { outFormat.format(it) }
    } catch (e: Exception) {
        Log.d(TAG, "Could not parse EXIF datetime: $exifDateTime", e)
        null
    }
}

/**
 * Converts EXIF date/time ("yyyy:MM:dd HH:mm:ss") to ISO 8601 for the API (e.g. "2025-01-15T15:42:00Z").
 * Returns null if parsing fails.
 */
fun exifDateTimeToIso(exifDateTime: String?): String? {
    if (exifDateTime.isNullOrBlank()) return null
    return try {
        val inFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val outFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        outFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = inFormat.parse(exifDateTime.trim())
        date?.let { outFormat.format(it) }
    } catch (e: Exception) {
        Log.d(TAG, "Could not convert EXIF to ISO: $exifDateTime", e)
        null
    }
}

/**
 * Formats a backend timestamp (e.g. ISO "2025-01-15T15:42:00" or "2025-01-15 15:42:00")
 * to display string like "Jan 15, 2025 · 3:42 PM". Returns null if parsing fails.
 */
fun formatBackendDateTime(backendDate: String?): String? {
    if (backendDate.isNullOrBlank()) return null
    return try {
        val trimmed = backendDate.trim()
        val inFormat = when {
            trimmed.contains("T") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            trimmed.contains(" ") -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
        inFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val outFormat = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        val date = inFormat.parse(trimmed.substringBefore(".").substringBefore("Z"))
        date?.let { outFormat.format(it) }
    } catch (e: Exception) {
        Log.d(TAG, "Could not parse backend datetime: $backendDate", e)
        null
    }
}

/**
 * Formats latitude and longitude for display (e.g. "43.65° N, 79.38° W").
 */
fun formatPhotoMetadataLocation(latitude: Double, longitude: Double): String {
    val latDir = if (latitude >= 0) "N" else "S"
    val lonDir = if (longitude >= 0) "E" else "W"
    return String.format(
        Locale.US,
        "%.4f° %s, %.4f° %s",
        kotlin.math.abs(latitude),
        latDir,
        kotlin.math.abs(longitude),
        lonDir
    )
}

/**
 * Logs all extracted [PhotoMetadata] to Logcat under tag [TAG].
 * Call from a background thread if you're doing I/O elsewhere.
 */
fun logPhotoMetadata(metadata: PhotoMetadata) {
    Log.d(TAG, "========== Photo metadata ==========")
    Log.d(TAG, "uri: ${metadata.uri}")
    Log.d(TAG, "displayName: ${metadata.displayName}")
    Log.d(TAG, "sizeBytes: ${metadata.sizeBytes}")
    Log.d(TAG, "mimeType: ${metadata.mimeType}")
    Log.d(TAG, "dateTaken (EXIF): ${metadata.dateTaken}")
    Log.d(TAG, "dateTimeOriginal (EXIF): ${metadata.dateTimeOriginal}")
    Log.d(TAG, "make: ${metadata.make}")
    Log.d(TAG, "model: ${metadata.model}")
    Log.d(TAG, "widthPx: ${metadata.widthPx}, heightPx: ${metadata.heightPx}")
    Log.d(TAG, "latitude: ${metadata.latitude}, longitude: ${metadata.longitude}")
    Log.d(TAG, "orientation: ${metadata.orientation}")
    Log.d(TAG, "=====================================")
}
