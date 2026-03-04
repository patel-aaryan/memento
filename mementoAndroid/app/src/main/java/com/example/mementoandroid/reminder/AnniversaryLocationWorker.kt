package com.example.mementoandroid.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import com.example.mementoandroid.MainActivity
import com.example.mementoandroid.R
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.util.AuthTokenStore
import com.example.mementoandroid.util.AnniversaryNotificationStore
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class AnniversaryLocationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "anniversary_reminders"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "AnniversaryLocationWorker"
        private const val UNIQUE_WORK_NAME = "anniversary_location_work"

        fun scheduleNext(context: Context) {
            Log.d(TAG, "scheduleNext() called")
            val constraints = Constraints.Builder().build()

            val workRequest = OneTimeWorkRequestBuilder<AnniversaryLocationWorker>()
                .setInitialDelay(1, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Log.d(TAG, "doWork() started")

        val token = AuthTokenStore.get()
        if (token == null) {
            Log.d(TAG, "No auth token; skipping anniversary check")
            return Result.success()
        }

        val hasFineLocation = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted; skipping anniversary check")
            return Result.success()
        }

        val location = getLastLocation(ctx)
        if (location == null) {
            Log.w(TAG, "No last known location; skipping anniversary check")
            return Result.success()
        }

        Log.d(
            TAG,
            "Checking anniversary for location lat=${location.latitude}, lng=${location.longitude}"
        )

        val path =
            "/location/anniversary-check?lat=${location.latitude}&lng=${location.longitude}"
        val result = BackendClient.get(path, token)

        result.onSuccess { json ->
            if (json.optBoolean("has_match", false)) {
                val key = buildAnniversaryKey(location)
                if (AnniversaryNotificationStore.hasSeen(ctx, key)) {
                    Log.d(TAG, "Anniversary already notified for key=$key; skipping notification")
                } else {
                    Log.d(TAG, "Anniversary match found; showing notification for key=$key")
                    showNotification(ctx)
                    AnniversaryNotificationStore.markSeen(ctx, key)
                }
            } else {
                Log.d(TAG, "No anniversary match for this run")
            }
        }.onFailure { e ->
            Log.w(TAG, "Anniversary check failed", e)
        }

        // Schedule the next run ~1 minute from now
        scheduleNext(ctx)
        return Result.success()
    }

    private suspend fun getLastLocation(context: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine<Location?> { cont ->
            client.lastLocation
                .addOnSuccessListener { loc ->
                    if (!cont.isCompleted) {
                        cont.resume(loc)
                    }
                }
                .addOnFailureListener {
                    if (!cont.isCompleted) {
                        cont.resume(null)
                    }
                }
        }
    }

    private fun buildAnniversaryKey(location: Location): String {
        // One key per calendar day and approx location (~0.001 deg ~ 100m) to avoid duplicates.
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val latBucket = (location.latitude * 1000).toInt()
        val lngBucket = (location.longitude * 1000).toInt()
        return "${day}_${latBucket}_${lngBucket}"
    }

    private fun showNotification(context: Context) {
        createChannelIfNeeded(context)
        val intent = Intent(context, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Memory anniversary")
            .setContentText("You were here a year ago. Want to add a new photo?")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Memory anniversaries",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description =
                "Reminders to take a photo when you revisit a place from a year ago"
        }
        manager.createNotificationChannel(channel)
    }
}

