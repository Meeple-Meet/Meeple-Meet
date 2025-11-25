package com.github.meeplemeet.model.account

// Claude Code generated the documentation

import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.FirestoreRepository
import com.github.meeplemeet.model.discussions.Discussion
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

/** Repository for managing user account data in Firestore. */
class AccountRepository : FirestoreRepository("accounts") {

  companion object {
    /** Firestore field name for relationship status */
    private const val FIELD_STATUS = "status"
  }

  /** Subcollection for relationships (friends, requests, blocked) */
  private fun relationships(uid: String) =
      collection.document(uid).collection(Account::relationships.name)

  /** Subcollection for notifications */
  private fun notifications(uid: String) =
      collection.document(uid).collection(Account::notifications.name)

  // ─────────────────────────────────────────────────────────────────────────────
  // Extract helpers
  // ─────────────────────────────────────────────────────────────────────────────

  private fun extractPreviews(
      documents: List<DocumentSnapshot>
  ): Map<String, DiscussionPreviewNoUid> {
    return documents.associate { doc ->
      doc.id to (doc.toObject(DiscussionPreviewNoUid::class.java) ?: DiscussionPreviewNoUid())
    }
  }

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

  /** Extracts notifications from a Firestore query snapshot. */
  private fun extractNotifications(
      documents: List<DocumentSnapshot>,
      receiverId: String
  ): List<Notification> {
    return documents.mapNotNull { doc ->
      val noUid = doc.toObject(NotificationNoUid::class.java)
      noUid?.let { Notification().fromNoUid(doc.id, receiverId, it) }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // CRUD
  // ─────────────────────────────────────────────────────────────────────────────

  suspend fun createAccount(
      userHandle: String,
      name: String,
      email: String,
      photoUrl: String?
  ): Account {
    val account =
        Account(
            uid = userHandle,
            handle = userHandle,
            name = name,
            email = email,
            photoUrl = photoUrl,
            description = null)
    val accountNoUid = AccountNoUid(userHandle, name, email, photoUrl, description = null)
    collection.document(account.uid).set(accountNoUid).await()
    return account
  }

  /** Old entry point kept for compatibility – always returns full data. */
  suspend fun getAccount(id: String): Account = getAccount(id, getAllData = true)

  /**
   * New entry point with `getAllData` flag used by tests.
   *
   * When `getAllData == false`, notifications are **not** loaded and `Account.notifications` is
   * empty.
   */
  suspend fun getAccount(id: String, getAllData: Boolean): Account {
    val snapshot = collection.document(id).get().await()
    val accountNoUid =
        snapshot.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()

    val previewsSnap = collection.document(id).collection(Account::previews.name).get().await()
    val previews = extractPreviews(previewsSnap.documents)

    val relationshipsSnap = relationships(id).get().await()
    val relationships = extractRelationships(relationshipsSnap.documents)

    val notifications =
        if (getAllData) {
          val notifSnap = notifications(id).get().await()
          extractNotifications(notifSnap.documents, id)
        } else {
          emptyList()
        }

    return fromNoUid(id, accountNoUid, previews, relationships, notifications)
  }

  suspend fun getAccounts(ids: List<String>): List<Account> = getAccounts(ids, getAllData = true)

  suspend fun getAccounts(ids: List<String>, getAllData: Boolean): List<Account> = coroutineScope {
    ids.map { id -> async { getAccount(id, getAllData) } }.awaitAll()
  }

  suspend fun setAccountName(id: String, name: String) {
    collection.document(id).update(Account::name.name, name).await()
  }

  suspend fun setAccountRole(id: String, isShopOwner: Boolean?, isSpaceRenter: Boolean?) {
    val updates = mutableMapOf<String, Any>()
    isShopOwner?.let { updates[AccountNoUid::shopOwner.name] = it }
    isSpaceRenter?.let { updates[AccountNoUid::spaceRenter.name] = it }
    collection.document(id).update(updates).await()
  }

  suspend fun setAccountDescription(id: String, description: String) {
    collection.document(id).update(Account::description.name, description).await()
  }

  suspend fun setAccountPhotoUrl(id: String, photoUrl: String) {
    collection.document(id).update(Account::photoUrl.name, photoUrl).await()
  }

  suspend fun setAccountEmail(id: String, email: String) {
    collection.document(id).update(Account::email.name, email).await()
  }

  suspend fun deleteAccount(id: String) {
    collection.document(id).delete().await()
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Relationships
  // ─────────────────────────────────────────────────────────────────────────────

  suspend fun sendFriendRequest(accountId: String, otherId: String) {
    setFriendStatus(accountId, otherId, RelationshipStatus.SENT, RelationshipStatus.PENDING)
  }

  suspend fun acceptFriendRequest(accountId: String, otherId: String) {
    setFriendStatus(accountId, otherId, RelationshipStatus.FRIEND, RelationshipStatus.FRIEND)
  }

  suspend fun blockUser(accountId: String, otherId: String) {
    db.runTransaction { tx ->
          val accountRef = relationships(accountId).document(otherId)
          val otherRef = relationships(otherId).document(accountId)

          val bSnap = tx[otherRef]
          val bStatus = bSnap.getString(FIELD_STATUS)

          tx[accountRef] = mapOf(FIELD_STATUS to RelationshipStatus.BLOCKED)

          if (bStatus != RelationshipStatus.BLOCKED.name) {
            tx.delete(otherRef)
          }
        }
        .await()
  }

  suspend fun unblockUser(accountId: String, otherId: String) {
    relationships(accountId).document(otherId).delete().await()
  }

  suspend fun resetRelationship(accountId: String, friendId: String) {
    setFriendStatus(accountId, friendId, null, null)
  }

  private suspend fun setFriendStatus(
      accountId: String,
      friendId: String,
      accountStatus: RelationshipStatus?,
      friendStatus: RelationshipStatus?
  ) {
    val batch = db.batch()

    val accountRef = relationships(accountId).document(friendId)
    val otherRef = relationships(friendId).document(accountId)

    if (accountStatus == null) batch.delete(accountRef)
    else batch[accountRef] = mapOf(FIELD_STATUS to accountStatus)

    if (friendStatus == null) batch.delete(otherRef)
    else batch[otherRef] = mapOf(FIELD_STATUS to friendStatus)

    batch.commit().await()
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Notifications
  // ─────────────────────────────────────────────────────────────────────────────

  suspend fun sendFriendRequestNotification(receiverId: String, sender: Account) {
    sendNotification(receiverId, sender.uid, NotificationType.FRIEND_REQUEST)
  }

  suspend fun sendJoinDiscussionNotification(receiverId: String, discussion: Discussion) {
    sendNotification(receiverId, discussion.uid, NotificationType.JOIN_DISCUSSION)
  }

  suspend fun sendJoinSessionNotification(receiverId: String, discussion: Discussion) {
    sendNotification(receiverId, discussion.uid, NotificationType.JOIN_SESSION)
  }

  suspend fun readNotification(accountId: String, notificationId: String) {
    notifications(accountId)
        .document(notificationId)
        .update(NotificationNoUid::read.name, true)
        .await()
  }

  suspend fun deleteNotification(accountId: String, notificationId: String) {
    notifications(accountId).document(notificationId).delete().await()
  }

  private suspend fun sendNotification(
      receiverId: String,
      senderOrDiscussionId: String,
      type: NotificationType
  ) {
    val uid = notifications(receiverId).document().id
    val payload = NotificationNoUid(senderOrDiscussionId = senderOrDiscussionId, type = type)
    notifications(receiverId).document(uid).set(payload).await()
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Listen account (with notifications)
  // ─────────────────────────────────────────────────────────────────────────────

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

  private inner class AccountListeners {
    private var previewsListener: ListenerRegistration? = null
    private var relationshipsListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null

    private var cachedPreviews: Map<String, DiscussionPreviewNoUid>? = null
    private var cachedRelationships: List<Relationship>? = null
    private var cachedNotifications: List<Notification>? = null

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
      notificationsListener = listenToNotifications(accountId, flow, accountNoUid)
    }

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

    private fun listenToNotifications(
        accountId: String,
        flow: kotlinx.coroutines.channels.ProducerScope<Account>,
        accountNoUid: AccountNoUid
    ): ListenerRegistration {
      return notifications(accountId).addSnapshotListener { qs, e ->
        if (e != null) {
          flow.close(e)
          return@addSnapshotListener
        }
        if (qs != null) {
          cachedNotifications = extractNotifications(qs.documents, accountId)
          tryEmitAccount(accountId, accountNoUid, flow)
        }
      }
    }

    private fun tryEmitAccount(
        accountId: String,
        accountNoUid: AccountNoUid,
        flow: kotlinx.coroutines.channels.ProducerScope<Account>
    ) {
      val previews = cachedPreviews
      val relationships = cachedRelationships
      val notifications = cachedNotifications
      if (previews != null && relationships != null && notifications != null) {
        flow.trySend(fromNoUid(accountId, accountNoUid, previews, relationships, notifications))
      }
    }

    private fun removeSubcollectionListeners() {
      previewsListener?.remove()
      relationshipsListener?.remove()
      notificationsListener?.remove()
    }

    private fun resetCache() {
      cachedPreviews = null
      cachedRelationships = null
      cachedNotifications = null
    }

    fun removeAll(accountListener: ListenerRegistration) {
      accountListener.remove()
      removeSubcollectionListeners()
    }
  }
}
