package com.example.lifeline

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "LifeLine"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "New update"

        NotificationHelper.showNotification(this, title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenManager.saveTokenToCurrentUser(this, token)
    }
}