package com.github.meeplemeet.model.systems

import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.structures.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firestore data access layer for accounts, discussions, and messages. */
const val ACCOUNT_COLLECTION_PATH = "accounts"
const val DISCUSSIONS_COLLECTION_PATH = "discussions"

/**
 * Repository wrapping Firestore CRUD operations and snapshot listeners.
 *
 * Provides suspend functions for one-shot reads/writes and Flow-based listeners for real-time
 * updates.
 */
class FirestoreRepository(db: FirebaseFirestore) {
  private val accounts = db.collection(ACCOUNT_COLLECTION_PATH)
  private val discussions = db.collection(DISCUSSIONS_COLLECTION_PATH)

  private fun newDiscussionUID(): String = discussions.document().id

  private fun accountUID(): String = accounts.document().id // Normally Firebase.auth.uid

  /** Create a new discussion and store an empty preview for the creator. */
  suspend fun createDiscussion(
      name: String,
      description: String,
      creatorId: String,
      participants: List<String> = emptyList()
  ): Pair<Account, Discussion> {
    val discussion =
        Discussion(
            newDiscussionUID(),
            name,
            description,
            emptyList(),
            participants + creatorId,
            listOf(creatorId),
            Timestamp.now())
    discussions.document(discussion.uid).set(toNoUid(discussion)).await()

    accounts
        .document(creatorId)
        .collection(Account::previews.name)
        .document(discussion.uid)
        .set(DiscussionPreviewNoUid())
        .await()

    return Pair(getAccount(creatorId), discussion)
  }

  /** Retrieve a discussion document by ID. */
  suspend fun getDiscussion(id: String): Discussion {
    val snapshot = discussions.document(id).get().await()
    val discussion = snapshot.toObject(DiscussionNoUid::class.java)
    if (discussion != null) return fromNoUid(id, discussion)
    throw DiscussionNotFoundException("Discussion not found.")
  }

  /** Update a discussion's name. */
  suspend fun setDiscussionName(id: String, name: String): Discussion {
    discussions.document(id).update(Discussion::name.name, name).await()
    return getDiscussion(id)
  }

  /** Update a discussion's description. */
  suspend fun setDiscussionDescription(id: String, name: String): Discussion {
    discussions.document(id).update(Discussion::description.name, name).await()
    return getDiscussion(id)
  }

  /** Delete a discussion document. */
  suspend fun deleteDiscussion(id: String): Discussion {
    discussions.document(id).delete().await()
    return getDiscussion(id)
  }

  /** Add a user to the participants array. */
  suspend fun addUserToDiscussion(discussion: Discussion, userId: String): Discussion {
    discussions
        .document(discussion.uid)
        .update(Discussion::participants.name, FieldValue.arrayUnion(userId))
        .await()
    return getDiscussion(discussion.uid)
  }

  /** Add multiple users to the participants array. */
  suspend fun addUsersToDiscussion(discussion: Discussion, userIds: List<String>): Discussion {
    discussions
        .document(discussion.uid)
        .update(Discussion::participants.name, FieldValue.arrayUnion(*userIds.toTypedArray()))
        .await()
    return getDiscussion(discussion.uid)
  }

  /** Add a user as admin (and participant if missing). */
  suspend fun addAdminToDiscussion(discussion: Discussion, userId: String): Discussion {
    if (!discussion.participants.contains(userId)) addUserToDiscussion(discussion, userId)
    discussions
        .document(discussion.uid)
        .update(Discussion::admins.name, FieldValue.arrayUnion(userId))
        .await()
    return getDiscussion(discussion.uid)
  }

  /** Add multiple admins (and participants if missing). */
  suspend fun addAdminsToDiscussion(discussion: Discussion, adminIds: List<String>): Discussion {
    val current = discussion.participants.toSet()
    val newParticipants = adminIds.filterNot { it in current }
    if (newParticipants.isNotEmpty()) {
      discussions
          .document(discussion.uid)
          .update(
              Discussion::participants.name, FieldValue.arrayUnion(*newParticipants.toTypedArray()))
          .await()
    }

    val currentAdmins = discussion.admins.toSet()
    val newAdmins = adminIds.filterNot { it in currentAdmins }
    if (newAdmins.isNotEmpty()) {
      discussions
          .document(discussion.uid)
          .update(Discussion::admins.name, FieldValue.arrayUnion(*newAdmins.toTypedArray()))
          .await()
    }

    return getDiscussion(discussion.uid)
  }

