package com.github.meeplemeet.model.viewmodels

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.R
import com.github.meeplemeet.model.repositories.AuthRepository
import com.github.meeplemeet.model.structures.Account
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents the UI state for authentication screens.
 *
 * This data class encapsulates all the state information that the UI needs to display
 * authentication screens properly, including loading states, user data, and error messages.
 *
 * @property isLoading Whether an authentication operation is currently in progress. Used to show
 *   loading indicators and disable buttons during operations.
 * @property account The currently signed-in User object, or null if not authenticated. Contains
 *   user profile information from both Firebase Auth and Firestore.
 * @property errorMsg An error message to display to the user, or null if no error. This is shown in
 *   the UI when authentication operations fail.
 * @property signedOut True if a sign-out operation has just completed. Used to reset UI state after
 *   logout.
 */
data class AuthUIState(
    val isLoading: Boolean = false,
    val account: Account? = null,
    val errorMsg: String? = null,
    val signedOut: Boolean = false
)

/**
 * ViewModel for authentication screens (Sign-In and Sign-Up).
 *
 * This ViewModel manages the authentication state and coordinates between the UI layer and the
 * authentication repository. It handles three types of authentication:
 * 1. Email/password registration
 * 2. Email/password login
 * 3. Google sign-in via Credential Manager
 *
 * The ViewModel uses coroutines to handle asynchronous authentication operations and exposes a
 * StateFlow for the UI to observe authentication state changes.
 *
 * @property repository The authentication repository that handles the actual auth operations.
 *   Defaults to AuthRepoFirebase for production use.
 */
class AuthViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {

  // Private mutable state flow for internal state management
  private val _uiState = MutableStateFlow(AuthUIState())

  // Public read-only state flow that UI components can observe for state changes
  val uiState: StateFlow<AuthUIState> = _uiState

  /**
   * Clears any error message in the UI state. This is typically called when the user dismisses an
   * error dialog or starts a new operation.
   */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /**
   * Creates Google sign-in options for the Credential Manager.
   *
   * This method uses the web client ID from the string resources, which is extracted from
   * google-services.json. The web client ID is essential for Google sign-in to work properly with
   * Firebase.
   *
   * @param context Android context used to access string resources
   * @return GetSignInWithGoogleOption configured with the appropriate client ID
   */
  private fun getSignInOptions(context: Context) =
      GetSignInWithGoogleOption.Builder(
              serverClientId = context.getString(R.string.default_web_client_id))
          .build()

  /**
   * Creates a credential request for the Credential Manager.
   *
   * This wraps the Google sign-in options in a GetCredentialRequest that can be passed to the
   * Credential Manager to initiate the sign-in flow.
   *
   * @param signInOptions The Google sign-in options to include in the request
   * @return GetCredentialRequest that can be used with CredentialManager.getCredential()
   */
  private fun signInRequest(signInOptions: GetSignInWithGoogleOption) =
      GetCredentialRequest.Builder().addCredentialOption(signInOptions).build()

  /**
   * Requests a credential from the Credential Manager.
   *
   * This is a wrapper around the CredentialManager.getCredential() call that extracts just the
   * credential from the response.
   *
   * @param context Android context (must be an Activity for UI flows)
   * @param request The credential request to process
   * @param credentialManager The credential manager instance
   * @return The credential returned by Google sign-in
   */
  private suspend fun getCredential(
      context: Context,
      request: GetCredentialRequest,
      credentialManager: CredentialManager
  ) = credentialManager.getCredential(context, request).credential

  /**
   * Initiates the Google sign-in flow using the Credential Manager API.
   *
   * This method handles the complete Google sign-in process:
   * 1. Validates the context and Google Play Services availability
   * 2. Configures the sign-in options with the correct client ID
   * 3. Requests credentials from the Credential Manager
   * 4. Passes the credential to the repository for Firebase authentication
   * 5. Updates the UI state based on the result
   *
   * The method includes comprehensive error handling for common issues like:
   * - Missing Activity context
   * - Google Play Services not available
   * - User cancellation
   * - No Google account on device
   * - Network or authentication errors
   *
   * @param context Android context (should be an Activity for UI operations)
   * @param credentialManager The credential manager instance for handling sign-in
   */
  fun googleSignIn(
      context: Context,
      credentialManager: CredentialManager,
      callback: () -> Unit = {}
  ) {
    // Prevent multiple simultaneous sign-in attempts
    if (_uiState.value.isLoading) return

    viewModelScope.launch {
      // Set loading state and clear any previous errors
      _uiState.update { it.copy(isLoading = true, errorMsg = null) }

      // Validate that we have an Activity context (required for Credential Manager UI)
      val activity = (context as? Activity)
      if (activity == null) {
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg = "Google sign-in requires an Activity context.",
              signedOut = true,
              account = null)
        }
        return@launch
      }

