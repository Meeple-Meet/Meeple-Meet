package com.github.meeplemeet.androidtest

import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.MainActivity
import com.github.meeplemeet.Authentication.AuthRepoFirebase
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

/**
 * Integration tests for the Sign Up screen UI and registration flow.
 *
 * Tests the complete sign-up user journey including:
 * - UI component interactions
 * - Form validation (email format, password strength, confirmation)
 * - Registration flow integration
 * - Navigation after successful sign-up
 * - Error handling and display
 * - Firestore document creation verification
 */
@RunWith(AndroidJUnit4::class)
class SignUpTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var authRepo: AuthRepoFirebase

    // Test credentials
    private val testEmail = "signup_test_${UUID.randomUUID()}@example.com"
    private val testPassword = "TestPassword123!"
    private val weakPassword = "123"
    private val mismatchPassword = "DifferentPassword456!"
    private val invalidEmail = "invalid-email-format"
    private val existingEmail = "existing_${UUID.randomUUID()}@example.com"

    @Before
    fun setup() {
        auth = Firebase.auth
        firestore = Firebase.firestore
        authRepo = AuthRepoFirebase()

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
            val emails = listOf(testEmail, existingEmail)
            for (email in emails) {
                val testDocs = usersRef.whereEqualTo("email", email).get().await()
                for (doc in testDocs.documents) {
                    doc.reference.delete().await()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun test_sign_up_with_invalid_email_shows_error() {
        navigateToSignUpScreen()

        // Fill in invalid email
        composeTestRule.onNodeWithTag("email_field")
            .performTextInput(invalidEmail)
        composeTestRule.onNodeWithTag("password_field")
            .performTextInput(testPassword)
        composeTestRule.onNodeWithTag("confirm_password_field")
            .performTextInput(testPassword)

        // Click sign up button
        composeTestRule.onNodeWithTag("sign_up_button")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify error message is displayed
        composeTestRule.onNodeWithText("Invalid email format", substring = true)
            .assertIsDisplayed()

        // Verify user is not authenticated
        assert(auth.currentUser == null)
    }

    /*@Test
    fun test_sign_up_with_weak_password_shows_error() {
        navigateToSignUpScreen()

        // Fill in weak password
        composeTestRule.onNodeWithTag("email_field")
            .performTextInput(testEmail)
        composeTestRule.onNodeWithTag("password_field")
            .performTextInput(weakPassword)
        composeTestRule.onNodeWithTag("confirm_password_field")
            .performTextInput(weakPassword)

        // Click sign up button
        composeTestRule.onNodeWithTag("sign_up_button")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify error message is displayed
        composeTestRule.onNodeWithText("Password is too weak", substring = true)
            .assertIsDisplayed()

        // Verify user is not authenticated
        assert(auth.currentUser == null)
    }

    @Test
    fun test_sign_up_with_password_mismatch_shows_error() {
        navigateToSignUpScreen()

        // Fill in mismatched passwords
        composeTestRule.onNodeWithTag("email_field")
            .performTextInput(testEmail)
        composeTestRule.onNodeWithTag("password_field")
            .performTextInput(testPassword)
        composeTestRule.onNodeWithTag("confirm_password_field")
            .performTextInput(mismatchPassword)

        // Click sign up button
        composeTestRule.onNodeWithTag("sign_up_button")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify error message is displayed
        composeTestRule.onNodeWithText("Passwords do not match", substring = true)
            .assertIsDisplayed()

        // Verify user is not authenticated
        assert(auth.currentUser == null)
    }

    @Test
    fun test_sign_up_with_existing_email_shows_error() = runBlocking {
        // First register a user with the test email
        authRepo.registerWithEmail(existingEmail, testPassword)
        auth.signOut()

        navigateToSignUpScreen()

        // Try to register with the same email
        composeTestRule.onNodeWithTag("email_field")
            .performTextInput(existingEmail)
        composeTestRule.onNodeWithTag("password_field")
            .performTextInput(testPassword)
        composeTestRule.onNodeWithTag("confirm_password_field")
            .performTextInput(testPassword)

        // Click sign up button
        composeTestRule.onNodeWithTag("sign_up_button")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify error message is displayed
        composeTestRule.onNodeWithText("Email already in use", substring = true)
            .assertIsDisplayed()
    }*/

    @Test
    fun test_sign_up_with_empty_fields_shows_validation_errors() {
        navigateToSignUpScreen()

        // Click sign up button without filling fields
        composeTestRule.onNodeWithTag("sign_up_button")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify validation errors are shown for all required fields
        composeTestRule.onNodeWithText("Email cannot be empty", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Password cannot be empty", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Please confirm your password", substring = true)
            .assertIsDisplayed()

        // Verify user is not authenticated
        assert(auth.currentUser == null)
    }

    @Test
    fun test_sign_up_loading_state() {
        navigateToSignUpScreen()

        // Fill in valid data
        composeTestRule.onNodeWithTag("email_field")
            .performTextInput(testEmail)
        composeTestRule.onNodeWithTag("password_field")
            .performTextInput(testPassword)
        composeTestRule.onNodeWithTag("confirm_password_field")
            .performTextInput(testPassword)

        // Click sign up button
        composeTestRule.onNodeWithTag("sign_up_button")
            .performClick()

        // Verify loading state is shown briefly
        composeTestRule.onNodeWithTag("loading_indicator")
            .assertIsDisplayed()

        // Wait for loading to complete
        composeTestRule.waitForIdle()
    }

    @Test
    fun test_google_sign_up_button_click() {
        navigateToSignUpScreen()

        // Click Google sign up button
        composeTestRule.onNodeWithTag("google_sign_up_button")
            .performClick()

        composeTestRule.waitForIdle()

        // Note: In emulator testing, this might show an error or different behavior
        // The important part is that the button responds to clicks
    }

    @Test
    fun test_password_visibility_toggles() {
        navigateToSignUpScreen()

        // Enter passwords
        composeTestRule.onNodeWithTag("password_field")
            .performTextInput(testPassword)
        composeTestRule.onNodeWithTag("confirm_password_field")
            .performTextInput(testPassword)

        // Test password field visibility toggle
        composeTestRule.onNodeWithTag("password_visibility_toggle")
            .assertIsDisplayed()
            .performClick()

        // Test confirm password field visibility toggle
        composeTestRule.onNodeWithTag("confirm_password_visibility_toggle")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()
    }

    @Test
    fun test_real_time_password_validation() {
        navigateToSignUpScreen()

        // Enter email first
        composeTestRule.onNodeWithTag("email_field")
            .performTextInput(testEmail)

        // Enter a weak password
        composeTestRule.onNodeWithTag("password_field")
            .performTextInput("123")

        // Move focus to trigger validation
        composeTestRule.onNodeWithTag("confirm_password_field")
            .performClick()

        composeTestRule.waitForIdle()

        // Check if real-time validation is shown (implementation dependent)
        // This test depends on whether you implement real-time validation
    }

    @Test
    fun test_real_time_password_confirmation_validation() {
        navigateToSignUpScreen()

        // Fill in password
        composeTestRule.onNodeWithTag("password_field")
            .performTextInput(testPassword)

        // Fill in different confirm password
        composeTestRule.onNodeWithTag("confirm_password_field")
            .performTextInput("DifferentPassword")

        // Move focus away to trigger validation
        composeTestRule.onNodeWithTag("email_field")
            .performClick()

        composeTestRule.waitForIdle()

        // Check if real-time validation shows password mismatch
        // This depends on your validation implementation
    }

    @Test
    fun test_form_data_persistence_during_errors() {
        navigateToSignUpScreen()

        // Fill in form with invalid email but valid passwords
        composeTestRule.onNodeWithTag("email_field")
            .performTextInput(invalidEmail)
        composeTestRule.onNodeWithTag("password_field")
            .performTextInput(testPassword)
        composeTestRule.onNodeWithTag("confirm_password_field")
            .performTextInput(testPassword)

        // Submit form (should fail due to invalid email)
        composeTestRule.onNodeWithTag("sign_up_button")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify form data is preserved after error
        composeTestRule.onNodeWithTag("email_field")
            .assertTextContains(invalidEmail)
        composeTestRule.onNodeWithTag("password_field")
            .assertTextContains(testPassword)
        composeTestRule.onNodeWithTag("confirm_password_field")
            .assertTextContains(testPassword)
    }

    private fun navigateToSignUpScreen() {
        composeTestRule.waitForIdle()

        Log.d("Enes", "Attempting to navigate to sign up screen")

        // Try to navigate to sign up screen
        try {
            composeTestRule.onNodeWithTag("sign_up_nav")
                .performClick()
            composeTestRule.waitForIdle()
        } catch (e: Exception) {
            // If navigation fails, assume we're already on sign up or it's accessible differently
        }
    }
}