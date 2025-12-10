package com.github.meeplemeet.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.utils.FirestoreTests
import java.util.*
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for email change functionality.
 *
 * These tests verify the complete email change flow using Firebase Auth emulators:
 * - Email change request with password reauthentication
 * - Duplicate email detection
 * - Wrong password handling
 * - Email validation
 * - Firestore synchronization after email change
 *
 * Prerequisites:
 * - Firebase emulators should be running (Auth on port 9099, Firestore on port 8080)
 * - Tests use random email addresses to avoid conflicts
 */
@RunWith(AndroidJUnit4::class)
class EmailChangeIntegrationTest : FirestoreTests() {
  private val testEmail1 = "email_change_test_${UUID.randomUUID()}@example.com"
  private val testEmail2 = "email_change_test_${UUID.randomUUID()}@example.com"
  private val newEmail = "new_email_${UUID.randomUUID()}@example.com"
  private val testPassword = "TestPassword123!"
  private val wrongPassword = "WrongPassword456!"

  private var testUserId1: String? = null
  private var testUserId2: String? = null

  @Before
  fun setup() = runBlocking {
    auth.signOut()
    clearTestData()

    // Create two test users for duplicate email testing
    val result1 = authenticationRepository.registerWithEmail(testEmail1, testPassword)
    assertTrue("First user registration should succeed", result1.isSuccess)
    testUserId1 = result1.getOrNull()?.uid

    auth.signOut()

    val result2 = authenticationRepository.registerWithEmail(testEmail2, testPassword)
    assertTrue("Second user registration should succeed", result2.isSuccess)
    testUserId2 = result2.getOrNull()?.uid
  }

  @After
  fun cleanup() = runBlocking {
    auth.signOut()
    clearTestData()
  }

