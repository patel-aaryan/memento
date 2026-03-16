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

/** Parse ISO to hour (0-23) and minute (0-59) in the device's local timezone (so edit form matches displayed time). */
fun parseIsoToHourMinute(iso: String?): Pair<Int, Int>? {
    val instant = parseIsoToMillis(iso) ?: return null
    val cal = Calendar.getInstance()
    cal.timeInMillis = instant
    return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

/** Given an instant (UTC millis), return millis for midnight at the start of that day in the device's local timezone (for date picker). */
fun instantToLocalDateMillis(instantMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = instantMillis
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH)
    val d = cal.get(Calendar.DAY_OF_MONTH)
    cal.set(y, m, d, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * Material3 DatePicker returns selectedDateMillis as midnight UTC for the chosen day.
 * Convert to midnight in the device's local timezone for that same calendar date so
 * formatDateMillis() shows the correct day (e.g. March 20 stays March 20).
 */
fun datePickerMillisToLocalMidnight(utcMidnightMillis: Long): Long {
    val calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calUtc.timeInMillis = utcMidnightMillis
    val y = calUtc.get(Calendar.YEAR)
    val m = calUtc.get(Calendar.MONTH)
    val d = calUtc.get(Calendar.DAY_OF_MONTH)
    val local = Calendar.getInstance()
    local.set(y, m, d, 0, 0, 0)
    local.set(Calendar.MILLISECOND, 0)
    return local.timeInMillis
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