      // Check if Google Play Services is available (required for Google sign-in)
      val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
      if (availability != ConnectionResult.SUCCESS) {
        val message =
            "Google Play services not available (code $availability). Use a Google Play system image and sign into the device."
        _uiState.update {
          it.copy(isLoading = false, errorMsg = message, signedOut = true, account = null)
        }
        return@launch
      }

      // Configure Google sign-in options and create credential request
      val signInOptions = getSignInOptions(context)
      val signInRequest = signInRequest(signInOptions)

      try {
        // Request credential from Google via Credential Manager
        val credential = getCredential(activity, signInRequest, credentialManager)

        // Pass the credential to the repository for Firebase authentication
        repository.loginWithGoogle(credential).fold({ user ->
          // Success: Update UI with authenticated user
          _uiState.update {
            it.copy(isLoading = false, account = user, errorMsg = null, signedOut = false)
          }
          callback()
        }) { failure ->
          // Repository authentication failed
          _uiState.update {
            it.copy(
                isLoading = false,
                errorMsg = failure.localizedMessage,
                signedOut = true,
                account = null)
          }
        }
      } catch (_: GetCredentialCancellationException) {
        // User cancelled the sign-in flow
        _uiState.update {
          it.copy(
              isLoading = false, errorMsg = "Sign-in cancelled", signedOut = true, account = null)
        }
      } catch (_: NoCredentialException) {
        // No Google account available on the device
        val msg =
            "No Google account available or no credential found. Add a Google account to the device."
        _uiState.update {
          it.copy(isLoading = false, errorMsg = msg, signedOut = true, account = null)
        }
      } catch (e: GetCredentialException) {
        // Other credential-related errors
        val msg = "Failed to get credentials: ${e.javaClass.simpleName}: ${e.localizedMessage}"
        _uiState.update {
          it.copy(isLoading = false, errorMsg = msg, signedOut = true, account = null)
        }
      } catch (e: Exception) {
        // Unexpected errors
        val msg = "Unexpected error: ${e.javaClass.simpleName}: ${e.localizedMessage}"
        _uiState.update {
          it.copy(isLoading = false, errorMsg = msg, signedOut = true, account = null)
        }
      }
    }
  }

  /**
   * Registers a new user with email and password.
   *
   * This method handles the complete registration process including validation, Firebase Auth
   * account creation, and Firestore profile creation through the repository.
   *
   * @param email The user's email address
   * @param password The user's chosen password
   */
  fun registerWithEmail(email: String, password: String, onRegister: () -> Unit = {}) {
    // Prevent multiple simultaneous operations
    if (_uiState.value.isLoading) return

    viewModelScope.launch {
      // Set loading state and clear previous errors
      _uiState.update { it.copy(isLoading = true, errorMsg = null) }

      // Call repository to handle the actual registration
      repository.registerWithEmail(email, password).fold({ user ->
        // Success: Update UI with the new user
        _uiState.update {
          it.copy(isLoading = false, account = user, errorMsg = null, signedOut = false)
        }
        onRegister()
      }) { failure ->
        // Registration failed: Show error message
        _uiState.update {
          it.copy(isLoading = false, errorMsg = failure.localizedMessage, account = null)
        }
      }
    }
  }

  /**
   * Logs in an existing user with email and password.
   *
   * This method authenticates the user with Firebase Auth and fetches their profile data from
   * Firestore through the repository.
   *
   * @param email The user's email address
   * @param password The user's password
   */
  fun loginWithEmail(email: String, password: String, callback: () -> Unit = {}) {
    // Prevent multiple simultaneous operations
    if (_uiState.value.isLoading) return

    viewModelScope.launch {
      // Set loading state and clear previous errors
      _uiState.update { it.copy(isLoading = true, errorMsg = null) }

      // Call repository to handle the authentication
      repository.loginWithEmail(email, password).fold({ user ->
        // Success: Update UI with authenticated user
        _uiState.update {
          it.copy(isLoading = false, account = user, errorMsg = null, signedOut = false)
        }
        callback()
      }) { failure ->
        // Login failed: Show error message
        _uiState.update {
          it.copy(isLoading = false, errorMsg = failure.localizedMessage, account = null)
        }
      }
    }
  }

  /**
   * Signs out the current user.
   *
   * This method calls the repository to sign out from Firebase Auth and updates the UI state to
   * reflect the signed-out status.
   */
  fun signOut() {
    // Prevent multiple simultaneous operations
    if (_uiState.value.isLoading) return

    viewModelScope.launch {
      // Set loading state
      _uiState.update { it.copy(isLoading = true, errorMsg = null) }

      // Call repository to handle sign-out
      repository.logout().fold({
        // Success: Update UI to signed-out state
        _uiState.update {
          it.copy(isLoading = false, account = null, signedOut = true, errorMsg = null)
        }
      }) { failure ->
        // Logout failed: Show error but keep user signed in
        _uiState.update { it.copy(isLoading = false, errorMsg = failure.localizedMessage) }
      }
    }
  }
}
