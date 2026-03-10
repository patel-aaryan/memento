package com.example.mementoandroid.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Parse ISO timestamp (e.g. 2026-01-10T21:59:42Z) to UTC millis, or null. */
fun parseIsoToMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        val trimmed = iso.trim().substringBefore(".").substringBefore("Z")
        val format = when {
            trimmed.contains("T") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            trimmed.contains(" ") -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
        format.timeZone = TimeZone.getTimeZone("UTC")
        format.parse(trimmed)?.time
    } catch (_: Exception) { null }
}

/** Parse ISO to hour (0-23) and minute (0-59); uses UTC. Returns Pair(hour, minute) or null. */
fun parseIsoToHourMinute(iso: String?): Pair<Int, Int>? {
    if (iso.isNullOrBlank()) return null
    return try {
        val trimmed = iso.trim().substringBefore(".").substringBefore("Z")
        val format = when {
            trimmed.contains("T") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            trimmed.contains(" ") -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            else -> return null
        }
        format.timeZone = TimeZone.getTimeZone("UTC")
        val date = format.parse(trimmed) ?: return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    } catch (_: Exception) { null }
}

/** Build ISO UTC string from date millis (day in UTC) and local hour/minute. */
fun buildIsoFromDateAndTime(dateMillis: Long, hour: Int, minute: Int): String {
    val local = Calendar.getInstance()
    local.timeInMillis = dateMillis
    val y = local.get(Calendar.YEAR)
    val m = local.get(Calendar.MONTH)
    val d = local.get(Calendar.DAY_OF_MONTH)
    local.set(y, m, d, hour, minute, 0)
    local.set(Calendar.MILLISECOND, 0)
    val outFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    outFormat.timeZone = TimeZone.getTimeZone("UTC")
    return outFormat.format(local.time)
}

/** Format date millis as "Jan 10, 2026". */
fun formatDateMillis(millis: Long): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(millis)
}

/** Format hour/minute as "9:59 PM". */
fun formatTime(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
    }
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
}
