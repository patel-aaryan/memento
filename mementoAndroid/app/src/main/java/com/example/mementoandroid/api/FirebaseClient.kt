package com.example.mementoandroid.api

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseClient : FirebaseMessagingService() {

    // Triggers when a new FCM token is generated (e.g., on first install)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "========================================")
        Log.d("FCM", "New device token generated: $token")
        Log.d("FCM", "========================================")
        // TODO: Make an HTTP POST request to your FastAPI backend to save this token
    }

    // Triggers when a message is received while the app is actively in the FOREGROUND
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message from: ${remoteMessage.from}")
        
        remoteMessage.notification?.let { notification ->
            Log.d("FCM", "Notification Title: ${notification.title}")
            Log.d("FCM", "Notification Body: ${notification.body}")
        }
        
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")
        }

        // Note: If the app is in the background, Android handles displaying
        // the notification in the system tray automatically.
    }
}