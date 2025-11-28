// This file was done first by hand, corrected and improved using ChatGPT-5
// and finally completed by copilot
package com.github.meeplemeet.model.account

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.images.ImageRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * ViewModel for the Friends screen.
 *
 * This ViewModel exposes only the operations needed for:
 * - Managing friendships (send, remove, cancel, accept)
 * - Blocking / unblocking users
 * - Loading account profile pictures
 *
 * It inherits from [CreateAccountViewModel] so it can reuse the existing handle-based search
 * functionality (e.g. [searchByHandle], handleSuggestions).
 *
 * @property accountRepository The AccountRepository used for data operations.
 * @property imageRepository The ImageRepository used for loading avatars.
 */
class FriendsScreenViewModel(
    private val accountRepository: AccountRepository = RepositoryProvider.accounts,
    handlesRepository: HandlesRepository = RepositoryProvider.handles,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
) : CreateAccountViewModel(handlesRepository) {

  private fun executeNotification(account: Account, notification: Notification) {
    if (notification.receiverId != account.uid) return

    viewModelScope.launch {
      notification.execute()
      // Only update the notification in Firestore if it exists in the account's notifications
      if (account.notifications.any { it.uid == notification.uid }) {
        accountRepository.executeNotification(account.uid, notification.uid)
      }
    }
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
   * @param notification The account whose friend request is being accepted
   */
  fun acceptFriendRequest(account: Account, notification: Notification) {
    val other: Account
    runBlocking { other = accountRepository.getAccount(notification.senderOrDiscussionId) }
    val accountRels = account.relationships[other.uid]
    val otherRels = other.relationships[account.uid]

    if (sameOrBlocked(account, other) ||
        accountRels != RelationshipStatus.PENDING ||
        otherRels != RelationshipStatus.SENT)
        return

    executeNotification(account, notification)
  }

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

    scope.launch {
      accountRepository.sendFriendRequest(account, other.uid)
      accountRepository.sendFriendRequestNotification(other.uid, account)
    }
  }

  /**
   * Blocks another user, preventing all future interactions.
   *
   * This method validates that the block operation is valid before proceeding. It prevents blocking
   * in the following cases:
   * - The accounts are the same user
   * - The other user is already blocked by the current account
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
   * Cancels or denies a sent friend request from the current account to another user.
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

  /**
   * Loads the account profile picture for the given [accountId].
   *
   * Used by the FriendsScreen avatar composable to show a profile image or fall back to initials.
   *
   * @param accountId The ID of the account whose picture to load
   * @param context Android Context required by the ImageRepository
   * @param onLoaded Callback with the loaded bytes, or null on failure
   */
  fun loadAccountProfilePicture(
      accountId: String,
      context: Context,
      onLoaded: (ByteArray?) -> Unit
  ) {
    viewModelScope.launch {
      val bytes =
          try {
            imageRepository.loadAccountProfilePicture(accountId, context)
          } catch (_: Exception) {
            null
          }
      onLoaded(bytes)
    }
  }
}
