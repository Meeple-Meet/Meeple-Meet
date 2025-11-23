package com.github.meeplemeet.model.account

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
   * @param friend The second account to check
   * @return True if the accounts are the same user, or if either has blocked the other
   */
  private fun sameOrBlocked(account: Account, friend: Account) =
      account.uid == friend.uid ||
          account.blocked.contains(friend.uid) ||
          friend.blocked.contains(account.uid)

  /**
   * Sends a friend request from the current account to another user.
   *
   * This method validates that a friend request can be sent before initiating the operation. It
   * prevents sending requests in the following cases:
   * - The accounts are the same user
   * - Either user has blocked the other
   * - A friend request has already been sent (in either direction)
   * - A friend request is already pending (from either side)
   * - The users are already friends
   *
   * If validation passes, the request is sent asynchronously via the repository.
   *
   * @param account The account sending the friend request
   * @param other The account receiving the friend request
   */
  fun sendFriendRequest(account: Account, other: Account) {
    // Prevent duplicate or invalid friend request
    if (sameOrBlocked(account, other) ||
        account.sent.contains(other.uid) ||
        account.pending.contains(other.uid) ||
        account.friends.contains(other.uid) ||
        other.sent.contains(account.uid) ||
        other.pending.contains(account.uid) ||
        other.friends.contains(other.uid))
        return

    viewModelScope.launch { repository.sendFriendRequest(account.uid, other.uid) }
  }

  /**
   * Accepts a pending friend request, establishing a mutual friendship.
   *
   * This method validates that a friend request can be accepted before proceeding. It requires:
   * - The current account has a pending request from the other user
   * - The other user has a sent request to the current account
   * - Neither user has blocked the other
   * - The accounts are not the same user
   * - The users are not already friends
   * - There are no conflicting request states
   *
   * If validation passes, the friendship is established asynchronously via the repository.
   *
   * @param account The account accepting the friend request
   * @param other The account whose friend request is being accepted
   */
  fun acceptFriendRequest(account: Account, other: Account) {
    if (sameOrBlocked(account, other) ||
        !account.pending.contains(other.uid) ||
        !other.sent.contains(account.uid) ||
        account.sent.contains(other.uid) ||
        other.pending.contains(account.uid) ||
        account.friends.contains(other.uid) ||
        other.friends.contains(account.uid))
        return

    viewModelScope.launch { repository.acceptFriendRequest(account.uid, other.uid) }
  }

  /**
   * Blocks another user, preventing all future interactions.
   *
   * This method validates that the block operation is valid before proceeding. It prevents blocking
   * in the following cases:
   * - The accounts are the same user
   * - The other user is already blocked
   *
   * The method handles the case where both users have blocked each other by checking if the other
   * user has already blocked the current account and passing this information to the repository to
   * preserve the other user's block status.
   *
   * If validation passes, the block is executed asynchronously via the repository.
   *
   * @param account The account performing the block action
   * @param other The account being blocked
   */
  fun blockUser(account: Account, other: Account) {
    if (account.uid == other.uid || account.blocked.contains(other.uid)) return

    viewModelScope.launch {
      repository.blockUser(account.uid, other.uid, other.blocked.contains(account.uid))
    }
  }

  /**
   * Resets the relationship between two users, removing all connection data.
   *
   * This method can be used for multiple purposes:
   * - Unblocking a user
   * - Canceling a sent friend request
   * - Denying a received friend request
   * - Removing an existing friend
   *
   * It validates that the operation is valid before proceeding. It prevents resetting relationships
   * in the following cases:
   * - The accounts are the same user
   * - Either user has blocked the other (use blockUser to unblock instead)
   *
   * If validation passes, the relationship is reset asynchronously via the repository.
   *
   * @param account The first account in the relationship
   * @param friend The second account in the relationship
   */
  fun resetRelationship(account: Account, friend: Account) {
    if (sameOrBlocked(account, friend)) return

    viewModelScope.launch { repository.resetRelationship(account.uid, friend.uid) }
  }
}
