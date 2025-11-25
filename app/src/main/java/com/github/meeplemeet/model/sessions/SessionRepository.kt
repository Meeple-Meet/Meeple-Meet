package com.github.meeplemeet.model.sessions
// AI was used in this file

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionNoUid
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing gaming sessions within discussions in Firestore.
 *
 * Handles CRUD operations for sessions that are nested within discussion documents.
 */
class SessionRepository(
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions,
    private val discussions: CollectionReference = RepositoryProvider.discussions.collection,
) {
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

    return discussionRepository.getDiscussion(discussionId)
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

    return discussionRepository.getDiscussion(discussionId)
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

  /**
   * Returns the list of discussion IDs that currently contain a session and where the session's
   * participant list contains [userId] and the session date is in the past.
   *
   * This method performs a paginated query to avoid reading too many documents at once. It pages
   * through results using `startAfter(lastSnapshot)` and a `limit(batchSize)`.
   *
   * @param userId user UID to look for in session.participants.
   * @param batchSize maximum documents to fetch per query page (default 50).
   * @return list of discussion document IDs matching the criteria.
   */
  suspend fun getPastSessionIdsForUser(userId: String, batchSize: Long = 50): List<String> {
    val allIds = mutableListOf<String>()
    var lastDocumentId: String? = null

    while (true) {
      val query =
          discussions
              .whereArrayContains("session.participants", userId)
              .whereLessThan("session.date", Timestamp.now())
              .orderBy("session.date")
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

  /**
   * Retrieves the single [Session] embedded in the discussion document identified by
   * [discussionId].
   *
   * @param discussionId Firestore document id of the parent discussion.
   * @return The embedded session, or `null` if the document does not exist or contains no session.
   */
  suspend fun getSession(discussionId: String): Session? {
    val snap = discussions.document(discussionId).get().await()
    if (!snap.exists()) return null
    return snap.toObject(DiscussionNoUid::class.java)?.session
  }

  /**
   * Real-time stream of discussion **document ids** whose embedded session contains the given user
   * in its participant list.
   *
   * The flow emits a new list on every relevant create / update / delete event in Firestore, making
   * it suitable for observing a live session list.
   *
   * @param userId UID of the participant to filter for.
   * @return Cold [Flow] that delivers a list of discussion document ids.
   */
  fun getSessionIdsForUserFlow(userId: String): Flow<List<String>> =
      discussions.whereArrayContains("session.participants", userId).snapshots().map { snap ->
        snap.documents.map { it.id }
      }

  /**
   * Real-time stream of discussion **document ids** whose embedded session contains the given user
   * in its participant list and the session date is in the future.
   *
   * The flow emits a new list on every relevant create / update / delete event in Firestore, making
   * it suitable for observing a live session list.
   *
   * @param userId UID of the participant to filter for.
   * @return Cold [Flow] that delivers a list of discussion document ids.
   */
  fun getUpcomingSessionIdsForUserFlow(userId: String): Flow<List<String>> =
      discussions
          .whereArrayContains("session.participants", userId)
          .whereGreaterThan("session.date", Timestamp.now())
          .snapshots()
          .map { snap -> snap.documents.map { it.id } }

  /**
   * Adds SessionPhoto objects to the session's photo list in the specified discussion.
   *
   * Uses Firestore's arrayUnion operation to atomically add photos without creating duplicates.
   * This method should be called after successfully uploading photos via
   * ImageRepository.saveSessionPhotos().
   *
   * @param discussionId The ID of the discussion containing the session.
   * @param photos List of SessionPhoto objects to add to the session (from saveSessionPhotos).
   * @throws FirebaseFirestoreException if the Firestore update fails
   */
  suspend fun addSessionPhotos(discussionId: String, photos: List<SessionPhoto>) {
    val sessionPhotosField = "session.sessionPhotos"
    discussions
        .document(discussionId)
        .update(sessionPhotosField, FieldValue.arrayUnion(*photos.toTypedArray()))
        .await()
  }

  /**
   * Removes a photo from the session's photo list by its UUID.
   *
   * Fetches the session, filters out the photo with the matching UUID, and updates Firestore.
   *
   * @param discussionId The ID of the discussion containing the session.
   * @param photoUuid The UUID of the photo to remove.
   * @throws FirebaseFirestoreException if the Firestore update fails
   */
  suspend fun removeSessionPhoto(discussionId: String, photoUuid: String) {
    val session = getSession(discussionId) ?: return
    val updatedPhotos = session.sessionPhotos.filterNot { it.uuid == photoUuid }

    val sessionPhotosField = "session.sessionPhotos"
    discussions.document(discussionId).update(sessionPhotosField, updatedPhotos).await()
  }
}
