package com.github.meeplemeet.model.account

// Claude Code generated the documentation

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.AuthUIState
import com.github.meeplemeet.model.auth.AuthenticationRepository
import com.github.meeplemeet.model.auth.coolDownErrMessage
import com.github.meeplemeet.model.images.ImageRepository
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
    private val imageRepository: ImageRepository = RepositoryProvider.images,
    private val authRepository: AuthenticationRepository = RepositoryProvider.authentication
) : CreateAccountViewModel(handlesRepository) {

  protected val _uiState = MutableStateFlow(AuthUIState())

  // Public read-only state flow that UI components can observe for state changes
  val uiState: StateFlow<AuthUIState> = _uiState

  /**
   * Checks if two accounts are the same user or if either has blocked the other.
   *
   * This helper method is used to prevent relationship operations between users who should not be
   * able to interact (same user or blocked relationship).
   *
   * @param account The first account to check
   * @param other The second account to check
   * @return True if the accounts are the same user, or if either has blocked the other
   */
  private fun sameOrBlocked(account: Account, other: Account) =
      account.uid == other.uid ||
          account.relationships[other.uid] == RelationshipStatus.BLOCKED ||
          other.relationships[account.uid] == RelationshipStatus.BLOCKED

  /**
   * Sends a friend request from the current account to another user.
   *
   * This method validates that a friend request can be sent before initiating the operation. It
   * prevents sending requests in the following cases:
   * - The accounts are the same user
   * - Either user has blocked the other
   * - A relationship already exists between the users (any status: Friend, Sent, Pending, or
   *   Blocked)
   *
   * The validation is simplified by checking if a relationship already exists in either direction,
   * as any existing relationship (regardless of status) should prevent a new friend request.
   *
   * If validation passes, the request is sent asynchronously via the repository.
   *
   * @param account The account sending the friend request
   * @param other The account receiving the friend request
   */
  fun sendFriendRequest(account: Account, other: Account) {
    if (sameOrBlocked(account, other) ||
        account.relationships[other.uid] != null ||
        other.relationships[account.uid] != null)
        return

    scope.launch { accountRepository.sendFriendRequest(account.uid, other.uid) }
  }

  /**
   * Accepts a pending friend request, establishing a mutual friendship.
   *
   * This method validates that a friend request can be accepted before proceeding. It requires:
   * - The current account has a Pending relationship status with the other user
   * - The other user has a Sent relationship status with the current account
   * - Neither user has blocked the other
   * - The accounts are not the same user
   *
   * The validation ensures the relationship states are consistent with a valid friend request that
   * can be accepted.
   *
   * If validation passes, the friendship is established asynchronously via the repository.
   *
   * @param account The account accepting the friend request
   * @param other The account whose friend request is being accepted
   */
  fun acceptFriendRequest(account: Account, other: Account) {
    val accountRels = account.relationships[other.uid]
    val otherRels = other.relationships[account.uid]

    if (sameOrBlocked(account, other) ||
        accountRels != RelationshipStatus.PENDING ||
        otherRels != RelationshipStatus.SENT)
        return

    scope.launch { accountRepository.acceptFriendRequest(account.uid, other.uid) }
  }

  /**
   * Blocks another user, preventing all future interactions.
   *
   * This method validates that the block operation is valid before proceeding. It prevents blocking
   * in the following cases:
   * - The accounts are the same user
   * - The other user is already blocked by the current account (relationship status is Blocked)
   *
   * The method handles the case where both users have blocked each other by checking if the other
   * user's relationship status with the current account is Blocked, and passing this information to
   * the repository to preserve the other user's block status.
   *
   * If validation passes, the block is executed asynchronously via the repository.
   *
   * @param account The account performing the block action
   * @param other The account being blocked
   */
  fun blockUser(account: Account, other: Account) {
    if (account.uid == other.uid || account.relationships[other.uid] == RelationshipStatus.BLOCKED)
        return

    scope.launch { accountRepository.blockUser(account.uid, other.uid) }
  }

  /**
   * Cancels or deny's a sent friend request from the current account to another user.
   *
   * This method validates that the operation is valid before proceeding. It prevents canceling in
   * the following cases:
   * - The accounts are the same user
   * - Either user has blocked the other
   *
   * If validation passes, the relationship is reset asynchronously via the repository.
   *
   * @param account The account canceling the sent friend request
   * @param other The account to whom the friend request was sent
   */
  fun rejectFriendRequest(account: Account, other: Account) {
    if (sameOrBlocked(account, other)) return

    scope.launch { accountRepository.resetRelationship(account.uid, other.uid) }
  }

  /**
   * Removes an existing friendship between two users.
   *
   * This method validates that the operation is valid before proceeding. It prevents removing
   * friendships in the following cases:
   * - The accounts are the same user
   * - Either user has blocked the other
   *
   * If validation passes, the relationship is reset asynchronously via the repository.
   *
   * @param account The account removing the friendship
   * @param friend The friend to be removed
   */
  fun removeFriend(account: Account, friend: Account) {
    if (sameOrBlocked(account, friend)) return

    scope.launch { accountRepository.resetRelationship(account.uid, friend.uid) }
  }

  /**
   * Unblocks a previously blocked user, removing the block status.
   *
   * This method validates that the operation is valid before proceeding. It prevents unblocking in
   * the following cases:
   * - The accounts are the same user
   * - The relationship from the current account to the other user is not Blocked
   *
   * If validation passes, the unblock is executed asynchronously via the repository.
   *
   * @param account The account unblocking the other user
   * @param other The account being unblocked
   */
  fun unblockUser(account: Account, other: Account) {
    if (account.uid == other.uid || account.relationships[other.uid] != RelationshipStatus.BLOCKED)
        return

    scope.launch { accountRepository.unblockUser(account.uid, other.uid) }
  }

  fun setAccountPhoto(account: Account, context: Context, localPath: String) {
    viewModelScope.launch {
      val downloadUrl =
          imageRepository.saveAccountProfilePicture(account.uid, context = context, localPath)
      accountRepository.setAccountPhotoUrl(account.uid, downloadUrl)
    }
  }

  fun removeAccountPhoto(account: Account, context: Context) {
    viewModelScope.launch {
      imageRepository.deleteAccountProfilePicture(account.uid, context)
      accountRepository.setAccountPhotoUrl(account.uid, "")
    }
  }

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

  fun loadAccountProfilePicture(
      accountId: String,
      context: Context,
      onLoaded: (ByteArray?) -> Unit
  ) {
    viewModelScope.launch {
      val bytes =
          try {
            imageRepository.loadAccountProfilePicture(accountId, context)
          } catch (e: Exception) {
            null
          }
      onLoaded(bytes)
    }
  }

  /**
   * Marks a notification as read, with basic validation.
   *
   * Only allows reading the notification if it actually belongs to the given account.
   */
  fun readNotification(owner: Account, notification: Notification) {
    // Prevent invalid operations (wrong owner)
    if (notification.receiverId != owner.uid) return

    // Delegate to repository
    scope.launch { accountRepository.readNotification(owner.uid, notification.uid) }
  }

  /**
   * Deletes a notification, with basic validation.
   *
   * Only allows deleting the notification if it actually belongs to the given account.
   */
  fun deleteNotification(owner: Account, notification: Notification) {
    // Prevent invalid operations (wrong owner)
    if (notification.receiverId != owner.uid) return

    // Delegate to repository
    scope.launch { accountRepository.deleteNotification(owner.uid, notification.uid) }
  }

  /**
   * Signs out the current user.
   *
   * This method calls the repository to sign out from Firebase Auth and updates the UI state to
   * reflect the signed-out status.
   */
  open fun signOut() {
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
}
