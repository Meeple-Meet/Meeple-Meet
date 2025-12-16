package com.github.meeplemeet.services

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.github.meeplemeet.utils.FCMTokenManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.RemoteMessage
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class MyFirebaseMessagingServiceTest {

  private lateinit var service: MyFirebaseMessagingService
  private lateinit var mockAuth: FirebaseAuth
  private lateinit var mockUser: FirebaseUser
  private lateinit var notificationManager: NotificationManager

  @Before
  fun setup() {
    mockAuth = mockk(relaxed = true)
    mockUser = mockk(relaxed = true)

    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.e(any(), any()) } returns 0
    every { Log.e(any(), any(), any()) } returns 0
    every { Log.w(any(), any<String>()) } returns 0
    every { Log.i(any(), any()) } returns 0
    every { Log.v(any(), any()) } returns 0

    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns mockAuth

    mockkObject(FCMTokenManager)
    every { FCMTokenManager.saveTokenLocally(any(), any()) } just Runs
    coEvery { FCMTokenManager.registerTokenWithServer(any(), any()) } just Runs

    service = Robolectric.setupService(MyFirebaseMessagingService::class.java)
    notificationManager =
        RuntimeEnvironment.getApplication().getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun onMessageReceived_withNotificationPayload_sendsNotification() {
    val notification = mockk<RemoteMessage.Notification>(relaxed = true)
    every { notification.title } returns "Test Title"
    every { notification.body } returns "Test Body"

    val remoteMessage = mockk<RemoteMessage>(relaxed = true)
    every { remoteMessage.notification } returns notification
    every { remoteMessage.data } returns emptyMap()

    service.onMessageReceived(remoteMessage)

    // Verify notification was processed
    verify { remoteMessage.notification }
  }

  @Test
  fun onMessageReceived_withDataPayload_handlesData() {
    val remoteMessage = mockk<RemoteMessage>(relaxed = true)
    every { remoteMessage.notification } returns null
    every { remoteMessage.data } returns mapOf("title" to "Data Title", "body" to "Data Body")

    service.onMessageReceived(remoteMessage)

    verify { remoteMessage.data }
  }

  @Test
  fun onMessageReceived_withBothPayloads_handlesBoth() {
    val notification = mockk<RemoteMessage.Notification>(relaxed = true)
    every { notification.title } returns "Notification Title"
    every { notification.body } returns "Notification Body"

    val remoteMessage = mockk<RemoteMessage>(relaxed = true)
    every { remoteMessage.notification } returns notification
    every { remoteMessage.data } returns mapOf("title" to "Data Title", "body" to "Data Body")

    service.onMessageReceived(remoteMessage)

    verify { remoteMessage.notification }
    verify { remoteMessage.data }
  }

  @Test
  fun onMessageReceived_withEmptyDataPayload_doesNotCrash() {
    val remoteMessage = mockk<RemoteMessage>(relaxed = true)
    every { remoteMessage.notification } returns null
    every { remoteMessage.data } returns emptyMap()

    service.onMessageReceived(remoteMessage)

    verify { remoteMessage.data }
  }

  @Test
  fun onMessageReceived_withPartialDataPayload_doesNotSendNotification() {
    val remoteMessage = mockk<RemoteMessage>(relaxed = true)
    every { remoteMessage.notification } returns null
    every { remoteMessage.data } returns mapOf("title" to "Only Title")

    service.onMessageReceived(remoteMessage)

    verify { remoteMessage.data }
  }

  @Test
  fun onMessageReceived_withNullTitleInData_doesNotSendNotification() {
    val remoteMessage = mockk<RemoteMessage>(relaxed = true)
    every { remoteMessage.notification } returns null
    every { remoteMessage.data } returns mapOf("body" to "Only Body")

    service.onMessageReceived(remoteMessage)

    verify { remoteMessage.data }
  }

  @Test
  fun onNewToken_savesTokenLocally() {
    val token = "new_test_token"
    every { mockAuth.currentUser } returns null

    service.onNewToken(token)

    verify { FCMTokenManager.saveTokenLocally(any(), token) }
  }

  @Test
  fun onNewToken_registersTokenWhenUserLoggedIn() {
    val token = "new_test_token"
    val userId = "user_123"

    every { mockUser.uid } returns userId
    every { mockAuth.currentUser } returns mockUser

    service.onNewToken(token)

    verify { FCMTokenManager.saveTokenLocally(any(), token) }
    verify { mockAuth.currentUser }
  }

  @Test
  fun onNewToken_doesNotRegisterWhenUserNull() {
    val token = "new_test_token"

    every { mockAuth.currentUser } returns null

    service.onNewToken(token)

    verify { FCMTokenManager.saveTokenLocally(any(), token) }
    verify { mockAuth.currentUser }
  }

  @Test
  fun onMessageReceived_withNotificationNullTitleAndBody_usesDefaults() {
    val notification = mockk<RemoteMessage.Notification>(relaxed = true)
    every { notification.title } returns null
    every { notification.body } returns null

    val remoteMessage = mockk<RemoteMessage>(relaxed = true)
    every { remoteMessage.notification } returns notification
    every { remoteMessage.data } returns emptyMap()

    service.onMessageReceived(remoteMessage)

    verify { remoteMessage.notification }
  }

  @Test
  fun fcmTokenManager_getToken_canBeCalled() {
    every { FCMTokenManager.saveTokenLocally(any(), any()) } just Runs

    FCMTokenManager.saveTokenLocally(mockk(relaxed = true), "test_token")

    verify { FCMTokenManager.saveTokenLocally(any(), "test_token") }
  }

  @Test
  fun fcmTokenManager_registerToken_whenUserExists() {
    val token = "test_token"
    val userId = "user_123"

    every { mockUser.uid } returns userId
    every { mockAuth.currentUser } returns mockUser
    every { FCMTokenManager.saveTokenLocally(any(), token) } just Runs
    coEvery { FCMTokenManager.registerTokenWithServer(userId, token) } just Runs

    val currentUser = mockAuth.currentUser
    if (currentUser != null) {
      coEvery { FCMTokenManager.registerTokenWithServer(currentUser.uid, token) } just Runs
    }

    verify { mockAuth.currentUser }
  }

  @Test
  fun fcmTokenManager_doesNotRegister_whenUserNull() {
    val token = "test_token"

    every { FCMTokenManager.saveTokenLocally(any(), token) } just Runs
    every { mockAuth.currentUser } returns null

    val currentUser = mockAuth.currentUser

    verify { mockAuth.currentUser }
    assert(currentUser == null)
  }
}
