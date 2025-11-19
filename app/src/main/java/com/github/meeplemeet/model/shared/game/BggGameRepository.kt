package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.HttpClientProvider
import com.github.meeplemeet.model.GameParseException
import com.github.meeplemeet.model.GameSearchException
import java.io.IOException
import java.io.StringReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jdom2.Document
import org.jdom2.input.SAXBuilder

/**
 * Result of fetching games from BoardGameGeek (BGG) API.
 *
 * Contains a list of successfully parsed [Game] objects and a list of [GameParseException] for
 * games that could not be parsed.
 *
 * @property games List of successfully parsed games.
 * @property errors List of parsing errors encountered while fetching games.
 */
data class GameFetchResult(val games: List<Game>, val errors: List<GameParseException>)

/**
 * Represents a minimal search result for a board game from BGG.
 *
 * Contains only the ID and the name of the game, suitable for autocomplete or search ranking.
 *
 * @property id The BGG ID of the game.
 * @property name The name of the game.
 */
data class GameSearchResult(val id: String, val name: String)

/**
 * Repository implementation for fetching BoardGameGeek (BGG) games.
 *
 * This repository uses the BGG XML API v2 to fetch game details by ID or to search games by name.
 * It uses [OkHttpClient] to perform HTTP requests and [org.jdom2] to parse XML responses.
 *
 * @property client The HTTP client used to make requests. Defaults to [HttpClientProvider.client].
 * @property baseUrl The base URL for the BGG XML API. Defaults to
 *   "https://boardgamegeek.com/xmlapi2".
 * @property authorizationToken Optional bearer token for authorization if needed.
 */
