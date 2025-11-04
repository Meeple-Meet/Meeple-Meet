package com.github.meeplemeet.model.auth

import com.github.meeplemeet.model.discussions.DiscussionPreview
import com.github.meeplemeet.model.discussions.DiscussionPreviewNoUid
import com.github.meeplemeet.model.discussions.fromNoUid
import kotlinx.serialization.Serializable

/**
 * Represents a user account stored in Firestore.
 *
 * @property uid Globally unique identifier of the account (Firestore document ID).
 * @property handle unique human-readable identifier for the user
 * @property name Human-readable name of the account.
 * @property email User's email used to create the account
 * @property previews Map of discussion previews keyed by discussion ID. Each preview stores
 *   lightweight metadata (e.g. last message, unread count) for discussions this account
 *   participates in.
 * @property photoUrl User's profile picture
 * @property description A user's description about himself
 * @property isShopOwner Tells if this account is a shop owner
 * @property isSpaceRenter Tells if this account is a space renter
 */
data class Account(
    val uid: String,
    val handle: String,
    val name: String,
    val email: String,
    val previews: Map<String, DiscussionPreview> = emptyMap(),
    var photoUrl: String? = null,
    var description: String? = null,
    var isShopOwner: Boolean = false,
    var isSpaceRenter: Boolean = false
)

/**
 * Minimal serializable form of [Account] without the UID, used for Firestore storage.
 *
 * Firestore stores the UID as the document ID, so it is omitted from the stored object.
 */
@Serializable
data class AccountNoUid(
    val handle: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val description: String? = null,
    val isShopOwner: Boolean = false,
    val isSpaceRenter: Boolean = false
)

/**
 * Reconstructs a full [Account] object from its Firestore representation.
 *
 * @param id The Firestore document ID (used as account UID).
 * @param accountNoUid The deserialized [AccountNoUid] data from Firestore.
 * @param previews Optional map of discussion preview data keyed by discussion ID.
 * @return A fully constructed [Account] instance.
 */
fun fromNoUid(
    id: String,
    accountNoUid: AccountNoUid,
    previews: Map<String, DiscussionPreviewNoUid> = emptyMap()
): Account =
    Account(
        id,
        accountNoUid.handle,
        accountNoUid.name,
        email = accountNoUid.email,
        previews.mapValues { (uid, preview) -> fromNoUid(uid, preview) },
        photoUrl = accountNoUid.photoUrl,
        description = accountNoUid.description,
        isShopOwner = accountNoUid.isShopOwner,
        isSpaceRenter = accountNoUid.isSpaceRenter,
    )
