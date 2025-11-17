package com.github.meeplemeet.model.discussions

import com.github.meeplemeet.model.sessions.Session
import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Represents a discussion (chat thread) stored in Firestore.
 *
 * A discussion is a group conversation that can be associated with a gaming session or exist
 * independently. It supports features like polls, photo sharing, and administrative controls.
 *
 * ## Architecture
 * - **Main document**: Stored at `discussions/{uid}` in Firestore
 * - **Messages subcollection**: Messages are stored at `discussions/{uid}/messages/{messageId}` to
 *   ensure scalability and efficient real-time updates
 *
 * ## Participants & Permissions
 * - **Participants**: Can read messages, send messages, send photos, create/vote on polls
 * - **Admins**: All participant permissions plus ability to manage participants, edit discussion
 *   details, and set profile picture
 * - **Creator**: Original creator is automatically added as both participant and admin
 *
 * ## Photo Support
 * - Profile pictures are stored in Firebase Storage at `discussions/{uid}/profile.jpg`
 * - Profile pictures can only be set/changed by admins
 *
 * @property uid Globally unique identifier of the discussion (Firestore document ID).
 * @property creatorId The unique identifier of the account that created this discussion.
 * @property name Human-readable title of the discussion (e.g., "Game Night Planning").
 * @property description Optional textual description providing context about the discussion.
 * @property participants List of account UIDs who can participate in this discussion.
 * @property admins List of account UIDs with administrative privileges. Must be a subset of
 *   participants.
 * @property createdAt Server-side timestamp indicating when the discussion was created.
 * @property session Optional reference to a gaming session this discussion is associated with.
 * @property profilePictureUrl Optional HTTPS URL to the discussion's profile picture stored in
 *   Firebase Storage. Set via [DiscussionRepository.setDiscussionProfilePictureUrl].
 * @see Message for the structure of individual messages in the discussion
 * @see DiscussionPreview for lightweight preview data shown in discussion lists
 * @see DiscussionRepository for operations on discussions
 */
data class Discussion(
    val uid: String,
    val creatorId: String,
    val name: String,
    val description: String = "",
    val participants: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val session: Session? = null,
    val profilePictureUrl: String? = null
)

/**
 * Minimal serializable form of [Discussion] without the UID, used for Firestore storage.
 *
 * This data class represents the actual fields stored in the Firestore document. The document ID
 * serves as the UID, so it's not duplicated in the stored fields.
 *
 * ## Firestore Structure
 *
 * ```
 * discussions/{uid}/
 *   ├─ creatorId: String
 *   ├─ name: String
 *   ├─ description: String
 *   ├─ participants: List<String>
 *   ├─ admins: List<String>
 *   ├─ createdAt: Timestamp
 *   ├─ session: Session? (optional)
 *   ├─ profilePictureUrl: String? (optional)
 *   └─ messages/ (subcollection, not in this object)
 *       ├─ {messageId}/
 *       └─ {messageId}/
 * ```
 *
 * @see Discussion for the full data class with UID
 * @see toNoUid to convert Discussion to DiscussionNoUid
 * @see fromNoUid to convert DiscussionNoUid back to Discussion
 */
@Serializable
data class DiscussionNoUid(
    val creatorId: String = "",
    val name: String = "",
    val description: String = "",
    val participants: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val session: Session? = null,
    val profilePictureUrl: String? = null
)

/**
 * Converts a full [Discussion] into its Firestore-storable form [DiscussionNoUid].
 *
 * The UID is stripped since Firestore uses it as the document ID. This conversion is used when
 * writing or updating discussion documents in Firestore.
 *
 * @param discussion The discussion instance to convert.
 * @return The stripped-down form without UID, ready for Firestore storage.
 */
fun toNoUid(discussion: Discussion): DiscussionNoUid =
    DiscussionNoUid(
        discussion.creatorId,
        discussion.name,
        discussion.description,
        discussion.participants,
        discussion.admins,
        discussion.createdAt,
        discussion.session,
        discussion.profilePictureUrl)

/**
 * Reconstructs a full [Discussion] object from its Firestore representation.
 *
 * This function combines the Firestore document ID with the document data to create a complete
 * Discussion object. Used when reading discussions from Firestore.
 *
 * @param id The Firestore document ID (used as discussion UID).
 * @param discussionNoUid The deserialized [DiscussionNoUid] data from the Firestore document.
 * @return A fully constructed [Discussion] instance with UID populated.
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
        discussionNoUid.session,
        discussionNoUid.profilePictureUrl)
