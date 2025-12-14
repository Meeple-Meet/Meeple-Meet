package com.github.meeplemeet.utils

import android.content.Context
import android.util.Log
import com.github.meeplemeet.RepositoryProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FCMTokenManager {
  private const val TAG = "FCMTokenManager"
  private const val PREFS_NAME = "fcm_prefs"
  private const val KEY_FCM_TOKEN = "fcm_token"

  /**
   * Retrieves the current FCM token from Firebase Messaging.
   *
   * @return The current FCM token, or null if unavailable
   */
  suspend fun getToken(): String? {
    return try {
      FirebaseMessaging.getInstance().token.await()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to get FCM token", e)
      null
    }
  }

  /**
   * Saves the FCM token to SharedPreferences for local caching.
   *
   * @param context Application context
   * @param token The FCM token to save
   */
  fun saveTokenLocally(context: Context, token: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
  }

  /**
   * Retrieves the locally cached FCM token from SharedPreferences.
   *
   * @param context Application context
   * @return The cached FCM token, or null if not found
   */
  fun getLocalToken(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_FCM_TOKEN, null)
  }

  /**
   * Registers the FCM token with the server by updating the user's account.
   *
   * @param userId The user's account ID
   * @param token The FCM token to register
   */
  suspend fun registerTokenWithServer(userId: String, token: String) {
    try {
      RepositoryProvider.accounts.updateFcmToken(userId, token)
      Log.d(TAG, "FCM token registered successfully for user: $userId")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to register FCM token with server", e)
      throw e
    }
  }

  /**
   * Retrieves and registers the FCM token for the current user. This combines fetching the token,
   * saving it locally, and uploading to the server.
   *
   * @param context Application context
   * @param userId The user's account ID
   */
  suspend fun initializeTokenForUser(context: Context, userId: String) {
    try {
      val token = getToken()
      if (token != null) {
        saveTokenLocally(context, token)
        registerTokenWithServer(userId, token)
        Log.d(TAG, "FCM token initialized for user: $userId")
      } else {
        Log.w(TAG, "FCM token is null, initialization skipped")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize FCM token", e)
    }
  }
}
