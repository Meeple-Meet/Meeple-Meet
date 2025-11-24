package com.github.meeplemeet.model.account

// Claude Code generated the documentation

import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.FirestoreRepository
import com.github.meeplemeet.model.discussions.DiscussionPreviewNoUid
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing user account data in Firestore.
 *
 * This repository handles CRUD operations for account documents and their associated discussion
 * previews. It extends [FirestoreRepository] to inherit common Firestore functionality.
 *
 * Each account document contains:
 * - Basic profile information (handle, name, email, photo)
 * - Role flags (shop owner, space renter)
 * - A subcollection of discussion previews
 * - A subcollection of relationships (friends, sent requests, pending requests, blocked users)
 *
 * ## Relationship Management
 *
 * Relationships between users are stored bidirectionally in Firestore as a subcollection of
 * relationship documents. When user A has a relationship with user B, both accounts store a
 * reference to each other with potentially different statuses:
 * - Friend request sent: A stores "Sent", B stores "Pending"
 * - Accepted friendship: Both A and B store "Friend"
 * - User blocked: A stores "Blocked", B has no relationship document (deleted)
 *
 * In the [Account] runtime model, these relationships are loaded into a map keyed by the other
 * user's UID for efficient lookup
 */
class AccountRepository : FirestoreRepository("accounts") {
  companion object {
    /** Firestore field name for relationship status */
    private const val FIELD_STATUS = "status"
  }

  /**
   * Returns a reference to the relationships subcollection for a given account.
   *
   * @param uid The account ID whose relationships subcollection to access
   * @return A CollectionReference to the relationships subcollection
   */
  private fun relationships(uid: String) =
      collection.document(uid).collection(Account::relationships.name)

  /**
   * Extracts discussion previews from a Firestore query snapshot.
   *
   * @param documents The list of documents from the previews subcollection
   * @return A map of discussion IDs to their preview objects
   */
  private fun extractPreviews(
      documents: List<DocumentSnapshot>
  ): Map<String, DiscussionPreviewNoUid> {
    return documents.associate { doc ->
      doc.id to (doc.toObject(DiscussionPreviewNoUid::class.java) ?: DiscussionPreviewNoUid())
    }
  }

  /**
   * Extracts relationships from a Firestore query snapshot.
   *
   * Parses the relationship status from each document, falling back to Friend status if the status
   * is invalid or missing.
   *
   * @param documents The list of documents from the relationships subcollection
   * @return A list of Relationship objects
   */
  private fun extractRelationships(documents: List<DocumentSnapshot>): List<Relationship> {
    return documents.map { doc ->
      val statusString = doc.getString(FIELD_STATUS)
      val status =
          statusString?.let {
            try {
              RelationshipStatus.valueOf(it)
            } catch (_: IllegalArgumentException) {
              RelationshipStatus.FRIEND
            }
          } ?: RelationshipStatus.FRIEND
      Relationship(doc.id, status)
    }
  }

  /**
   * Creates a new account document in Firestore.
   *
   * @param userHandle The unique handle for the user (also used as the document ID)
   * @param name The user's display name
   * @param email The user's email address
   * @param photoUrl The URL of the user's profile photo, or null if not available
   * @return The created Account object
   */
  suspend fun createAccount(
      userHandle: String,
      name: String,
      email: String,
      photoUrl: String?
  ): Account {
    val account =
        Account(
            userHandle, userHandle, name, email = email, photoUrl = photoUrl, description = null)
    val accountNoUid = AccountNoUid(userHandle, name, email, photoUrl, description = null)
    collection.document(account.uid).set(accountNoUid).await()
    return account
  }

  /**
   * Retrieves an account and its associated discussion previews and relationships by ID.
   *
   * This method fetches the account document, its discussion previews subcollection, and its
   * relationships subcollection, then combines them into a single [Account] object. The
   * relationships are converted from a list to a map for efficient lookup.
   *
   * @param id The account ID to retrieve
   * @return The Account object with populated discussion previews and relationships map
   * @throws AccountNotFoundException if the account does not exist
   */
  suspend fun getAccount(id: String): Account {
    val snapshot = collection.document(id).get().await()
    val account = snapshot.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()

    val previewsSnap = collection.document(id).collection("previews").get().await()
    val previews = extractPreviews(previewsSnap.documents)

    val relationshipsSnap = relationships(id).get().await()
    val relationships = extractRelationships(relationshipsSnap.documents)

    return fromNoUid(id, account, previews, relationships)
  }

