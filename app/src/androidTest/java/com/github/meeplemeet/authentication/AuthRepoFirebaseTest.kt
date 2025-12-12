package com.github.meeplemeet.authentication

import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.utils.FirestoreTests
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for AuthenticationRepository using Firebase emulators.
 *
 * These tests verify authentication functionality without mocking:
 * - Email/password registration
 * - Email/password login
 * - Logout functionality
 * - Error handling for invalid inputs
 * - Google Sign-In credential handling
 *
 * Prerequisites:
 * - Firebase emulators should be running (Auth on port 9099, Firestore on port 8080)
 * - Tests use random email addresses to avoid conflicts between test runs
 */
@RunWith(AndroidJUnit4::class)
class AuthRepoFirebaseTest : FirestoreTests() {
  // Test user credentials - using random emails to avoid conflicts
  private lateinit var testEmail: String
  private lateinit var testPassword: String
  private val invalidEmail = "invalid-email-format"
  private val weakPassword = "123"

  @Before
  fun setup() = runBlocking {
    testEmail = "auth_repo_test_${UUID.randomUUID()}@example.com"
    testPassword = "TestPassword123!"
    auth.signOut()
    clearTestData()
  }

  @After
  fun cleanup() = runBlocking {
    auth.signOut()
    clearTestData()
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

  //// Tests for registerWithEmail ////
  @Test
  fun registerWithEmail_returns_failure_for_invalid_email() = runBlocking {
    val result = authenticationRepository.registerWithEmail(invalidEmail, testPassword)

    assertTrue("Registration with invalid email should fail", result.isFailure)
    assertNull("User should not be authenticated", auth.currentUser)
  }

  @Test
  fun registerWithEmail_returns_failure_for_empty_password() = runBlocking {
    val result = authenticationRepository.registerWithEmail(testEmail, "")

    assertTrue("Registration with empty password should fail", result.isFailure)
    assertNull("User should not be authenticated", auth.currentUser)
  }

  @Test
  fun registerWithEmail_returns_success_for_valid_email_and_password() = runBlocking {
    val result = authenticationRepository.registerWithEmail(testEmail, testPassword)

    assertTrue("Registration should succeed", result.isSuccess)
    val account = result.getOrNull()
    assertNotNull("Account should be created", account)
    assertEquals("Email should match", testEmail, account?.email)
    assertNotNull("Account should have a UID", account?.uid)

    // Verify Firebase Auth user is created
    assertNotNull("Firebase user should be authenticated", auth.currentUser)
    assertEquals("Firebase user email should match", testEmail, auth.currentUser?.email)

    // Verify account exists in Firestore
    val retrievedAccount = accountRepository.getAccount(account!!.uid)
    assertEquals("Firestore account UID should match", account.uid, retrievedAccount.uid)
    assertEquals("Firestore email should match", testEmail, retrievedAccount.email)
  }

  @Test
  fun registerWithEmail_returns_failure_for_duplicate_email() = runBlocking {
    // First registration
    val firstResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("First registration should succeed", firstResult.isSuccess)

    // Sign out
    auth.signOut()

    // Second registration with same email
    val secondResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Duplicate registration should fail", secondResult.isFailure)

    val exception = secondResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    val errorMessage = exception?.message?.lowercase() ?: ""
    assertTrue(
        "Error should mention email already in use",
        errorMessage.contains("email") ||
            errorMessage.contains("already") ||
            errorMessage.contains("use"))
  }

  //// Tests for loginWithEmail ////
  @Test
  fun loginWithEmail_returns_failure_for_invalid_credentials() = runBlocking {
    val result = authenticationRepository.loginWithEmail(testEmail, "wrongpassword")

    assertTrue("Login with invalid credentials should fail", result.isFailure)
    assertNull("User should not be authenticated", auth.currentUser)
  }

  @Test
  fun loginWithEmail_returns_success_for_valid_credentials() = runBlocking {
    // First register a user
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    // Sign out
    auth.signOut()

    // Now login
    val loginResult = authenticationRepository.loginWithEmail(testEmail, testPassword)

    assertTrue("Login should succeed", loginResult.isSuccess)
    val account = loginResult.getOrNull()
    assertNotNull("Account should be retrieved", account)
    assertEquals("Email should match", testEmail, account?.email)
    assertNotNull("Account should have a UID", account?.uid)

    // Verify Firebase Auth user is authenticated
    assertNotNull("Firebase user should be authenticated", auth.currentUser)
    assertEquals("Firebase user email should match", testEmail, auth.currentUser?.email)
  }

  @Test
  fun loginWithEmail_returns_failure_for_nonexistent_user() = runBlocking {
    val nonExistentEmail = "nonexistent_${UUID.randomUUID()}@example.com"
    val result = authenticationRepository.loginWithEmail(nonExistentEmail, testPassword)

    assertTrue("Login with nonexistent user should fail", result.isFailure)
    assertNull("User should not be authenticated", auth.currentUser)

    val exception = result.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
  }

  @Test
  fun loginWithEmail_returns_failure_for_invalid_email_format() = runBlocking {
    val result = authenticationRepository.loginWithEmail(invalidEmail, testPassword)

    assertTrue("Login with invalid email should fail", result.isFailure)
    assertNull("User should not be authenticated", auth.currentUser)

    val exception = result.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    assertTrue(
        "Error message should mention email",
        exception?.message?.lowercase()?.contains("email") == true)
  }

  //// Tests for logout ////
  @Test
  fun logout_returns_success() = runBlocking {
    // Register and login a user
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    // Verify user is authenticated
    assertNotNull("User should be authenticated", auth.currentUser)

    // Test logout
    val logoutResult = authenticationRepository.logout()

    assertTrue("Logout should succeed", logoutResult.isSuccess)

    // Verify user is no longer authenticated
    assertNull("User should not be authenticated after logout", auth.currentUser)
  }

  @Test
  fun logout_succeeds_when_no_user_is_logged_in() = runBlocking {
    // Ensure no user is logged in
    auth.signOut()
    assertNull("No user should be authenticated", auth.currentUser)

    // Test logout
    val result = authenticationRepository.logout()

    assertTrue("Logout should succeed even when no user is logged in", result.isSuccess)
  }

  //// Tests for loginWithGoogle ////
  @Test
  fun loginWithGoogle_returns_failure_for_invalid_credential_type() = runBlocking {
    // Create a custom credential with wrong type
    val credential = CustomCredential(type = "WRONG_TYPE", data = Bundle())

    val result = authenticationRepository.loginWithGoogle(credential)

    assertTrue("Login with invalid credential type should fail", result.isFailure)
    assertNull("User should not be authenticated", auth.currentUser)
  }

  @Test
  fun loginWithGoogle_returns_failure_for_wrong_google_credential_type() = runBlocking {
    // Create a custom credential with GoogleIdTokenCredential type but wrong data
    val credential =
        CustomCredential(
            type = GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, data = Bundle())

    val result = authenticationRepository.loginWithGoogle(credential)

    // This should fail because the credential doesn't have valid Google ID token data
    assertTrue("Login with invalid Google credential should fail", result.isFailure)
  }

  //// Tests for error messages ////
  @Test
  fun error_messages_are_user_friendly() = runBlocking {
    // Test invalid email
    val invalidEmailResult =
        authenticationRepository.registerWithEmail("invalid-email", testPassword)
    assertTrue("Should fail with invalid email", invalidEmailResult.isFailure)
    val emailError = invalidEmailResult.exceptionOrNull()?.message ?: ""
    assertFalse("Error should not contain technical details", emailError.contains("Exception"))
    assertFalse("Error should not contain stack trace", emailError.contains("at "))

    // Test wrong password login
    authenticationRepository.registerWithEmail(testEmail, testPassword) // Register first
    auth.signOut()
    val wrongPasswordResult = authenticationRepository.loginWithEmail(testEmail, "wrongpassword")
    assertTrue("Should fail with wrong password", wrongPasswordResult.isFailure)
    val passwordError = wrongPasswordResult.exceptionOrNull()?.message ?: ""
    assertFalse("Error should not contain technical details", passwordError.contains("Exception"))
    assertFalse("Error should not contain stack trace", passwordError.contains("at "))
  }

  //// Tests for account persistence ////
  @Test
  fun registration_creates_account_in_firestore() = runBlocking {
    val result = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", result.isSuccess)

    val account = result.getOrNull()
    assertNotNull("Account should be created", account)

    // Verify account exists in Firestore by retrieving it directly
    val retrievedAccount = accountRepository.getAccount(account!!.uid)

    // Verify account data matches
    assertEquals("UID should match", account.uid, retrievedAccount.uid)
    assertEquals("Email should match", account.email, retrievedAccount.email)
    assertEquals("Name should match", account.name, retrievedAccount.name)
    assertNotNull("Account should have a name", retrievedAccount.name)
  }

  @Test
  fun login_retrieves_existing_account_from_firestore() = runBlocking {
    // Register a user
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)
    val originalAccount = registrationResult.getOrNull()!!

    // Sign out
    auth.signOut()

    // Login again
    val loginResult = authenticationRepository.loginWithEmail(testEmail, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)
    val retrievedAccount = loginResult.getOrNull()!!

    // Verify account data matches
    assertEquals("UID should match", originalAccount.uid, retrievedAccount.uid)
    assertEquals("Email should match", originalAccount.email, retrievedAccount.email)
    assertEquals("Name should match", originalAccount.name, retrievedAccount.name)
  }

  //// Tests for reauthenticateWithPassword ////
  @Test
  fun reauthenticateWithPassword_returns_success_for_correct_password() = runBlocking {
    // Register and login a user
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    // Test reauthentication
    val reauthResult = authenticationRepository.reauthenticateWithPassword(testPassword)
    assertTrue("Reauthentication with correct password should succeed", reauthResult.isSuccess)
  }

  @Test
  fun reauthenticateWithPassword_returns_failure_for_wrong_password() = runBlocking {
    // Register and login a user
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    // Test reauthentication with wrong password
    val reauthResult = authenticationRepository.reauthenticateWithPassword("wrongpassword")
    assertTrue("Reauthentication with wrong password should fail", reauthResult.isFailure)
  }

  @Test
  fun reauthenticateWithPassword_returns_failure_when_no_user_logged_in() = runBlocking {
    // Ensure no user is logged in
    auth.signOut()

    // Test reauthentication
    val reauthResult = authenticationRepository.reauthenticateWithPassword(testPassword)
    assertTrue("Reauthentication without logged in user should fail", reauthResult.isFailure)
  }
}
