package com.github.meeplemeet.model.discussions

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Lightweight metadata about a discussion, stored per-account.
 *
 * Used to show a preview of a discussion without loading all messages.
 *
 * @property uid ID of the discussion this preview refers to.
 * @property lastMessage Text content of the most recent message in the discussion.
 * @property lastMessageSender UID of the account that sent the most recent message.
 * @property lastMessageAt Timestamp of when the most recent message was sent.
 * @property unreadCount Number of messages not yet read by this account.
 */
data class DiscussionPreview(
    val uid: String = "",
    val lastMessage: String = "",
    val lastMessageSender: String = "",
    val lastMessageAt: Timestamp = Timestamp.now(),
    val unreadCount: Int = 0
)

/**
 * Minimal serializable form of [DiscussionPreview] without the discussion UID.
 *
 * Stored inside each account’s `previews` subcollection in Firestore.
 */
@Serializable
data class DiscussionPreviewNoUid(
    val lastMessage: String = "",
    val lastMessageSender: String = "",
    val lastMessageAt: Timestamp = Timestamp.now(),
    val unreadCount: Int = 0
)

/**
 * Reconstructs a full [DiscussionPreview] from its Firestore representation.
 *
 * @param id The discussion UID (document ID in Firestore).
 * @param discussionPreviewNoUid The deserialized preview data.
 * @return A fully constructed [DiscussionPreview] instance.
 */
fun fromNoUid(id: String, discussionPreviewNoUid: DiscussionPreviewNoUid): DiscussionPreview =
    DiscussionPreview(
        id,
        discussionPreviewNoUid.lastMessage,
        discussionPreviewNoUid.lastMessageSender,
        discussionPreviewNoUid.lastMessageAt,
        discussionPreviewNoUid.unreadCount)

fun toPreview(discussion: Discussion): DiscussionPreview {
  val lastMessage: Message? =
      if (discussion.messages.isNotEmpty()) discussion.messages.last() else null
  return DiscussionPreview(
      discussion.uid,
      lastMessage?.content ?: "",
      lastMessage?.senderId ?: "",
      lastMessage?.createdAt ?: Timestamp.now(),
      0)
}
