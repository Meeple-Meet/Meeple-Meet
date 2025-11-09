package com.github.meeplemeet.model.auth

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignInViewModel : AuthenticationViewModel() {
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
}
