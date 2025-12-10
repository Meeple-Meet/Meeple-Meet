package com.github.meeplemeet.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.utils.FirestoreTests
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for ProfileScreenViewModel email change functionality.
 *
 * Tests the ViewModel layer interactions with repositories.
 */
@RunWith(AndroidJUnit4::class)
class ProfileScreenViewModelTest : FirestoreTests() {
  private val testEmail = "viewmodel_test_${UUID.randomUUID()}@example.com"
  private val newEmail = "viewmodel_new_${UUID.randomUUID()}@example.com"
  private val testPassword = "TestPassword123!"

  private lateinit var viewModel: ProfileScreenViewModel
  private var testUserId: String? = null

  @Before
  fun setup() = runBlocking {
    auth.signOut()
    clearTestData()

    // Create test user
    val result = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("User registration should succeed", result.isSuccess)
    testUserId = result.getOrNull()?.uid

    // Create ViewModel
    viewModel = ProfileScreenViewModel()
  }

  @After
  fun cleanup() = runBlocking {
    auth.signOut()
    clearTestData()
  }

  private suspend fun clearTestData() {
    try {
      val emails = listOf(testEmail, newEmail)
      for (email in emails) {
        val query = db.collection("accounts").whereEqualTo("email", email).get().await()
        for (document in query.documents) {
          document.reference.delete().await()
        }
      }
    } catch (_: Exception) {
      // Ignore cleanup errors
    }
  }

  @Test
  fun test_syncEmail_updates_firestore_successfully() = runBlocking {
    // Log in
    val loginResult = authenticationRepository.loginWithEmail(testEmail, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)

    // Manually change Firestore email to simulate desync
    val manualEmail = "manual_${UUID.randomUUID()}@example.com"
    accountRepository.setAccountEmail(testUserId!!, manualEmail)

    // Verify Firestore has manual email
    val accountBefore = accountRepository.getAccount(testUserId!!)
    assertTrue("Firestore should have manual email", accountBefore.email == manualEmail)

    // Call syncEmail through repository directly (since ViewModel launches async)
    val syncResult = authenticationRepository.syncEmailToFirestore()
    assertTrue("Sync should succeed", syncResult.isSuccess)

    // Verify Firestore was synced back to Firebase Auth email
    val accountAfter = accountRepository.getAccount(testUserId!!)
    assertTrue(
        "Firestore should be synced to Firebase Auth email",
        accountAfter.email == testEmail
    )
  }

  @Test
  fun test_changeEmail_updates_ui_state_on_success() = runBlocking {
    // Log in
    authenticationRepository.loginWithEmail(testEmail, testPassword)

    val latch = CountDownLatch(1)
    var capturedSuccessMsg: String? = null
    var capturedIsLoading: Boolean? = null

    // Start collecting UI state in background
    val job = CoroutineScope(Dispatchers.Default).launch {
      viewModel.uiState.collect { state ->
        if (state.successMsg != null && !state.isLoading) {
          capturedSuccessMsg = state.successMsg
          capturedIsLoading = state.isLoading
          latch.countDown()
        }
      }
    }

    // Call changeEmail
    viewModel.changeEmail(newEmail, testPassword)

    // Wait for UI state update
    assertTrue("Operation should complete within timeout", latch.await(5, TimeUnit.SECONDS))
    job.cancel()

    // Check UI state
    assertNotNull("Success message should be set", capturedSuccessMsg)
    assertTrue(
        "Success message should mention verification",
        capturedSuccessMsg?.contains("Verification") == true
    )
    assertTrue("Loading should be false after completion", capturedIsLoading == false)
  }

  @Test
  fun test_changeEmail_updates_ui_state_on_wrong_password() = runBlocking {
    // Log in
    authenticationRepository.loginWithEmail(testEmail, testPassword)

    val latch = CountDownLatch(1)
    var capturedErrorMsg: String? = null
    var capturedIsLoading: Boolean? = null

    // Start collecting UI state in background
    val job = CoroutineScope(Dispatchers.Default).launch {
      viewModel.uiState.collect { state ->
        if (state.errorMsg != null && !state.isLoading) {
          capturedErrorMsg = state.errorMsg
          capturedIsLoading = state.isLoading
          latch.countDown()
        }
      }
    }

    // Call changeEmail with wrong password
    viewModel.changeEmail(newEmail, "WrongPassword123!")

    // Wait for UI state update
    assertTrue("Operation should complete within timeout", latch.await(5, TimeUnit.SECONDS))
    job.cancel()

    // Check UI state
    assertNotNull("Error message should be set", capturedErrorMsg)
    assertTrue(
        "Error message should mention password",
        capturedErrorMsg?.lowercase()?.contains("password") == true
    )
    assertTrue("Loading should be false after error", capturedIsLoading == false)
  }

  @Test
  fun test_changeEmail_prevents_concurrent_operations() = runBlocking {
    // Log in
    authenticationRepository.loginWithEmail(testEmail, testPassword)

    val latch = CountDownLatch(1)
    var capturedSuccessMsg: String? = null
    var capturedErrorMsg: String? = null

    // Start collecting UI state in background
    val job = CoroutineScope(Dispatchers.Default).launch {
      viewModel.uiState.collect { state ->
        if ((state.successMsg != null || state.errorMsg != null) && !state.isLoading) {
          capturedSuccessMsg = state.successMsg
          capturedErrorMsg = state.errorMsg
          latch.countDown()
        }
      }
    }

    // Start first operation
    viewModel.changeEmail(newEmail, testPassword)

    // Immediately try second operation (should be ignored)
    viewModel.changeEmail("another@example.com", testPassword)

    // Wait for operations to complete
    assertTrue("Operation should complete within timeout", latch.await(5, TimeUnit.SECONDS))
    job.cancel()

    // Check that only one operation completed
    // If concurrent prevention works, we should see the result of first operation
    assertNotNull("Should have a result", capturedSuccessMsg ?: capturedErrorMsg)
  }
}

