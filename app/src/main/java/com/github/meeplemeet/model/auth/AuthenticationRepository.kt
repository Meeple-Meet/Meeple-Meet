package com.github.meeplemeet.model.auth

// Github copilot was used for this file
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountRepository
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.EmailAuthProvider
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

    private val EMAIL_IN_USE_KEYWORDS =
        listOf(
            "email address is already in use",
            "email-already-in-use",
            "already in use",
            "email already exists",
            "email is already registered")

    private val REQUIRES_RECENT_LOGIN_KEYWORDS =
        listOf("requires recent login", "requires-recent-login")

    // User-friendly error messages
    private const val INVALID_CREDENTIALS_MSG = "Invalid email or password"
    private const val INVALID_EMAIL_MSG = "Invalid email format"
    private const val TOO_MANY_REQUESTS_MSG = "Too many failed attempts. Please try again later."
    private const val ERROR_NO_LOGGED_IN_USER = "No user is currently logged in."
    private const val ERROR_NO_EMAIL_ADDRESS = "Current user has no email address."
    private const val EMAIL_VERIFICATION_FAILED_MSG =
        "Failed to send verification email. Please try again later."
    private const val DEFAULT_ERROR_MSG = "Unexpected error."
    private const val USER_INFO_ERROR_MSG = "Could not retrieve user information"
    private const val EMAIL_IN_USE_MSG = "This email address is already in use by another account."
    private const val EMAIL_SAME_AS_CURRENT_MSG = "New email is the same as current email."
    private const val WRONG_PASSWORD_MSG = "The password you entered is incorrect."
    private const val REAUTHENTICATION_REQUIRED_MSG = "Please re-authenticate to continue."

    // Operation names for error reporting
    private const val OPERATION_EMAIL_UPDATE = "Email update"
    private const val OPERATION_EMAIL_SYNC = "Email synchronization"
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
      EMAIL_IN_USE_KEYWORDS.any { errorMessage.contains(it) } -> EMAIL_IN_USE_MSG
      REQUIRES_RECENT_LOGIN_KEYWORDS.any { errorMessage.contains(it) } ->
          REAUTHENTICATION_REQUIRED_MSG
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
   * @return A [Result] containing true if the email is verified, false otherwise, or a failure with
   *   a user-friendly error message when the status cannot be retrieved.
   */
  suspend fun isEmailVerified(): Result<Boolean> {
    return try {
      auth.currentUser?.reload()?.await()
      Result.success(auth.currentUser?.isEmailVerified ?: false)
    } catch (e: Exception) {
      Result.failure(IllegalStateException(EMAIL_VERIFICATION_FAILED_MSG, e))
    }
  }

  /**
   * Sends a verification email to the given Firebase user. This function is the function called
   * during registration.
   *
   * @param user The [FirebaseUser] to send the verification email to.
   * @return A [Result] indicating success, or a failure with a user-friendly error message if the
   *   email could not be sent.
   */
  private suspend fun sendVerificationEmailInternal(user: FirebaseUser): Result<Unit> {
    return try {
      user.sendEmailVerification().await()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(IllegalStateException(EMAIL_VERIFICATION_FAILED_MSG, e))
    }
  }

  /**
   * Sends a verification email to the currently logged-in user.
   *
   * @return A [Result] indicating success, or a failure with a user-friendly error message if no
   *   user is logged in or if the email sending fails.
   */
  suspend fun sendVerificationEmail(): Result<Unit> {
    val currentUser =
        auth.currentUser ?: return Result.failure(IllegalStateException(ERROR_NO_LOGGED_IN_USER))

    return sendVerificationEmailInternal(currentUser)
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
      sendVerificationEmailInternal(firebaseUser).onFailure {
        return Result.failure(Exception(EMAIL_VERIFICATION_FAILED_MSG))
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
   * Updates the user's email address in Firebase Authentication.
   *
   * This method performs the following steps:
   * 1. Validates that a user is currently logged in and retrieves current email
   * 2. Checks if the new email is different from the current one
   * 3. Checks if the new email is already in use (via Firestore)
   * 4. Reauthenticates the user with their current password
   * 5. Sends verification email to new address (Firebase Auth updates after verification)
   *
   * IMPORTANT SECURITY BEHAVIOR:
   * - After clicking the verification link, the email changes in Firebase Auth
   * - User remains logged in until auth.currentUser.reload() is called
   * - When reload() is called (e.g., on Profile screen), Firebase invalidates the old token
   * - This causes closing the app or signing out to be required to continue
   *
   * NOTE ON DUPLICATE EMAIL DETECTION:
   * - This method checks Firestore for duplicate emails before sending the verification email
   * - This prevents the common case where another account in your system uses the email
   * - Firebase Auth will also check when the verification link is clicked
   *
   * @param newEmail The new email address to set for the user
   * @param currentPassword The user's current password for reauthentication
   * @return Result indicating success, or failure with a user-friendly error message
   */
  suspend fun updateEmail(newEmail: String, currentPassword: String): Result<Unit> {
    return try {
      // Step 1: Verify user is logged in
      val currentUser =
          auth.currentUser ?: return Result.failure(IllegalStateException(ERROR_NO_LOGGED_IN_USER))

      val currentEmail =
          currentUser.email ?: return Result.failure(IllegalStateException(ERROR_NO_EMAIL_ADDRESS))

      // Step 2: Check if the new email is different from the current one
      if (newEmail == currentEmail) {
        return Result.failure(IllegalStateException(EMAIL_SAME_AS_CURRENT_MSG))
      }

      // Step 3: Check if the new email is already in use in Firestore
      val emailInUse = accountRepository.isEmailInUse(newEmail, currentUser.uid)
      if (emailInUse) {
        return Result.failure(IllegalStateException(EMAIL_IN_USE_MSG))
      }

      // Step 4: Reauthenticate the user with their current password
      try {
        val credential = EmailAuthProvider.getCredential(currentEmail, currentPassword)
        currentUser.reauthenticate(credential).await()
      } catch (_: Exception) {
        // Reauthentication failed - likely wrong password
        return Result.failure(IllegalStateException(WRONG_PASSWORD_MSG))
      }

      // Step 5: Send verification email and schedule email update
      // Firebase requires verification before changing email for security
      // The email in Firebase Auth will update after user clicks the verification link
      try {
        currentUser.verifyBeforeUpdateEmail(newEmail).await()
      } catch (e: Exception) {
        // Map Firebase exceptions to user-friendly messages
        // This catches email-already-in-use, invalid format, and other Firebase errors
        return createFailureResult(OPERATION_EMAIL_UPDATE, e)
      }

      Result.success(Unit)
    } catch (e: Exception) {
      createFailureResult(OPERATION_EMAIL_UPDATE, e)
    }
  }

  /**
   * Synchronizes the user's email from Firebase Auth to Firestore.
   *
   * This should be called when the user returns to the app to ensure that if they verified a new
   * email address, it gets updated in Firestore.
   *
   * Only updates Firestore if the emails differ to avoid unnecessary writes.
   *
   * @return Result with the current email from Firebase Auth, or failure
   */
  suspend fun syncEmailToFirestore(): Result<String> {
    return try {
      val currentUser =
          auth.currentUser ?: return Result.failure(IllegalStateException(ERROR_NO_LOGGED_IN_USER))

      val authEmail =
          currentUser.email ?: return Result.failure(IllegalStateException(ERROR_NO_EMAIL_ADDRESS))

      // Only update Firestore if the emails differ
      try {
        val account = accountRepository.getAccount(currentUser.uid)
        if (account.email != authEmail) {
          accountRepository.setAccountEmail(currentUser.uid, authEmail)
        }
      } catch (_: Exception) {
        // If we can't check or update, still return success with the auth email
        // The sync can be retried later
      }

      Result.success(authEmail)
    } catch (e: Exception) {
      createFailureResult(OPERATION_EMAIL_SYNC, e)
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
