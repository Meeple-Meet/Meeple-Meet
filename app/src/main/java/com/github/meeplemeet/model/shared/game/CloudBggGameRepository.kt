package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.BuildConfig
import com.github.meeplemeet.HttpClientProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.json.JSONArray
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
   * @throws IOException If the HTTP request fails.
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
   * @throws IllegalArgumentException If more than 20 IDs are provided.
   * @throws IOException If the HTTP request fails.
   */
  override suspend fun getGamesById(vararg gameIDs: String): List<Game> =
      withContext(ioDispatcher) {
        require(gameIDs.size <= 20) { "A maximum of 20 IDs can be requested at once." }

        val url =
            baseUrl
                .newBuilder()
                .addPathSegment(PATH_GET_GAMES_BY_IDS)
                .addQueryParameter("ids", gameIDs.joinToString(","))
                .build()

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) throw IOException("Failed: ${response.code}")
          val jsonArray = JSONArray(response.body?.string().orEmpty())
          return@withContext (0 until jsonArray.length()).map { i ->
            jsonArray.getJSONObject(i).toGame()
          }
        }
      }

  /**
   * Deprecated: use [searchGamesByNameLight] instead.
   *
   * @throws UnsupportedOperationException Always thrown.
   */
  @Deprecated("Use searchGamesByNameLight for partial search results")
  override suspend fun searchGamesByNameContains(
      query: String,
      maxResults: Int,
      ignoreCase: Boolean
  ): List<Game> {
    throw UnsupportedOperationException(
        "searchGamesByNameContains(...) is deprecated and unsupported. Use searchGamesByNameLight(...) and then fetch full games if needed.")
  }

  /**
   * Searches games by name and returns lightweight results (ID + name).
   *
   * @param query The search query string.
   * @param maxResults Maximum number of results to return.
   * @return A list of [GameSearchResult] objects.
   * @throws IOException If the HTTP request fails.
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
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) throw IOException("Failed: ${response.code}")
          val jsonArray = JSONArray(response.body?.string().orEmpty())
          return@withContext (0 until jsonArray.length()).map { i ->
            jsonArray.getJSONObject(i).toGameSearchResult()
          }
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