  /**
   * Retrieves multiple accounts and their discussion previews and relationships concurrently.
   *
   * This method fetches all accounts in parallel using coroutines for optimal performance by
   * calling [getAccount] for each ID concurrently.
   *
   * @param ids List of account IDs to retrieve
   * @return List of Account objects corresponding to the provided IDs, each with populated
   *   discussion previews and relationships map
   * @throws AccountNotFoundException if any of the accounts do not exist
   */
  suspend fun getAccounts(ids: List<String>): List<Account> = coroutineScope {
    ids.map { id -> async { getAccount(id) } }.awaitAll()
  }

  /**
   * Updates the display name of an account.
   *
   * @param id The account ID to update
   * @param name The new display name
   */
  suspend fun setAccountName(id: String, name: String) {
    collection.document(id).update(Account::name.name, name).await()
  }

  /**
   * Update account roles in the repository (space renter & shop owner)
   *
   * @param id Account id's to update it's roles
   * @param isShopOwner Boolean for the role Shop Owner
   * @param isSpaceRenter Boolean for the role Space Renter
   */
  suspend fun setAccountRole(id: String, isShopOwner: Boolean?, isSpaceRenter: Boolean?) {
    val updates = mutableMapOf<String, Any>()
    isShopOwner?.let { updates[AccountNoUid::shopOwner.name] = isShopOwner }
    isSpaceRenter?.let { updates[AccountNoUid::spaceRenter.name] = isSpaceRenter }
    collection.document(id).update(updates).await()
  }

  /**
   * Deletes an account document from Firestore.
   *
   * @param id The account ID to delete
   */
  suspend fun deleteAccount(id: String) {
    collection.document(id).delete().await()
  }

  /**
   * Sends a friend request from one user to another.
   *
   * Creates a bidirectional relationship where the sender has status "Sent" and the recipient has
   * status "Pending". This is executed atomically using a Firestore batch.
   *
   * @param accountId The ID of the account sending the friend request
   * @param otherId The ID of the account receiving the friend request
   */
  suspend fun sendFriendRequest(accountId: String, otherId: String) {
    setFriendStatus(accountId, otherId, RelationshipStatus.SENT, RelationshipStatus.PENDING)
  }

  /**
   * Accepts a pending friend request, establishing a mutual friendship.
   *
   * Updates both users' relationship status to "Friend". This should be called by the user who
   * received the original friend request. This is executed atomically using a Firestore batch.
   *
   * @param accountId The ID of the account accepting the friend request
   * @param otherId The ID of the account whose friend request is being accepted
   */
  suspend fun acceptFriendRequest(accountId: String, otherId: String) {
    setFriendStatus(accountId, otherId, RelationshipStatus.FRIEND, RelationshipStatus.FRIEND)
  }

  /**
   * Blocks a user, preventing any further interaction.
   *
   * Sets the blocking user's relationship status to "Blocked" and removes the relationship document
   * from the blocked user's account. This is a one-way operation - the blocked user will have no
   * record of the relationship. This is executed atomically using a Firestore batch.
   *
   * @param accountId The ID of the account performing the block action
   * @param otherId The ID of the account being blocked
   */
  suspend fun blockUser(accountId: String, otherId: String) {
    db.runTransaction { tx ->
          val accountRef = relationships(accountId).document(otherId)
          val otherRef = relationships(otherId).document(accountId)

          val bSnap = tx[otherRef]
          val bStatus = bSnap.getString("status")

          tx[accountRef] = mapOf(FIELD_STATUS to RelationshipStatus.BLOCKED)

          if (bStatus != RelationshipStatus.BLOCKED.name) {
            tx.delete(otherRef)
          }
        }
        .await()
  }

  /**
   * Unblocks a previously blocked user.
   *
   * Removes the "Blocked" relationship entry from the unblocking user's account. This restores the
   * state to "no relationship" and does not modify the other user's data.
   *
   * @param accountId The ID of the account performing the unblock action
   * @param otherId The ID of the account being unblocked
   */
  suspend fun unblockUser(accountId: String, otherId: String) {
    relationships(accountId).document(otherId).delete().await()
  }

