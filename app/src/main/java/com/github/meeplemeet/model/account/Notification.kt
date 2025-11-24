package com.github.meeplemeet.model.account

// Claude Code generated the documentation

import com.github.meeplemeet.RepositoryProvider
import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Types of notifications that can be sent to users.
 *
 * Each type represents a specific action that can be triggered when the user accepts/executes the
 * notification.
 */
enum class NotificationType {
  /** Notification for a friend request from another user. */
  FriendRequest,
  /** Notification to join a discussion. */
  JoinDiscussion,
  /** Notification to join a session. */
  JoinSession
}

/**
 * Represents a notification sent to a user that can trigger an action when executed.
 *
 * Notifications are used to inform users about events and allow them to respond by accepting or
 * dismissing the notification. When accepted, the notification executes a specific action based on
 * its type.
 *
 * ## Storage Location
 * Stored in the account document at:
 * ```
 * accounts/{accountId}/
 *   └─ notifications: Map<String, NotificationNoUid>
 *        ├─ {notificationId}: NotificationNoUid
 *        └─ {notificationId}: NotificationNoUid
 * ```
 *
 * ## Notification Types and Actions
 * - **FriendRequest**: Accepts a friend request from the sender
 * - **JoinDiscussion**: Adds the receiver to the specified discussion
 * - **JoinSession**: Adds the receiver as a participant in the specified session
 *
 * ## Execution Behavior
 * - Notifications can only be executed once (idempotent)
 * - The [execute] method is marked as `suspend` since it performs async Firestore operations
 * - Executed notifications remain in storage but won't perform actions again
 *
 * @property uid Unique identifier for this notification (map key in Firestore).
 * @property senderOrDiscussionId ID of the sender (for FriendRequest) or discussion/session ID (for
 *   JoinDiscussion/JoinSession).
 * @property receiverId ID of the user receiving this notification.
 * @property read Whether the user has seen/read this notification.
 * @property type The type of notification, determines the action when executed.
 * @property sentAt Server-side timestamp of when the notification was created/sent.
 * @property executed Whether this notification has been executed (accepted) by the user. Private to
 *   prevent external modification.
 * @see NotificationType for available notification types
 * @see NotificationNoUid for the Firestore-serializable form
 */
data class Notification(
    val uid: String = "",
    val senderOrDiscussionId: String = "",
    val receiverId: String = "",
    val read: Boolean = false,
    val type: NotificationType = NotificationType.FriendRequest,
    val sentAt: Timestamp = Timestamp.now(),
    private var executed: Boolean = false,
) {
  /**
   * Executes the action associated with this notification type.
   *
   * This method is idempotent - it can only execute once per notification. After the first
   * execution, subsequent calls will have no effect.
   *
   * ## Actions by Type
   * - **FriendRequest**: Accepts the friend request by calling
   *   [AccountRepository.acceptFriendRequest]
   * - **JoinDiscussion**: Adds the receiver to the discussion by calling
   *   [com.github.meeplemeet.model.discussions.DiscussionRepository.addUserToDiscussion]
   * - **JoinSession**: Retrieves the discussion participants and updates the session with the
   *   receiver added
   *
   * ## Error Handling
   * This method may throw exceptions if the underlying repository operations fail (e.g., network
   * issues, permission errors, or if the referenced entities don't exist).
   *
   * @throws Exception if the repository operation fails
   * @see RepositoryProvider for accessing repository instances
   */
  suspend fun execute() {
    if (executed) return

    executed = true
    when (type) {
      NotificationType.FriendRequest -> {
        RepositoryProvider.accounts.acceptFriendRequest(receiverId, senderOrDiscussionId)
      }
      NotificationType.JoinDiscussion -> {
        RepositoryProvider.discussions.addUserToDiscussion(senderOrDiscussionId, receiverId)
      }
      NotificationType.JoinSession -> {
        val disc = RepositoryProvider.discussions.getDiscussion(senderOrDiscussionId)
        RepositoryProvider.sessions.updateSession(
            senderOrDiscussionId, newParticipantList = disc.participants + receiverId)
      }
    }
  }

  /**
   * Reconstructs a full [Notification] from its Firestore representation.
   *
   * Combines the map key (notification UID) and receiver ID with the map value (notification data)
   * to create a complete Notification object.
   *
   * @param uid The notification UID (map key in the account's notifications field).
   * @param receiverId The ID of the user receiving this notification.
   * @param notificationNoUid The deserialized notification data (map value).
   * @return A fully constructed [Notification] instance with UID and receiverId populated.
   */
  fun fromNoUid(
      uid: String,
      receiverId: String,
      notificationNoUid: NotificationNoUid
  ): Notification =
      Notification(
          uid = uid,
          receiverId = receiverId,
          senderOrDiscussionId = notificationNoUid.senderOrDiscussionId,
          read = notificationNoUid.read,
          type = notificationNoUid.type,
          sentAt = notificationNoUid.sentAt,
          executed = notificationNoUid.executed,
      )

  /**
   * Returns whether this notification has been executed.
   *
   * This is a getter method for the private [executed] property. It allows external code to check
   * if a notification has already been executed without being able to modify the execution state
   * directly.
   *
   * @return True if the notification has been executed, false otherwise
   */
  fun executed() = executed
}

/**
 * Minimal serializable form of [Notification] without UID and receiverId.
 *
 * This is the actual data stored in Firestore as values in the `notifications` map field of each
 * account document. The map key serves as the notification UID, and the receiverId is inferred from
 * the account document location, so they're not duplicated in the value.
 *
 * @property senderOrDiscussionId ID of the sender or discussion/session being referenced.
 * @property read Whether the notification has been read.
 * @property type The notification type.
 * @property executed Whether the notification has been executed/accepted.
 * @property sentAt Server-side timestamp of when the notification was created/sent.
 * @see Notification for the full data class with UID and receiverId
 */
@Serializable
data class NotificationNoUid(
    val senderOrDiscussionId: String = "",
    val read: Boolean = false,
    val type: NotificationType = NotificationType.FriendRequest,
    val executed: Boolean = false,
    val sentAt: Timestamp = Timestamp.now(),
)
