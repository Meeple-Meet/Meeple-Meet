package com.github.meeplemeet.model.discussions

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Represents a single message within a discussion.
 *
 * Messages are stored as documents in the `messages` subcollection of each discussion. This
 * architecture ensures scalability and enables efficient real-time message streaming without
 * loading the entire message history.
 *
 * ## Storage Location
 *
 * ```
 * discussions/{discussionId}/messages/{messageId}/
 *   ├─ senderId: String
 *   ├─ content: String
 *   ├─ createdAt: Timestamp
 *   ├─ poll: Poll? (optional)
 *   └─ photoUrl: String? (optional)
 * ```
 *
 * ## Message Types
 * A message can be one of three types (mutually exclusive):
 * 1. **Text message**: Just content, no poll or photo
 * 2. **Poll message**: Contains a poll object (content is the poll question)
 * 3. **Photo message**: Contains photoUrl to Firebase Storage (content is optional caption)
 *
 * ## Photo Messages
 * - Photos are uploaded to Firebase Storage at
 *   `discussions/{discussionId}/messages/{messageId}.jpg`
 * - `photoUrl` contains the HTTPS download URL from Firebase Storage
 * - `content` can optionally contain a caption/description for the photo
 * - Preview text in discussion list shows "📷 {first 5 chars of content or 'Photo'}"
 *
 * ## Poll Messages
 * - `poll` contains question, options, votes, and settings
 * - `content` duplicates the poll question for preview purposes
 * - See [Poll] documentation for voting mechanics
 *
 * ## Ordering
 * Messages are ordered by `createdAt` timestamp (server-side) when queried from Firestore.
 *
 * @property uid Unique identifier of the message (Firestore document ID).
 * @property senderId UID of the account that sent this message.
 * @property content Text body of the message. For polls, this is the question. For photos, this is
 *   an optional caption.
 * @property createdAt Server-side timestamp of when the message was created.
 * @property poll Optional poll data attached to this message. Mutually exclusive with photoUrl.
 * @property photoUrl Optional HTTPS URL to a photo stored in Firebase Storage. Mutually exclusive
 *   with poll.
 * @throws IllegalArgumentException if both poll and photoUrl are non-null (validation in init
 *   block).
 * @see Poll for poll structure and voting mechanics
 * @see Discussion for parent discussion structure
 * @see DiscussionRepository.sendMessageToDiscussion for sending text messages
 * @see DiscussionRepository.sendPhotoMessageToDiscussion for sending photo messages
 * @see DiscussionRepository.createPoll for creating poll messages
 */
data class Message(
    val uid: String,
    val senderId: String,
    val content: String,
    val createdAt: Timestamp,
    val poll: Poll? = null,
    val photoUrl: String? = null,
    val edited: Boolean = false,
) {
  init {
    require(poll == null || photoUrl == null) { "A message cannot contain both a poll and a photo" }
  }
}

/**
 * Minimal serializable form of [Message] without the UID, used for Firestore storage.
 *
 * This data class represents the actual fields stored in the Firestore message document. The
 * document ID serves as the UID, so it's not duplicated in the stored fields.
 *
 * @throws IllegalArgumentException if both poll and photoUrl are non-null (validation in init
 *   block).
 * @see Message for the full data class with UID
 * @see toNoUid to convert Message to MessageNoUid
 * @see fromNoUid to convert MessageNoUid back to Message
 */
@Serializable
data class MessageNoUid(
    val senderId: String = "",
    val content: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val poll: Poll? = null,
    val photoUrl: String? = null,
    val edited: Boolean = false,
) {
  init {
    require(poll == null || photoUrl == null) { "A message cannot contain both a poll and a photo" }
  }
}

/**
 * Converts a full [Message] into its Firestore-storable form [MessageNoUid].
 *
 * The UID is stripped since Firestore uses it as the document ID. This conversion is used when
 * writing or updating message documents in the messages subcollection.
 *
 * @param message The message instance to convert.
 * @return The stripped-down form without UID, ready for Firestore storage.
 */
fun toNoUid(message: Message): MessageNoUid =
    MessageNoUid(
        message.senderId,
        message.content,
        message.createdAt,
        message.poll,
        message.photoUrl,
        message.edited)

/**
 * Reconstructs a full [Message] object from its Firestore representation.
 *
 * This function combines the Firestore document ID with the document data to create a complete
 * Message object. Used when reading messages from the messages subcollection in Firestore.
 *
 * @param id The Firestore document ID (used as message UID).
 * @param messageNoUid The deserialized [MessageNoUid] data from the Firestore document.
 * @return A fully constructed [Message] instance with UID populated.
 */
fun fromNoUid(id: String, messageNoUid: MessageNoUid): Message =
    Message(
        id,
        messageNoUid.senderId,
        messageNoUid.content,
        messageNoUid.createdAt,
        messageNoUid.poll,
        messageNoUid.photoUrl,
        messageNoUid.edited)
