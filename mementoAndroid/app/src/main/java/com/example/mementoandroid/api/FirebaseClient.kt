package com.example.mementoandroid.api

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mementoandroid.MainActivity
import com.example.mementoandroid.R
import com.example.mementoandroid.util.AuthTokenStore
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class FirebaseClient : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        private const val CHANNEL_ID = "memento_notifications"
        private const val CHANNEL_NAME = "Memento Notifications"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AuthTokenStore.init(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New device token generated: $token")
        AuthTokenStore.init(applicationContext)
        val authToken = AuthTokenStore.get()
        if (authToken != null) {
            serviceScope.launch {
                val body = org.json.JSONObject().put("fcm_token", token)
                BackendClient.post("/notifications/register-device", body, authToken)
                    .onSuccess { Log.d(TAG, "Device token registered with server") }
                    .onFailure { Log.w(TAG, "Failed to register device token: $it") }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        val messageType = remoteMessage.data["type"]

        when (messageType) {
            "anniversary_location_check" -> {
                serviceScope.launch {
                    handleAnniversaryLocationCheck()
                }
            }
            else -> {
                val title = remoteMessage.notification?.title ?: "Memento"
                val body = remoteMessage.notification?.body ?: "New notification"
                showNotification(title, body)
            }
        }
    }

    private suspend fun handleAnniversaryLocationCheck() {
        withContext(Dispatchers.IO) {
            AuthTokenStore.init(applicationContext)
            val authToken = AuthTokenStore.get() ?: return@withContext
            val location = getLastLocation() ?: return@withContext
            val path = "/location/anniversary-check?lat=${location.latitude}&lng=${location.longitude}"
            val result = BackendClient.get(path, authToken)
            result.onSuccess { json ->
                if (json.optBoolean("has_match", false)) {
                    BackendClient.post("/notifications/send-anniversary-push", null, authToken)
                        .onSuccess { Log.d(TAG, "Anniversary push triggered") }
                        .onFailure { Log.w(TAG, "Failed to trigger anniversary push: $it") }
                }
            }.onFailure { Log.w(TAG, "Anniversary check failed: $it") }
        }
    }

    private suspend fun getLastLocation(): android.location.Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted")
            return null
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc ->
                    if (!cont.isCompleted) cont.resume(loc)
                }
                .addOnFailureListener {
                    if (!cont.isCompleted) cont.resume(null)
                }
        }
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Memento app"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        Log.d(TAG, "Notification displayed successfully")
    }
}
