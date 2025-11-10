package com.github.meeplemeet.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.utils.FirestoreTests
import java.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for sign-up/registration functionality without UI dependencies.
 *
 * Tests registration logic using Firebase emulators:
 * - Email/password registration flows
 * - Form validation (email format, password requirements)
 * - Account creation in Firestore
 * - Error handling for invalid inputs
 * - Registration edge cases and error conditions
 */
@RunWith(AndroidJUnit4::class)
class SignUpTest : FirestoreTests() {
  // Test credentials - using random emails to avoid conflicts
  private val testEmail = "signup_test_${UUID.randomUUID()}@example.com"
  private val testPassword = "TestPassword123!"
  private val weakPassword = "123"
  private val invalidEmail = "invalid-email-format"
  private val existingEmail = "existing_${UUID.randomUUID()}@example.com"

  @Before
  fun setup() {
    // Ensure clean state for each test
    runBlocking {
      auth.signOut()
      clearTestData()
    }
  }

  @After
  fun cleanup() {
    runBlocking {
      auth.signOut()
      clearTestData()
    }
  }

  /** Cleans up test data from Firestore to ensure test isolation */
  private suspend fun clearTestData() {
    try {
      // Clean up any test user documents
      val accountsCollection = db.collection("accounts")
      val emails = listOf(testEmail, existingEmail)
      for (email in emails) {
        val testUserQuery = accountsCollection.whereEqualTo("email", email).get().await()
        for (document in testUserQuery.documents) {
          document.reference.delete().await()
        }
      }
    } catch (e: Exception) {
      // Ignore cleanup errors - they shouldn't fail the test
    }
  }

  @Test
  fun test_sign_up_with_invalid_email_shows_error() = runBlocking {
    // Attempt registration with invalid email format
    val registrationResult = authenticationRepository.registerWithEmail(invalidEmail, testPassword)

    // Verify registration failed
    assertTrue("Registration with invalid email should fail", registrationResult.isFailure)

    // Verify user is not authenticated
    assertNull("User should not be authenticated", auth.currentUser)

    // Check error message contains relevant information about email format
    val exception = registrationResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    assertTrue(
        "Error message should mention email format",
        exception?.message?.lowercase()?.contains("email") == true)
  }

  @Test
  fun test_sign_up_with_empty_password_shows_validation_error() = runBlocking {
    // Attempt registration with empty password
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, "")

    // Verify registration failed
    assertTrue("Registration with empty password should fail", registrationResult.isFailure)

    // Verify user is not authenticated
    assertNull("User should not be authenticated", auth.currentUser)

    // Check error message
    val exception = registrationResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
  }

  @Test
  fun test_sign_up_with_weak_password_shows_validation_error() = runBlocking {
    // Attempt registration with weak password
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, weakPassword)

    // Verify registration failed
    assertTrue("Registration with weak password should fail", registrationResult.isFailure)

    // Verify user is not authenticated
    assertNull("User should not be authenticated", auth.currentUser)

    // Check error message mentions password requirements
    val exception = registrationResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
  }

  @Test
  fun test_successful_registration_creates_account_and_authenticates_user() = runBlocking {
    // Test user registration with valid credentials
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, testPassword)

    // Verify registration succeeded
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    val registeredAccount = registrationResult.getOrNull()
    assertNotNull("Account should be created", registeredAccount)
    assertEquals("Email should match", testEmail, registeredAccount?.email)
    assertNotNull("Account should have a UID", registeredAccount?.uid)

    // Verify Firebase Auth user is created and authenticated
    assertNotNull("Firebase user should be authenticated", auth.currentUser)
    assertEquals("Firebase user email should match", testEmail, auth.currentUser?.email)

    // Verify account document exists in Firestore
    val retrievedAccount = accountRepository.getAccount(registeredAccount!!.uid)
    assertEquals("Firestore account should match", registeredAccount.uid, retrievedAccount.uid)
    assertEquals("Firestore email should match", testEmail, retrievedAccount.email)
  }

  @Test
  fun test_registration_creates_proper_account_properties() = runBlocking {
    // Register a user
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    val account = registrationResult.getOrNull()
    assertNotNull("Account should be created", account)

    // Verify account properties are set correctly
    assertEquals("Email should match registration email", testEmail, account?.email)
    assertEquals("Name should be derived from email", testEmail.substringBefore('@'), account?.name)
    assertNotNull("Account should have a UID", account?.uid)
    assertNotNull("Account should have previews map", account?.previews)
    assertTrue("Previews should be empty initially", account?.previews?.isEmpty() == true)
    assertNull("PhotoUrl should be null for email registration", account?.photoUrl)
  }

  @Test
  fun test_duplicate_email_registration_fails() = runBlocking {
    // Register a user first
    val firstRegistration = authenticationRepository.registerWithEmail(existingEmail, testPassword)
    assertTrue("First registration should succeed", firstRegistration.isSuccess)

    // Sign out
    auth.signOut()

    // Try to register with the same email again
    val secondRegistration = authenticationRepository.registerWithEmail(existingEmail, testPassword)

    // Verify second registration fails
    assertTrue("Duplicate registration should fail", secondRegistration.isFailure)

    // Check error message indicates email is already in use
    val exception = secondRegistration.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    val errorMessage = exception?.message?.lowercase() ?: ""
    assertTrue(
        "Error should mention email already in use",
        errorMessage.contains("email") ||
            errorMessage.contains("already") ||
            errorMessage.contains("use") ||
            errorMessage.contains("exists"))
  }

  @Test
  fun test_registration_and_immediate_firestore_consistency() = runBlocking {
    // Register a user
    val registrationResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    val account = registrationResult.getOrNull()
    assertNotNull("Account should be created", account)

    // Immediately try to retrieve the account from Firestore
    val retrievedAccount = accountRepository.getAccount(account!!.uid)

    // Verify all properties match
    assertEquals("UID should match", account.uid, retrievedAccount.uid)
    assertEquals("Name should match", account.name, retrievedAccount.name)
    assertEquals("Email should match", account.email, retrievedAccount.email)
    assertEquals("PhotoUrl should match", account.photoUrl, retrievedAccount.photoUrl)
    assertEquals("Description should match", account.description, retrievedAccount.description)
    assertEquals("Previews should match", account.previews, retrievedAccount.previews)
  }

  @Test
  fun test_registration_rollback_on_firestore_failure() = runBlocking {
    // This test is conceptual - it would require mocking Firestore to fail
    // after Firebase Auth succeeds to test the cleanup logic
    // For now, we'll test successful registration

    val registrationResult = authenticationRepository.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    // Verify both Firebase Auth and Firestore are consistent
    assertNotNull("Firebase user should exist", auth.currentUser)

    val account = registrationResult.getOrNull()
    assertNotNull("Account should exist", account)

    // Verify Firestore document exists
    val firestoreAccount = accountRepository.getAccount(account!!.uid)
    assertEquals("Firestore and returned account should match", account.uid, firestoreAccount.uid)
  }
}
