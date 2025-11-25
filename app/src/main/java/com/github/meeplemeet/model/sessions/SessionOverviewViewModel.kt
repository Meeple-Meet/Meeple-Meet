package com.github.meeplemeet.model.sessions

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shared.game.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
   * Loads all SessionPhoto objects for a user across all past sessions.
   *
   * Fetches all past sessions and collects their SessionPhoto objects (UUID + URL).
   * Individual session failures are handled gracefully and don't prevent loading other photos.
   *
   * @param userId The ID of the user whose session photos are to be loaded
   * @param onResult Callback invoked with Result containing the list of SessionPhoto objects, or an exception
   */
  fun loadAllPhotosForAUser(
      userId: String,
      onResult: (Result<List<SessionPhoto>>) -> Unit
  ) {
    viewModelScope.launch {
      try {
        val sessionIds = sessionRepository.getPastSessionIdsForUser(userId)

        // Load photos from all sessions in parallel
        val allPhotos = sessionIds.map { sessionId ->
          async {
            try {
              // Get session to retrieve SessionPhoto objects
              val session = sessionRepository.getSession(sessionId)
              session?.sessionPhotos ?: emptyList()
            } catch (e: Exception) {
              // Continue with other sessions if one fails
              emptyList<SessionPhoto>()
            }
          }
        }.awaitAll().flatten()

        onResult(Result.success(allPhotos))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  /**
   * Retrieves the session that contains the specified photo UUID for a given user.
   *
   * Searches through all past sessions in parallel to find the one containing the specified photo.
   *
   * @param userId The ID of the user
   * @param photoUuid The UUID of the photo to search for
   * @param onResult Callback invoked with Result containing the found Session (or null if not found), or an exception
   */
  fun getSessionFromPhoto(userId: String, photoUuid: String, onResult: (Result<Session?>) -> Unit) {
    viewModelScope.launch {
      try {
        val sessionIds = sessionRepository.getPastSessionIdsForUser(userId)

        // Search all sessions in parallel and return first match
        val foundSession = sessionIds.map { sessionId ->
          async {
            try {
              val session = sessionRepository.getSession(sessionId)
              // Check if any SessionPhoto has matching UUID
              if (session?.sessionPhotos?.any { it.uuid == photoUuid } == true) {
                session
              } else {
                null
              }
            } catch (e: Exception) {
              null
            }
          }
        }.awaitAll().firstOrNull { it != null }

        onResult(Result.success(foundSession))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }
}
