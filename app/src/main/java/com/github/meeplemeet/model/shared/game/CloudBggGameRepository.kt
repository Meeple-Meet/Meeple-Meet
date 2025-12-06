package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.BuildConfig
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.GameFetchException
import com.github.meeplemeet.model.GameSearchException
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository implementation for fetching board games from the custom Cloud Functions backend.
 *
 * This repository uses Firebase Cloud Functions to fetch game details by ID or to search games by
 * name.
 *
 * @property functions The [FirebaseFunctions] instance used to call cloud functions.
 * @property ioDispatcher The [CoroutineDispatcher] used for IO operations. Defaults to
 *   [Dispatchers.IO].
 */
class CloudBggGameRepository(
    private val functions: FirebaseFunctions = FirebaseProvider.functions,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GameRepository {

  // Use emulator for debug builds
  init {
    if (BuildConfig.DEBUG) functions.useEmulator("10.0.2.2", 5001)
  }

  companion object {
    /** Cloud Function Endpoints */
    private const val PATH_GET_GAMES_BY_IDS = "getGamesByIds"
    private const val PATH_SEARCH_GAMES = "searchGames"
  }

  /**
   * Fetches a single game by its unique ID.
   *
   * @param gameID The game ID to fetch.
   * @return The [Game] object corresponding to the given ID.
   * @throws GameFetchException if fetching fails.
   */
  override suspend fun getGameById(gameID: String): Game =
      withContext(ioDispatcher) {
        return@withContext getGamesById(gameID).first()
      }

  /**
   * Fetches multiple games by their IDs.
   *
   * @param gameIDs One or more game IDs to fetch.
   * @return A list of [Game] objects.
   * @throws IllegalArgumentException if more than 20 IDs are provided.
   * @throws GameFetchException if fetching fails.
   */
  override suspend fun getGamesById(vararg gameIDs: String): List<Game> {
    require(gameIDs.size <= 20) { "A maximum of 20 IDs can be requested at once." }

    return withContext(ioDispatcher) {
      try {
        val result =
            functions
                .getHttpsCallable(PATH_GET_GAMES_BY_IDS)
                .call(mapOf("ids" to gameIDs.toList()))
                .await()

        val data =
            result.data as? List<Map<String, Any>>
                ?: throw GameFetchException("Invalid response from Firebase Functions")

        return@withContext data.map { it.toGame() }
      } catch (e: Exception) {
        throw GameFetchException("Failed to fetch games: ${e.message}", e)
      }
    }
  }

  /**
   * Quick fix: temporarily re-implemented to maintain backward compatibility.
   *
   * This method delegates to [searchGamesByNameLight] to get lightweight results (IDs), then
   * immediately fetches the full [Game] objects via [getGamesById].
   *
   * Deprecated: use [searchGamesByNameLight] instead. This method will be permanently removed in a
   * future release.
   *
   * @throws GameSearchException if the search fails
   * @throws GameFetchException if fetching full games fails
   */
  @Deprecated("Use searchGamesByNameLight for partial search results")
  override suspend fun searchGamesByNameContains(
      query: String,
      maxResults: Int,
      ignoreCase: Boolean
  ): List<Game> =
      withContext(ioDispatcher) {
        val lightResults = searchGamesByNameLight(query, maxResults)
        if (lightResults.isEmpty()) return@withContext emptyList()
        return@withContext getGamesById(*lightResults.map { it.id }.toTypedArray())
      }

  /**
   * Searches games by name and returns lightweight results (ID + name).
   *
   * @param query The search query string.
   * @param maxResults Maximum number of results to return.
   * @return A list of [GameSearchResult] objects.
   * @throws GameSearchException if the search fails.
   */
  suspend fun searchGamesByNameLight(query: String, maxResults: Int): List<GameSearchResult> =
      withContext(ioDispatcher) {
        try {
          val result =
              functions
                  .getHttpsCallable(PATH_SEARCH_GAMES)
                  .call(mapOf("query" to query, "maxResults" to maxResults))
                  .await()

          val data =
              result.data as? List<Map<String, Any>>
                  ?: throw GameSearchException("Invalid response from Firebase Functions")

          return@withContext data.map { it.toGameSearchResult() }
        } catch (e: Exception) {
          throw GameSearchException("Failed to search games: ${e.message}", e)
        }
      }

  /** Converts a [Map] returned by Firebase Functions to a [Game] object. */
  private fun Map<String, Any>.toGame(): Game =
      Game(
          uid = this["uid"] as String,
          name = this["name"] as String,
          description = this["description"] as String,
          imageURL = this["imageURL"] as String,
          minPlayers = (this["minPlayers"] as Number).toInt(),
          maxPlayers = (this["maxPlayers"] as Number).toInt(),
          recommendedPlayers = (this["recommendedPlayers"] as? Number)?.toInt(),
          averagePlayTime = (this["averagePlayTime"] as? Number)?.toInt(),
          minAge = (this["minAge"] as? Number)?.toInt(),
          genres = (this["genres"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList())

  /** Converts a [Map] returned by Firebase Functions to a [GameSearchResult] object. */
  private fun Map<String, Any>.toGameSearchResult(): GameSearchResult =
      GameSearchResult(id = this["id"] as String, name = this["name"] as String)
}
