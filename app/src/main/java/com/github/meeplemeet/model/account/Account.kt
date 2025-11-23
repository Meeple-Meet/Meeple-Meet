package com.github.meeplemeet.model.account

import com.github.meeplemeet.model.discussions.DiscussionPreview
import com.github.meeplemeet.model.discussions.DiscussionPreviewNoUid
import com.github.meeplemeet.model.discussions.fromNoUid
import kotlinx.serialization.Serializable

/**
 * Represents the status of a relationship between two users.
 *
 * This enum is used to categorize the type of social connection or interaction between the current
 * user and another user in the system.
 */
@Serializable
enum class RelationshipStatus {
  /** Mutual friendship - both users have accepted the friend request. */
  Friend,

  /** Friend request sent - the current user has sent a request to the other user. */
  Sent,

  /** Friend request pending - the other user has sent a request to the current user. */
  Pending,

  /** Blocked - the current user has blocked the other user. */
  Blocked,

  /** Deleted - removes the relationship with the other user altogether */
  Delete,
}

/**
 * Represents a relationship between the current user and another user.
 *
 * This data class pairs a user's UID with the status of their relationship to the current account.
 * It is used for serialization to/from Firestore.
 *
 * @property uid The unique identifier of the other user in the relationship.
 * @property status The type of relationship (Friend, Sent, Pending, or Blocked). Defaults to Friend
 *   if not specified.
 */
@Serializable
data class Relationship(
    val uid: String,
    val status: RelationshipStatus = RelationshipStatus.Friend
)

/**
 * Represents a user account stored in Firestore.
 *
 * This data class models the complete account structure including user profile information, social
 * relationships, and discussion metadata. It serves as the runtime representation of account data
 * throughout the application.
 *
 * @property uid Globally unique identifier of the account (Firestore document ID).
 * @property handle Unique human-readable identifier for the user (e.g., @username).
 * @property name Human-readable display name of the account.
 * @property email User's email address used to create the account.
 * @property previews Map of discussion previews keyed by discussion ID. Each preview stores
 *   lightweight metadata (e.g. last message, unread count) for discussions this account
 *   participates in.
 * @property photoUrl URL to the user's profile picture. Null if no profile picture is set.
 * @property description User's self-description or bio. Null if not provided.
 * @property shopOwner Indicates whether this account owns a board game shop.
 * @property spaceRenter Indicates whether this account rents out gaming spaces.
 * @property relationships Map of user relationships keyed by the other user's UID, with values
 *   indicating the relationship status from this account's perspective. Possible statuses include
 *   Friend (mutual friendship), Sent (friend request sent), Pending (friend request received), and
 *   Blocked (user has been blocked). An absent key indicates no relationship exists.
 */
data class Account(
    val uid: String,
    val handle: String,
    val name: String,
    val email: String,
    val previews: Map<String, DiscussionPreview> = emptyMap(),
    var photoUrl: String? = null,
    var description: String? = null,
    var shopOwner: Boolean = false,
    var spaceRenter: Boolean = false,
    val relationships: Map<String, RelationshipStatus> = emptyMap()
)

/**
 * Minimal serializable form of [Account] without the UID, used for Firestore storage.
 *
 * Firestore stores the UID as the document ID, so it is omitted from the stored object. This class
 * is optimized for serialization and contains only the fields that need to be persisted in the
 * database. The relationships are stored as a flat list rather than separate fields for friends,
 * sent, pending, and blocked.
 *
 * @property handle Unique human-readable identifier for the user. Defaults to empty string.
 * @property name Human-readable display name. Defaults to empty string.
 * @property email User's email address. Defaults to empty string.
 * @property photoUrl URL to the user's profile picture. Null if not set.
 * @property description User's self-description or bio. Null if not provided.
 * @property shopOwner Indicates whether this account owns a board game shop. Defaults to false.
 * @property spaceRenter Indicates whether this account rents out gaming spaces. Defaults to false.
 * @property relationships List of all relationships this account has with other users. Defaults to
 *   empty list.
 */
@Serializable
data class AccountNoUid(
    val handle: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val description: String? = null,
    val shopOwner: Boolean = false,
    val spaceRenter: Boolean = false,
    val relationships: List<Relationship> = emptyList()
)

/**
 * Reconstructs a full [Account] object from its Firestore representation.
 *
 * This function deserializes account data from Firestore by combining the document ID with the
 * stored [AccountNoUid] data. It transforms the list of [Relationship] objects from Firestore into
 * a map structure for efficient lookup by user ID.
 *
 * @param id The Firestore document ID (used as account UID).
 * @param accountNoUid The deserialized [AccountNoUid] data from Firestore.
 * @param previews Optional map of discussion preview data keyed by discussion ID. Each preview will
 *   be converted from [DiscussionPreviewNoUid] to [DiscussionPreview]. Defaults to empty map.
 * @param relationships List of all relationships this account has. This will be converted into a
 *   map keyed by the other user's UID with the relationship status as the value. Defaults to empty
 *   list.
 * @return A fully constructed [Account] instance with relationships map populated.
 */
fun fromNoUid(
    id: String,
    accountNoUid: AccountNoUid,
    previews: Map<String, DiscussionPreviewNoUid> = emptyMap(),
    relationships: List<Relationship> = emptyList()
): Account {
  // Convert relationship list to map for efficient lookup
  val mappedRelationships = mutableMapOf<String, RelationshipStatus>()
  for (rel in relationships) {
    mappedRelationships += rel.uid to rel.status
  }

  return Account(
      id,
      accountNoUid.handle,
      accountNoUid.name,
      email = accountNoUid.email,
      previews.mapValues { (uid, preview) -> fromNoUid(uid, preview) },
      photoUrl = accountNoUid.photoUrl,
      description = accountNoUid.description,
      shopOwner = accountNoUid.shopOwner,
      spaceRenter = accountNoUid.spaceRenter,
      relationships = mappedRelationships)
}
