package com.github.meeplemeet.model.auth

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignUpViewModel : AuthenticationViewModel() {
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
}
