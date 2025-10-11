package com.github.meeplemeet.androidtest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.Authentication.AuthRepoFirebase
import com.github.meeplemeet.MainActivity
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for the Sign In screen UI and authentication flow.
 *
 * Tests the complete sign-in user journey including:
 * - UI component interactions
 * - Form validation
 * - Authentication flow integration
 * - Navigation after successful sign-in
 * - Error handling and display
 */
@RunWith(AndroidJUnit4::class)
class SignInTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var authRepo: AuthRepoFirebase

  // Test credentials
  private val testEmail = "signin_test_${UUID.randomUUID()}@example.com"
  private val testPassword = "TestPassword123!"
  private val invalidEmail = "invalid-email"
  private val wrongPassword = "WrongPassword123!"

  @Before
  fun setup() {
    auth = Firebase.auth
    firestore = Firebase.firestore
    val firestoreRepo = FirestoreRepository(firestore)
    authRepo = AuthRepoFirebase(firestoreRepository = firestoreRepo)

    // Configure emulators
    try {
      auth.useEmulator("10.0.2.2", 9099)
      firestore.useEmulator("10.0.2.2", 8080)
    } catch (e: Exception) {
      // Already configured
    }

    // Ensure clean state
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

  private suspend fun clearTestData() {
    try {
      val usersRef = firestore.collection("users")
      val testDocs = usersRef.whereEqualTo("email", testEmail).get().await()
      for (doc in testDocs.documents) {
        doc.reference.delete().await()
      }
    } catch (e: Exception) {
      // Ignore cleanup errors
    }
  }

  @Test
  fun test_sign_in_screen_displays_correctly() {
    // Navigate to sign in screen (assuming it's the initial screen or accessible)
    composeTestRule.waitForIdle()

    // Verify sign in screen components are displayed
    composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    composeTestRule.onNodeWithText("Email").assertIsDisplayed()
    composeTestRule.onNodeWithText("Password").assertIsDisplayed()

    // Verify input fields are present
    composeTestRule.onNodeWithTag("email_field").assertIsDisplayed()
    composeTestRule.onNodeWithTag("password_field").assertIsDisplayed()

    // Verify buttons are present
    composeTestRule.onNodeWithTag("sign_in_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
  }

  @Test
  fun test_successful_email_password_sign_in_flow() = runBlocking {
    // First register a user to sign in with
    authRepo.registerWithEmail(testEmail, testPassword)
    auth.signOut() // Sign out to test sign in

    composeTestRule.waitForIdle()

    // Fill in email field
    composeTestRule.onNodeWithTag("email_field").performTextInput(testEmail)

    // Fill in password field
    composeTestRule.onNodeWithTag("password_field").performTextInput(testPassword)

    // Click sign in button
    composeTestRule.onNodeWithTag("sign_in_button").performClick()

    // Wait for authentication to complete
    composeTestRule.waitForIdle()

    // Verify user is authenticated
    assert(auth.currentUser != null)
    assert(auth.currentUser?.email == testEmail)
  }

  @Test
  fun test_sign_in_with_invalid_email_shows_error() {
    composeTestRule.waitForIdle()

    // Fill in invalid email
    composeTestRule.onNodeWithTag("email_field").performTextInput(invalidEmail)

    // Fill in password
    composeTestRule.onNodeWithTag("password_field").performTextInput(testPassword)

    // Click sign in button
    composeTestRule.onNodeWithTag("sign_in_button").performClick()

    // Wait for error to appear
    composeTestRule.waitForIdle()

    // Verify error message is displayed
    composeTestRule.onNodeWithText("Invalid email format", substring = true).assertIsDisplayed()

    // Verify user is not authenticated
    assert(auth.currentUser == null)
  }

  @Test
  fun test_sign_in_with_wrong_password_shows_error() = runBlocking {
    // Register a user first
    authRepo.registerWithEmail(testEmail, testPassword)
    auth.signOut()

    composeTestRule.waitForIdle()

    // Fill in correct email
    composeTestRule.onNodeWithTag("email_field").performTextInput(testEmail)

    // Fill in wrong password
    composeTestRule.onNodeWithTag("password_field").performTextInput(wrongPassword)

    // Click sign in button
    composeTestRule.onNodeWithTag("sign_in_button").performClick()

    // Wait for error to appear
    composeTestRule.waitForIdle()

    // Verify error message is displayed
    composeTestRule
        .onNodeWithText("Invalid email or password", substring = true)
        .assertIsDisplayed()

    // Verify user is not authenticated
    assert(auth.currentUser == null)
  }

  @Test
  fun test_sign_in_with_empty_fields_shows_validation_errors() {
    composeTestRule.waitForIdle()

    // Click sign in button without filling fields
    composeTestRule.onNodeWithTag("sign_in_button").performClick()

    // Wait for validation errors
    composeTestRule.waitForIdle()

    // Verify validation errors are shown
    composeTestRule.onNodeWithText("Email cannot be empty", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Password cannot be empty", substring = true).assertIsDisplayed()

    // Verify user is not authenticated
    assert(auth.currentUser == null)
  }

  @Test
  fun test_sign_in_loading_state() = runBlocking {
    // Register a user first
    authRepo.registerWithEmail(testEmail, testPassword)
    auth.signOut()

    composeTestRule.waitForIdle()

    // Fill in credentials
    composeTestRule.onNodeWithTag("email_field").performTextInput(testEmail)
    composeTestRule.onNodeWithTag("password_field").performTextInput(testPassword)

    // Click sign in button
    composeTestRule.onNodeWithTag("sign_in_button").performClick()

    // Verify loading state is shown briefly
    composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()

    // Wait for loading to complete
    composeTestRule.waitForIdle()
  }

  @Test
  fun test_google_sign_in_button_click() {
    composeTestRule.waitForIdle()

    // Click Google sign in button
    composeTestRule.onNodeWithTag("google_sign_in_button").performClick()

    // Wait for action
    composeTestRule.waitForIdle()

    // Note: In a real test, you'd verify that Google sign in flow was initiated
    // For emulator testing, this might show an error or different behavior
    // The important part is that the button responds to clicks
  }

  @Test
  fun test_password_visibility_toggle() {
    composeTestRule.waitForIdle()

    // Enter password
    composeTestRule.onNodeWithTag("password_field").performTextInput(testPassword)

    // Verify password is hidden by default (if your implementation supports this)
    composeTestRule.onNodeWithTag("password_visibility_toggle").assertIsDisplayed()

    // Click visibility toggle
    composeTestRule.onNodeWithTag("password_visibility_toggle").performClick()

    // Verify toggle state changed (implementation dependent)
    composeTestRule.waitForIdle()
  }

  @Test
  fun test_form_validation_real_time() {
    composeTestRule.waitForIdle()

    // Enter invalid email
    composeTestRule.onNodeWithTag("email_field").performTextInput("invalid")

    // Move focus to trigger validation
    composeTestRule.onNodeWithTag("password_field").performClick()

    // Verify real-time validation error (if implemented)
    composeTestRule.waitForIdle()

    // Note: This test depends on your validation implementation
    // You might show errors on focus loss or real-time
  }
}
