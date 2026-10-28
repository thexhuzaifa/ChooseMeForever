package com.chooseme.forever.data.remote.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "❤️ ChooseMe Forever"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "You have a new update!"

        val notificationManager = AppNotificationManager(applicationContext)
        notificationManager.showNewMessageNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }
}
