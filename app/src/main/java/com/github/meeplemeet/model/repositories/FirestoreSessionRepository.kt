package com.github.meeplemeet.model.repositories

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.DiscussionNoUid
import com.github.meeplemeet.model.structures.Location
import com.github.meeplemeet.model.structures.Session
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing gaming sessions within discussions in Firestore.
 *
 * Handles CRUD operations for sessions that are nested within discussion documents.
 */
class FirestoreSessionRepository(db: FirebaseFirestore = FirebaseProvider.db) {
  private val discussions = db.collection(DISCUSSIONS_COLLECTION_PATH)
  private val discussionRepo = FirestoreRepository()

  /**
   * Creates a new session within a discussion.
   *
   * @return The updated discussion containing the new session
   */
  suspend fun createSession(
      discussionId: String,
      name: String,
      gameId: String,
      date: Timestamp,
      location: Location,
      vararg participants: String
  ): Discussion {
    val session = Session(name, gameId, date, location, participants.toList())
    discussions.document(discussionId).update(DiscussionNoUid::session.name, session).await()
    return discussionRepo.getDiscussion(discussionId)
  }

  /**
   * Updates one or more fields of a session. Only provided (non-null) fields are updated.
   *
   * @return The updated discussion with modified session
   * @throws IllegalArgumentException if no fields are provided for update
   */
  suspend fun updateSession(
      discussionId: String,
      name: String? = null,
      gameId: String? = null,
      date: Timestamp? = null,
      location: Location? = null,
      newParticipantList: List<String>? = null
  ): Discussion {
    val updates = mutableMapOf<String, Any>()

    name?.let { updates["${DiscussionNoUid::session.name}.${Session::name.name}"] = it }
    gameId?.let { updates["${DiscussionNoUid::session.name}.${Session::gameId.name}"] = it }
    date?.let { updates["${DiscussionNoUid::session.name}.${Session::date.name}"] = it }
    location?.let { updates["${DiscussionNoUid::session.name}.${Session::location.name}"] = it }
    newParticipantList?.let {
      updates["${DiscussionNoUid::session.name}.${Session::participants.name}"] = it
    }

    if (updates.isEmpty())
        throw IllegalArgumentException("At least one field must be provided for update")

    discussions.document(discussionId).update(updates).await()
    return discussionRepo.getDiscussion(discussionId)
  }

  /** Deletes the session from a discussion by setting it to null. */
  suspend fun deleteSession(discussionId: String) {
    discussions.document(discussionId).update(DiscussionNoUid::session.name, null).await()
  }
}
