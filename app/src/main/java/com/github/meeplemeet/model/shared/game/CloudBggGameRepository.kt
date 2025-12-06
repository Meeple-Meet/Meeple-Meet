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

class CloudBggGameRepository(
    private val functionsWrapper: FirebaseFunctionsWrapper =
        FirebaseFunctionsWrapperImpl(FirebaseProvider.functions),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GameRepository {

  init {
    if (BuildConfig.DEBUG) functionsWrapper.useEmulator("10.0.2.2", 5001)
  }

  companion object {
    private const val PATH_GET_GAMES_BY_IDS = "getGamesByIds"
    private const val PATH_SEARCH_GAMES = "searchGames"
  }

  override suspend fun getGameById(gameID: String): Game =
      withContext(ioDispatcher) { getGamesById(gameID).first() }

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

      try {
        data.mapNotNull { element ->
          if (element is Map<*, *>) {
            mapToGame(element)
          } else null
        }
      } catch (e: Exception) {
        throw GameFetchException("Failed parsing JSON: ${e.message}", e)
      }
    }
  }

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

  suspend fun searchGamesByNameLight(query: String, maxResults: Int): List<GameSearchResult> =
      withContext(ioDispatcher) {
        val params = mapOf("query" to query, "maxResults" to maxResults)

        val data =
            try {
              functionsWrapper.call<List<*>>(PATH_SEARCH_GAMES, params).await()
            } catch (e: Exception) {
              throw GameSearchException("Failed calling Cloud Function: ${e.message}", e)
            }

        try {
          data.mapNotNull { element ->
            if (element is Map<*, *>) {
              mapToGameSearchResult(element)
            } else null
          }
        } catch (e: Exception) {
          throw GameSearchException("Failed parsing JSON: ${e.message}", e)
        }
      }

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

  private fun mapToGameSearchResult(map: Map<*, *>): GameSearchResult {
    return GameSearchResult(
        id = map["id"]?.toString() ?: throw IllegalArgumentException("Missing id"),
        name = map["name"]?.toString() ?: throw IllegalArgumentException("Missing name"))
  }
}