  private suspend fun clearTestData() {
    try {
      val emails = listOf(testEmail1, testEmail2, newEmail)
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
  fun test_updateEmail_with_valid_password_sends_verification_email() = runBlocking {
    // Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)

    // Attempt to change email
    val updateResult = authenticationRepository.updateEmail(newEmail, testPassword)

    // Verify the operation succeeded (verification email sent)
    assertTrue("Email update should succeed", updateResult.isSuccess)

    // Verify user is still logged in (email not updated yet - requires verification)
    assertNotNull("User should still be logged in", auth.currentUser)
    assertEquals("Email should still be the old one", testEmail1, auth.currentUser?.email)
  }

  @Test
  fun test_updateEmail_with_wrong_password_fails() = runBlocking {
    // Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)

    // Attempt to change email with wrong password
    val updateResult = authenticationRepository.updateEmail(newEmail, wrongPassword)

    // Verify the operation failed
    assertTrue("Email update should fail with wrong password", updateResult.isFailure)

    // Verify error message is user-friendly
    val exception = updateResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    assertTrue(
        "Error message should mention password",
        exception?.message?.lowercase()?.contains("password") == true
    )

    // Verify email unchanged
    assertEquals("Email should remain unchanged", testEmail1, auth.currentUser?.email)
  }

  @Test
  fun test_updateEmail_with_duplicate_email_fails() = runBlocking {
    // Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)

    // Attempt to change email to the second user's email
    val updateResult = authenticationRepository.updateEmail(testEmail2, testPassword)

    // Verify the operation failed
    assertTrue("Email update should fail for duplicate email", updateResult.isFailure)

    // Verify error message mentions email is in use
    val exception = updateResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    assertTrue(
        "Error message should mention email in use",
        exception?.message?.lowercase()?.contains("already in use") == true ||
        exception?.message?.lowercase()?.contains("in use") == true
    )

    // Verify email unchanged
    assertEquals("Email should remain unchanged", testEmail1, auth.currentUser?.email)
  }

  @Test
  fun test_updateEmail_with_same_email_fails() = runBlocking {
    // Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)

    // Attempt to change email to the same email
    val updateResult = authenticationRepository.updateEmail(testEmail1, testPassword)

    // Verify the operation failed
    assertTrue("Email update should fail when email is the same", updateResult.isFailure)

    // Verify error message
    val exception = updateResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    assertTrue(
        "Error message should mention same email",
        exception?.message?.lowercase()?.contains("same") == true
    )
  }

  @Test
  fun test_updateEmail_when_not_logged_in_fails() = runBlocking {
    // Ensure user is logged out
    auth.signOut()

    // Attempt to change email
    val updateResult = authenticationRepository.updateEmail(newEmail, testPassword)

    // Verify the operation failed
    assertTrue("Email update should fail when not logged in", updateResult.isFailure)

    // Verify error message mentions login
    val exception = updateResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    assertTrue(
        "Error message should mention login or user",
        exception?.message?.lowercase()?.contains("logged in") == true ||
        exception?.message?.lowercase()?.contains("user") == true
    )
  }

  @Test
  fun test_syncEmailToFirestore_updates_firestore_when_emails_differ() = runBlocking {
    // Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)
    val userId = loginResult.getOrNull()?.uid
    assertNotNull("User ID should exist", userId)

    // Manually update email in Firebase Auth (simulating verification)
    // Note: In emulator, we can't actually verify, so we'll test the sync logic

    // Change the Firestore email manually to simulate desync
    val newTestEmail = "manually_changed_${UUID.randomUUID()}@example.com"
    accountRepository.setAccountEmail(userId!!, newTestEmail)

    // Verify Firestore has the manual change
    val accountBefore = accountRepository.getAccount(userId)
    assertEquals("Firestore should have manual email", newTestEmail, accountBefore.email)

    // Now sync - should revert to Firebase Auth email (testEmail1)
    val syncResult = authenticationRepository.syncEmailToFirestore()
    assertTrue("Sync should succeed", syncResult.isSuccess)

    // Verify Firestore was updated back to Firebase Auth email
    val accountAfter = accountRepository.getAccount(userId)
    assertEquals("Firestore should match Firebase Auth", testEmail1, accountAfter.email)
  }

  @Test
  fun test_syncEmailToFirestore_skips_write_when_emails_match() = runBlocking {
    // Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)
    val userId = loginResult.getOrNull()?.uid
    assertNotNull("User ID should exist", userId)

    // Ensure Firestore email matches Firebase Auth
    val accountBefore = accountRepository.getAccount(userId!!)
    assertEquals("Emails should match before sync", testEmail1, accountBefore.email)

    // Sync - should succeed but not write (optimization)
    val syncResult = authenticationRepository.syncEmailToFirestore()
    assertTrue("Sync should succeed", syncResult.isSuccess)
    assertEquals("Sync should return Firebase Auth email", testEmail1, syncResult.getOrNull())

    // Verify email still matches (no unnecessary write)
    val accountAfter = accountRepository.getAccount(userId)
    assertEquals("Email should still match after sync", testEmail1, accountAfter.email)
  }

  @Test
  fun test_isEmailInUse_returns_true_for_existing_email() = runBlocking {
    // Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)
    val userId = loginResult.getOrNull()?.uid
    assertNotNull("User ID should exist", userId)

    // Check if second user's email is in use
    val isInUse = accountRepository.isEmailInUse(testEmail2, userId!!)

    // Should return true because testEmail2 is used by second user
    assertTrue("Email should be in use by another account", isInUse)
  }

  @Test
  fun test_isEmailInUse_returns_false_for_current_users_email() = runBlocking {
    // Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)
    val userId = loginResult.getOrNull()?.uid
    assertNotNull("User ID should exist", userId)

    // Check if own email is in use
    val isInUse = accountRepository.isEmailInUse(testEmail1, userId!!)

    // Should return false because it's the current user's email
    assertFalse("Own email should not be considered in use", isInUse)
  }

  @Test
  fun test_isEmailInUse_returns_false_for_unused_email() = runBlocking {
    // Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)
    val userId = loginResult.getOrNull()?.uid
    assertNotNull("User ID should exist", userId)

    // Check if a completely new email is in use
    val unusedEmail = "definitely_unused_${UUID.randomUUID()}@example.com"
    val isInUse = accountRepository.isEmailInUse(unusedEmail, userId!!)

    // Should return false because email doesn't exist
    assertFalse("Unused email should not be in use", isInUse)
  }

  @Test
  fun test_syncEmailToFirestore_when_not_logged_in_fails() = runBlocking {
    // Ensure user is logged out
    auth.signOut()

    // Attempt to sync email
    val syncResult = authenticationRepository.syncEmailToFirestore()

    // Verify the operation failed
    assertTrue("Sync should fail when not logged in", syncResult.isFailure)

    // Verify error message
    val exception = syncResult.exceptionOrNull()
    assertNotNull("Exception should be present", exception)
    assertTrue(
        "Error message should mention login or user",
        exception?.message?.lowercase()?.contains("logged in") == true ||
        exception?.message?.lowercase()?.contains("user") == true
    )
  }

  @Test
  fun test_complete_email_change_flow_with_sync() = runBlocking {
    // Step 1: Log in with first user
    val loginResult = authenticationRepository.loginWithEmail(testEmail1, testPassword)
    assertTrue("Login should succeed", loginResult.isSuccess)
    val userId = loginResult.getOrNull()?.uid
    assertNotNull("User ID should exist", userId)

    // Step 2: Verify initial state
    val accountBefore = accountRepository.getAccount(userId!!)
    assertEquals("Initial email should match", testEmail1, accountBefore.email)

    // Step 3: Request email change
    val updateResult = authenticationRepository.updateEmail(newEmail, testPassword)
    assertTrue("Email update request should succeed", updateResult.isSuccess)

    // Step 4: Verify email hasn't changed yet (requires verification)
    assertEquals("Email should still be old until verified", testEmail1, auth.currentUser?.email)

    // Note: In a real scenario, user would click verification link here
    // In emulator, we can't test the actual verification flow
    // So we test that the sync logic works when called

    // Step 5: Test sync logic (would happen after user logs back in)
    val syncResult = authenticationRepository.syncEmailToFirestore()
    assertTrue("Sync should succeed", syncResult.isSuccess)

    // Verify Firestore matches Firebase Auth (still testEmail1 since not verified)
    val accountAfter = accountRepository.getAccount(userId)
    assertEquals("Firestore should match Firebase Auth", testEmail1, accountAfter.email)
  }
}

