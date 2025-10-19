package com.github.meeplemeet.model.repositories

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.FirebaseProvider.db
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.AccountNoUid
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.DiscussionNoUid
import com.github.meeplemeet.model.structures.DiscussionPreviewNoUid
import com.github.meeplemeet.model.structures.Message
import com.github.meeplemeet.model.structures.fromNoUid
import com.github.meeplemeet.model.structures.toNoUid
import com.github.meeplemeet.model.structures.toPreview
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
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
class FirestoreRepository(db: FirebaseFirestore = FirebaseProvider.db) {
  private val accounts = db.collection(ACCOUNT_COLLECTION_PATH)
  private val discussions = db.collection(DISCUSSIONS_COLLECTION_PATH)

  private fun newDiscussionUID(): String = discussions.document().id

  /** Create a new discussion and store an empty preview for the creator. */
  suspend fun createDiscussion(
      name: String,
      description: String,
      creatorId: String,
      participants: List<String> = emptyList()
  ): Discussion {
    val discussion =
        Discussion(
            newDiscussionUID(),
            creatorId,
            name,
            description,
            emptyList(),
            participants + creatorId,
            listOf(creatorId),
            Timestamp.now(),
            null)

    val batch = db.batch()
    batch.set(discussions.document(discussion.uid), toNoUid(discussion))
    (participants + creatorId).forEach { id ->
      val ref = accounts.document(id).collection(Account::previews.name).document(discussion.uid)
      batch.set(ref, DiscussionPreviewNoUid())
    }
    batch.commit().await()

    return discussion
  }

  /** Retrieve a discussion document by ID. */
  suspend fun getDiscussion(id: String): Discussion {
    val snapshot = discussions.document(id).get().await()
    val discussion = snapshot.toObject(DiscussionNoUid::class.java)
    if (discussion != null) return fromNoUid(id, discussion)
    throw DiscussionNotFoundException()
  }

  /** Update a discussion's name. */
  suspend fun setDiscussionName(id: String, name: String) {
    discussions.document(id).update(Discussion::name.name, name).await()
  }

  /** Update a discussion's description. */
  suspend fun setDiscussionDescription(id: String, description: String) {
    discussions.document(id).update(Discussion::description.name, description).await()
  }

  /** Delete a discussion document. */
  suspend fun deleteDiscussion(discussion: Discussion) {
    val batch = db.batch()
    batch.delete(discussions.document(discussion.uid))
    discussion.participants.forEach { id ->
      val ref = accounts.document(id).collection(Account::previews.name).document(discussion.uid)
      batch.delete(ref)
    }
    batch.commit().await()
  }

  /** Add a user to the participants array. */
  suspend fun addUserToDiscussion(discussion: Discussion, userId: String) {
    discussions
        .document(discussion.uid)
        .update(Discussion::participants.name, FieldValue.arrayUnion(userId))
        .await()
    accounts
        .document(userId)
        .collection(Account::previews.name)
        .document(discussion.uid)
        .set(toPreview(discussion))
        .await()
  }

  /** Remove a user from the participants and admins array */
  suspend fun removeUserFromDiscussion(discussion: Discussion, userId: String) {
    discussions
        .document(discussion.uid)
        .update(
            Discussion::participants.name,
            FieldValue.arrayRemove(userId),
            Discussion::admins.name,
            FieldValue.arrayRemove(userId))
        .await()
    accounts
        .document(userId)
        .collection(Account::previews.name)
        .document(discussion.uid)
        .delete()
        .await()
  }

  /** Add multiple users to the participants array. */
  suspend fun addUsersToDiscussion(discussion: Discussion, userIds: List<String>) {
    discussions
        .document(discussion.uid)
        .update(Discussion::participants.name, FieldValue.arrayUnion(*userIds.toTypedArray()))
        .await()
    val batch = db.batch()
    userIds.forEach { id ->
      val ref = accounts.document(id).collection(Account::previews.name).document(discussion.uid)
      batch.set(ref, toPreview(discussion))
    }
    batch.commit().await()
  }

  /** Remove multiple users from the participants and admins array. */
  suspend fun removeUsersFromDiscussion(discussion: Discussion, userIds: List<String>) {
    discussions
        .document(discussion.uid)
        .update(
            Discussion::participants.name,
            FieldValue.arrayRemove(*userIds.toTypedArray()),
            Discussion::admins.name,
            FieldValue.arrayRemove(*userIds.toTypedArray()))
        .await()
    val batch = db.batch()
    userIds.forEach { id ->
      val ref = accounts.document(id).collection(Account::previews.name).document(discussion.uid)
      batch.delete(ref)
    }
    batch.commit().await()
  }

  /** Add a user as admin (and participant if missing). */
  suspend fun addAdminToDiscussion(discussion: Discussion, userId: String) {
    if (!discussion.participants.contains(userId)) addUserToDiscussion(discussion, userId)
    discussions
        .document(discussion.uid)
        .update(Discussion::admins.name, FieldValue.arrayUnion(userId))
        .await()
  }

  /** Remove a user from the admins array */
  suspend fun removeAdminFromDiscussion(discussion: Discussion, userId: String) {
    discussions
        .document(discussion.uid)
        .update(Discussion::admins.name, FieldValue.arrayRemove(userId))
        .await()
  }

  /** Add multiple admins (and participants if missing). */
  suspend fun addAdminsToDiscussion(discussion: Discussion, adminIds: List<String>) {
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
  }

