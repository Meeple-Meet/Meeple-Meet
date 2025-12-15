package com.github.meeplemeet.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.github.meeplemeet.MainActivity
import com.github.meeplemeet.R
import com.github.meeplemeet.utils.FCMTokenManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)

    // Check if message contains a notification payload
    remoteMessage.notification?.let { sendNotification(it.title, it.body) }

    // Check if message contains a data payload
    if (remoteMessage.data.isNotEmpty()) {
      handleDataPayload(remoteMessage.data)
    }
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)

    // Save token locally
    FCMTokenManager.saveTokenLocally(applicationContext, token)

    // Register token with server if user is logged in
    sendRegistrationToServer(token)
  }

  private fun handleDataPayload(data: Map<String, String>) {
    // Handle data payload here
    // You can customize this based on your app's requirements
    val title = data["title"]
    val body = data["body"]

    if (title != null && body != null) {
      sendNotification(title, body)
    }
  }

  private fun sendRegistrationToServer(token: String) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    if (userId != null) {
      CoroutineScope(Dispatchers.IO).launch {
        runCatching { FCMTokenManager.registerTokenWithServer(userId, token) }
      }
    }
  }

  private fun sendNotification(title: String?, messageBody: String?) {
    val intent = Intent(this, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val channelId = getString(R.string.default_notification_channel_id)
    val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val notificationBuilder =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title ?: getString(R.string.app_name))
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    // Since android Oreo notification channel is needed.
    val channel =
        NotificationChannel(
            channelId,
            getString(R.string.default_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT)
    notificationManager.createNotificationChannel(channel)

    notificationManager.notify(0, notificationBuilder.build())
  }
}
