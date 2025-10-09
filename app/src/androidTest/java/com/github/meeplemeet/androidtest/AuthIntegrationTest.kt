package com.github.meeplemeet.androidtest

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.Authentication.AuthRepoFirebase
import com.github.meeplemeet.Authentication.User
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

/**
 * Integration tests for authentication functionality.
 *
 * These tests verify the complete authentication flow using real Firebase services
 * with emulators. They test:
 * - Email/password registration
 * - Email/password login
 * - User profile creation and retrieval
 * - Sign out functionality
 * - Error handling scenarios
 *
 * Prerequisites:
 * - Firebase emulators must be running (Auth and Firestore)
 * - Test device/emulator must be configured to use emulators
 */
@RunWith(AndroidJUnit4::class)
class AuthIntegrationTest {

    private lateinit var context: Context
    private lateinit var authRepo: AuthRepoFirebase
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // Test user credentials - using random emails to avoid conflicts
    private val testEmail = "test_${UUID.randomUUID()}@example.com"
    private val testPassword = "TestPassword123!"
    private val testEmail2 = "test2_${UUID.randomUUID()}@example.com"

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Get Firebase instances
        auth = Firebase.auth
        firestore = Firebase.firestore

        // Initialize the repository
        authRepo = AuthRepoFirebase()

        // Configure emulators (should already be configured in MainActivity)
        // These settings should match what's in MainActivity
        try {
            auth.useEmulator("10.0.2.2", 9099)
            firestore.useEmulator("10.0.2.2", 8080)
        } catch (e: Exception) {
            // Emulators already configured, ignore
        }

