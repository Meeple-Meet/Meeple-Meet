package com.github.meeplemeet.model.systems

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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

const val ACCOUNT_COLLECTION_PATH = "accounts"
const val DISCUSSIONS_COLLECTION_PATH = "discussions"

class FirestoreRepository(db: FirebaseFirestore) {
  private val accounts = db.collection(ACCOUNT_COLLECTION_PATH)
  private val discussions = db.collection(DISCUSSIONS_COLLECTION_PATH)

  private fun newDiscussionUID(): String = discussions.document().id

  private fun accountUID(): String = accounts.document().id // Firebase.auth.uid
  // ?: throw NotSignedInException("User needs to be signed in to access Firebase")

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

  suspend fun getDiscussion(id: String): Discussion {
    val snapshot = discussions.document(id).get().await()
    val discussion = snapshot.toObject(DiscussionNoUid::class.java)

    if (discussion != null) return fromNoUid(id, discussion)
    throw DiscussionNotFoundException("Discussion not found.")
  }

  suspend fun setDiscussionName(id: String, name: String): Discussion {
    discussions.document(id).update(Discussion::name.name, name).await()
    return getDiscussion(id)
  }

  suspend fun setDiscussionDescription(id: String, name: String): Discussion {
    discussions.document(id).update(Discussion::description.name, name).await()
    return getDiscussion(id)
  }

  suspend fun deleteDiscussion(id: String): Discussion {
    discussions.document(id).delete().await()
    return getDiscussion(id)
  }

  suspend fun addUserToDiscussion(discussion: Discussion, userId: String): Discussion {
    discussions
        .document(discussion.uid)
        .update(Discussion::participants.name, FieldValue.arrayUnion(userId))
        .await()

    return getDiscussion(discussion.uid)
  }

  suspend fun addUsersToDiscussion(discussion: Discussion, userIds: List<String>): Discussion {
    discussions
        .document(discussion.uid)
        .update(Discussion::participants.name, FieldValue.arrayUnion(*userIds.toTypedArray()))
        .await()

    return getDiscussion(discussion.uid)
  }

  suspend fun addAdminToDiscussion(discussion: Discussion, userId: String): Discussion {
    if (!discussion.participants.contains(userId)) addUserToDiscussion(discussion, userId)

    discussions
        .document(discussion.uid)
        .update(Discussion::admins.name, FieldValue.arrayUnion(userId))
        .await()

    return getDiscussion(discussion.uid)
  }

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

  suspend fun createAccount(name: String): Account {
    val account = Account(accountUID(), name)
    accounts.document(account.uid).set(mapOf("name" to account.name)).await()
    return account
  }

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

  suspend fun getCurrentAccount(): Account {
    return getAccount(accountUID())
  }

  suspend fun setAccountName(id: String, name: String): Account {
    accounts.document(id).update(Account::name.name, name).await()
    return getAccount(id)
  }

  suspend fun deleteAccount(id: String) {
    accounts.document(id).delete().await()
  }

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
}
