package com.github.meeplemeet.Authentication

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Firebase implementation of the AuthRepo interface. Handles user authentication using Firebase
 * Auth and user data storage using Firestore.
 *
 * This repository integrates Firebase Authentication for login/registration and Firestore for
 * storing additional user profile information that Firebase Auth doesn't handle by default.
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

  companion object {
    // Error message mappings for cleaner error handling
    private val INVALID_CREDENTIALS_KEYWORDS = listOf(
        "password is invalid",
        "invalid_login_credentials",
        "wrong password",
        "user not found"
    )

    private val INVALID_EMAIL_KEYWORDS = listOf(
        "badly formatted",
        "invalid email"
    )

    private val TOO_MANY_REQUESTS_KEYWORDS = listOf(
        "too many requests"
    )

    // User-friendly error messages
    private const val INVALID_CREDENTIALS_MSG = "Invalid email or password"
    private const val INVALID_EMAIL_MSG = "Invalid email format"
    private const val TOO_MANY_REQUESTS_MSG = "Too many failed attempts. Please try again later."
    private const val DEFAULT_AUTH_ERROR_MSG = "Authentication failed. Please try again."
    private const val DEFAULT_ERROR_MSG = "Unexpected error."
    private const val USER_INFO_ERROR_MSG = "Could not retrieve user information"
  }

  /**
   * Maps Firebase exception messages to user-friendly error messages.
   * Uses keyword matching to categorize errors and provide appropriate feedback.
   *
   * @param exception The caught exception
   * @param defaultMessage Default message if no specific mapping is found
   * @return User-friendly error message
   */
  private fun mapErrorMessage(exception: Exception, defaultMessage: String = DEFAULT_ERROR_MSG): String {
    val errorMessage = exception.message?.lowercase() ?: return exception.localizedMessage ?: defaultMessage

    return when {
      INVALID_CREDENTIALS_KEYWORDS.any { errorMessage.contains(it) } -> INVALID_CREDENTIALS_MSG
      INVALID_EMAIL_KEYWORDS.any { errorMessage.contains(it) } -> INVALID_EMAIL_MSG
      TOO_MANY_REQUESTS_KEYWORDS.any { errorMessage.contains(it) } -> TOO_MANY_REQUESTS_MSG
      else -> exception.localizedMessage ?: defaultMessage
    }
  }

  /**
   * Creates a Result.failure with a consistent error format and user-friendly message.
   *
   * @param operation Description of the operation that failed (e.g., "Registration", "Login")
   * @param exception The caught exception
   * @return Result.failure with formatted error message
   */
  private fun createFailureResult(operation: String, exception: Exception): Result<Nothing> {
    val userMessage = mapErrorMessage(exception, "$operation failed. Please try again.")
    return Result.failure(IllegalStateException("$operation failed: $userMessage"))
  }

  /**
   * Creates a new user document in Firestore with the provided user information. This is called
   * after successful Firebase Auth registration to store additional profile data.
   *
   * @param firebaseUser The authenticated Firebase user
   * @param name Display name for the user (typically extracted from email)
   * @return User object with the created profile information
   */
  private suspend fun createUser(firebaseUser: FirebaseUser, name: String?): User {
    // Create our custom User object with Firebase user data
    val user =
        User(
            uid = firebaseUser.uid,
            name = name,
            email = firebaseUser.email,
            photoUrl = firebaseUser.photoUrl?.toString(),
            description = null // Default to null for new users
            )

    // Convert User object to a map for Firestore storage
    val userMap =
        mapOf(
            "uid" to user.uid,
            "name" to user.name,
            "email" to user.email,
            "photoUrl" to user.photoUrl,
            "description" to user.description)

    // Store user profile in Firestore under "users" collection with UID as document ID
    firestore.collection("users").document(user.uid).set(userMap).await()
    return user
  }

  /**
   * Fetches the user's description from Firestore. This is additional profile data not stored in
   * Firebase Auth.
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
   * Registers a new user with email and password using Firebase Auth. Also creates a corresponding
   * user profile document in Firestore.
   *
   * @param email User's email address
   * @param password User's password
   * @return Result containing User object on success, or error on failure
   */
  override suspend fun registerWithEmail(email: String, password: String): Result<User> {
    return try {
      // Create user account with Firebase Auth
      val authResult = auth.createUserWithEmailAndPassword(email, password).await()
      val firebaseUser = authResult.user
          ?: return Result.failure(IllegalStateException("Registration failed: $USER_INFO_ERROR_MSG"))

      // Extract display name from email (part before @)
      // e.g., "john.doe@example.com" becomes "john.doe"
      val name = email.substringBefore('@')

      // Create corresponding Firestore user document with profile data
      val user = createUser(firebaseUser, name)
      Result.success(user)
    } catch (e: Exception) {
      createFailureResult("Registration", e)
    }
  }

  /**
   * Signs in an existing user with email and password. Fetches additional profile data from
   * Firestore after successful authentication.
   *
   * @param email User's email address
   * @param password User's password
   * @return Result containing User object on success, or error on failure
   */
  override suspend fun loginWithEmail(email: String, password: String): Result<User> {
    return try {
      // Authenticate with Firebase Auth
      val authResult = auth.signInWithEmailAndPassword(email, password).await()
      val firebaseUser = authResult.user
          ?: return Result.failure(IllegalStateException("Login failed: $USER_INFO_ERROR_MSG"))

      // Fetch additional profile data from Firestore (like description)
      val description = fetchUserDescription(firebaseUser.uid)
      // Convert FirebaseUser to our custom User object with Firestore data
      val user = firebaseUser.toUser(description)

      Result.success(user)
    } catch (e: Exception) {
      createFailureResult("Login", e)
    }
  }

  /**
   * Signs in a user with Google credentials using the Credential Manager. Handles the complete
   * Google sign-in flow including Firebase Auth integration.
   *
   * @param credential Google credential from Credential Manager
   * @return Result containing User object on success, or error on failure
   */
  override suspend fun loginWithGoogle(credential: Credential): Result<User> {
    return try {
      // Verify that we received a Google ID token credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

        // Extract the Google ID token from the credential
        val idToken = helper.extractIdTokenCredential(credential.data).idToken

        // Convert Google ID token to Firebase credential
        val firebaseCred = helper.toFirebaseCredential(idToken)

        // Sign in to Firebase using the Google credential
        val user = auth.signInWithCredential(firebaseCred).await().user
            ?: return Result.failure(IllegalStateException("Login failed: $USER_INFO_ERROR_MSG"))

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
            IllegalStateException("Login failed: Credential is not of type Google ID"))
      }
    } catch (e: Exception) {
      createFailureResult("Login", e)
    }
  }

  /**
   * Signs out the current user from Firebase Auth. This clears the authentication state and returns
   * the user to signed-out state.
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
      createFailureResult("Logout", e)
    }
  }

  /**
   * Gets the currently authenticated user if one exists. This is useful for checking authentication
   * state when the app starts.
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
 * Extension function to convert a FirebaseUser to our custom User data class. This bridges the gap
 * between Firebase's user representation and our app's user model.
 *
 * @param description Optional description field from Firestore
 * @return User object with data from FirebaseUser and Firestore
 */
fun FirebaseUser.toUser(description: String? = null) =
    User(
        uid = uid, // Firebase UID (unique identifier)
        name = displayName, // Display name from Firebase (may be null)
        email = email, // Email address from Firebase
        photoUrl = photoUrl?.toString(), // Profile photo URL as string
        description = description // Additional field from Firestore
        )
