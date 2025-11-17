package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.HttpClientProvider
import com.github.meeplemeet.model.GameParseException
import com.github.meeplemeet.model.GameSearchException
import java.io.IOException
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jdom2.Document
import org.jdom2.input.SAXBuilder

data class GameFetchResult(val games: List<Game>, val errors: List<GameParseException>)

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

  override suspend fun getGameById(gameID: String): Game {
    val result = getGamesByIdWithErrors(gameID)
    return result.games.first()
  }

  @Deprecated("Use getGamesByIdWithErrors() for partial results and parse errors")
  override suspend fun getGamesById(vararg gameIDs: String): List<Game> {
    val result = getGamesByIdWithErrors(*gameIDs)
    return result.games
  }

  suspend fun getGamesByIdWithErrors(vararg gameIDs: String): GameFetchResult =
      withContext(Dispatchers.IO) {
        if (gameIDs.isEmpty()) return@withContext GameFetchResult(emptyList(), emptyList())
        require(gameIDs.size <= 20) { "A maximum of 20 IDs can be requested at once." }

        val ids = gameIDs.joinToString(",")
        val url =
            baseUrl
                .newBuilder()
                .addPathSegment("thing")
                .addQueryParameter("id", ids)
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
            val doc = SAXBuilder().build(StringReader(body))
            val (games, parseErrors) = parseThings(doc)

            // Everything fail (or single ID fail)
            if (games.isEmpty() && parseErrors.isNotEmpty()) {
              val message =
                  if (gameIDs.size == 1) {
                    "Failed to parse the game with id '${gameIDs[0]}'. Error: ${parseErrors.first().message}"
                  } else {
                    "Failed to parse any game data. Errors: ${parseErrors.joinToString { it.message.toString() }}"
                  }
              throw GameSearchException(message)
            }

            return@withContext GameFetchResult(games, parseErrors)
          }
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
                .addQueryParameter("exact", "1")
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
            val doc = SAXBuilder().build(StringReader(body))
            val results = parseSearchResults(doc)

            return@withContext results
                .filter { it.name.contains(query, ignoreCase) }
                .take(maxResults)
          }
        } catch (e: IOException) {
          throw GameSearchException("Failed to search games: ${e.message}")
        }
      }

  // ----------------------------
  // XML parsing helpers
  // ----------------------------
  private fun parseThings(doc: Document): Pair<List<Game>, List<GameParseException>> {
    val root = doc.rootElement
    val items = root.getChildren("item")

    val errors = mutableListOf<GameParseException>()

    val games =
        items.mapNotNull { item ->
          val rawId = item.getAttributeValue("id")
          val id = rawId ?: "<unknown>"

          try {
            val thumbnail = item.getChildText("thumbnail").orThrow("thumbnail", rawId)
            val image = item.getChildText("image").orThrow("image", rawId)

            val name =
                item
                    .getChildren("name")
                    .firstOrNull { it.getAttributeValue("type") == "primary" }
                    ?.getAttributeValue("value")
                    .orThrow("name", rawId)
            val description = item.getChildText("description").orThrow("description", rawId)

            val minPlayers =
                item
                    .getChild("minPlayers")
                    ?.getAttributeValue("value")
                    ?.toIntOrNull()
                    .orThrow("minPlayers", rawId)
            val maxPlayers =
                item
                    .getChild("maxPlayers")
                    ?.getAttributeValue("value")
                    ?.toIntOrNull()
                    .orThrow("maxPlayers", rawId)

            val pollSummary =
                item.getChildren("poll-summary").firstOrNull {
                  it.getAttributeValue("name") == "suggested_numplayers"
                }
            val recommendedPlayers =
                pollSummary
                    ?.getChildren("result")
                    ?.firstOrNull { it.getAttributeValue("name") == "bestwith" }
                    ?.getAttributeValue("value")
                    ?.toIntOrNull()
                    .orThrow("recommendedPlayers", rawId)

            val playingTime =
                item
                    .getChild("playingtime")
                    ?.getAttributeValue("value")
                    ?.toIntOrNull()
                    .orThrow("playingTime", rawId)
            val minAge =
                item
                    .getChild("minage")
                    ?.getAttributeValue("value")
                    ?.toIntOrNull()
                    .orThrow("minAge", rawId)

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

  private fun <T> T?.orThrow(field: String, id: String?): T =
      this
          ?: throw GameParseException(
              itemId = id,
              field = field,
              message = "Missing required field '$field' for item id '$id'")

  private fun parseSearchResults(doc: Document): List<Game> {
    throw NotImplementedError("parseSearchResult() is not implemented yet")
  }
}
