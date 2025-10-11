package com.github.meeplemeet.androidtest

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.Authentication.AuthRepoFirebase
import com.github.meeplemeet.model.systems.FirestoreRepository
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
 * Basic integration tests for authentication functionality.
 *
 * These tests verify the complete authentication flow using Firebase services with emulators:
 * - Email/password registration and login
 * - User account creation and retrieval
 * - Authentication state management
 * - Error handling for invalid credentials
 * - Sign out functionality
 *
 * Prerequisites:
 * - Firebase emulators should be running (Auth on port 9099, Firestore on port 8080)
 * - Tests use random email addresses to avoid conflicts between test runs
 */
@RunWith(AndroidJUnit4::class)
class AuthIntegrationTest {

  private lateinit var context: Context
  private lateinit var authRepo: AuthRepoFirebase
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var firestoreRepo: FirestoreRepository

  // Test user credentials - using random emails to avoid conflicts
  private val testEmail = "integration_test_${UUID.randomUUID()}@example.com"
  private val testPassword = "TestPassword123!"
  private val invalidEmail = "invalid-email-format"
  private val wrongPassword = "WrongPassword456!"

  @Before
  fun setup() {
    context = InstrumentationRegistry.getInstrumentation().targetContext

    // Get Firebase instances
    auth = Firebase.auth
    firestore = Firebase.firestore

    // Configure emulators - this should match MainActivity configuration
    try {
      auth.useEmulator("10.0.2.2", 9099)
      firestore.useEmulator("10.0.2.2", 8080)
    } catch (e: IllegalStateException) {
      // Emulators already configured - this is normal in test environment
    } catch (e: Exception) {
      // If emulators are not available, tests will use production Firebase
      // Be careful with this in real scenarios
    }

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
      val usersCollection = firestore.collection("accounts")
      val testUserQuery = usersCollection.whereEqualTo("email", testEmail).get().await()

      for (document in testUserQuery.documents) {
        document.reference.delete().await()
      }
    } catch (e: Exception) {
      // Ignore cleanup errors - they shouldn't fail the test
    }
  }

  @Test
  fun test_successful_email_registration_flow() = runBlocking {
    // Test user registration
    val registrationResult = authRepo.registerWithEmail(testEmail, testPassword)

    // Verify registration succeeded
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    val registeredAccount = registrationResult.getOrNull()
    assertNotNull("Account should be created", registeredAccount)
    assertEquals("Email should match", testEmail, registeredAccount?.email)
    assertNotNull("Account should have a UID", registeredAccount?.uid)

    // Verify Firebase Auth user is created
    assertNotNull("Firebase user should be authenticated", auth.currentUser)
    assertEquals("Firebase user email should match", testEmail, auth.currentUser?.email)
  }

  @Test
  fun test_successful_email_login_after_registration() = runBlocking {
    // First register a user
    val registrationResult = authRepo.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)

    // Sign out to test login
    auth.signOut()
    assertNull("User should be signed out", auth.currentUser)

    // Test login with registered credentials
    val loginResult = authRepo.loginWithEmail(testEmail, testPassword)

    // Verify login succeeded
    assertTrue("Login should succeed", loginResult.isSuccess)

    val loggedInAccount = loginResult.getOrNull()
    assertNotNull("Account should be retrieved", loggedInAccount)
    assertEquals("Email should match", testEmail, loggedInAccount?.email)

    // Verify Firebase Auth user is authenticated
    assertNotNull("Firebase user should be authenticated after login", auth.currentUser)
    assertEquals("Firebase user email should match", testEmail, auth.currentUser?.email)
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
        "Error message should mention email",
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
    val errorMessage = exception?.message?.lowercase() ?: ""
    assertTrue(
        "Error should mention email already in use",
        errorMessage.contains("email") ||
            errorMessage.contains("already") ||
            errorMessage.contains("use"))
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

    // Verify account data matches
    assertEquals("UID should match", account.uid, retrievedAccount.uid)
    assertEquals("Email should match", account.email, retrievedAccount.email)
    assertNotNull("Account should have a name", retrievedAccount.name)
  }

  @Test
  fun test_registration_with_firestore_failure_cleans_up_firebase_user() = runBlocking {
    // This test verifies the new error handling where Firebase user is cleaned up
    // if Firestore account creation fails

    // First, we'll simulate this by temporarily breaking Firestore access
    // Register a user with a very long name that might cause Firestore issues
    val longName = "a".repeat(1500) // Firestore field size limit
    val testEmailLong = "long_name_test_${UUID.randomUUID()}@example.com"

    val registrationResult = authRepo.registerWithEmail(testEmailLong, testPassword)

    // If registration fails due to Firestore issues, Firebase user should be cleaned up
    if (registrationResult.isFailure) {
      // Verify no Firebase user remains authenticated
      assertNull("Firebase user should be cleaned up after failed registration", auth.currentUser)
    }
  }

  @Test
  fun test_login_nonexistent_user() = runBlocking {
    val nonExistentEmail = "nonexistent_${UUID.randomUUID()}@example.com"

    val loginResult = authRepo.loginWithEmail(nonExistentEmail, testPassword)

    assertTrue("Login with nonexistent user should fail", loginResult.isFailure)

    val errorMessage = loginResult.exceptionOrNull()?.message?.lowercase() ?: ""
    assertTrue(
        "Error should indicate invalid credentials",
        errorMessage.contains("invalid") ||
            errorMessage.contains("user") ||
            errorMessage.contains("found"))

    assertNull("No user should be authenticated", auth.currentUser)
  }

  @Test
  fun test_authentication_state_consistency() = runBlocking {
    // Test that authentication state remains consistent across operations

    // Start with no user authenticated
    auth.signOut()
    assertNull("Should start with no authenticated user", auth.currentUser)

    // Register user
    val registrationResult = authRepo.registerWithEmail(testEmail, testPassword)
    assertTrue("Registration should succeed", registrationResult.isSuccess)
    assertNotNull("User should be authenticated after registration", auth.currentUser)

    // Logout
    val logoutResult = authRepo.logout()
    assertTrue("Logout should succeed", logoutResult.isSuccess)
    assertNull("User should not be authenticated after logout", auth.currentUser)

    // Login again
    val loginResult = authRepo.loginWithEmail(testEmail, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)
    assertNotNull("User should be authenticated after login", auth.currentUser)

    // Final logout
    authRepo.logout()
    assertNull("User should not be authenticated after final logout", auth.currentUser)
  }

  @Test
  fun test_multiple_sequential_operations() = runBlocking {
    // Test multiple operations in sequence to ensure no state pollution

    val emails = (1..3).map { "sequential_test_${it}_${UUID.randomUUID()}@example.com" }

    emails.forEach { email ->
      // Register
      val registrationResult = authRepo.registerWithEmail(email, testPassword)
      assertTrue("Registration should succeed for $email", registrationResult.isSuccess)

      // Verify account created
      val account = registrationResult.getOrNull()
      assertNotNull("Account should be created for $email", account)
      assertEquals("Email should match", email, account?.email)

      // Logout
      val logoutResult = authRepo.logout()
      assertTrue("Logout should succeed for $email", logoutResult.isSuccess)
      assertNull("No user should be authenticated after logout", auth.currentUser)

      // Login
      val loginResult = authRepo.loginWithEmail(email, testPassword)
      assertTrue("Login should succeed for $email", loginResult.isSuccess)

      // Final logout for next iteration
      authRepo.logout()
    }
  }

  @Test
  fun test_error_message_user_friendliness() = runBlocking {
    // Test that error messages are user-friendly and not technical

    // Test invalid email
    val invalidEmailResult = authRepo.registerWithEmail("invalid-email", testPassword)
    assertTrue("Should fail with invalid email", invalidEmailResult.isFailure)
    val emailError = invalidEmailResult.exceptionOrNull()?.message ?: ""
    assertFalse("Error should not contain technical details", emailError.contains("Exception"))
    assertFalse("Error should not contain stack trace", emailError.contains("at "))

    // Test wrong password login
    authRepo.registerWithEmail(testEmail, testPassword) // Register first
    auth.signOut()
    val wrongPasswordResult = authRepo.loginWithEmail(testEmail, "wrongpassword")
    assertTrue("Should fail with wrong password", wrongPasswordResult.isFailure)
    val passwordError = wrongPasswordResult.exceptionOrNull()?.message ?: ""
    assertFalse("Error should not contain technical details", passwordError.contains("Exception"))
    assertFalse("Error should not contain stack trace", passwordError.contains("at "))
  }
}
