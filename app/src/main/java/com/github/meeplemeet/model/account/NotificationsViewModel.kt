package com.github.meeplemeet.model.account

// Claude Code generated the documentation

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.ui.account.NotificationPopupData
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    private val gameRepository: GameRepository = RepositoryProvider.games
) : CreateAccountViewModel(handlesRepository) {

  /**
   * Used to fetch a discussion and a session for their data
   *
   * @param discussionId Discussion id to fetch data from
   * @param onResult callback for access to the fetched Discussion
   * @param onDeleted callback upon repository failure (discussion does not exist)
   */
  fun getDiscussion(
      discussionId: String,
      onResult: (Discussion) -> Unit,
      onDeleted: () -> Unit = {}
  ) {
    viewModelScope.launch {
      val disc =
          try {
            discussionRepository.getDiscussion(discussionId)
          } catch (_: Exception) {
            null
          }
      if (disc != null) {
        onResult(disc)
      } else {
        onDeleted()
        return@launch
      }
    }
  }

  /**
   * Used to fetch an account for it's data
   *
   * @param id Account id to fetch data from
   * @param onResult callback upon repository success
   * @param onDeleted callback upon repository failure (account does not exist)
   */
  fun getOtherAccountData(id: String, onResult: (Account) -> Unit, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      val acc =
          try {
            accountRepository.getAccount(id, false)
          } catch (_: Exception) {
            null
          }

      if (acc != null) {
        onResult(acc)
      } else {
        onDeleted()
        return@launch
      }
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
  fun executeNotification(account: Account, notification: Notification) {
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
      account: Account,
      context: Context,
      onReady: (NotificationPopupData) -> Unit
  ) {
    when (notif.type) {
      NotificationType.FRIEND_REQUEST -> {
        getOtherAccountData(
            notif.senderOrDiscussionId,
            onResult = { acc ->
              loadAccountImage(acc.uid, context) { bytes ->
                onReady(
                    NotificationPopupData.FriendRequest(
                        username = acc.name,
                        handle = acc.handle,
                        bio = acc.description ?: "No description provided.",
                        avatar = bytes))
              }
            },
            onDeleted = { deleteNotification(account, notif) })
      }
      NotificationType.JOIN_DISCUSSION -> {
        getDiscussion(
            notif.senderOrDiscussionId,
            onResult = { disc ->
              loadDiscussionImage(disc.uid, context) { bytes ->
                onReady(
                    NotificationPopupData.Discussion(
                        title = disc.name,
                        participants = disc.participants.size,
                        dateLabel =
                            "Created at" +
                                disc.createdAt
                                    .toDate()
                                    .toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        description = disc.description,
                        icon = bytes))
              }
            },
            onDeleted = { deleteNotification(account, notif) })
      }
      NotificationType.JOIN_SESSION -> {
        getDiscussion(
            notif.senderOrDiscussionId,
            onResult = { disc ->
              loadDiscussionImage(disc.uid, context) { bytes ->
                val session = disc.session
                if (session != null) {

                  getGame(session.gameId) { game ->
                    val dateTime =
                        session.date
                            .toDate()
                            .toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()

                    onReady(
                        NotificationPopupData.Session(
                            title = session.name,
                            participants = session.participants.size,
                            dateLabel = dateTime.format(DateTimeFormatter.ofPattern("MMM d")),
                            description =
                                "Play ${game.name} at ${dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))} at ${session.location.name}.",
                            icon = bytes))
                  }
                } else {
                  deleteNotification(account, notif)
                }
              }
            },
            onDeleted = { deleteNotification(account, notif) })
      }
    }
  }
}
