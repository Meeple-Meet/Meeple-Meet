package com.github.meeplemeet.model.sessions

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shared.game.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SessionOverviewViewModel(
    private val sessionRepository: SessionRepository = RepositoryProvider.sessions,
    private val gameRepository: GameRepository = RepositoryProvider.games,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions
) : CreateSessionViewModel() {
  suspend fun getGameNameByGameId(gameId: String): String? =
      kotlin.runCatching { gameRepository.getGameById(gameId).name }.getOrNull()
  /**
   * Live map of discussion-id → Session for the given user. Emits a new map on every Firestore
   * change.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun sessionMapFlow(userId: String): Flow<Map<String, Session>> =
      sessionRepository
          .getSessionIdsForUserFlow(userId)
          .mapLatest { ids ->
            ids.associateWith { id ->
                  sessionRepository.getSession(id) // <- use the function that IS in the repo
                }
                .filterValues { it != null }
                .mapValues { it.value!! }
          }
          .flowOn(Dispatchers.IO)

  /**
   * Checks if the session with the given ID has passed, and if so, archives it. This function is
   * intended to be called when a user views the discussion associated with the session.
   *
   * @param context The context used for image operations.
   * @param id The ID of the session to check and potentially archive.
   */
  fun updateSession(context: Context, id: String) {
    viewModelScope.launch {
      try {
        if (sessionRepository.isSessionPassed(id)) {
          val session = sessionRepository.getSession(id)
          if (session != null) {
            val newUuid = sessionRepository.newUUID()
            var newUrl: String? = null
            if (session.photoUrl != null) {
              newUrl = imageRepository.moveSessionPhoto(context, id, newUuid)
            }
            sessionRepository.archiveSession(id, newUuid, newUrl)
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  /**
   * Manually archives a session.
   *
   * @param context The context used for image operations.
   * @param id The ID of the session to archive.
   */
  fun archiveSession(context: Context, id: String) {
    viewModelScope.launch {
      try {
        val session = sessionRepository.getSession(id)
        if (session != null) {
          val newUuid = sessionRepository.newUUID()
          var newUrl: String? = null
          if (session.photoUrl != null) {
            newUrl = imageRepository.moveSessionPhoto(context, id, newUuid)
          }
          sessionRepository.archiveSession(id, newUuid, newUrl)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  /**
   * Retrieves all archived sessions for a given account.
   *
   * @param userId The user ID whose archived sessions should be retrieved
   * @param callback Callback to receive the list of sessions
   */
  fun getArchivedSessions(userId: String, callback: (List<Session>) -> Unit) {
    viewModelScope.launch {
      val result = sessionRepository.getArchivedSessions(userId)
      callback(result)
    }
  }

  /**
   * Finds an archived session by its photo URL.
   *
   * @param photoUrl The photo URL to search for
   * @return The Session object if found, or null if not found
   */
  fun getArchivedSessionByPhotoUrl(photoUrl: String, callback: (Session?) -> Unit) {
    viewModelScope.launch {
      val result = sessionRepository.getArchivedSessionByPhotoUrl(photoUrl)
      callback(result)
    }
  }

  /**
   * Checks if a user is an admin of the discussion associated with a session.
   *
   * @param discussionId The ID of the discussion
   * @param userId The ID of the user
   * @return True if the user is an admin, false otherwise
   */
  suspend fun isAdmin(discussionId: String, userId: String): Boolean {
    return discussionRepository.getDiscussion(discussionId).admins.contains(userId)
  }
}
