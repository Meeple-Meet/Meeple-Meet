package com.github.meeplemeet.model.sessions

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.shared.game.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class SessionOverviewViewModel(
    private val sessionRepository: SessionRepository = RepositoryProvider.sessions,
    private val gameRepository: GameRepository = RepositoryProvider.games
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
}