        // Ensure we start with a clean state
        runBlocking {
            try {
                auth.signOut()
                // Clear any existing test users from previous runs
                clearTestData()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    @After
    fun cleanup() {
        runBlocking {
            try {
                // Sign out current user
                auth.signOut()
                // Clean up test data
                clearTestData()
            } catch (e: Exception) {
                // Ignore cleanup errors in teardown
            }
        }
    }

    /**
     * Clean up test data from Firestore and Auth
     */
    private suspend fun clearTestData() {
        try {
            // Note: In a real production app, you'd need admin SDK to delete users
            // For emulator testing, users are automatically cleared when emulator restarts

            // Clear Firestore test documents
            val usersRef = firestore.collection("users")
            val testDocs = usersRef.whereEqualTo("email", testEmail).get().await()
            for (doc in testDocs.documents) {
                doc.reference.delete().await()
            }

            val testDocs2 = usersRef.whereEqualTo("email", testEmail2).get().await()
            for (doc in testDocs2.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun test_successful_email_registration_flow() = runBlocking {
        // Test email/password registration
        val result = authRepo.registerWithEmail(testEmail, testPassword)

        // Verify registration succeeded
        assertTrue("Registration should succeed", result.isSuccess)

        val user = result.getOrNull()
        assertNotNull("User should not be null", user)
        assertEquals("Email should match", testEmail, user?.email)
        assertNotNull("User ID should not be null", user?.uid)
        assertNotNull("Display name should not be null", user?.name)

        // Verify user is signed in to Firebase Auth
        val currentUser = auth.currentUser
        assertNotNull("Current user should be signed in", currentUser)
        assertEquals("Auth user email should match", testEmail, currentUser?.email)

        // Verify user document was created in Firestore
        val userDoc = firestore.collection("users").document(user!!.uid).get().await()
        assertTrue("User document should exist in Firestore", userDoc.exists())
        assertEquals("Firestore email should match", testEmail, userDoc.getString("email"))
    }

    @Test
    fun test_successful_email_login_flow() = runBlocking {
        // First register a user
        val registerResult = authRepo.registerWithEmail(testEmail, testPassword)
        assertTrue("Registration should succeed", registerResult.isSuccess)
        val registeredUser = registerResult.getOrNull()!!

        // Sign out
        auth.signOut()
        assertNull("Should be signed out", auth.currentUser)

        // Test login
        val loginResult = authRepo.loginWithEmail(testEmail, testPassword)

        // Verify login succeeded
        assertTrue("Login should succeed", loginResult.isSuccess)

        val loggedInUser = loginResult.getOrNull()
        assertNotNull("Logged in user should not be null", loggedInUser)
        assertEquals("User ID should match", registeredUser.uid, loggedInUser?.uid)
        assertEquals("Email should match", testEmail, loggedInUser?.email)

        // Verify user is signed in to Firebase Auth
        val currentUser = auth.currentUser
        assertNotNull("Current user should be signed in", currentUser)
        assertEquals("Auth user email should match", testEmail, currentUser?.email)
    }

    @Test
    fun test_getCurrentUser_returns_null_when_not_signed_in() = runBlocking {
        // Ensure no user is signed in
        auth.signOut()
        assertNull("Should start with no user", auth.currentUser)

        // Test getCurrentUser
        val currentUser = authRepo.getCurrentUser()

        // Verify returns null
        assertNull("getCurrentUser should return null when not signed in", currentUser)
    }

    @Test
    fun test_getCurrentUser_returns_user_when_signed_in() = runBlocking {
        // Register and sign in a user
        val registerResult = authRepo.registerWithEmail(testEmail, testPassword)
        assertTrue("Registration should succeed", registerResult.isSuccess)
        val registeredUser = registerResult.getOrNull()!!

        // Test getCurrentUser
        val currentUser = authRepo.getCurrentUser()

        // Verify returns correct user
        assertNotNull("getCurrentUser should return user when signed in", currentUser)
        assertEquals("User ID should match", registeredUser.uid, currentUser?.uid)
        assertEquals("Email should match", testEmail, currentUser?.email)
    }

    @Test
    fun test_logout_flow() = runBlocking {
        // Register and sign in a user
        val registerResult = authRepo.registerWithEmail(testEmail, testPassword)
        assertTrue("Registration should succeed", registerResult.isSuccess)

        // Verify user is signed in
        assertNotNull("Should be signed in before logout", auth.currentUser)

        // Test logout
        val logoutResult = authRepo.logout()

        // Verify logout succeeded
        assertTrue("Logout should succeed", logoutResult.isSuccess)

        // Verify user is signed out
        assertNull("Should be signed out after logout", auth.currentUser)

        // Verify getCurrentUser returns null
        val currentUser = authRepo.getCurrentUser()
        assertNull("getCurrentUser should return null after logout", currentUser)
    }

    @Test
    fun test_registration_with_invalid_email_fails() = runBlocking {
        val invalidEmail = "invalid-email"

        val result = authRepo.registerWithEmail(invalidEmail, testPassword)

        // Verify registration failed
        assertTrue("Registration with invalid email should fail", result.isFailure)

        val exception = result.exceptionOrNull()
        assertNotNull("Should have exception", exception)

        // Verify no user is signed in
        assertNull("No user should be signed in after failed registration", auth.currentUser)
    }

    @Test
    fun test_registration_with_weak_password_fails() = runBlocking {
        val weakPassword = "123" // Too short

        val result = authRepo.registerWithEmail(testEmail, weakPassword)

        // Verify registration failed
        assertTrue("Registration with weak password should fail", result.isFailure)

        val exception = result.exceptionOrNull()
        assertNotNull("Should have exception", exception)

        // Verify no user is signed in
        assertNull("No user should be signed in after failed registration", auth.currentUser)
    }

    @Test
    fun test_login_with_wrong_password_fails() = runBlocking {
        // First register a user
        val registerResult = authRepo.registerWithEmail(testEmail, testPassword)
        assertTrue("Registration should succeed", registerResult.isSuccess)

        // Sign out
        auth.signOut()

        // Try to login with wrong password
        val wrongPassword = "WrongPassword123!"
        val loginResult = authRepo.loginWithEmail(testEmail, wrongPassword)

        // Verify login failed
        assertTrue("Login with wrong password should fail", loginResult.isFailure)

        val exception = loginResult.exceptionOrNull()
        assertNotNull("Should have exception", exception)

        // Verify no user is signed in
        assertNull("No user should be signed in after failed login", auth.currentUser)
    }

    @Test
    fun test_login_with_nonexistent_user_fails() = runBlocking {
        val nonexistentEmail = "nonexistent_${UUID.randomUUID()}@example.com"

        val loginResult = authRepo.loginWithEmail(nonexistentEmail, testPassword)

        // Verify login failed
        assertTrue("Login with nonexistent user should fail", loginResult.isFailure)

        val exception = loginResult.exceptionOrNull()
        assertNotNull("Should have exception", exception)

        // Verify no user is signed in
        assertNull("No user should be signed in after failed login", auth.currentUser)
    }

    @Test
    fun test_duplicate_email_registration_fails() = runBlocking {
        // Register first user
        val firstResult = authRepo.registerWithEmail(testEmail, testPassword)
        assertTrue("First registration should succeed", firstResult.isSuccess)

        // Sign out
        auth.signOut()

        // Try to register with same email
        val duplicateResult = authRepo.registerWithEmail(testEmail, testPassword)

        // Verify duplicate registration failed
        assertTrue("Duplicate registration should fail", duplicateResult.isFailure)

        val exception = duplicateResult.exceptionOrNull()
        assertNotNull("Should have exception", exception)
    }

    @Test
    fun test_firestore_user_document_consistency_after_registration() = runBlocking {
        // Register user
        val registerResult = authRepo.registerWithEmail(testEmail, testPassword)
        assertTrue("Registration should succeed", registerResult.isSuccess)
        val user = registerResult.getOrNull()!!

        // Get Firebase Auth user
        val firebaseUser = auth.currentUser
        assertNotNull("Firebase user should exist", firebaseUser)

        // Get Firestore document
        val userDoc = firestore.collection("users").document(user.uid).get().await()
        assertTrue("Firestore document should exist", userDoc.exists())

        // Verify consistency between Auth, Repository User, and Firestore
        assertEquals("UID consistency", firebaseUser!!.uid, user.uid)
        assertEquals("UID in Firestore", user.uid, userDoc.getString("uid"))
        assertEquals("Email consistency", firebaseUser.email, user.email)
        assertEquals("Email in Firestore", user.email, userDoc.getString("email"))
        assertEquals("Name in Firestore", user.name, userDoc.getString("name"))
    }

    @Test
    fun test_authentication_state_consistency_after_app_restart_simulation() = runBlocking {
        // Register and login user
        val registerResult = authRepo.registerWithEmail(testEmail, testPassword)
        assertTrue("Registration should succeed", registerResult.isSuccess)
        val originalUser = registerResult.getOrNull()!!

        // Simulate app restart by creating new repository instance
        val freshRepo = AuthRepoFirebase()

        // Verify user is still authenticated
        val currentUser = freshRepo.getCurrentUser()
        assertNotNull("User should still be authenticated after 'restart'", currentUser)
        assertEquals("UID should match", originalUser.uid, currentUser?.uid)
        assertEquals("Email should match", originalUser.email, currentUser?.email)
    }

    @Test
    fun test_sign_out_clears_authentication_state() = runBlocking {
        // Register user
        val registerResult = authRepo.registerWithEmail(testEmail, testPassword)
        assertTrue("Registration should succeed", registerResult.isSuccess)

        // Verify user is authenticated
        assertNotNull("User should be authenticated", auth.currentUser)
        assertNotNull("getCurrentUser should return user", authRepo.getCurrentUser())

        // Sign out
        val logoutResult = authRepo.logout()
        assertTrue("Logout should succeed", logoutResult.isSuccess)

        // Verify authentication state is cleared
        assertNull("Firebase auth should be cleared", auth.currentUser)
        assertNull("Repository should return null", authRepo.getCurrentUser())

        // Verify can't access user data without re-authentication
        val currentUser = authRepo.getCurrentUser()
        assertNull("Should not have access to user data after logout", currentUser)
    }
}