  /**
   * Resets a relationship between two users, removing all connection data.
   *
   * This method can be used for multiple purposes:
   * - Unblocking a user (removes the "Blocked" status)
   * - Canceling a sent friend request (removes the "Sent"/"Pending" status)
   * - Denying a received friend request (removes the "Pending"/"Sent" status)
   * - Removing an existing friend (removes the "Friend" status)
   *
   * This deletes the relationship documents from both users' accounts, returning them to a neutral
   * state with no connection. This is executed atomically using a Firestore batch.
   *
   * @param accountId The ID of the first account in the relationship
   * @param friendId The ID of the second account in the relationship
   */
  suspend fun resetRelationship(accountId: String, friendId: String) {
    setFriendStatus(accountId, friendId, null, null)
  }

  /**
   * Internal helper method to set relationship statuses bidirectionally in a single transaction.
   *
   * This method atomically updates the relationship documents for both users involved. It uses
   * Firestore batched writes to ensure both sides of the relationship are updated together,
   * preventing inconsistent states.
   *
   * For each user, the method either:
   * - Creates/updates a relationship document with the specified status
   * - Deletes the relationship document if the status is null
   *
   * @param accountId The ID of the first account in the relationship
   * @param friendId The ID of the second account in the relationship
   * @param accountStatus The relationship status to set for the first account's perspective. Use
   *   null to remove the relationship document.
   * @param friendStatus The relationship status to set for the second account's perspective. Use
   *   null to remove the relationship document.
   */
  private suspend fun setFriendStatus(
      accountId: String,
      friendId: String,
      accountStatus: RelationshipStatus?,
      friendStatus: RelationshipStatus?
  ) {
    val batch = db.batch()

    val accountRef = relationships(accountId).document(friendId)
    val otherRef = relationships(friendId).document(accountId)

    // Update or delete the first user's relationship document
    if (accountStatus == null) batch.delete(accountRef)
    else batch[accountRef] = mapOf(FIELD_STATUS to accountStatus)

    // Update or delete the second user's relationship document
    if (friendStatus == null) batch.delete(otherRef)
    else batch[otherRef] = mapOf(FIELD_STATUS to friendStatus)

    batch.commit().await()
  }

  /**
   * Creates a Flow that listens for real-time changes to an account, its discussion previews, and
   * its relationships.
   *
   * This method sets up Firestore listeners that emit updated account data whenever changes occur
   * in the account document, previews subcollection, or relationships subcollection. The flow emits
   * a complete Account object including all discussion previews and the relationships map.
   *
   * The listeners are automatically cleaned up when the flow is cancelled.
   *
   * @param accountId The ID of the account to listen to
   * @return A Flow that emits Account objects when changes are detected in the account, its
   *   previews, or its relationships
   */
  fun listenAccount(accountId: String): Flow<Account> = callbackFlow {
    val accountRef = collection.document(accountId)
    val listeners = AccountListeners()

    val accountListener =
        accountRef.addSnapshotListener { snapshot, e ->
          if (e != null) {
            close(e)
            return@addSnapshotListener
          }

          val accountNoUid = snapshot?.toObject(AccountNoUid::class.java)
          if (accountNoUid != null) {
            listeners.updateAccount(accountId, accountNoUid, accountRef, this)
          }
        }

    awaitClose { listeners.removeAll(accountListener) }
  }

