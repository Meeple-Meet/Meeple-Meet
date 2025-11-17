package com.github.meeplemeet.model.discussions

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Lightweight metadata about a discussion, stored per-account for efficient list rendering.
 *
 * Used to show discussion previews in the discussions overview screen without loading all messages
 * from the subcollection. Each user has their own preview for each discussion they participate in,
 * stored in their account document.
 *
 * ## Storage Location
 * Stored as a map field `previews` in each account document:
 * ```
 * accounts/{accountId}/
 *   └─ previews: Map<String, DiscussionPreviewNoUid>
 *        ├─ {discussionId}: DiscussionPreviewNoUid
 *        └─ {discussionId}: DiscussionPreviewNoUid
 * ```
 *
 * ## Update Behavior
 * - Updated automatically when messages are sent to the discussion
 * - Photo messages show "📷 Photo" as preview text (first 5 chars of content shown)
 * - Poll messages show "📊 Poll: {question}" as preview text
 * - `unreadCount` increments for all participants except the sender
 * - Reading messages resets the `unreadCount` to 0 for that user
 *
 * ## Photo Message Preview Format
 * When a message contains a photo, the preview shows:
 * - "📷 " + first 5 characters of message content (or "Photo" if content is empty)
 * - Example: "📷 Check" for message "Check this out!"
 *
 * @property uid ID of the discussion this preview refers to (maps to Discussion.uid).
 * @property lastMessage Text content of the most recent message, with special formatting for photos
 *   and polls.
 * @property lastMessageSender UID of the account that sent the most recent message.
 * @property lastMessageAt Server-side timestamp of when the most recent message was sent.
 * @property unreadCount Number of unread messages for this account. Reset when user reads messages.
 * @see Discussion for the full discussion data structure
 * @see Message for message structure
 * @see DiscussionRepository.readDiscussionMessages for resetting unread count
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
 * This is the actual data stored in Firestore as values in the `previews` map field of each account
 * document. The map key serves as the discussion UID, so it's not duplicated in the value.
 *
 * @see DiscussionPreview for the full data class with discussion UID
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
 * Combines the map key (discussion UID) with the map value (preview data) to create a complete
 * DiscussionPreview object.
 *
 * @param id The discussion UID (map key in the account's previews field).
 * @param discussionPreviewNoUid The deserialized preview data (map value).
 * @return A fully constructed [DiscussionPreview] instance with UID populated.
 */
fun fromNoUid(id: String, discussionPreviewNoUid: DiscussionPreviewNoUid): DiscussionPreview =
    DiscussionPreview(
        id,
        discussionPreviewNoUid.lastMessage,
        discussionPreviewNoUid.lastMessageSender,
        discussionPreviewNoUid.lastMessageAt,
        discussionPreviewNoUid.unreadCount)

/**
 * Creates a preview from a discussion and optional last message.
 *
 * This helper function generates a new DiscussionPreview, typically used when first adding a user
 * to a discussion. If no lastMessage is provided, the preview will have empty strings and current
 * timestamp.
 *
 * @param discussion The discussion to create preview for.
 * @param lastMessage Optional last message to populate preview data. If null, preview will have
 *   empty lastMessage and lastMessageSender fields.
 * @return A [DiscussionPreview] instance with unreadCount initialized to 0.
 */
fun toPreview(discussion: Discussion, lastMessage: Message? = null): DiscussionPreview {
  return DiscussionPreview(
      discussion.uid,
      lastMessage?.content ?: "",
      lastMessage?.senderId ?: "",
      lastMessage?.createdAt ?: Timestamp.now(),
      0)
}
