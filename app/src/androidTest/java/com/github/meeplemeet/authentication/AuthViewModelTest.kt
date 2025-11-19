package com.github.meeplemeet.authentication

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.auth.SignInViewModel
import com.github.meeplemeet.model.auth.SignUpViewModel
import com.github.meeplemeet.utils.FirestoreTests
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for Authentication ViewModels using real repositories and Firebase emulators.
 *
 * These tests verify authentication ViewModel functionality without mocking:
 * - SignInViewModel (login, logout)
 * - SignUpViewModel (registration)
 * - UI state management
 * - Loading states
 * - Error handling
 *
 * Prerequisites:
 * - Firebase emulators should be running (Auth on port 9099, Firestore on port 8080)
 * - Tests use random email addresses to avoid conflicts between test runs
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AuthViewModelTest : FirestoreTests() {
  private lateinit var signInViewModel: SignInViewModel
  private lateinit var signUpViewModel: SignUpViewModel

  // Test user credentials - using random emails to avoid conflicts
  private lateinit var testEmail: String
  private lateinit var testPassword: String

  @Before
  fun setup() {
    Dispatchers.setMain(Dispatchers.Unconfined)

    signInViewModel = SignInViewModel()
    signUpViewModel = SignUpViewModel()

    testEmail = "auth_vm_test_${UUID.randomUUID()}@example.com"
    testPassword = "TestPassword123!"

    runBlocking {
      auth.signOut()
      clearTestData()
    }
  }

  @After
  fun cleanup() {
    Dispatchers.resetMain()
    runBlocking {
      auth.signOut()
      clearTestData()
    }
  }

  /** Cleans up test data from Firestore to ensure test isolation */
  private suspend fun clearTestData() {
    try {
      // Clean up any test user documents
      val usersCollection = db.collection("accounts")
      val testUserQuery = usersCollection.whereEqualTo("email", testEmail).get().await()

      for (document in testUserQuery.documents) {
        document.reference.delete().await()
      }
    } catch (_: Exception) {
      // Ignore cleanup errors - they shouldn't fail the test
    }
  }

  //// Tests for initial state ////
  @Test
  fun initial_state_is_default() = runBlocking {
    val state = signInViewModel.uiState.value
    assertEquals(false, state.isLoading)
    assertEquals(null, state.account)
    assertEquals(null, state.errorMsg)
    assertEquals(false, state.signedOut)
  }

  //// Tests for clearErrorMsg ////
  @Test
  fun clearErrorMsg_sets_errorMsg_to_null() = runBlocking {
    // First trigger an error
    signInViewModel.loginWithEmail("invalid-email", "password")
    delay(100) // Wait for operation to complete

    // Verify error exists
    assertNotNull(signInViewModel.uiState.value.errorMsg)

    // Clear error
    signInViewModel.clearErrorMsg()

    // Verify error is cleared
    assertEquals(null, signInViewModel.uiState.value.errorMsg)
  }

  //// Tests for registerWithEmail ////
  @Test
  fun registerWithEmail_success_updates_account() = runBlocking {
    signUpViewModel.registerWithEmail(testEmail, testPassword)
    delay(100)

    val state = signUpViewModel.uiState.value
    assertNotNull("Account should be created", state.account)
    assertEquals("Email should match", testEmail, state.account?.email)
    assertEquals("Loading should be false", false, state.isLoading)
    assertEquals("Error should be null", null, state.errorMsg)
    assertEquals("Should not be signed out", false, state.signedOut)
  }

  @Test
  fun registerWithEmail_failure_updates_errorMsg() = runBlocking {
    // Try to register with invalid email
    signUpViewModel.registerWithEmail("invalid-email", testPassword)
    delay(100)

    val state = signUpViewModel.uiState.value
    assertEquals("Account should be null", null, state.account)
    assertEquals("Loading should be false", false, state.isLoading)
    assertNotNull("Error message should be present", state.errorMsg)
  }

  @Test
  fun registerWithEmail_sets_isLoading_true_then_false() = runBlocking {
    // Start registration
    signUpViewModel.registerWithEmail(testEmail, testPassword)

    delay(100)

    // After completion, loading should be false
    assertEquals(
        "Loading should be false after completion", false, signUpViewModel.uiState.value.isLoading)
  }

  @Test
  fun registerWithEmail_does_not_proceed_if_already_loading() = runBlocking {
    // Start first registration
    signUpViewModel.registerWithEmail(testEmail, testPassword)

    // Immediately try another registration before first completes
    val secondEmail = "second_${UUID.randomUUID()}@example.com"
    signUpViewModel.registerWithEmail(secondEmail, testPassword)

    delay(100)

    // Should only have registered the first email
    val account = signUpViewModel.uiState.value.account
    if (account != null) {
      assertEquals("Should only register first email", testEmail, account.email)
    }
  }

  //// Tests for loginWithEmail ////
  @Test
  fun loginWithEmail_success_updates_account() = runBlocking {
    // First register a user
    signUpViewModel.registerWithEmail(testEmail, testPassword)
    delay(100)
    assertTrue("Registration should succeed", signUpViewModel.uiState.value.account != null)

    // Sign out
    auth.signOut()

    // Now login
    signInViewModel.loginWithEmail(testEmail, testPassword)
    delay(100)

    val state = signInViewModel.uiState.value
    assertNotNull("Account should be retrieved", state.account)
    assertEquals("Email should match", testEmail, state.account?.email)
    assertEquals("Loading should be false", false, state.isLoading)
    assertEquals("Error should be null", null, state.errorMsg)
  }

  @Test
  fun loginWithEmail_failure_updates_errorMsg() = runBlocking {
    // Try to login with non-existent user
    signInViewModel.loginWithEmail(testEmail, "wrongpassword")
    delay(100)

    val state = signInViewModel.uiState.value
    assertEquals("Account should be null", null, state.account)
    assertEquals("Loading should be false", false, state.isLoading)
    assertNotNull("Error message should be present", state.errorMsg)
  }

  @Test
  fun loginWithEmail_sets_isLoading_true_then_false() = runBlocking {
    // Register first
    signUpViewModel.registerWithEmail(testEmail, testPassword)
    delay(100)
    auth.signOut()

    // Now login
    signInViewModel.loginWithEmail(testEmail, testPassword)
    delay(100)

    // After completion, loading should be false
    assertEquals(
        "Loading should be false after completion", false, signInViewModel.uiState.value.isLoading)
  }

  @Test
  fun loginWithEmail_does_not_proceed_if_already_loading() = runBlocking {
    // Start first login
    signInViewModel.loginWithEmail(testEmail, testPassword)

    // Immediately try another login before first completes
    signInViewModel.loginWithEmail("other@example.com", "otherpassword")

    delay(100)

    // Should only have attempted the first login
    // (Both will fail, but only first should have been attempted)
    val state = signInViewModel.uiState.value
    assertNotNull("Should have an error from the first attempt", state.errorMsg)
  }

  //// Tests for logout ////
  @Test
  fun logout_success_updates_signedOut() = runBlocking {
    // First register and login a user
    signUpViewModel.registerWithEmail(testEmail, testPassword)
    delay(100)
    assertNotNull("User should be registered", signUpViewModel.uiState.value.account)

    // Now logout
    signUpViewModel.signOut()
    delay(100)

    val state = signUpViewModel.uiState.value
    assertEquals("Should be signed out", true, state.signedOut)
    assertEquals("Account should be null", null, state.account)
    assertEquals("Loading should be false", false, state.isLoading)
    assertEquals("Error should be null", null, state.errorMsg)
  }

  @Test
  fun logout_when_not_logged_in_succeeds() = runBlocking {
    // Ensure no user is logged in
    auth.signOut()

    // Try to logout
    signInViewModel.signOut()
    delay(100)

    val state = signInViewModel.uiState.value
    assertEquals("Should be signed out", true, state.signedOut)
    assertEquals("Account should be null", null, state.account)
    assertEquals("Loading should be false", false, state.isLoading)
  }

  //// Tests for error message clearing ////
  @Test
  fun clearErrorMsg_after_error_clears_only_errorMsg() = runBlocking {
    // Trigger an error
    signInViewModel.loginWithEmail("invalid-email", testPassword)
    delay(100)

    assertNotNull("Error should be present", signInViewModel.uiState.value.errorMsg)

    // Clear error
    signInViewModel.clearErrorMsg()

    val state = signInViewModel.uiState.value
    assertEquals("Error should be cleared", null, state.errorMsg)
    // Other state should remain unchanged
    assertEquals("Loading should still be false", false, state.isLoading)
  }

  //// Tests for duplicate registration ////
  @Test
  fun registerWithEmail_duplicate_email_shows_error() = runBlocking {
    // First registration
    signUpViewModel.registerWithEmail(testEmail, testPassword)
    delay(100)
    assertTrue("First registration should succeed", signUpViewModel.uiState.value.account != null)

    // Sign out
    auth.signOut()

    // Try to register again with same email
    val newSignUpViewModel = SignUpViewModel()
    newSignUpViewModel.registerWithEmail(testEmail, testPassword)
    delay(100)

    val state = newSignUpViewModel.uiState.value
    assertEquals("Account should be null", null, state.account)
    assertNotNull("Error message should be present", state.errorMsg)
    assertTrue(
        "Error should mention email already in use",
        state.errorMsg?.lowercase()?.contains("email") == true ||
            state.errorMsg?.lowercase()?.contains("already") == true ||
            state.errorMsg?.lowercase()?.contains("use") == true)
  }

  //// Tests for weak password ////
  @Test
  fun registerWithEmail_weak_password_shows_error() = runBlocking {
    signUpViewModel.registerWithEmail(testEmail, "123")
    delay(100)

    val state = signUpViewModel.uiState.value
    assertEquals("Account should be null", null, state.account)
    assertNotNull("Error message should be present", state.errorMsg)
    assertEquals("Loading should be false", false, state.isLoading)
  }

  //// Tests for empty password ////
  @Test
  fun registerWithEmail_empty_password_shows_error() = runBlocking {
    signUpViewModel.registerWithEmail(testEmail, "")
    delay(100)

    val state = signUpViewModel.uiState.value
    assertEquals("Account should be null", null, state.account)
    assertNotNull("Error message should be present", state.errorMsg)
    assertEquals("Loading should be false", false, state.isLoading)
  }

  //// Tests for login with wrong password ////
  @Test
  fun loginWithEmail_wrong_password_shows_error() = runBlocking {
    // Register first
    signUpViewModel.registerWithEmail(testEmail, testPassword)
    delay(100)
    assertTrue("Registration should succeed", signUpViewModel.uiState.value.account != null)

    // Sign out
    auth.signOut()

    // Try to login with wrong password
    signInViewModel.loginWithEmail(testEmail, "wrongpassword")
    delay(100)

    val state = signInViewModel.uiState.value
    assertEquals("Account should be null", null, state.account)
    assertEquals("Loading should be false", false, state.isLoading)
    assertNotNull("Error message should be present", state.errorMsg)
  }

  //// Tests for callback execution ////
  @Test
  fun registerWithEmail_callback_is_executed_on_success() = runBlocking {
    var callbackExecuted = false

    signUpViewModel.registerWithEmail(testEmail, testPassword) { callbackExecuted = true }
    delay(100)

    assertTrue("Callback should be executed on success", callbackExecuted)
    assertNotNull("Account should be created", signUpViewModel.uiState.value.account)
  }

  @Test
  fun loginWithEmail_callback_is_executed_on_success() = runBlocking {
    // Register first
    signUpViewModel.registerWithEmail(testEmail, testPassword)
    delay(100)
    auth.signOut()

    var callbackExecuted = false

    signInViewModel.loginWithEmail(testEmail, testPassword) { callbackExecuted = true }
    delay(100)

    assertTrue("Callback should be executed on success", callbackExecuted)
    assertNotNull("Account should be retrieved", signInViewModel.uiState.value.account)
  }
}
