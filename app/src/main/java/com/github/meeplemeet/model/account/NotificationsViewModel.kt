package com.github.meeplemeet.model.account

// Claude Code generated the documentation

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shared.game.BggGameRepository
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.ui.account.NotificationPopupData
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
class NotificationsViewModel(
    private val accountRepository: AccountRepository = RepositoryProvider.accounts,
    handlesRepository: HandlesRepository = RepositoryProvider.handles,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions,
    private val gameRepository: BggGameRepository = RepositoryProvider.gamesApi
) : CreateAccountViewModel(handlesRepository) {

  /**
   * Used to fetch a discussion and a session for their data
   *
   * @param discussionId Discussion id to fetch data from
   * @param onResult callback for access to the fetched Discussion
   */
  fun getDiscussion(discussionId: String, onResult: (Discussion) -> Unit) {
    viewModelScope.launch {
      val disc = discussionRepository.getDiscussion(discussionId)
      onResult(disc)
    }
  }

  /**
   * Used to fetch a game for its data
   *
   * @param gameId Game id to fetch data from
   * @param onResult callback for access to fetched game
   */
  fun getGame(gameId: String, onResult: (Game) -> Unit) {
    viewModelScope.launch {
      val game = gameRepository.getGameById(gameId)
      onResult(game)
    }
  }

  fun loadAccountImage(accountId: String, context: Context, onLoaded: (ByteArray?) -> Unit) {
    viewModelScope.launch {
      val bytes =
          try {
            imageRepository.loadAccountProfilePicture(accountId, context)
          } catch (_: Exception) {
            ByteArray(0)
          }
      onLoaded(bytes.takeIf { it.isNotEmpty() })
    }
  }

  fun loadDiscussionImage(discussionId: String, context: Context, onLoaded: (ByteArray?) -> Unit) {
    viewModelScope.launch {
      val bytes =
          try {
            imageRepository.loadDiscussionProfilePicture(context, discussionId)
          } catch (_: Exception) {
            ByteArray(0)
          }
      onLoaded(bytes.takeIf { it.isNotEmpty() })
    }
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

    viewModelScope.launch { accountRepository.acceptFriendRequest(account.uid, other.uid) }
    executeNotification(account, notification)
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

    viewModelScope.launch {
      accountRepository.resetRelationship(account.uid, other.uid)
      // Clear out previous notifications
      account.notifications
          .find { not -> not.senderOrDiscussionId == other.uid }
          ?.let { not -> accountRepository.deleteNotification(account.uid, not.uid) }
      other.notifications
          .find { not -> not.senderOrDiscussionId == account.uid }
          ?.let { not -> accountRepository.deleteNotification(other.uid, not.uid) }
    }
  }

  /**
   * Executes the action associated with a notification.
   *
   * This method validates that the notification belongs to the current account before executing it.
   * The execution behavior depends on the notification type:
   * - **FriendRequest**: Accepts the friend request
   * - **JoinDiscussion**: Adds the user to the discussion
   * - **JoinSession**: Adds the user as a participant in the session
   *
   * The method is idempotent - executing the same notification multiple times has no additional
   * effect after the first execution.
   *
   * @param account The account executing the notification (must match the notification's receiver)
   * @param notification The notification to execute
   * @see Notification.execute for the specific actions performed by each notification type
   */
  private fun executeNotification(account: Account, notification: Notification) {
    if (notification.receiverId != account.uid) return

    viewModelScope.launch { notification.execute() }
  }

  /**
   * Marks a notification as read.
   *
   * This method validates that the notification belongs to the current account before marking it as
   * read. Read notifications are typically displayed differently in the UI to indicate the user has
   * seen them.
   *
   * @param account The account that owns the notification (must match the notification's receiver)
   * @param notification The notification to mark as read
   */
  fun readNotification(account: Account, notification: Notification) {
    if (notification.receiverId != account.uid) return

    viewModelScope.launch { accountRepository.readNotification(account.uid, notification.uid) }
  }

  /**
   * Deletes a notification from the user's notification list.
   *
   * This method validates that the notification belongs to the current account before deleting it.
   * Once deleted, the notification is permanently removed from Firestore and cannot be recovered.
   *
   * @param account The account that owns the notification (must match the notification's receiver)
   * @param notification The notification to delete
   */
  fun deleteNotification(account: Account, notification: Notification) {
    if (notification.receiverId != account.uid) return

    viewModelScope.launch { accountRepository.deleteNotification(account.uid, notification.uid) }
  }

  /**
   * Used to load data before showing a popup, avoiding weird UI displays
   *
   * @param notif Notif to load data from
   * @param context Context where this was invoked
   * @param onReady Callback to execute when the data is ready
   */
  fun preparePopupData(
      notif: Notification,
      context: Context,
      onReady: (NotificationPopupData) -> Unit
  ) {
    when (notif.type) {
      NotificationType.FRIEND_REQUEST -> {
        getOtherAccount(notif.senderOrDiscussionId) { acc ->
          loadAccountImage(acc.uid, context) { bytes ->
            onReady(
                NotificationPopupData.FriendRequest(
                    username = acc.name,
                    handle = acc.handle,
                    bio = acc.description ?: "No description provided.",
                    avatar = bytes))
          }
        }
      }
      NotificationType.JOIN_DISCUSSION -> {
        getDiscussion(notif.senderOrDiscussionId) { disc ->
          loadDiscussionImage(disc.uid, context) { bytes ->
            onReady(
                NotificationPopupData.Discussion(
                    title = disc.name,
                    participants = disc.participants.size,
                    dateLabel = disc.createdAt.toString(),
                    description = disc.description,
                    icon = bytes))
          }
        }
      }
      NotificationType.JOIN_SESSION -> {
        getDiscussion(notif.senderOrDiscussionId) { disc ->
          loadDiscussionImage(disc.uid, context) { bytes ->
            val session = disc.session
            if (session != null) {

              getGame(session.gameId) { game ->
                onReady(
                    NotificationPopupData.Session(
                        title = session.name,
                        participants = session.participants.size,
                        dateLabel = session.date.toString(),
                        description =
                            "Play ${game?.name ?: "Unknown game"} at ${session.date} at ${session.location.name}",
                        icon = bytes))
              }
            }
          }
        }
      }
    }
  }
}
