package com.github.meeplemeet.model.structures

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Represents a single message within a discussion.
 *
 * Stored inside the `messages` list of a [Discussion] document.
 *
 * @property senderId UID of the account that sent this message.
 * @property content Text body of the message.
 * @property createdAt Timestamp of when the message was created on the server.
 */
@Serializable
data class Message(
    val senderId: String = "",
    val content: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