  /** Remove multiple users from the admins array. */
  suspend fun removeAdminsFromDiscussion(discussion: Discussion, userIds: List<String>) {
    discussions
        .document(discussion.uid)
        .update(Discussion::admins.name, FieldValue.arrayRemove(*userIds.toTypedArray()))
        .await()
  }

  /**
   * Append a new message to the discussion and update unread counts in all participants' previews.
   */
  suspend fun sendMessageToDiscussion(discussion: Discussion, sender: Account, content: String) {
    val message = Message(sender.uid, content)
    val batch = FirebaseFirestore.getInstance().batch()

    // Append message
    batch.update(
        discussions.document(discussion.uid),
        Discussion::messages.name,
        FieldValue.arrayUnion(message))

    // Update previews for all participants
    discussion.participants.forEach { userId ->
      val ref =
          accounts.document(userId).collection(Account::previews.name).document(discussion.uid)
      val unreadCountValue = if (userId == sender.uid) 0 else FieldValue.increment(1)
      batch.set(
          ref,
          mapOf(
              "lastMessage" to message.content,
              "lastMessageSender" to message.senderId,
              "lastMessageAt" to message.createdAt,
              "unreadCount" to unreadCountValue),
          SetOptions.merge())
    }

    batch.commit().await()
  }

  /** Create a new account document. */
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
    accounts.document(account.uid).set(accountNoUid).await()
    return account
  }

  /** Retrieve an account and its discussion previews. */
  suspend fun getAccount(id: String): Account {
    val snapshot = accounts.document(id).get().await()
    val account = snapshot.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()

    val previewsSnap = accounts.document(id).collection("previews").get().await()
    val previews: Map<String, DiscussionPreviewNoUid> =
        previewsSnap.documents.associate { doc ->
          doc.id to (doc.toObject(DiscussionPreviewNoUid::class.java)!!)
        }

    return fromNoUid(id, account, previews)
  }

  /** Retrieve an account and its discussion previews. */
  suspend fun getAccounts(ids: List<String>): List<Account> = coroutineScope {
    ids.map { id ->
          async {
            val accountSnap = accounts.document(id).get().await()
            val account =
                accountSnap.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()

            val previewsSnap = accounts.document(id).collection("previews").get().await()
            val previews =
                previewsSnap.documents.associate { doc ->
                  doc.id to (doc.toObject(DiscussionPreviewNoUid::class.java)!!)
                }

            fromNoUid(id, account, previews)
          }
        }
        .awaitAll()
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
  fun listenAccount(accountId: String): Flow<Account> = callbackFlow {
    val accountRef = accounts.document(accountId)

    var previewsListener: ListenerRegistration? = null

    val accountListener =
        accountRef.addSnapshotListener { snapshot, e ->
          if (e != null) {
            close(e)
            return@addSnapshotListener
          }
          if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

          val accountNoUid = snapshot.toObject(AccountNoUid::class.java) ?: AccountNoUid()

          // Remove old previews listener before adding a new one
          previewsListener?.remove()
          previewsListener =
              accountRef.collection(Account::previews.name).addSnapshotListener { qs, e2 ->
                if (e2 != null) {
                  close(e2)
                  return@addSnapshotListener
                }
                if (qs != null) {
                  val previews =
                      qs.documents.associate { d ->
                        d.id to
                            (d.toObject(DiscussionPreviewNoUid::class.java)
                                ?: DiscussionPreviewNoUid())
                      }
                  trySend(fromNoUid(accountId, accountNoUid, previews))
                }
              }
        }

    awaitClose {
      accountListener.remove()
      previewsListener?.remove()
    }
  }

  /**
   * Searches for accounts whose handles start with the specified [handle] string.
   *
   * The search uses Firestore queries with a range between [handle] and the next lexicographical
   * string to efficiently find matching handles. Results are returned as a [Flow] of [List] of
   * [Account] objects and update in real-time when Firestore data changes.
   *
   * If the provided [handle] is invalid (contains characters other than letters, digits, or
   * underscores), the flow emits an empty list and closes immediately.
   *
   * @param handle The handle prefix to search for.
   * @return A [Flow] emitting lists of matching [Account] objects.
   */
  fun searchByHandle(handle: String): Flow<List<Account>> = callbackFlow {
    if (!validHandle(handle)) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }

    val reg =
        accounts
            .whereGreaterThanOrEqualTo(Account::handle.name, handle)
            .whereLessThanOrEqualTo(Account::handle.name, nextString(handle))
            .addSnapshotListener { qs, e ->
              if (e != null) {
                close(e)
                return@addSnapshotListener
              }
              if (qs != null) {
                val list =
                    qs.documents.mapNotNull { d ->
                      d.toObject(AccountNoUid::class.java)?.let { fromNoUid(d.id, it, emptyMap()) }
                    }
                trySend(list)
              }
            }
    awaitClose { reg.remove() }
  }

  /**
   * Calculates the next lexicographical string after [s].
   *
   * This is used to define an exclusive upper bound for Firestore range queries.
   *
   * Example: If [s] = "abc", the next string is "abd".
   *
   * @param s The input string.
   * @return The next lexicographical string.
   */
  private fun nextString(s: String): String {
    if (s.isEmpty()) return s
    val lastChar = s.last()
    val nextChar = lastChar + 1
    return s.dropLast(1) + nextChar
  }

  /**
   * Checks if a handle is valid.
   *
   * A valid handle contains only letters, digits, or underscores.
   *
   * @param handle The handle string to validate.
   * @return True if the handle is valid; false otherwise.
   */
  private fun validHandle(handle: String): Boolean {
    return handle.all { it.isLetterOrDigit() || it == '_' }
  }
}
