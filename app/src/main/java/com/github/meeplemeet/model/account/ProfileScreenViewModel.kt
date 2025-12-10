package com.github.meeplemeet.model.account

// Claude Code generated the documentation

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.AuthUIState
import com.github.meeplemeet.model.auth.AuthenticationRepository
import com.github.meeplemeet.model.auth.coolDownErrMessage
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val AUTH_FAILED_DEFAULT_MESSAGE = "Authentication failed"

/**
 * ViewModel for managing user profile interactions and friend system operations.
 *
 * This ViewModel provides methods for handling friend requests, accepting requests, blocking users,
 * and resetting relationships. It includes validation logic to prevent invalid operations (e.g.,
 * sending duplicate friend requests, accepting non-existent requests).
 *
 * @property accountRepository The AccountRepository used for data operations. Defaults to the
 *   shared instance from RepositoryProvider.
 */
class ProfileScreenViewModel(
    private val accountRepository: AccountRepository = RepositoryProvider.accounts,
    handlesRepository: HandlesRepository = RepositoryProvider.handles,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
    private val authRepository: AuthenticationRepository = RepositoryProvider.authentication
) : CreateAccountViewModel(handlesRepository) {

  private val _uiState = MutableStateFlow(AuthUIState())

  // Public read-only state flow that UI components can observe for state changes
  val uiState: StateFlow<AuthUIState> = _uiState

  /**
   * Changes the account profile picture
   *
   * @param account User to change it's profile picture
   * @param context Context of call
   * @param localPath Path to photo
   */
  fun setAccountPhoto(account: Account, context: Context, localPath: String) {
    viewModelScope.launch {
      val downloadUrl =
          imageRepository.saveAccountProfilePicture(account.uid, context = context, localPath)
      accountRepository.setAccountPhotoUrl(account.uid, downloadUrl)
    }
  }

  /**
   * Removes the account profile picture
   *
   * @param account User to remove it's profile picture
   * @param context Context of call
   */
  fun removeAccountPhoto(account: Account, context: Context) {
    viewModelScope.launch {
      imageRepository.deleteAccountProfilePicture(account.uid, context)
      accountRepository.setAccountPhotoUrl(account.uid, "")
    }
  }

  /**
   * Refreshes the user's email verification status.
   *
   * This function is safe to call from the UI and does not return a value, as all updates are
   * propagated via [_uiState].
   */
  fun refreshEmailVerificationStatus() {
    viewModelScope.launch {
      val result = authRepository.isEmailVerified()
      result
          .onSuccess { isVerified ->
            _uiState.update { it.copy(isEmailVerified = isVerified, errorMsg = null) }
          }
          .onFailure { error -> _uiState.update { it.copy(errorMsg = error.localizedMessage) } }
    }
  }

  /**
   * Sends a verification email to the current user.
   *
   * This method enforces a 1-minute cooldown between emails and calls the repository to send a
   * verification email. If it fails, the error message is updated in the UI state.
   */
  fun sendVerificationEmail() {
    val now = System.currentTimeMillis()
    val lastSent = _uiState.value.lastVerificationEmailSentAtMillis

    // Enforce a 1-minute (60,000 ms) cooldown between verification emails
    if (lastSent != null && now - lastSent < 60_000) {
      _uiState.update { it.copy(errorMsg = coolDownErrMessage) }
      return
    }

    viewModelScope.launch {
      authRepository
          .sendVerificationEmail()
          .onSuccess {
            // Update the timestamp on successful send
            _uiState.update {
              it.copy(
                  lastVerificationEmailSentAtMillis = System.currentTimeMillis(), errorMsg = null)
            }
          }
          .onFailure { error -> _uiState.update { it.copy(errorMsg = error.localizedMessage) } }
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
      authRepository.logout().fold({
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

  /**
   * Updates the theme preference of an account
   *
   * @param account The account ID to update
   * @param theme The new theme preference
   */
  fun setAccountTheme(account: Account, theme: ThemeMode) {
    viewModelScope.launch { RepositoryProvider.accounts.setAccountTheme(account.uid, theme) }
  }

  /**
   * Deletes all the shops associated to an account
   *
   * @param account Account to delete it's shops
   */
  fun deleteAccountShops(account: Account) {
    viewModelScope.launch {
      val (shops, _) = RepositoryProvider.accounts.getBusinessIds(account.uid)
      RepositoryProvider.shops.deleteShops(shops)
    }
  }

  /**
   * Deletes all the space renters associated to an account
   *
   * @param account Account to delete it's space renters
   */
  fun deleteAccountSpaceRenters(account: Account) {
    viewModelScope.launch {
      val (_, spaces) = RepositoryProvider.accounts.getBusinessIds(account.uid)
      RepositoryProvider.spaceRenters.deleteSpaceRenters(spaces)
    }
  }

  /**
   * Deletes the user's account with password reauthentication.
   *
   * Deletion flow:
   * 1. Reauthenticates the user with their password
   * 2. Deletes profile picture from storage (if exists)
   * 3. Deletes all Firestore data (discussions, sessions, shops, spaces, account document)
   * 4. Attempts to delete Firebase Auth account (with 3 retry attempts)
   *
   * Note: If reauthentication fails, the entire operation is aborted and the user is shown an
   * error. If Firebase Auth deletion fails after Firestore cleanup, we still treat it as success
   * since the user's data is already removed.
   *
   * @param account The account to delete
   * @param password The user's password for reauthentication
   * @param context Context needed for deleting profile picture from storage
   * @param onSuccess Callback invoked when account deletion is successful
   * @param onFailure Callback invoked with error message if reauthentication fails
   */
  fun deleteAccountWithReauth(
      account: Account,
      password: String,
      context: Context,
      onSuccess: () -> Unit,
      onFailure: (String) -> Unit
  ) {
    if (_uiState.value.isLoading) return

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMsg = null) }

      authRepository
          .reauthenticateWithPassword(password)
          .onSuccess {
            if (!account.photoUrl.isNullOrEmpty()) {
              runCatching { imageRepository.deleteAccountProfilePicture(account.uid, context) }
            }

            deleteAccount(account).await()

            deleteFirebaseAuthAccount(
                onSuccess = {
                  _uiState.update {
                    it.copy(isLoading = false, account = null, signedOut = true, errorMsg = null)
                  }
                  onSuccess()
                },
                onFailure = {
                  _uiState.update {
                    it.copy(isLoading = false, account = null, signedOut = true, errorMsg = null)
                  }
                  onSuccess()
                })
          }
          .onFailure { exception ->
            _uiState.update {
              it.copy(
                  isLoading = false,
                  errorMsg = exception.message ?: AUTH_FAILED_DEFAULT_MESSAGE)
            }
            onFailure(exception.message ?: AUTH_FAILED_DEFAULT_MESSAGE)
          }
    }
  }
}
