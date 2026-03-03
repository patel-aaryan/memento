package com.example.mementoandroid.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mementoandroid.MainActivity
import com.example.mementoandroid.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseClient : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "memento_notifications"
        private const val CHANNEL_NAME = "Memento Notifications"
    }

    // Triggers when a new FCM token is generated (e.g., on first install)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "========================================")
        Log.d("FCM", "New device token generated: $token")
        Log.d("FCM", "========================================")
        // TODO: Make an HTTP POST request to your FastAPI backend to save this token
    }

    // Triggers when a message is received while the app is in the FOREGROUND or BACKGROUND
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "========================================")
        Log.d("FCM", "Message received from: ${remoteMessage.from}")
        
        // Extract notification data
        val title = remoteMessage.notification?.title ?: "Memento"
        val body = remoteMessage.notification?.body ?: "New notification"
        
        Log.d("FCM", "Notification Title: $title")
        Log.d("FCM", "Notification Body: $body")
        
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")
        }
        Log.d("FCM", "========================================")
        
        // Display the notification
        showNotification(title, body)

        // Note: If the app is in the background, Android handles displaying
        // the notification in the system tray automatically.
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android 8.0+
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
        
        // Intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Build the notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        // Show the notification
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        Log.d("FCM", "✓ Notification displayed successfully")
    }
}