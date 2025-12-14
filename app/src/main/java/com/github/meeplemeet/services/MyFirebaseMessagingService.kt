package com.github.meeplemeet.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
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

    Log.d(TAG, "From: ${remoteMessage.from}")

    // Check if message contains a notification payload
    remoteMessage.notification?.let {
      Log.d(TAG, "Message Notification Body: ${it.body}")
      sendNotification(it.title, it.body)
    }

    // Check if message contains a data payload
    if (remoteMessage.data.isNotEmpty()) {
      Log.d(TAG, "Message data payload: ${remoteMessage.data}")
      handleDataPayload(remoteMessage.data)
    }
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d(TAG, "Refreshed token: $token")

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
        try {
          FCMTokenManager.registerTokenWithServer(userId, token)
          Log.d(TAG, "Token registered with server for user: $userId")
        } catch (e: Exception) {
          Log.e(TAG, "Failed to register token with server", e)
        }
      }
    } else {
      Log.d(TAG, "User not logged in, token registration skipped")
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

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Since android Oreo notification channel is needed.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
          NotificationChannel(
              channelId,
              getString(R.string.default_notification_channel_name),
              NotificationManager.IMPORTANCE_DEFAULT)
      notificationManager.createNotificationChannel(channel)
    }

    notificationManager.notify(0, notificationBuilder.build())
  }

  companion object {
    private const val TAG = "MyFirebaseMsgService"
  }
}
