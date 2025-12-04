package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.BuildConfig
import com.github.meeplemeet.HttpClientProvider
import com.github.meeplemeet.model.GameFetchException
import com.github.meeplemeet.model.GameSearchException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Repository implementation for fetching board games from the custom Cloud Functions backend.
 *
 * This repository uses Firebase Cloud Functions to fetch game details by ID or to search games by
 * name.
 *
 * @property client The HTTP client used to perform requests. Defaults to
 *   [HttpClientProvider.client].
 * @property baseUrl The base URL for the Cloud Functions backend. Defaults to [getBaseUrl].
 */
class CloudBggGameRepository(
    private val client: OkHttpClient = HttpClientProvider.client,
    private val baseUrl: HttpUrl = getBaseUrl(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GameRepository {

  companion object {

    /** Local emulator URL for development. */
    private val LOCAL_URL: HttpUrl =
        HttpUrl.Builder()
            .scheme("http")
            .host("127.0.0.1")
            .port(5001)
            .addPathSegment("meeple-meet-36ecb")
            .addPathSegment("us-central1")
            .build()

    /** Production Cloud Function URL (uses BuildConfig for security). */
    private val PROD_URL: HttpUrl =
        HttpUrl.Builder().scheme("https").host(BuildConfig.GAME_API_HOST).build()

    /**
     * Returns the appropriate base URL depending on the build type.
     *
     * Uses [LOCAL_URL] for debug builds and [PROD_URL] for release builds.
     */
    fun getBaseUrl(): HttpUrl = if (BuildConfig.DEBUG) LOCAL_URL else PROD_URL

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
      val url =
          baseUrl
              .newBuilder()
              .addPathSegment(PATH_GET_GAMES_BY_IDS)
              .addQueryParameter("ids", gameIDs.joinToString(","))
              .build()

      val request = Request.Builder().url(url).build()
      try {
        client.newCall(request).execute().use { response ->
          val bodyString = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            throw GameFetchException("Failed to fetch games (HTTP ${response.code}): $bodyString")
          }

          val jsonArray = JSONArray(bodyString)
          return@withContext (0 until jsonArray.length()).map { i ->
            jsonArray.getJSONObject(i).toGame()
          }
        }
      } catch (e: JSONException) {
        throw GameFetchException("Invalid JSON while fetching games: ${e.message}", e)
      } catch (e: IOException) {
        throw GameFetchException("Network error while fetching games: ${e.message}", e)
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
        val url =
            baseUrl
                .newBuilder()
                .addPathSegment(PATH_SEARCH_GAMES)
                .addQueryParameter("query", query)
                .addQueryParameter("maxResults", maxResults.toString())
                .build()

        val request = Request.Builder().url(url).build()

        try {
          client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
              throw GameSearchException(
                  "Failed to search games (HTTP ${response.code}): $bodyString")
            }

            val jsonArray = JSONArray(bodyString)
            return@withContext (0 until jsonArray.length()).map { i ->
              jsonArray.getJSONObject(i).toGameSearchResult()
            }
          }
        } catch (e: Exception) {
          throw GameSearchException("Network error while searching games: ${e.message}", e)
        }
      }

  /** Converts a [JSONObject] to a [Game] object. */
  private fun JSONObject.toGame(): Game =
      Game(
          uid = getString("uid"),
          name = getString("name"),
          description = getString("description"),
          imageURL = getString("imageURL"),
          minPlayers = getInt("minPlayers"),
          maxPlayers = getInt("maxPlayers"),
          recommendedPlayers = optInt("recommendedPlayers"),
          averagePlayTime = optInt("averagePlayTime"),
          minAge = optInt("minAge"),
          genres =
              optJSONArray("genres")?.let { arr -> List(arr.length()) { i -> arr.getString(i) } }
                  ?: emptyList())

  /** Converts a [JSONObject] to a [GameSearchResult] object. */
  private fun JSONObject.toGameSearchResult(): GameSearchResult =
      GameSearchResult(id = getString("id"), name = getString("name"))
}
