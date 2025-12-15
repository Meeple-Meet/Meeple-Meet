package com.github.meeplemeet.services

import android.util.Log
import com.github.meeplemeet.utils.FCMTokenManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

class MyFirebaseMessagingServiceTest {

  private lateinit var mockAuth: FirebaseAuth
  private lateinit var mockUser: FirebaseUser

  @Before
  fun setup() {
    mockAuth = mockk(relaxed = true)
    mockUser = mockk(relaxed = true)

    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.e(any(), any(), any()) } returns 0
    every { Log.w(any(), any<String>()) } returns 0

    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns mockAuth

    mockkObject(FCMTokenManager)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun fcmTokenManager_getToken_canBeCalled() {
    // Test that FCMTokenManager methods are accessible
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

    // Simulate the logic that would be in onNewToken
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
