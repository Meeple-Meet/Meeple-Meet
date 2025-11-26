package com.github.meeplemeet.model.sessions

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionNoUid
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private val archivedSessions: String = "archived_sessions"

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
      newParticipantList: List<String>? = null,
      photoUrl: String? = null
  ): Discussion {
    val updates = mutableMapOf<String, Any>()

    name?.let { updates["${DiscussionNoUid::session.name}.${Session::name.name}"] = it }
    gameId?.let { updates["${DiscussionNoUid::session.name}.${Session::gameId.name}"] = it }
    date?.let { updates["${DiscussionNoUid::session.name}.${Session::date.name}"] = it }
    location?.let { updates["${DiscussionNoUid::session.name}.${Session::location.name}"] = it }
    newParticipantList?.let {
      updates["${DiscussionNoUid::session.name}.${Session::participants.name}"] = it
    }
    photoUrl?.let {
      // Use FieldValue.delete() to clear the field when empty string is passed
      val value = if (it.isEmpty()) FieldValue.delete() else it
      updates["${DiscussionNoUid::session.name}.${Session::photoUrl.name}"] = value
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

  suspend fun isSessionPassed(sessionId: String): Boolean {
    val session = getSession(sessionId) ?: return false
    val date = session.date
    // Add 3 hours to the session time to account for session duration
    val threeHoursInMillis = 3 * 60 * 60 * 1000L
    return (date.toDate().time + threeHoursInMillis) < Timestamp.now().toDate().time
  }

  suspend fun updateSessionPhoto(sessionId: String, photoUrl: String) {
    val session = getSession(sessionId)
    if (session != null) {
      discussions
          .document(sessionId)
          .update(DiscussionNoUid::session.name, session.copy(photoUrl = photoUrl))
          .await()
    }
  }

  /**
   * Archives the current session by moving it to the past_sessions subcollection and removing it
   * from the discussion. Also updates each participant's account to include this session in their
   * pastSessionIds list.
   *
   * @param discussionId The ID of the discussion containing the session.
   * @param newSessionId The ID for the new archived session document.
   * @param newPhotoUrl The URL of the moved photo (if applicable).
   */
  suspend fun archiveSession(discussionId: String, newSessionId: String, newPhotoUrl: String?) {
    val session = getSession(discussionId) ?: return

    val archivedSession = session.copy(photoUrl = newPhotoUrl)

    val batch = FirebaseFirestore.getInstance().batch()

    // 1. Save to archived_sessions collection
    val archivedRef =
        FirebaseFirestore.getInstance().collection(archivedSessions).document(newSessionId)
    batch.set(archivedRef, archivedSession)

    // 2. Add session UUID to each participant's pastSessionIds
    val accountsRef = RepositoryProvider.accounts.collection
    session.participants.forEach { participantId ->
      val accountRef = accountsRef.document(participantId)
      batch.update(accountRef, "pastSessionIds", FieldValue.arrayUnion(newSessionId))
    }

    // 3. Delete from active session (update discussion)
    val discussionRef = discussions.document(discussionId)
    batch.update(discussionRef, DiscussionNoUid::session.name, null)

    batch.commit().await()

    // 4. Delete GeoPin (performed after batch commit as it's likely a separate system/collection)
    geoPinsRepo.deleteGeoPin(discussionId)
  }

  /**
   * Retrieves photo URLs from archived sessions with pagination support.
   *
   * This method supports loading photo URLs in batches (default 12 per page) to enable efficient
   * gradual loading in the UI.
   *
   * @param pastSessionIds List of archived session UUIDs from Account.pastSessionIds
   * @param page Page number (0-indexed). Page 0 returns the first batch, page 1 the next, etc.
   * @param pageSize Number of photos to return per page (default 12)
   * @return List of photo URLs for the requested page (may be smaller than pageSize on the last
   *   page)
   */
  suspend fun getArchivedSessionPhotoUrls(
      userId: String,
      page: Int = 0,
      pageSize: Int = 12
  ): List<String> = coroutineScope {
    val pastSessionIds = RepositoryProvider.accounts.getAccount(userId).pastSessionIds

    val startIndex = page * pageSize
    if (startIndex >= pastSessionIds.size) return@coroutineScope emptyList()

    val endIndex = minOf(startIndex + pageSize, pastSessionIds.size)
    val sessionIdsForPage = pastSessionIds.subList(startIndex, endIndex)

    val archivedSessionsCollection = FirebaseFirestore.getInstance().collection(archivedSessions)

    sessionIdsForPage
        .map { sessionId ->
          async {
            try {
              val snapshot = archivedSessionsCollection.document(sessionId).get().await()
              snapshot.toObject(Session::class.java)?.photoUrl
            } catch (e: Exception) {
              null // Skip sessions that fail to load or don't exist
            }
          }
        }
        .awaitAll()
        .filterNotNull()
  }

  /**
   * Finds an archived session by its photo URL.
   *
   * Queries the archived_sessions collection to find a session that has the given photo URL. This
   * is useful for reverse-lookup scenarios where you have a photo and need to find which session it
   * belongs to.
   *
   * @param photoUrl The photo URL to search for
   * @return The Session object if found, or null if no archived session has this photo URL
   */
  suspend fun getArchivedSessionByPhotoUrl(photoUrl: String): Session? {
    return try {
      val archivedSessionsCollection = FirebaseFirestore.getInstance().collection(archivedSessions)
      val querySnapshot =
          archivedSessionsCollection.whereEqualTo("photoUrl", photoUrl).limit(1).get().await()

      if (querySnapshot.isEmpty) {
        null
      } else {
        querySnapshot.documents.first().toObject(Session::class.java)
      }
    } catch (e: Exception) {
      null
    }
  }
}
