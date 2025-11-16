package com.github.meeplemeet.model.discussions

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Represents a single message within a discussion.
 *
 * Stored as a document in the `messages` subcollection of a [Discussion]. Can optionally contain
 * either a poll or a photo URL, but not both.
 *
 * @property uid Unique identifier of the message (Firestore document ID).
 * @property senderId UID of the account that sent this message.
 * @property content Text body of the message.
 * @property createdAt Timestamp of when the message was created on the server.
 * @property poll Optional poll data attached to this message.
 * @property photoUrl Optional URL to a photo attached to this message.
 * @throws IllegalArgumentException if both poll and photoUrl are non-null.
 */
data class Message(
    val uid: String,
    val senderId: String,
    val content: String,
    val createdAt: Timestamp,
    val poll: Poll? = null,
    val photoUrl: String? = null
) {
  init {
    require(poll == null || photoUrl == null) { "A message cannot contain both a poll and a photo" }
  }
}

/**
 * Minimal serializable form of [Message] without the UID, used for Firestore storage.
 *
 * Firestore stores the UID as the document ID, so it is omitted from the stored object.
 *
 * @throws IllegalArgumentException if both poll and photoUrl are non-null.
 */
@Serializable
data class MessageNoUid(
    val senderId: String = "",
    val content: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val poll: Poll? = null,
    val photoUrl: String? = null
) {
  init {
    require(poll == null || photoUrl == null) { "A message cannot contain both a poll and a photo" }
  }
}

/**
 * Converts a full [Message] into its Firestore-storable form [MessageNoUid].
 *
 * @param message The message instance to convert.
 * @return The stripped-down form without UID for storage.
 */
fun toNoUid(message: Message): MessageNoUid =
    MessageNoUid(
        message.senderId, message.content, message.createdAt, message.poll, message.photoUrl)

/**
 * Reconstructs a full [Message] object from its Firestore representation.
 *
 * @param id The Firestore document ID (used as message UID).
 * @param messageNoUid The deserialized [MessageNoUid] data from Firestore.
 * @return A fully constructed [Message] instance.
 */
fun fromNoUid(id: String, messageNoUid: MessageNoUid): Message =
    Message(
        id,
        messageNoUid.senderId,
        messageNoUid.content,
        messageNoUid.createdAt,
        messageNoUid.poll,
        messageNoUid.photoUrl)
