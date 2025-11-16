package com.github.meeplemeet.model.discussions

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Represents a single message within a discussion.
 *
 * Stored as a document in the `messages` subcollection of a [Discussion]. Can optionally contain a
 * poll.
 *
 * @property uid Unique identifier of the message (Firestore document ID).
 * @property senderId UID of the account that sent this message.
 * @property content Text body of the message.
 * @property createdAt Timestamp of when the message was created on the server.
 * @property poll Optional poll data attached to this message.
 */
data class Message(
    val uid: String,
    val senderId: String,
    val content: String,
    val createdAt: Timestamp,
    val poll: Poll? = null
)

/**
 * Minimal serializable form of [Message] without the UID, used for Firestore storage.
 *
 * Firestore stores the UID as the document ID, so it is omitted from the stored object.
 */
@Serializable
data class MessageNoUid(
    val senderId: String = "",
    val content: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val poll: Poll? = null
)

/**
 * Converts a full [Message] into its Firestore-storable form [MessageNoUid].
 *
 * @param message The message instance to convert.
 * @return The stripped-down form without UID for storage.
 */
fun toNoUid(message: Message): MessageNoUid =
    MessageNoUid(message.senderId, message.content, message.createdAt, message.poll)

/**
 * Reconstructs a full [Message] object from its Firestore representation.
 *
 * @param id The Firestore document ID (used as message UID).
 * @param messageNoUid The deserialized [MessageNoUid] data from Firestore.
 * @return A fully constructed [Message] instance.
 */
fun fromNoUid(id: String, messageNoUid: MessageNoUid): Message =
    Message(
        id, messageNoUid.senderId, messageNoUid.content, messageNoUid.createdAt, messageNoUid.poll)
