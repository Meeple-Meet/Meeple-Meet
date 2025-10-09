package com.github.meeplemeet.Authentication

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log
import com.google.firebase.Firebase

/**
 * Firebase implementation of the AuthRepo interface.
 * Handles user authentication using Firebase Auth and user data storage using Firestore.
 *
 * This repository integrates Firebase Authentication for login/registration and Firestore
 * for storing additional user profile information that Firebase Auth doesn't handle by default.
 *
 * @param auth Firebase Auth instance for authentication operations
 * @param helper Helper for processing Google sign-in credentials
 * @param firestore Firestore instance for user profile data storage
 */
class AuthRepoFirebase(
    private val auth: FirebaseAuth = Firebase.auth,
    private val helper: GoogleSignInHelper = DefaultGoogleSignInHelper(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepo {

    /**
     * Creates a new user document in Firestore with the provided user information.
     * This is called after successful Firebase Auth registration to store additional profile data.
     *
     * @param firebaseUser The authenticated Firebase user
     * @param name Display name for the user (typically extracted from email)
     * @return User object with the created profile information
     */
    private suspend fun createUser(firebaseUser: FirebaseUser, name: String?): User {
        // Create our custom User object with Firebase user data
        val user = User(
            uid = firebaseUser.uid,
            name = name,
            email = firebaseUser.email,
            photoUrl = firebaseUser.photoUrl?.toString(),
            description = null // Default to null for new users
        )

        // Convert User object to a map for Firestore storage
        val userMap = mapOf(
            "uid" to user.uid,
            "name" to user.name,
            "email" to user.email,
            "photoUrl" to user.photoUrl,
            "description" to user.description
        )

        // Store user profile in Firestore under "users" collection with UID as document ID
        firestore.collection("users").document(user.uid).set(userMap).await()
        return user
    }

    /**
     * Fetches the user's description from Firestore.
     * This is additional profile data not stored in Firebase Auth.
     *
     * @param uid The user's unique identifier
     * @return The user's description if it exists, null otherwise
     */
    private suspend fun fetchUserDescription(uid: String): String? {
        return try {
            // Get user document from Firestore
            val doc = firestore.collection("users").document(uid).get().await()

            // Return description field if document exists, null otherwise
            if (doc.exists()) doc.getString("description") else null
        } catch (_: Exception) {
            // If Firestore is unavailable or there's an error, gracefully return null
            // This allows the app to continue functioning even if Firestore fails
            null
        }
    }

    /**
     * Registers a new user with email and password using Firebase Auth.
     * Also creates a corresponding user profile document in Firestore.
     *
     * @param email User's email address
     * @param password User's password
     * @return Result containing User object on success, or error on failure
     */
    override suspend fun registerWithEmail(
        email: String,
        password: String
    ): Result<User> {
        return try {
            // Create user account with Firebase Auth
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: return Result.failure(
                IllegalStateException("Registration failed: Could not retrieve user information")
            )

            // Extract display name from email (part before @)
            // e.g., "john.doe@example.com" becomes "john.doe"
            val name = email.substringBefore('@')

            // Create corresponding Firestore user document with profile data
            val user = createUser(firebaseUser, name)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("Registration failed: ${e.localizedMessage ?: "Unexpected error."}")
            )
        }
    }

    /**
     * Signs in an existing user with email and password.
     * Fetches additional profile data from Firestore after successful authentication.
     *
     * @param email User's email address
     * @param password User's password
     * @return Result containing User object on success, or error on failure
     */
    override suspend fun loginWithEmail(
        email: String,
        password: String
    ): Result<User> {
        return try {
            // Authenticate with Firebase Auth
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: return Result.failure(
                IllegalStateException("Login failed: Could not retrieve user information")
            )

            // Fetch additional profile data from Firestore (like description)
            val description = fetchUserDescription(firebaseUser.uid)
            // Convert FirebaseUser to our custom User object with Firestore data
            val user = firebaseUser.toUser(description)

            Result.success(user)
        } catch (e: Exception) {
            // Return user-friendly error messages for common authentication failures
            val errorMessage = when {
                e.message?.contains("password is invalid", ignoreCase = true) == true ||
                e.message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true ||
                e.message?.contains("wrong password", ignoreCase = true) == true ||
                e.message?.contains("user not found", ignoreCase = true) == true ->
                    "Invalid email or password"
                e.message?.contains("badly formatted", ignoreCase = true) == true ||
                e.message?.contains("invalid email", ignoreCase = true) == true ->
                    "Invalid email format"
                e.message?.contains("too many requests", ignoreCase = true) == true ->
                    "Too many failed attempts. Please try again later."
                else -> e.localizedMessage ?: "Authentication failed. Please try again."
            }

            Result.failure(IllegalStateException(errorMessage))
        }
    }

    /**
     * Signs in a user with Google credentials using the Credential Manager.
     * Handles the complete Google sign-in flow including Firebase Auth integration.
     *
     * @param credential Google credential from Credential Manager
     * @return Result containing User object on success, or error on failure
     */
    override suspend fun loginWithGoogle(credential: Credential): Result<User> {
        return try {
            // Verify that we received a Google ID token credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Log.d("AuthRepoFirebase", "Received Google ID token credential")

                // Extract the Google ID token from the credential
                val idToken = helper.extractIdTokenCredential(credential.data).idToken
                Log.d("AuthRepoFirebase", "Extracted ID token: $idToken")

                // Convert Google ID token to Firebase credential
                val firebaseCred = helper.toFirebaseCredential(idToken)

                // Sign in to Firebase using the Google credential
                val user = auth.signInWithCredential(firebaseCred).await().user
                    ?: return Result.failure(
                        IllegalStateException("Login failed : Could not retrieve user information")
                    )

                // Check if user profile exists in Firestore, create if it doesn't
                val userDocRef = firestore.collection("users").document(user.uid)
                val doc = userDocRef.get().await()
                if (!doc.exists()) {
                    // First-time Google sign-in - create Firestore profile
                    val name = user.email?.substringBefore('@')
                    createUser(user, name)
                }

                // Fetch any additional profile data from Firestore
                val description = fetchUserDescription(user.uid)

                // Convert to our custom User object
                return Result.success(user.toUser(description))
            } else {
                // Invalid credential type received
                return Result.failure(
                    IllegalStateException("Login failed: Credential is not of type Google ID")
                )
            }
        } catch (e: Exception) {
            // Handle any errors during Google sign-in process
            Result.failure(
                IllegalStateException("Login failed: ${e.localizedMessage ?: "Unexpected error."}")
            )
        }
    }

    /**
     * Signs out the current user from Firebase Auth.
     * This clears the authentication state and returns the user to signed-out state.
     *
     * @return Result indicating success or failure of the logout operation
     */
    override suspend fun logout(): Result<Unit> {
        return try {
            // Sign out from Firebase Auth
            // This clears the current user session
            auth.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
            // Handle any errors during logout
            Result.failure(
                IllegalStateException("Logout failed: ${e.localizedMessage ?: "Unexpected error."}")
            )
        }
    }

    /**
     * Gets the currently authenticated user if one exists.
     * This is useful for checking authentication state when the app starts.
     *
     * @return User object if authenticated, null if not signed in
     */
    override suspend fun getCurrentUser(): User? {
        // Get current Firebase user (null if not signed in)
        val firebaseUser = auth.currentUser ?: return null

        // Fetch additional profile data from Firestore
        val description = fetchUserDescription(firebaseUser.uid)

        // Convert to our custom User object
        return firebaseUser.toUser(description)
    }
}

/**
 * Extension function to convert a FirebaseUser to our custom User data class.
 * This bridges the gap between Firebase's user representation and our app's user model.
 *
 * @param description Optional description field from Firestore
 * @return User object with data from FirebaseUser and Firestore
 */
fun FirebaseUser.toUser(description: String? = null) = User(
    uid = uid,                                    // Firebase UID (unique identifier)
    name = displayName,                          // Display name from Firebase (may be null)
    email = email,                               // Email address from Firebase
    photoUrl = photoUrl?.toString(),             // Profile photo URL as string
    description = description                    // Additional field from Firestore
)
