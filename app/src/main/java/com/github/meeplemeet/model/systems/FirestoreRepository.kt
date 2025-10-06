package com.github.meeplemeet.model.systems

import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.DiscussionNoUid
import com.github.meeplemeet.model.structures.Message
import com.github.meeplemeet.model.structures.fromNoUid
import com.github.meeplemeet.model.structures.toNoUid
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

const val USERS_COLLECTION_PATH = "users"
const val DISCUSSIONS_COLLECTION_PATH = "discussions"

class FirestoreRepository(db: FirebaseFirestore) {
  private val users = db.collection(USERS_COLLECTION_PATH)
  private val discussions = db.collection(DISCUSSIONS_COLLECTION_PATH)

  private fun newDiscussionUID(): String = discussions.document().id

  suspend fun createDiscussion(
      name: String,
      description: String,
      creatorId: String,
      participants: List<String> = emptyList()
  ): Discussion {
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
    return discussion
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
    discussions
        .document(discussion.uid)
        .update(Discussion::messages.name, FieldValue.arrayUnion(Message(sender.uid, content)))
        .await()

    return getDiscussion(discussion.uid)
  }
}
