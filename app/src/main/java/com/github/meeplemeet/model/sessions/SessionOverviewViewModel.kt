package com.github.meeplemeet.model.sessions

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shared.game.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SessionOverviewViewModel(
    private val sessionRepository: SessionRepository = RepositoryProvider.sessions,
    private val gameRepository: GameRepository = RepositoryProvider.games,
    private val imageRepository: ImageRepository = RepositoryProvider.images
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
   * Updates sessions for a user by archiving those that have already passed. To be called from time
   * to time to ensure sessions are archived appropriately.
   */
  fun updateSessions(context: Context, userId: String) {
    viewModelScope.launch {
      val sessionIds = sessionRepository.getSessionIdsForUser(userId)
      for (id in sessionIds) {
        try {
          if (sessionRepository.isSessionPassed(id)) {
            val session = sessionRepository.getSession(id)
            if (session != null) {
              val newUuid = java.util.UUID.randomUUID().toString()
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
  }

  /**
   * Retrieves all photo URLs from the archived sessions for a given account.
   *
   * @param pastSessionIds List of archived session UUIDs from Account.pastSessionIds
   * @return List of photo URLs from archived sessions
   */
  suspend fun getArchivedSessionPhotoUrls(userId: String): List<String> {
    return sessionRepository.getArchivedSessionPhotoUrls(userId)
  }

  /**
   * Finds an archived session by its photo URL.
   *
   * @param photoUrl The photo URL to search for
   * @return The Session object if found, or null if not found
   */
  suspend fun getArchivedSessionByPhotoUrl(photoUrl: String): Session? {
    return sessionRepository.getArchivedSessionByPhotoUrl(photoUrl)
  }
}
