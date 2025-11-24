package com.github.meeplemeet.model.account

// Claude Code generated the documentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ViewModel for managing user profile interactions and friend system operations.
 *
 * This ViewModel provides methods for handling friend requests, accepting requests, blocking users,
 * and resetting relationships. It includes validation logic to prevent invalid operations (e.g.,
 * sending duplicate friend requests, accepting non-existent requests).
 *
 * @property repository The AccountRepository used for data operations. Defaults to the shared
 *   instance from RepositoryProvider.
 */
class ProfileScreenViewModel(
    private val repository: AccountRepository = RepositoryProvider.accounts
) : ViewModel(), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope

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
          account.relationships[other.uid] == RelationshipStatus.Blocked ||
          other.relationships[account.uid] == RelationshipStatus.Blocked

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

    viewModelScope.launch { repository.sendFriendRequest(account.uid, other.uid) }
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
    val a = account.relationships[other.uid]
    val b = other.relationships[account.uid]

    if (sameOrBlocked(account, other) ||
        a != RelationshipStatus.Pending ||
        b != RelationshipStatus.Sent)
        return

    viewModelScope.launch { repository.acceptFriendRequest(account.uid, other.uid) }
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
    val a = account.relationships[other.uid]
    if (account.uid == other.uid || (a != null && a == RelationshipStatus.Blocked)) return

    viewModelScope.launch { repository.blockUser(account.uid, other.uid) }
  }

  /**
   * Cancels a sent friend request from the current account to another user.
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
  fun cancelFriendRequest(account: Account, other: Account) {
    if (sameOrBlocked(account, other)) return

    viewModelScope.launch { repository.resetRelationship(account.uid, other.uid) }
  }

  /**
   * Denies a received friend request from another user.
   *
   * This method validates that the operation is valid before proceeding. It prevents denying in the
   * following cases:
   * - The accounts are the same user
   * - Either user has blocked the other
   *
   * If validation passes, the relationship is reset asynchronously via the repository.
   *
   * @param account The account denying the received friend request
   * @param other The account who sent the friend request
   */
  fun denyFriendRequest(account: Account, other: Account) {
    if (sameOrBlocked(account, other)) return

    viewModelScope.launch { repository.resetRelationship(account.uid, other.uid) }
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

    viewModelScope.launch { repository.resetRelationship(account.uid, friend.uid) }
  }

  /**
   * Unblocks a previously blocked user, removing the block status.
   *
   * This method validates that the operation is valid before proceeding. It prevents unblocking in
   * the following cases:
   * - The accounts are the same user
   * - Either user has blocked the other
   *
   * If validation passes, the relationship is reset asynchronously via the repository.
   *
   * @param account The account unblocking the other user
   * @param other The account being unblocked
   */
  fun unblockUser(account: Account, other: Account) {
    if (account.uid == other.uid || account.relationships[other.uid] != RelationshipStatus.Blocked)
        return

    viewModelScope.launch { repository.unblockUser(account.uid, other.uid) }
  }
}
