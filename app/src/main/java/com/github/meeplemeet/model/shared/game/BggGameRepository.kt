package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.HttpClientProvider
import com.github.meeplemeet.model.GameSearchException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Document

class BggGameRepository(
    private val client: OkHttpClient = HttpClientProvider.client,
    private val baseUrl: HttpUrl = DEFAULT_URL,
    private val authorizationToken: String? = null
) : GameRepository {
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
  }

  override suspend fun getGames(maxResults: Int): List<Game> {
    throw NotImplementedError("getGames() is intentionally not implemented and will be removed.")
  }

  override suspend fun getGameById(gameID: String): Game =
      withContext(Dispatchers.IO) {
        val url =
            baseUrl
                .newBuilder()
                .addPathSegment("thing")
                .addQueryParameter("id", gameID)
                .addQueryParameter("type", "boardgame")
                .addQueryParameter("stats", "1")
                .build()

        val builder = Request.Builder().url(url)

        if (!authorizationToken.isNullOrBlank()) {
          builder.header("Authorization", "Bearer $authorizationToken")
        }

        builder.header("User-Agent", USER_AGENT)

        val request = builder.build()

        try {
          val response = client.newCall(request).execute()
          response.use {
            if (!response.isSuccessful) {
              throw GameSearchException("BGG thing request failed: ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val doc = parseXml(body)
            val game = parseThing(doc) ?: throw GameSearchException("Game not found: $gameID")

            return@withContext game
          }
        } catch (e: IOException) {
          throw GameSearchException("Failed to fetch game by id: ${e.message}")
        }
      }

  override suspend fun getGamesByGenre(genreID: Int, maxResults: Int): List<Game> {
    throw NotImplementedError(
        "getGamesByGenre() is intentionally not implemented and will be removed.")
  }

  override suspend fun getGamesByGenres(genreIDs: List<Int>, maxResults: Int): List<Game> {
    throw NotImplementedError(
        "getGamesByGenres() is intentionally not implemented and will be removed.")
  }

  override suspend fun searchGamesByNameContains(
      query: String,
      maxResults: Int,
      ignoreCase: Boolean
  ): List<Game> =
      withContext(Dispatchers.IO) {
        val url =
            baseUrl
                .newBuilder()
                .addPathSegment("search")
                .addQueryParameter("query", query)
                .addQueryParameter("type", "boardgame")
                .build()

        val builder = Request.Builder().url(url)

        if (!authorizationToken.isNullOrBlank()) {
          builder.header("Authorization", "Bearer $authorizationToken")
        }

        builder.header("User-Agent", USER_AGENT)

        val request = builder.build()

        try {
          val response = client.newCall(request).execute()
          response.use {
            if (!response.isSuccessful) {
              throw GameSearchException("BGG thing request failed: ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val doc = parseXml(body)
            val results =
                parseSearchResults(doc)
                    .let { list ->
                      if (ignoreCase) list.filter { it.name.contains(query, ignoreCase = true) }
                      else list.filter { it.name.contains(query) }
                    }
                    .take(maxResults)

            return@withContext results
          }
        } catch (e: IOException) {
          throw GameSearchException("Failed to search games: ${e.message}")
        }
      }

  // ----------------------------
  // XML parsing helpers
  // ----------------------------
  private fun parseXml(xml: String): Document {
    throw NotImplementedError("parseXml() is not implemented yet")
  }

  private fun parseThing(doc: Document): Game? {
    throw NotImplementedError("parseThing() is not implemented yet")
  }

  private fun parseSearchResults(doc: Document): List<Game> {
    throw NotImplementedError("parseSearchResult() is not implemented yet")
  }
}