  /**
   * Append a new message to the discussion and update unread counts in all participants' previews.
   */
  suspend fun sendMessageToDiscussion(
      discussion: Discussion,
      sender: Account,
      content: String
  ): Discussion {
    val message = Message(sender.uid, content)
    discussions
        .document(discussion.uid)
        .update(Discussion::messages.name, FieldValue.arrayUnion(message))
        .await()

    discussion.participants.forEach { userId ->
      val ref =
          accounts.document(userId).collection(Account::previews.name).document(discussion.uid)
      val snapshot = ref.get().await()
      val existing = snapshot.toObject(DiscussionPreviewNoUid::class.java)
      val nextCount = (existing?.unreadCount ?: 0) + 1 - (if (sender.uid == userId) 1 else 0)

      ref.set(
          DiscussionPreviewNoUid(message.content, message.senderId, message.createdAt, nextCount))
    }

    return getDiscussion(discussion.uid)
  }

  /** Create a new account document. */
  suspend fun createAccount(name: String): Account {
    val account = Account(accountUID(), name)
    accounts.document(account.uid).set(mapOf("name" to account.name)).await()
    return account
  }

  /** Retrieve an account and its discussion previews. */
  suspend fun getAccount(id: String): Account {
    val snapshot = accounts.document(id).get().await()
    val account =
        snapshot.toObject(AccountNoUid::class.java)
            ?: throw AccountNotFoundException("Account not found.")

    val previewsSnap = accounts.document(id).collection("previews").get().await()
    val previews: Map<String, DiscussionPreviewNoUid> =
        previewsSnap.documents.associate { doc ->
          doc.id to (doc.toObject(DiscussionPreviewNoUid::class.java)!!)
        }

    return fromNoUid(id, account, previews)
  }

  /** Convenience for current signed-in account. */
  suspend fun getCurrentAccount(): Account {
    return getAccount(accountUID())
  }

  /** Update account display name. */
  suspend fun setAccountName(id: String, name: String): Account {
    accounts.document(id).update(Account::name.name, name).await()
    return getAccount(id)
  }

  /** Delete an account document. */
  suspend fun deleteAccount(id: String) {
    accounts.document(id).delete().await()
  }

  /** Reset unread count for a given discussion for this account. */
  suspend fun readDiscussionMessages(
      accountId: String,
      discussionId: String,
      message: Message
  ): Account {
    val ref = accounts.document(accountId).collection(Account::previews.name).document(discussionId)
    val snapshot = ref.get().await()
    val existing = snapshot.toObject(DiscussionPreviewNoUid::class.java)

    if (existing != null) ref.set(existing.copy(unreadCount = 0))
    else ref.set(DiscussionPreviewNoUid(message.content, message.senderId, message.createdAt, 0))

    return getAccount(accountId)
  }

  /**
   * Listen for changes to a specific discussion document.
   *
   * Emits a new [Discussion] every time the Firestore snapshot updates.
   */
  fun listenDiscussion(discussionId: String): Flow<Discussion> = callbackFlow {
    val reg =
        discussions.document(discussionId).addSnapshotListener { snap, e ->
          if (e != null) {
            close(e)
            return@addSnapshotListener
          }
          if (snap != null && snap.exists()) {
            snap.toObject(DiscussionNoUid::class.java)?.let { trySend(fromNoUid(snap.id, it)) }
          }
        }
    awaitClose { reg.remove() }
  }

  /**
   * Listen for changes to all discussion previews for a given account.
   *
   * Emits a map keyed by discussion ID whenever any preview changes.
   */
  fun listenMyPreviews(accountId: String): Flow<Map<String, DiscussionPreview>> = callbackFlow {
    val reg =
        accounts.document(accountId).collection(Account::previews.name).addSnapshotListener { qs, e
          ->
          if (e != null) {
            close(e)
            return@addSnapshotListener
          }
          if (qs != null) {
            val m =
                qs.documents.associate { d ->
                  d.id to
                      fromNoUid(
                          d.id,
                          (d.toObject(DiscussionPreviewNoUid::class.java)
                              ?: DiscussionPreviewNoUid()))
                }
            trySend(m)
          }
        }
    awaitClose { reg.remove() }
  }
}
