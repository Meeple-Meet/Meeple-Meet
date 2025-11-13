package com.github.meeplemeet.model.sessions

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionNoUid
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing gaming sessions within discussions in Firestore.
 *
 * Handles CRUD operations for sessions that are nested within discussion documents.
 */
class SessionRepository(
    discussionRepository: DiscussionRepository = RepositoryProvider.discussions
) {
  private val discussions = discussionRepository.collection
  private val discussionRepo = DiscussionRepository()
  private val geoPinsRepo = RepositoryProvider.geoPins

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

    geoPinsRepo.upsertGeoPin(ref = discussionId, type = PinType.SESSION, location = location)

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

    if (location != null) geoPinsRepo.upsertGeoPin(discussionId, PinType.SESSION, location)

    return discussionRepo.getDiscussion(discussionId)
  }

  /** Deletes the session from a discussion by setting it to null. */
  suspend fun deleteSession(discussionId: String) {
    geoPinsRepo.deleteGeoPin(discussionId)
    discussions.document(discussionId).update(DiscussionNoUid::session.name, null).await()
  }

  /**
   * Returns the list of discussion IDs that currently contain a session and where the session's
   * participant list contains [userId].
   *
   * This method performs a paginated query to avoid reading too many documents at once. It pages
   * through results using `startAfter(lastSnapshot)` and a `limit(batchSize)`.
   *
   * @param userId user UID to look for in session.participants.
   * @param batchSize maximum documents to fetch per query page (default 50).
   * @return list of discussion document IDs matching the criteria.
   */
  suspend fun getSessionIdsForUser(userId: String, batchSize: Long = 50): List<String> {
    val allIds = mutableListOf<String>()
    var lastDocumentId: String? = null

    while (true) {
      val query =
          discussions
              .whereArrayContains("session.participants", userId)
              .orderBy(FieldPath.documentId())
              .let { if (lastDocumentId != null) it.startAfter(lastDocumentId) else it }
              .limit(batchSize)

      val snapshot = query.get().await()
      if (snapshot.isEmpty) break

      allIds.addAll(snapshot.documents.map { it.id })
      lastDocumentId = snapshot.documents.last().id

      if (snapshot.size() < batchSize) break
    }

    return allIds
  }
}