  /**
   * Helper class to manage account-related Firestore listeners and coordinate data updates.
   *
   * This class encapsulates the logic for listening to previews and relationships subcollections,
   * caching their data, and emitting complete Account objects when all data is available.
   */
  private inner class AccountListeners {
    private var previewsListener: ListenerRegistration? = null
    private var relationshipsListener: ListenerRegistration? = null
    private var cachedPreviews: Map<String, DiscussionPreviewNoUid>? = null
    private var cachedRelationships: List<Relationship>? = null

    /**
     * Updates the account data and sets up listeners for its subcollections.
     *
     * @param accountId The ID of the account
     * @param accountNoUid The account data without UID
     * @param accountRef Reference to the account document
     * @param flow The callback flow to emit updates to
     */
    fun updateAccount(
        accountId: String,
        accountNoUid: AccountNoUid,
        accountRef: com.google.firebase.firestore.DocumentReference,
        flow: kotlinx.coroutines.channels.ProducerScope<Account>
    ) {
      removeSubcollectionListeners()
      resetCache()

      previewsListener = listenToPreviews(accountRef, flow, accountId, accountNoUid)
      relationshipsListener = listenToRelationships(accountId, flow, accountNoUid)
    }

    /**
     * Sets up a Firestore listener for the discussion previews subcollection.
     *
     * When previews data changes, it updates the cached previews and attempts to emit a complete
     * Account object if all required data is available.
     *
     * @param accountRef Reference to the account document
     * @param flow The callback flow to emit updates to
     * @param accountId The ID of the account
     * @param accountNoUid The account data without UID
     * @return A ListenerRegistration that can be used to remove the listener
     */
    private fun listenToPreviews(
        accountRef: com.google.firebase.firestore.DocumentReference,
        flow: kotlinx.coroutines.channels.ProducerScope<Account>,
        accountId: String,
        accountNoUid: AccountNoUid
    ): ListenerRegistration {
      return accountRef.collection(Account::previews.name).addSnapshotListener { qs, e ->
        if (e != null) {
          flow.close(e)
          return@addSnapshotListener
        }
        if (qs != null) {
          cachedPreviews = extractPreviews(qs.documents)
          tryEmitAccount(accountId, accountNoUid, flow)
        }
      }
    }

    /**
     * Sets up a Firestore listener for the relationships subcollection.
     *
     * When relationships data changes, it updates the cached relationships and attempts to emit a
     * complete Account object if all required data is available.
     *
     * @param accountId The ID of the account
     * @param flow The callback flow to emit updates to
     * @param accountNoUid The account data without UID
     * @return A ListenerRegistration that can be used to remove the listener
     */
    private fun listenToRelationships(
        accountId: String,
        flow: kotlinx.coroutines.channels.ProducerScope<Account>,
        accountNoUid: AccountNoUid
    ): ListenerRegistration {
      return relationships(accountId).addSnapshotListener { qs, e ->
        if (e != null) {
          flow.close(e)
          return@addSnapshotListener
        }
        if (qs != null) {
          cachedRelationships = extractRelationships(qs.documents)
          tryEmitAccount(accountId, accountNoUid, flow)
        }
      }
    }

    /**
     * Attempts to emit a complete Account object if all required data is cached.
     *
     * This method checks if both previews and relationships data are available. If both are
     * present, it constructs a complete Account object and emits it to the flow. This ensures that
     * partial data is never emitted.
     *
     * @param accountId The ID of the account
     * @param accountNoUid The account data without UID
     * @param flow The callback flow to emit the complete Account to
     */
    private fun tryEmitAccount(
        accountId: String,
        accountNoUid: AccountNoUid,
        flow: kotlinx.coroutines.channels.ProducerScope<Account>
    ) {
      val previews = cachedPreviews
      val relationships = cachedRelationships
      if (previews != null && relationships != null) {
        flow.trySend(fromNoUid(accountId, accountNoUid, previews, relationships))
      }
    }

    /**
     * Removes all active subcollection listeners (previews and relationships).
     *
     * This method should be called before setting up new listeners to prevent memory leaks and
     * duplicate listener registrations.
     */
    private fun removeSubcollectionListeners() {
      previewsListener?.remove()
      relationshipsListener?.remove()
    }

    /**
     * Resets the cached data for previews and relationships to null.
     *
     * This ensures that partial data from previous listeners is not accidentally combined with new
     * data, preventing inconsistent Account objects from being emitted.
     */
    private fun resetCache() {
      cachedPreviews = null
      cachedRelationships = null
    }

    /**
     * Removes all listeners including the main account listener and subcollection listeners.
     *
     * This method is called when the flow is closed to ensure proper cleanup of all Firestore
     * listeners and prevent memory leaks.
     *
     * @param accountListener The main account document listener to remove
     */
    fun removeAll(accountListener: ListenerRegistration) {
      accountListener.remove()
      removeSubcollectionListeners()
    }
  }
}
