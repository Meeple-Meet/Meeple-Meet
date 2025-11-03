package com.github.meeplemeet.model.sessions

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.discussions.DISCUSSIONS_COLLECTION_PATH
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionNoUid
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.shared.Location
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing gaming sessions within discussions in Firestore.
 *
 * Handles CRUD operations for sessions that are nested within discussion documents.
 */
class SessionRepository(db: FirebaseFirestore = FirebaseProvider.db) {
  private val discussions = db.collection(DISCUSSIONS_COLLECTION_PATH)
  private val discussionRepo = DiscussionRepository()

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
