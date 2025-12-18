package com.github.meeplemeet.integration

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.MainActivity
import com.github.meeplemeet.model.account.AccountNoUid
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests for account deletion flow using Firebase emulators. These tests exercise the
 * real repositories (Firestore / Auth / Storage) via the emulator.
 */
class ProfileDeleteIntegrationTest : FirestoreTests() {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  private lateinit var ctx: Context
  private val email = "integ-delete@example.com"
  private val password = "testpassword"

  @Before
  fun setupIntegration() {
    runBlocking {
      ctx = ApplicationProvider.getApplicationContext()

      // Ensure a clean environment (FirestoreTests already clears collections in its @Before)

      // Create or get an auth user in emulator
      val uid: String =
          try {
            val create =
                FirebaseProvider.auth.createUserWithEmailAndPassword(email, password).await()
            create.user?.uid ?: throw IllegalStateException("Failed to create user")
          } catch (_: Exception) {
            // If user already exists, sign in and use that uid
            try {
              FirebaseProvider.auth.signInWithEmailAndPassword(email, password).await()
              FirebaseProvider.auth.currentUser?.uid
                  ?: throw IllegalStateException("Failed to sign in existing user")
            } catch (e2: Exception) {
              throw e2
            }
          }

      // At this point we have a uid and are signed in; recreate/overwrite account doc and storage
      val payload =
          AccountNoUid(
              handle = "@integ",
              name = "Integration",
              email = email,
              photoUrl = "profiles/$uid/photo.jpg",
              description = "",
              shopOwner = false,
              spaceRenter = false)

      db.collection("accounts").document(uid).set(payload).await()

      // Upload a small file to storage at the expected profile path (overwrite if exists)
      val ref = storage.reference.child("profiles/$uid/photo.jpg")
      try {
        ref.putBytes("dummy".toByteArray()).await()
      } catch (_: Exception) {
        // ignore upload issues in emulator environment
      }
    }
  }

  // Helper to check existence of a specific file in storage by attempting to read its metadata.
  private fun storageFileExists(path: String): Boolean {
    return try {
      // Use runBlocking to synchronously await the metadata result inside the predicate
      kotlinx.coroutines.runBlocking { storage.reference.child(path).metadata.await() }
      true
    } catch (_: Exception) {
      false
    }
  }

  @Test
  fun deleteFlow_reauthFailure_keepsData() = runBlocking {
    // Ensure signed in
    FirebaseProvider.auth.signInWithEmailAndPassword(email, password).await()
    val uid = FirebaseProvider.auth.currentUser?.uid ?: "No current user after sign in"

    val account = accountRepository.getAccount(uid)

    var failureMsg: String? = null

    val vm = ProfileScreenViewModel()
    vm.deleteAccountWithReauth(
        account,
        "wrongpassword",
        ctx,
        onSuccess = { fail("Should not succeed with wrong password") },
        onFailure = { failureMsg = it })

    // Wait for failure using composeRule.waitUntil instead of delay
    composeRule.waitUntil(timeoutMillis = 5_000) { failureMsg != null }

    assertNotNull("Expected failure message when reauth fails", failureMsg)

    // Firestore account doc should still exist
    val doc = db.collection("accounts").document(uid).get().await()
    assertTrue("Account doc should still exist after failed reauth", doc.exists())

    // Storage file should still exist (check single file metadata instead of listing)
    val profilePath = "profiles/$uid/photo.jpg"
    assertTrue("Profile storage should still contain the file", storageFileExists(profilePath))

    // Auth user should still be present (able to sign in)
    val signedIn =
        try {
          FirebaseProvider.auth.signInWithEmailAndPassword(email, password).await()
          true
        } catch (_: Exception) {
          false
        }
    assertTrue("Auth user should still be present after failed reauth", signedIn)
  }

  @Test
  fun deleteFlow_reauthSuccess_deletesEverything() = runBlocking {
    // Ensure signed in
    FirebaseProvider.auth.signInWithEmailAndPassword(email, password).await()
    val uid = FirebaseProvider.auth.currentUser?.uid ?: "No current user after sign in"

    val account = accountRepository.getAccount(uid)

    var success = false

    val vm = ProfileScreenViewModel()
    vm.deleteAccountWithReauth(
        account,
        password,
        ctx,
        onSuccess = { success = true },
        onFailure = { fail("Should not fail: $it") })

    // Wait for success using composeRule.waitUntil instead of delay
    composeRule.waitUntil(timeoutMillis = 8_000) { success }

    assertTrue("Expected success callback after deletion", success)

    // Firestore account doc should be removed
    val doc = db.collection("accounts").document(uid).get().await()
    assertFalse("Account doc should be deleted", doc.exists())

    // Storage file should not exist anymore
    val profilePath = "profiles/$uid/photo.jpg"
    // Wait until storage file no longer exists (or time out)
    composeRule.waitUntil(timeoutMillis = 5_000) { !storageFileExists(profilePath) }
    assertFalse("Profile storage should be empty after deletion", storageFileExists(profilePath))

    // Try sign in - should fail because auth user deleted
    val signInSucceeded =
        try {
          FirebaseProvider.auth.signInWithEmailAndPassword(email, password).await()
          true
        } catch (_: Exception) {
          false
        }

    assertFalse("Sign in should fail after auth user deletion", signInSucceeded)
  }
}
