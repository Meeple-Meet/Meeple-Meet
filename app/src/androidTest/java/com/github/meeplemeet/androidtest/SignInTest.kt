package com.github.meeplemeet.androidtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.systems.AuthRepoFirebase
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.github.meeplemeet.model.utils.FirestoreTests
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for authentication functionality without UI dependencies.
 *
 * Tests authentication logic using Firebase emulators:
 * - Email/password registration and login flows
 * - Error handling for invalid credentials
 * - Account creation and retrieval from Firestore
 * - Authentication state management
 */
@RunWith(AndroidJUnit4::class)
class SignInTest : FirestoreTests() {

  private lateinit var authRepo: AuthRepoFirebase
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var firestoreRepo: FirestoreRepository

  // Test credentials - using random emails to avoid conflicts
  private val testEmail = "signin_test_${UUID.randomUUID()}@example.com"
  private val testPassword = "TestPassword123!"
  private val invalidEmail = "invalid-email-format"
  private val wrongPassword = "WrongPassword123!"

  @Before
  fun setup() {
    // Get Firebase instances
    auth = Firebase.auth
    firestore = Firebase.firestore

    // Initialize repositories
    firestoreRepo = FirestoreRepository(firestore)
    authRepo = AuthRepoFirebase(auth = auth, firestoreRepository = firestoreRepo)

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
      val accountsCollection = firestore.collection("accounts")
      val testUserQuery = accountsCollection.whereEqualTo("email", testEmail).get().await()

      for (document in testUserQuery.documents) {
        document.reference.delete().await()
      }
    } catch (e: Exception) {
      // Ignore cleanup errors - they shouldn't fail the test
    }
  }

  @Test
  fun test_login_with_invalid_email_format_fails() = runBlocking {
    // Attempt login with invalid email format
    val loginResult = authRepo.loginWithEmail(invalidEmail, testPassword)

    // Verify login failed
    assertTrue("Login with invalid email should fail", loginResult.isFailure)

    // Verify user is not authenticated
    assertNull("User should not be authenticated", auth.currentUser)

    // Check error message contains relevant information
    val exception = loginResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    assertTrue(
        "Error message should mention email format",
        exception?.message?.lowercase()?.contains("email") == true)
  }

  @Test
  fun test_login_with_wrong_password_fails() = runBlocking {
    // First register a user
    val registrationResult = authRepo.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    // Sign out to test login
    auth.signOut()

    // Attempt login with wrong password
    val loginResult = authRepo.loginWithEmail(testEmail, wrongPassword)

    // Verify login failed
    assertTrue("Login with wrong password should fail", loginResult.isFailure)

    // Verify user is not authenticated
    assertNull("User should not be authenticated", auth.currentUser)

    // Check error message is user-friendly
    val exception = loginResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    val errorMessage = exception?.message?.lowercase() ?: ""
    assertTrue(
        "Error should mention invalid credentials",
        errorMessage.contains("invalid") ||
            errorMessage.contains("password") ||
            errorMessage.contains("credentials"))
  }

  @Test
  fun test_login_with_nonexistent_user_fails() = runBlocking {
    // Attempt login with credentials that were never registered
    val nonExistentEmail = "nonexistent_${UUID.randomUUID()}@example.com"
    val loginResult = authRepo.loginWithEmail(nonExistentEmail, testPassword)

    // Verify login failed
    assertTrue("Login with nonexistent user should fail", loginResult.isFailure)

    // Verify user is not authenticated
    assertNull("User should not be authenticated", auth.currentUser)

    // Check error message
    val exception = loginResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
  }

  @Test
  fun test_logout_functionality() = runBlocking {
    // Register and login a user
    val registrationResult = authRepo.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    // Verify user is authenticated
    assertNotNull("User should be authenticated", auth.currentUser)

    // Test logout
    val logoutResult = authRepo.logout()

    // Verify logout succeeded
    assertTrue("Logout should succeed", logoutResult.isSuccess)

    // Verify user is no longer authenticated
    assertNull("User should not be authenticated after logout", auth.currentUser)
  }

  @Test
  fun test_account_persistence_in_firestore() = runBlocking {
    // Register a user
    val registrationResult = authRepo.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    val account = registrationResult.getOrNull()
    assertNotNull("Account should be created", account)

    // Verify account exists in Firestore by retrieving it directly
    val retrievedAccount = firestoreRepo.getAccount(account!!.uid)

    assertEquals("Account UID should match", account.uid, retrievedAccount.uid)
    assertEquals("Account name should match", account.name, retrievedAccount.name)
    assertEquals("Account email should match", account.email, retrievedAccount.email)
  }

  @Test
  fun test_registration_creates_proper_account_document() = runBlocking {
    // Register a user
    val registrationResult = authRepo.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    val account = registrationResult.getOrNull()
    assertNotNull("Account should be created", account)

    // Verify account properties
    assertEquals("Email should match registration email", testEmail, account?.email)
    assertEquals("Name should be derived from email", testEmail.substringBefore('@'), account?.name)
    assertNotNull("Account should have a UID", account?.uid)
    assertNotNull("Account should have previews map", account?.previews)
    assertTrue("Previews should be empty initially", account?.previews?.isEmpty() == true)
  }

  @Test
  fun test_duplicate_email_registration_fails() = runBlocking {
    // Register a user first
    val firstRegistration = authRepo.registerWithEmail(testEmail, testPassword)
    assertTrue("First registration should succeed", firstRegistration.isSuccess)

    // Sign out
    auth.signOut()

    // Try to register with the same email again
    val secondRegistration = authRepo.registerWithEmail(testEmail, testPassword)

    // Verify second registration fails
    assertTrue("Duplicate registration should fail", secondRegistration.isFailure)

    // Check error message indicates email is already in use
    val exception = secondRegistration.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
  }
}
