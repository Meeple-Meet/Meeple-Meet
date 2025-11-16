package com.github.meeplemeet.model.discussions

import com.github.meeplemeet.model.sessions.Session
import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Represents a discussion (chat thread) stored in Firestore.
 *
 * @property uid Globally unique identifier of the discussion (Firestore document ID).
 * @property name Human-readable title of the discussion.
 * @property creatorId The unique identifier of the account that created this discussion
 * @property description Optional textual description of the discussion.
 * @property participants List of account UIDs participating in this discussion.
 * @property admins List of account UIDs with administrative privileges in this discussion.
 * @property createdAt Timestamp indicating when the discussion was created.
 *
 * Note: Messages are stored in a subcollection and not included in this data class.
 */
data class Discussion(
    val uid: String,
    val creatorId: String,
    val name: String,
    val description: String = "",
    val participants: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val session: Session? = null
)

/**
 * Minimal serializable form of [Discussion] without the UID, used for Firestore storage.
 *
 * Firestore stores the UID as the document ID, so it is omitted from the stored object.
 *
 * Note: Messages are stored in a subcollection and not included in this data class.
 */
@Serializable
data class DiscussionNoUid(
    val creatorId: String = "",
    val name: String = "",
    val description: String = "",
    val participants: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val session: Session? = null
)

/**
 * Converts a full [Discussion] into its Firestore-storable form [DiscussionNoUid].
 *
 * @param discussion The discussion instance to convert.
 * @return The stripped-down form without UID for storage.
 */
fun toNoUid(discussion: Discussion): DiscussionNoUid =
    DiscussionNoUid(
        discussion.creatorId,
        discussion.name,
        discussion.description,
        discussion.participants,
        discussion.admins,
        discussion.createdAt,
        discussion.session)

/**
 * Reconstructs a full [Discussion] object from its Firestore representation.
 *
 * @param id The Firestore document ID (used as discussion UID).
 * @param discussionNoUid The deserialized [DiscussionNoUid] data from Firestore.
 * @return A fully constructed [Discussion] instance.
 */
fun fromNoUid(id: String, discussionNoUid: DiscussionNoUid): Discussion =
    Discussion(
        id,
        discussionNoUid.creatorId,
        discussionNoUid.name,
        discussionNoUid.description,
        discussionNoUid.participants,
        discussionNoUid.admins,
        discussionNoUid.createdAt,
        discussionNoUid.session)
