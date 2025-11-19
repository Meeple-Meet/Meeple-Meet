package com.github.meeplemeet.model.auth

// Github copilot was used for this file
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.RepositoryProvider
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

private const val OPERATION_LOGIN = "Login"

/**
 * Firebase implementation of the AuthRepo interface. Handles user authentication using Firebase
 * Auth and delegates account creation to FirestoreRepository.
 *
 * This repository integrates Firebase Authentication for login/registration and uses
 * FirestoreRepository for storing account data in the discussions/messaging system.
 *
 * @param auth Firebase Auth instance for authentication operations
 * @param helper Helper for processing Google sign-in credentials
 * @param accountRepository Repository for managing account data in Firestore
 */
class AuthenticationRepository(
    private val auth: FirebaseAuth = FirebaseProvider.auth,
    private val helper: GoogleSignInHelper = DefaultGoogleSignInHelper(),
    private val accountRepository: AccountRepository = RepositoryProvider.accounts
) {
  companion object {
    // Error message mappings for cleaner error handling
    private val INVALID_CREDENTIALS_KEYWORDS =
        listOf(
            "password is invalid", "invalid_login_credentials", "wrong password", "user not found")

    private val INVALID_EMAIL_KEYWORDS = listOf("badly formatted", "invalid email")

    private val TOO_MANY_REQUESTS_KEYWORDS = listOf("too many requests")

    // User-friendly error messages
    private const val INVALID_CREDENTIALS_MSG = "Invalid email or password"
    private const val INVALID_EMAIL_MSG = "Invalid email format"
    private const val TOO_MANY_REQUESTS_MSG = "Too many failed attempts. Please try again later."
    private const val EMAIL_VERIFICATION_FAILED_MSG =
        "Failed to send verification email. Please try again later."
    private const val DEFAULT_ERROR_MSG = "Unexpected error."
    private const val USER_INFO_ERROR_MSG = "Could not retrieve user information"
  }

  /**
   * Maps Firebase exception messages to user-friendly error messages. Uses keyword matching to
   * categorize errors and provide appropriate feedback.
   *
   * @param exception The caught exception
   * @param defaultMessage Default message if no specific mapping is found
   * @return User-friendly error message
   */
  private fun mapErrorMessage(
      exception: Exception,
      defaultMessage: String = DEFAULT_ERROR_MSG
  ): String {
    val errorMessage =
        exception.message?.lowercase() ?: return exception.localizedMessage ?: defaultMessage

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
    // Return clean user-friendly message without operation prefix for known error types
    val cleanMessage =
        when (userMessage) {
          INVALID_CREDENTIALS_MSG -> INVALID_CREDENTIALS_MSG
          INVALID_EMAIL_MSG -> INVALID_EMAIL_MSG
          TOO_MANY_REQUESTS_MSG -> TOO_MANY_REQUESTS_MSG
          else -> userMessage
        }
    return Result.failure(IllegalStateException(cleanMessage))
  }

  /**
   * Checks if the current user's email is verified.
   *
   * @return A [Result] indicating true if the email is verified, false otherwise.
   */
  suspend fun isEmailVerified(): Result<Boolean> {
    return try {
      auth.currentUser?.reload()?.await()
      Result.success(auth.currentUser?.isEmailVerified ?: false)
    } catch (e: Exception) {
      return Result.failure(IllegalStateException(EMAIL_VERIFICATION_FAILED_MSG))
    }
  }

  /**
   * Sends a verification email to the user.
   *
   * @param user The Firebase user to send the verification email to.
   * @return A [Result] indicating success or failure.
   */
  private suspend fun sendVerificationEmail(user: FirebaseUser): Result<Unit> {
    return try {
      user.sendEmailVerification().await()
      Result.success(Unit)
    } catch (e: Exception) {
      return Result.failure(IllegalStateException(EMAIL_VERIFICATION_FAILED_MSG))
    }
  }

  /**
   * Registers a new user with email and password using Firebase Auth. Also creates a corresponding
   * account document in Firestore for the discussions/messaging system.
   *
   * @param email User's email address
   * @param password User's password
   * @return Result containing Account object on success, or error on failure
   */
  suspend fun registerWithEmail(email: String, password: String): Result<Account> {
    return try {
      // Create user account with Firebase Auth
      val authResult = auth.createUserWithEmailAndPassword(email, password).await()
      val firebaseUser =
          authResult.user
              ?: return Result.failure(
                  IllegalStateException("Registration failed: $USER_INFO_ERROR_MSG"))

      // Send verification email
      sendVerificationEmail(firebaseUser).onFailure {
        // If email sending fails, delete the user to maintain consistency
        try {
          firebaseUser.delete().await()
        } catch (_: Exception) {}
        return Result.failure(it)
      }

      // Extract display name from email (part before @)
      // e.g., "john.doe@example.com" becomes "john.doe"
      val name = email.substringBefore('@')

      try {
        // Delegate account creation to FirestoreRepository - this MUST succeed
        val account =
            accountRepository.createAccount(
                userHandle = firebaseUser.uid,
                name = name,
                email = email,
                photoUrl = firebaseUser.photoUrl?.toString())
        Result.success(account)
      } catch (firestoreException: Exception) {
        // If Firestore account creation fails, delete the Firebase Auth user to maintain
        // consistency
        try {
          firebaseUser.delete().await()
        } catch (_: Exception) {}
        return Result.failure(
            IllegalStateException(
                "Registration failed: Could not create user profile. ${firestoreException.localizedMessage}"))
      }
    } catch (e: Exception) {
      createFailureResult("Registration", e)
    }
  }

  /**
   * Signs in an existing user with email and password. Fetches the account from Firestore after
   * successful authentication.
   *
   * @param email User's email address
   * @param password User's password
   * @return Result containing Account object on success, or error on failure
   */
  suspend fun loginWithEmail(email: String, password: String): Result<Account> {
    return try {
      // Authenticate with Firebase Auth
      val authResult = auth.signInWithEmailAndPassword(email, password).await()
      val firebaseUser =
          authResult.user
              ?: return Result.failure(IllegalStateException("Login failed: $USER_INFO_ERROR_MSG"))

      try {
        // Fetch the account from Firestore
        val account = accountRepository.getAccount(firebaseUser.uid)
        Result.success(account)
      } catch (firestoreException: Exception) {
        // Account exists in Firebase Auth but not in Firestore - this is an error condition
        return Result.failure(
            IllegalStateException(
                "Login failed: User profile not found. ${firestoreException.localizedMessage}"))
      }
    } catch (e: Exception) {
      createFailureResult(OPERATION_LOGIN, e)
    }
  }

  /**
   * Signs in a user with Google credentials using the Credential Manager. Handles the complete
   * Google sign-in flow including Firebase Auth integration and account creation/retrieval.
   *
   * @param credential Google credential from Credential Manager
   * @return Result containing Account object on success, or error on failure
   */
  suspend fun loginWithGoogle(credential: Credential): Result<Account> {
    return try {
      // Verify that we received a Google ID token credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

        // Extract the Google ID token from the credential
        val idToken = helper.extractIdTokenCredential(credential.data).idToken

        // Convert Google ID token to Firebase credential
        val firebaseCred = helper.toFirebaseCredential(idToken)

        // Sign in to Firebase using the Google credential
        val firebaseUser =
            auth.signInWithCredential(firebaseCred).await().user
                ?: return Result.failure(
                    IllegalStateException("Login failed: $USER_INFO_ERROR_MSG"))

        // Check if account exists in Firestore, create if it doesn't
        val account =
            try {
              // Try to fetch existing account
              accountRepository.getAccount(firebaseUser.uid)
            } catch (_: Exception) {
              // Account doesn't exist - first-time Google sign-in
              val name =
                  firebaseUser.email?.substringBefore('@') ?: firebaseUser.displayName ?: "User"
              accountRepository.createAccount(
                  userHandle = firebaseUser.uid,
                  name = name,
                  email =
                      firebaseUser.email
                          ?: throw IllegalStateException("Google sign-in must provide email"),
                  photoUrl = firebaseUser.photoUrl?.toString())
            }

        return Result.success(account)
      } else {
        // Invalid credential type received
        return Result.failure(
            IllegalStateException("Login failed: Credential is not of type Google ID"))
      }
    } catch (e: Exception) {
      createFailureResult(OPERATION_LOGIN, e)
    }
  }

  /**
   * Signs out the current user from Firebase Auth. This clears the authentication state and returns
   * the user to signed-out state.
   *
   * @return Result indicating success or failure of the logout operation
   */
  fun logout(): Result<Unit> {
    return try {
      // Sign out from Firebase Auth
      // This clears the current user session
      auth.signOut()
      Result.success(Unit)
    } catch (e: Exception) {
      createFailureResult("Logout", e)
    }
  }
}
