// Claude Code generated the documentation

package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.BuildConfig
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.FirebaseFunctionsWrapper
import com.github.meeplemeet.model.FirebaseFunctionsWrapperImpl
import com.github.meeplemeet.model.GameFetchException
import com.github.meeplemeet.model.GameSearchException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository implementation for fetching board games using Firebase Cloud Functions.
 *
 * This repository communicates with a custom backend (Firebase Cloud Functions) to retrieve board
 * game data either by identifiers or via name-based search.
 *
 * It uses a [FirebaseFunctionsWrapper] abstraction to facilitate testing and allow mocking of Cloud
 * Functions calls.
 *
 * @property functionsWrapper Wrapper around Firebase Cloud Functions, used to perform remote calls.
 * @property ioDispatcher Coroutine dispatcher used for IO-bound operations. Defaults to
 *   [Dispatchers.IO].
 */
class CloudBggGameRepository(
    private val functionsWrapper: FirebaseFunctionsWrapper =
        FirebaseFunctionsWrapperImpl(FirebaseProvider.functions),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GameRepository {

  /**
   * Initializes the repository.
   *
   * In debug builds, this configures Firebase Functions to use the local emulator.
   */
  init {
    if (BuildConfig.DEBUG) functionsWrapper.useEmulator("10.0.2.2", 5001)
  }

  companion object {

    /** Cloud Function endpoint for fetching multiple games by ID. */
    private const val PATH_GET_GAMES_BY_IDS = "getGamesByIds"

    /** Cloud Function endpoint for searching games by name. */
    private const val PATH_SEARCH_GAMES = "searchGames"
  }

  /**
   * Fetches a single [Game] using its unique identifier.
   *
   * @param gameID Unique identifier of the game to fetch.
   * @return The matching [Game] object.
   * @throws GameFetchException If no game is found or if the request fails.
   */
  override suspend fun getGameById(gameID: String): Game =
      withContext(ioDispatcher) {
        getGamesById(gameID).firstOrNull()
            ?: throw GameFetchException("No game found for id: $gameID")
      }

  /**
   * Fetches a list of games corresponding to the provided identifiers.
   *
   * The Cloud Function restricts batch requests to a maximum of 20 IDs.
   *
   * @param gameIDs One or more game identifiers. Must not exceed 20.
   * @return A list of [Game] objects matching the IDs.
   * @throws IllegalArgumentException If more than 20 IDs are requested.
   * @throws GameFetchException If the Cloud Function fails or if JSON parsing fails.
   */
  override suspend fun getGamesById(vararg gameIDs: String): List<Game> {
    require(gameIDs.size <= 20) { "A maximum of 20 IDs can be requested at once." }

    return withContext(ioDispatcher) {
      val params = mapOf("ids" to gameIDs.toList())

      val data =
          try {
            functionsWrapper.call<List<*>>(PATH_GET_GAMES_BY_IDS, params).await()
          } catch (e: Exception) {
            throw GameFetchException("Failed calling Cloud Function: ${e.message}", e)
          }

      if (data.any { it == null || it !is Map<*, *> }) {
        throw GameFetchException("Failed parsing JSON: invalid element(s) in response")
      }

      try {
        data.map { elem -> mapToGame(elem as Map<*, *>) }
      } catch (e: Exception) {
        throw GameFetchException("Failed parsing JSON: ${e.message}", e)
      }
    }
  }

  /**
   * Deprecated full search method that returns complete [Game] objects.
   *
   * This method remains available for backward compatibility but will be removed in the future. It
   * now delegates to [searchGamesByNameLight], then resolves the returned IDs via [getGamesById].
   *
   * @param query The text query to match in game names.
   * @param maxResults Maximum number of results to return.
   * @param ignoreCase (Unused) Provided for legacy signature compatibility.
   * @return A list of full [Game] objects matching the query.
   * @throws GameSearchException If the search operation fails.
   * @throws GameFetchException If loading the full game objects fails.
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
   * Searches for games by name and returns lightweight results containing only ID and name.
   *
   * This method is recommended over the deprecated full search, as it reduces backend load and
   * allows better UI interactivity.
   *
   * @param query The search query string.
   * @param maxResults Maximum number of results to return.
   * @return A list of [GameSearchResult] objects containing minimal game info.
   * @throws GameSearchException If calling the Cloud Function fails or if JSON parsing fails.
   */
  suspend fun searchGamesByNameLight(query: String, maxResults: Int): List<GameSearchResult> =
      withContext(ioDispatcher) {
        val params = mapOf("query" to query, "maxResults" to maxResults)

        val data =
            try {
              functionsWrapper.call<List<*>>(PATH_SEARCH_GAMES, params).await()
            } catch (e: Exception) {
              throw GameSearchException("Failed calling Cloud Function: ${e.message}", e)
            }

        if (data.any { it == null || it !is Map<*, *> }) {
          throw GameSearchException("Failed parsing JSON: invalid element(s) in response")
        }

        try {
          data.map { elem -> mapToGameSearchResult(elem as Map<*, *>) }
        } catch (e: Exception) {
          throw GameSearchException("Failed parsing JSON: ${e.message}", e)
        }
      }

  /**
   * Converts a JSON-like map returned by Cloud Functions into a fully populated [Game] object.
   *
   * All mandatory fields must be present or an [IllegalArgumentException] is thrown.
   *
   * @param map Raw JSON structure representing a game.
   * @return The corresponding [Game] instance.
   */
  private fun mapToGame(map: Map<*, *>): Game {
    return Game(
        uid = map["uid"]?.toString() ?: throw IllegalArgumentException("Missing uid"),
        name = map["name"]?.toString() ?: throw IllegalArgumentException("Missing name"),
        description =
            map["description"]?.toString() ?: throw IllegalArgumentException("Missing description"),
        imageURL =
            map["imageURL"]?.toString() ?: throw IllegalArgumentException("Missing imageURL"),
        minPlayers =
            (map["minPlayers"] as? Number)?.toInt()
                ?: throw IllegalArgumentException("Missing minPlayers"),
        maxPlayers =
            (map["maxPlayers"] as? Number)?.toInt()
                ?: throw IllegalArgumentException("Missing maxPlayers"),
        recommendedPlayers = (map["recommendedPlayers"] as? Number)?.toInt(),
        averagePlayTime = (map["averagePlayTime"] as? Number)?.toInt(),
        minAge = (map["minAge"] as? Number)?.toInt(),
        genres = (map["genres"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList())
  }

  /**
   * Converts a JSON-like map returned by Cloud Functions into a [GameSearchResult].
   *
   * Only lightweight fields are extracted.
   *
   * @param map Raw JSON structure representing a search result.
   * @return A parsed [GameSearchResult] instance.
   */
  private fun mapToGameSearchResult(map: Map<*, *>): GameSearchResult {
    return GameSearchResult(
        id = map["id"]?.toString() ?: throw IllegalArgumentException("Missing id"),
        name = map["name"]?.toString() ?: throw IllegalArgumentException("Missing name"))
  }
}
