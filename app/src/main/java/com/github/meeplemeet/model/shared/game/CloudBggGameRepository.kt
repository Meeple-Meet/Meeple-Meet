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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

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
   * DTO representing the JSON returned by Firebase Functions for full game details.
   *
   * Keeping this separate from the domain [Game] model prevents backend format changes from
   * breaking the rest of the app.
   */
  @Serializable
  data class GameDto(
      val uid: String,
      val name: String,
      val description: String,
      val imageURL: String,
      val minPlayers: Int,
      val maxPlayers: Int,
      val recommendedPlayers: Int? = null,
      val averagePlayTime: Int? = null,
      val minAge: Int? = null,
      val genres: List<String> = emptyList()
  )

  /** DTO for lightweight search results (ID + name). */
  @Serializable data class GameSearchResultDto(val id: String, val name: String)

  /**
   * Fetches a single game by its unique ID.
   *
   * @param gameID The game ID to fetch.
   * @return The [Game] object corresponding to the given ID.
   * @throws GameFetchException if fetching fails.
   */
  override suspend fun getGameById(gameID: String): Game =
      withContext(ioDispatcher) { getGamesById(gameID).first() }

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
      val params = mapOf("ids" to gameIDs.toList())

      val result =
          try {
            functions.getHttpsCallable(PATH_GET_GAMES_BY_IDS).call(params).await()
          } catch (e: Exception) {
            throw GameFetchException("Failed calling Cloud Function: ${e.message}", e)
          }

      val data = result.data as? List<*> ?: throw GameFetchException("Invalid response from server")

      try {
        data.map { element ->
          val json = Json.parseToJsonElement(element.toString())
          Json.decodeFromJsonElement<GameDto>(json).toGame()
        }
      } catch (e: Exception) {
        throw GameFetchException("Failed parsing JSON: ${e.message}", e)
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
        val params = mapOf("query" to query, "maxResults" to maxResults)

        val result =
            try {
              functions.getHttpsCallable(PATH_SEARCH_GAMES).call(params).await()
            } catch (e: Exception) {
              throw GameSearchException("Failed calling Cloud Function: ${e.message}", e)
            }

        val data =
            result.data as? List<*> ?: throw GameSearchException("Invalid response from server")

        try {
          data.map { element ->
            val json = Json.parseToJsonElement(element.toString())
            Json.decodeFromJsonElement<GameSearchResultDto>(json).toGameSearchResult()
          }
        } catch (e: Exception) {
          throw GameSearchException("Failed parsing JSON: ${e.message}", e)
        }
      }

  /** Converts a Dto to domain model. */
  private fun GameDto.toGame(): Game =
      Game(
          uid = uid,
          name = name,
          description = description,
          imageURL = imageURL,
          minPlayers = minPlayers,
          maxPlayers = maxPlayers,
          recommendedPlayers = recommendedPlayers,
          averagePlayTime = averagePlayTime,
          minAge = minAge,
          genres = genres)

  /** Converts DTO to domain model. */
  private fun GameSearchResultDto.toGameSearchResult(): GameSearchResult =
      GameSearchResult(id = id, name = name)
}
