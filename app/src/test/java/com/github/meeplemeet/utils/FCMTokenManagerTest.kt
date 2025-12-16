package com.github.meeplemeet.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.AccountRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class FCMTokenManagerTest {

  private lateinit var mockContext: Context
  private lateinit var mockSharedPreferences: SharedPreferences
  private lateinit var mockEditor: SharedPreferences.Editor
  private lateinit var mockFirebaseMessaging: FirebaseMessaging
  private lateinit var mockAccountRepository: AccountRepository
  private lateinit var mockTask: Task<String>

  @Before
  fun setup() {
    mockContext = mockk(relaxed = true)
    mockSharedPreferences = mockk(relaxed = true)
    mockEditor = mockk(relaxed = true)
    mockFirebaseMessaging = mockk(relaxed = true)
    mockAccountRepository = mockk(relaxed = true)
    mockTask = mockk(relaxed = true)

    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.e(any(), any(), any()) } returns 0
    every { Log.w(any(), any<String>()) } returns 0

    mockkStatic(FirebaseMessaging::class)
    every { FirebaseMessaging.getInstance() } returns mockFirebaseMessaging

    mockkObject(RepositoryProvider)
    every { RepositoryProvider.accounts } returns mockAccountRepository

    every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
    every { mockSharedPreferences.edit() } returns mockEditor
    every { mockEditor.putString(any(), any()) } returns mockEditor
    every { mockEditor.apply() } just Runs
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun getToken_returnsToken_whenSuccessful() = runTest {
    val expectedToken = "test_fcm_token_123"
    coEvery { mockTask.isComplete } returns true
    coEvery { mockTask.exception } returns null
    coEvery { mockTask.isCanceled } returns false
    coEvery { mockTask.result } returns expectedToken
    every { mockFirebaseMessaging.token } returns mockTask

    val result = FCMTokenManager.getToken()

    assertEquals(expectedToken, result)
  }

  @Test
  fun getToken_returnsNull_whenExceptionOccurs() = runTest {
    val exception = Exception("Firebase token error")
    coEvery { mockTask.isComplete } returns true
    coEvery { mockTask.exception } returns exception
    coEvery { mockTask.isCanceled } returns false
    every { mockFirebaseMessaging.token } returns mockTask

    val result = FCMTokenManager.getToken()

    assertNull(result)
  }

  @Test
  fun saveTokenLocally_savesTokenToSharedPreferences() {
    val token = "test_token_456"

    FCMTokenManager.saveTokenLocally(mockContext, token)

    verify { mockSharedPreferences.edit() }
    verify { mockEditor.putString("fcm_token", token) }
    verify { mockEditor.apply() }
  }

  @Test
  fun getLocalToken_returnsToken_whenExists() {
    val expectedToken = "cached_token_789"
    every { mockSharedPreferences.getString("fcm_token", null) } returns expectedToken

    val result = FCMTokenManager.getLocalToken(mockContext)

    assertEquals(expectedToken, result)
  }

  @Test
  fun getLocalToken_returnsNull_whenTokenDoesNotExist() {
    every { mockSharedPreferences.getString("fcm_token", null) } returns null

    val result = FCMTokenManager.getLocalToken(mockContext)

    assertNull(result)
  }

  @Test
  fun registerTokenWithServer_updatesAccountRepository() = runTest {
    val userId = "user_123"
    val token = "token_abc"
    coEvery { mockAccountRepository.updateFcmToken(userId, token) } just Runs

    FCMTokenManager.registerTokenWithServer(userId, token)

    coVerify { mockAccountRepository.updateFcmToken(userId, token) }
  }

  @Test
  fun registerTokenWithServer_throwsException_whenUpdateFails() = runTest {
    val userId = "user_456"
    val token = "token_def"
    val exception = Exception("Update failed")
    coEvery { mockAccountRepository.updateFcmToken(userId, token) } throws exception

    try {
      FCMTokenManager.registerTokenWithServer(userId, token)
      throw AssertionError("Expected exception to be thrown")
    } catch (e: Exception) {
      assertEquals("Update failed", e.message)
    }
  }

  @Test
  fun initializeTokenForUser_getsTokenAndSavesAndRegisters() = runTest {
    val userId = "user_789"
    val token = "token_ghi"
    coEvery { mockTask.isComplete } returns true
    coEvery { mockTask.exception } returns null
    coEvery { mockTask.isCanceled } returns false
    coEvery { mockTask.result } returns token
    every { mockFirebaseMessaging.token } returns mockTask
    coEvery { mockAccountRepository.updateFcmToken(userId, token) } just Runs

    FCMTokenManager.initializeTokenForUser(mockContext, userId)

    verify { mockEditor.putString("fcm_token", token) }
    coVerify { mockAccountRepository.updateFcmToken(userId, token) }
  }

  @Test
  fun initializeTokenForUser_doesNothing_whenTokenIsNull() = runTest {
    val userId = "user_null"
    coEvery { mockTask.isComplete } returns true
    coEvery { mockTask.exception } returns null
    coEvery { mockTask.isCanceled } returns false
    coEvery { mockTask.result } returns null
    every { mockFirebaseMessaging.token } returns mockTask

    FCMTokenManager.initializeTokenForUser(mockContext, userId)

    verify(exactly = 0) { mockEditor.putString(any(), any()) }
    coVerify(exactly = 0) { mockAccountRepository.updateFcmToken(any(), any()) }
  }

  @Test
  fun initializeTokenForUser_handlesException_gracefully() = runTest {
    val userId = "user_exception"
    val exception = Exception("Token fetch failed")
    coEvery { mockTask.isComplete } returns true
    coEvery { mockTask.exception } returns exception
    coEvery { mockTask.isCanceled } returns false
    every { mockFirebaseMessaging.token } returns mockTask

    FCMTokenManager.initializeTokenForUser(mockContext, userId)

    verify(exactly = 0) { mockEditor.putString(any(), any()) }
    coVerify(exactly = 0) { mockAccountRepository.updateFcmToken(any(), any()) }
  }
}