class BggGameRepository(
    private val client: OkHttpClient = HttpClientProvider.client,
    private val baseUrl: HttpUrl = DEFAULT_URL,
    private val authorizationToken: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxCachedGames: Int = DEFAULT_MAX_GAMES,
    private val maxCachedSearches: Int = DEFAULT_MAX_SEARCHES,
    private val gamesTtlMs: Long = DEFAULT_GAMES_TTL,
    private val searchesTtlMs: Long = DEFAULT_SEARCHES_TTL
) : GameRepository {

  /**
   * Constants used for BGG API requests.
   *
   * Includes the default base URL and user-agent string required by BGG.
   *
   * See: https://boardgamegeek.com/wiki/page/XML_API_Terms_of_Use
   */
  companion object {
    private const val APP_NAME = "MeepleMeet"
    private const val APP_VERSION = "1.0"
    private const val USER_AGENT = "$APP_NAME/$APP_VERSION"

    /** Default base URL for BoardGame Geek API (used in production) */
    val DEFAULT_URL: HttpUrl =
        HttpUrl.Builder()
            .scheme("https")
            .host("boardgamegeek.com")
            .addPathSegment("xmlapi2")
            .build()

    /** Default caches size and ttl */
    private const val DEFAULT_MAX_GAMES = 1000
    private const val DEFAULT_MAX_SEARCHES = 100
    private const val DEFAULT_GAMES_TTL = 24 * 60 * 60 * 1000L
    private const val DEFAULT_SEARCHES_TTL = 24 * 60 * 60 * 1000L
  }

  // ------------ Caches & synchronization ------------

  /** Small cache entry with timestamp */
  private data class CacheEntry<T>(
      val value: T,
      val createdAtMs: Long = System.currentTimeMillis()
  )

  // LRU cache for games (id -> CacheEntry<Game>)
  private val gamesCache =
      object : LinkedHashMap<String, CacheEntry<Game>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<Game>>?) =
            size > maxCachedGames
      }
  private val gamesCacheMutex = Mutex()

  // LRU cache for search queries (query -> CacheEntry<List<GameSearchResult>>)
  private val searchCache =
      object : LinkedHashMap<String, CacheEntry<List<GameSearchResult>>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CacheEntry<List<GameSearchResult>>>?
        ) = size > maxCachedSearches
      }
  private val searchCacheMutex = Mutex()

  private fun <T> isExpired(entry: CacheEntry<T>, ttlMs: Long) =
      (System.currentTimeMillis() - entry.createdAtMs) > ttlMs

  // ------------------ public API ------------------

  /**
   * Fetch a single game by its BGG ID.
   *
   * @param gameID The BGG game ID.
   * @return The [Game] corresponding to the given ID.
   * @throws GameSearchException If the game cannot be fetched or parsed.
   */
  override suspend fun getGameById(gameID: String): Game {
    val result = getGamesByIdWithErrors(gameID)
    return result.games.first()
  }

  /**
   * Deprecated and unsupported.
   *
   * This wrapper was previously returning only successfully parsed games. It is now intentionally
   * unsupported — use [getGamesByIdWithErrors] to fetch games and inspect parse errors.
   *
   * @throws UnsupportedOperationException always
   */
  @Deprecated("Unsupported: use getGamesByIdWithErrors(...) instead")
  override suspend fun getGamesById(vararg gameIDs: String): List<Game> {
    throw UnsupportedOperationException(
        "getGamesById(vararg) is deprecated and unsupported. Use getGamesByIdWithErrors(...) instead.")
  }

  /**
   * Search for games whose names contain the specified query string.
   *
   * This method is deprecated and intentionally unsupported. Use [searchGamesByName] which returns
   * lightweight search results (id + name).
   *
   * @throws UnsupportedOperationException always
   */
  @Deprecated("Unsupported: use searchGamesByName(...) instead")
  override suspend fun searchGamesByNameContains(
      query: String,
      maxResults: Int,
      ignoreCase: Boolean
  ): List<Game> {
    throw UnsupportedOperationException(
        "searchGamesByNameContains(...) is deprecated and unsupported. Use searchGamesByName(...) and then fetch full games if needed.")
  }

  /**
   * Fetch multiple games by their BGG IDs, returning both successfully parsed games and any parsing
   * errors.
   *
   * This method does not discard parsing errors. The caller can inspect which games were
   * successfully parsed and which failed.
   *
   * @param gameIDs One or more BGG game IDs (maximum 20 per request).
   * @return A [GameFetchResult] containing successfully parsed games and a list of
   *   [GameParseException] for failed items.
   * @throws IllegalArgumentException If more than 20 IDs are provided.
   * @throws GameSearchException If all games fail to fetch or parse.
   */
  suspend fun getGamesByIdWithErrors(vararg gameIDs: String): GameFetchResult =
      withContext(ioDispatcher) {
        if (gameIDs.isEmpty()) return@withContext GameFetchResult(emptyList(), emptyList())
        require(gameIDs.size <= 20) { "A maximum of 20 IDs can be requested at once." }

        // Check game cache first
        val cached = mutableMapOf<String, Game>()
        val missing = mutableListOf<String>()
        gamesCacheMutex.withLock {
          for (id in gameIDs) {
            val entry = gamesCache[id]
            if (entry != null && !isExpired(entry, gamesTtlMs)) {
              cached[id] = entry.value
            } else {
              missing += id
            }
          }
        }

        if (missing.isEmpty()) {
          val orderedGames = gameIDs.mapNotNull { cached[it] }
          return@withContext GameFetchResult(orderedGames, emptyList())
        }

        // Fetch missing ids
        val ids = missing.joinToString(",")
        val url =
            baseUrl
                .newBuilder()
                .addPathSegment("thing")
                .addQueryParameter("id", ids)
                .addQueryParameter("type", "boardgame")
                .addQueryParameter("stats", "1")
                .build()

        try {
          val doc = fetchDocument(url, "game")
          val (fetchedGames, parseErrors) = parseThings(doc)

          // Store fetched games in game cache
          gamesCacheMutex.withLock {
            for (game in fetchedGames) {
              gamesCache[game.uid] = CacheEntry(game)
            }
          }

          // Reorder games
          val fetchedById = fetchedGames.associateBy { it.uid }
          val orderedGames = gameIDs.mapNotNull { id -> cached[id] ?: fetchedById[id] }

          // Everything fail (or single ID fail)
          if (orderedGames.isEmpty() && parseErrors.isNotEmpty()) {
            val message =
                if (gameIDs.size == 1) {
                  "Failed to parse the game with id '${gameIDs[0]}'. Error: ${parseErrors.first().message}"
                } else {
                  "Failed to parse any game data. Errors: ${parseErrors.joinToString { it.message.toString() }}"
                }
            throw GameSearchException(message)
          }

          return@withContext GameFetchResult(orderedGames, parseErrors)
        } catch (e: IOException) {
          val message =
              if (gameIDs.size == 1) {
                "Failed to fetch game '${gameIDs[0]}': ${e.message}"
              } else {
                "Failed to fetch games: ${e.message}"
              }
          throw GameSearchException(message)
        }
      }

  /**
   * Search for board games whose name contains the given query string.
   *
   * This method only return gameId and name. The caller can then fetch the game he consider as
   * result of the search.
   *
   * @param query The string to search for in game names.
   * @param maxResults Maximum number of results to return.
   * @param ignoreCase Whether the search should ignore case.
   * @return A list of [Game] objects matching the search.
   * @throws GameSearchException If the search request fails.
   */
  suspend fun searchGamesByName(
      query: String,
      maxResults: Int,
      ignoreCase: Boolean
  ): List<GameSearchResult> =
      withContext(ioDispatcher) {
        // Check search cache first
        searchCacheMutex.withLock {
          searchCache[query]?.let {
            if (!isExpired(it, searchesTtlMs)) return@withContext it.value.take(maxResults)
            else searchCache.remove(query)
          }
        }

        val url =
            baseUrl
                .newBuilder()
                .addPathSegment("search")
                .addQueryParameter("query", query)
                .addQueryParameter("type", "boardgame")
                .build()

        try {
          val doc = fetchDocument(url, "search")
          val rawResults = parseSearchResults(doc)

          val ranked = rankSearchResults(rawResults, query, ignoreCase).take(maxResults)

          // Store query result in search cache
          searchCacheMutex.withLock { searchCache[query] = CacheEntry(ranked) }

          return@withContext ranked
        } catch (e: IOException) {
          throw GameSearchException("Failed to search games: ${e.message}")
        }
      }

  // ----------------------------
  // HTTP request helpers
  // ----------------------------

  /**
   * Builds an OkHttp [Request] for the given [url], applying the user-agent and optional
   * authorization token header.
   *
   * This helper centralizes header configuration to avoid duplication.
   *
   * @param url The target endpoint.
   * @return A configured [Request] ready to be executed.
   */
  private fun buildRequest(url: HttpUrl): Request {
    val builder = Request.Builder().url(url)

    if (!authorizationToken.isNullOrBlank()) {
      builder.header("Authorization", "Bearer $authorizationToken")
    }

    builder.header("User-Agent", USER_AGENT)

    return builder.build()
  }

  /**
   * Executes an HTTP request on the given [url] and parses the XML response body into a JDOM
   * [Document].
   *
   * This helper:
   * - Executes the network call.
   * - Throws a [GameSearchException] if the HTTP code is not successful.
   * - Lets [IOException] bubble up so callers can generate contextual error messages.
   *
   * @param url The endpoint to request.
   * @param opName A short operation name used to format error messages consistently.
   * @return The parsed XML [Document].
   * @throws GameSearchException If the HTTP response code is not successful.
   * @throws IOException If the underlying network call fails.
   */
  private fun fetchDocument(url: HttpUrl, opName: String): Document {
    val request = buildRequest(url)

    val response = client.newCall(request).execute()
    response.use {
      if (!response.isSuccessful) {
        throw GameSearchException("BGG $opName request failed: ${response.code}")
      }

      val body = response.body?.string().orEmpty()
      return SAXBuilder().build(StringReader(body))
    }
  }

  // ----------------------------
  // XML parsing helpers
  // ----------------------------

  /**
   * Parse a BGG XML "thing" response and extract games, collecting any parse errors.
   *
   * @param doc The XML [Document] returned by the BGG API.
   * @return A pair containing a list of successfully parsed [Game] objects and a list of
   *   [GameParseException].
   */
  private fun parseThings(doc: Document): Pair<List<Game>, List<GameParseException>> {
    val root = doc.rootElement
    val items = root.getChildren("item").toList()

    val errors = mutableListOf<GameParseException>()

    val games =
        items.mapNotNull { item ->
          val rawId = item.getAttributeValue("id")
          val id = rawId ?: "<unknown>"

          try {
            val thumbnail = item.getChildText("thumbnail").orThrow("thumbnail", rawId)

            val name =
                item
                    .getChildren("name")
                    .firstOrNull { it.getAttributeValue("type") == "primary" }
                    ?.getAttributeValue("value")
                    .orThrow("name", rawId)
            val description = item.getChildText("description").orThrow("description", rawId)

            val minPlayers =
                item
                    .getChild("minplayers")
                    ?.getAttributeValue("value")
                    ?.toIntOrNull()
                    .orThrow("minplayers", rawId)
            val maxPlayers =
                item
                    .getChild("maxplayers")
                    ?.getAttributeValue("value")
                    ?.toIntOrNull()
                    .orThrow("maxplayers", rawId)

            val pollSummary =
                item.getChildren("poll-summary").firstOrNull {
                  it.getAttributeValue("name") == "suggested_numplayers"
                }
            val recommendedPlayers =
                pollSummary
                    ?.getChildren("result")
                    ?.firstOrNull { it.getAttributeValue("name") == "bestwith" }
                    ?.getAttributeValue("value")
                    ?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

            val playingTime =
                item.getChild("playingtime")?.getAttributeValue("value")?.toIntOrNull()
            val minAge = item.getChild("minage")?.getAttributeValue("value")?.toIntOrNull()

            val genres =
                item
                    .getChildren("link")
                    .filter { it.getAttributeValue("type") == "boardgamecategory" }
                    .map { it.getAttributeValue("value") }

            Game(
                uid = id,
                name = name,
                description = description,
                imageURL = thumbnail,
                minPlayers = minPlayers,
                maxPlayers = maxPlayers,
                recommendedPlayers = recommendedPlayers,
                averagePlayTime = playingTime,
                minAge = minAge,
                genres = genres)
          } catch (e: GameParseException) {
            errors.add(e)
            null
          }
        }

    return games to errors
  }

  /**
   * Throws a [GameParseException] if the receiver is null.
   *
   * @param field The field that was expected in the XML.
   * @param id The BGG ID of the game being parsed.
   * @return The non-null value of the receiver.
   * @throws GameParseException if the receiver is null.
   */
  private fun <T> T?.orThrow(field: String, id: String?): T =
      this
          ?: throw GameParseException(
              itemId = id,
              field = field,
              message = "Missing required field '$field' for item id '$id'")

  /**
   * Parse search results XML from BGG API.
   *
   * @param doc The XML [Document] returned by the search API.
   * @return A list of [Game] objects from the search results.
   * @throws NotImplementedError Currently not implemented.
   */
  private fun parseSearchResults(doc: Document): List<GameSearchResult> {
    val root = doc.rootElement
    val items = root.getChildren("item").toList()

    return items.mapNotNull { item ->
      val id = item.getAttributeValue("id") ?: return@mapNotNull null

      val nameElement =
          item.getChild("name").takeIf { it.getAttributeValue("type") == "primary" }
              ?: return@mapNotNull null

      val name = nameElement.getAttributeValue("value") ?: return@mapNotNull null

      GameSearchResult(id = id, name = name)
    }
  }

  // ----------------------------
  // Game search ranking helpers
  // ----------------------------

  /**
   * Rank and sort search results based on their relevance to the query.
   *
   * Ranking order:
   * 1. Exact name match first
   * 2. Partial name match (substring) — earlier occurrence preferred
   * 3. Fallback by Levenshtein distance (edit distance)
   *
   * @param results List of [GameSearchResult] to rank.
   * @param query The search query string.
   * @param ignoreCase Whether to ignore case when comparing names.
   * @return A list of [GameSearchResult] sorted by relevance.
   */
  private fun rankSearchResults(
      results: List<GameSearchResult>,
      query: String,
      ignoreCase: Boolean
  ): List<GameSearchResult> {
    val q = if (ignoreCase) query.lowercase() else query

    return results.sortedWith(
        compareBy<GameSearchResult> {
              // 1. Exact match first
              val n = if (ignoreCase) it.name.lowercase() else it.name
              if (n == q) 0 else 1
            }
            .thenBy {
              // 2. Contains match (smaller index = better)
              val n = if (ignoreCase) it.name.lowercase() else it.name
              val idx = n.indexOf(q)
              if (idx >= 0) idx else Int.MAX_VALUE
            }
            .thenBy {
              // 3. Levenshtein distance fallback
              levenshtein((if (ignoreCase) it.name.lowercase() else it.name), q)
            })
  }

  /**
   * Compute the Levenshtein distance (edit distance) between two strings.
   *
   * Used as a fallback ranking metric for search results to measure similarity.
   *
   * @param a First string.
   * @param b Second string.
   * @return The number of single-character edits (insertions, deletions, substitutions) needed to
   *   transform [a] into [b].
   */
  private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j

    for (i in 1..a.length) {
      for (j in 1..b.length) {
        dp[i][j] =
            minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
      }
    }
    return dp[a.length][b.length]
  }
}
