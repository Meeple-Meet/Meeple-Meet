package com.github.meeplemeet.model.account

// Claude Code generated the documentation
// AI was used for this file

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.AuthUIState
import com.github.meeplemeet.model.auth.AuthenticationRepository
import com.github.meeplemeet.model.auth.coolDownErrMessage
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.github.meeplemeet.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val shopRepository: ShopRepository = RepositoryProvider.shops,
    private val spaceRenterRepository: SpaceRenterRepository = RepositoryProvider.spaceRenters,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
    private val authRepository: AuthenticationRepository = RepositoryProvider.authentication
) : CreateAccountViewModel(handlesRepository) {

  companion object {
    private const val EMAIL_CHANGE_SUCCESS_MSG_TEMPLATE =
        "Verification email sent to %s. Please log back in once you verified your new email."
    private const val COOLDOWN_TIME_MSG = 4000L
  }

  private val _uiState = MutableStateFlow(AuthUIState())
  private val _businesses =
      MutableStateFlow(Pair<List<Shop>, List<SpaceRenter>>(emptyList(), emptyList()))

  // Public read-only state flow that UI components can observe for state changes
  val uiState: StateFlow<AuthUIState> = _uiState
  val businesses: StateFlow<Pair<List<Shop>, List<SpaceRenter>>> = _businesses

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
   * Changes the user's email address in Firebase Auth.
   *
   * This is a sensitive operation that requires:
   * - User reauthentication with password
   * - Email validation and duplicate checking
   * - Updating Firebase Auth (NOT Firestore - Auth is the single source of truth)
   * - Sending verification email to the new address
   *
   * Note: Email is NOT updated in Firestore. Firebase Auth is the single source of truth for email.
   * The UI should always fetch email from Firebase.auth.currentUser?.email
   *
   * @param newEmail The new email address
   * @param password The user's current password for reauthentication
   */
  fun changeEmail(newEmail: String, password: String) {
    // Prevent concurrent operations
    if (_uiState.value.isLoading) return

    viewModelScope.launch {
      // Step 1: Set loading state
      _uiState.update { it.copy(isLoading = true, errorMsg = null, successMsg = null) }

      // Step 2: Update Firebase Auth email (includes reauthentication & validation)
      authRepository
          .updateEmail(newEmail, password)
          .onSuccess {
            // Step 3: Update UI state - success with message
            _uiState.update {
              it.copy(
                  isLoading = false,
                  errorMsg = null,
                  successMsg = EMAIL_CHANGE_SUCCESS_MSG_TEMPLATE.format(newEmail))
            }
            // Step 4: Add delay for user to read the success message
            kotlinx.coroutines.delay(COOLDOWN_TIME_MSG)
            // Step 5: Sign out the user after delay
            signOut()
          }
          .onFailure { error ->
            // Firebase Auth failed (wrong password, email in use, etc.)
            _uiState.update {
              it.copy(isLoading = false, errorMsg = error.localizedMessage, successMsg = null)
            }
          }
    }
  }

  /**
   * Synchronizes the email from Firebase Auth to Firestore.
   *
   * This should be called when navigating back to check if the user has verified their new email
   * address, and if so, update it in Firestore.
   */
  fun syncEmail() {
    viewModelScope.launch {
      authRepository
          .syncEmailToFirestore()
          .onSuccess { /* Email synced successfully, no need to show message */}
          .onFailure { /* Silently fail - this is a background sync */}
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

  fun loadAccountBusinesses(account: Account) {
    viewModelScope.launch {
      val (shopIds, spaceRenterIds) = RepositoryProvider.accounts.getBusinessIds(account.uid)

      val shops =
          shopIds.mapNotNull { id ->
            // safely call repository per id; getOrNull ignores failures
            runCatching { shopRepository.getShop(id) }.getOrNull()
          }

      val spaceRenters =
          spaceRenterIds.mapNotNull { id ->
            runCatching { spaceRenterRepository.getSpaceRenter(id) }.getOrNull()
          }

      _businesses.value = Pair(shops, spaceRenters)
    }
  }
}
