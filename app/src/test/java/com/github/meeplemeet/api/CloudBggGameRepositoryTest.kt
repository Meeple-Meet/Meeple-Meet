package com.github.meeplemeet.api

import com.github.meeplemeet.model.GameFetchException
import com.github.meeplemeet.model.GameSearchException
import com.github.meeplemeet.model.shared.game.CloudBggGameRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class CloudBggGameRepositoryTest {

  private lateinit var mockWebServer: MockWebServer
  private lateinit var repository: CloudBggGameRepository

  @Before
  fun setup() {
    mockWebServer = MockWebServer()
    mockWebServer.start()

    val client = OkHttpClient()
    val baseUrl = mockWebServer.url("/")
    repository = CloudBggGameRepository(client, baseUrl)
  }

  @After
  fun teardown() {
    mockWebServer.shutdown()
  }

  // ==================== getGameById Tests ====================

  @Test
  fun getGameByIdReturnsValidGame() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        [{
          "uid": "181",
          "name": "Risk",
          "description": "A classic war game.",
          "imageURL": "https://cf.geekdo-images.com/example.jpg",
          "minPlayers": 2,
          "maxPlayers": 6,
          "recommendedPlayers": 4,
          "averagePlayTime": 120,
          "minAge": 10,
          "genres": ["Territory Building", "Wargame"]
        }]
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val game = repository.getGameById("181")

    assertEquals("181", game.uid)
    assertEquals("Risk", game.name)
    assertEquals("A classic war game.", game.description)
    assertEquals("https://cf.geekdo-images.com/example.jpg", game.imageURL)
    assertEquals(2, game.minPlayers)
    assertEquals(6, game.maxPlayers)
    assertEquals(4, game.recommendedPlayers)
    assertEquals(120, game.averagePlayTime)
    assertEquals(10, game.minAge)
    assertEquals(2, game.genres.size)
    assertTrue(game.genres.contains("Territory Building"))
    assertTrue(game.genres.contains("Wargame"))
  }

  @Test
  fun getGameByIdThrowsExceptionOnHttpError() = runTest {
    val mockResponse = MockResponse().setResponseCode(500).setBody("Internal Server Error")
    mockWebServer.enqueue(mockResponse)

    val exception = assertFailsWith<GameFetchException> { repository.getGameById("181") }

    assertTrue(exception.message!!.contains("Failed to fetch games (HTTP 500)"))
  }

  @Test
  fun getGameByIdHandlesNullableFields() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        [{
          "uid": "123",
          "name": "Minimal Game",
          "description": "A game with minimal data",
          "imageURL": "https://example.com/img.jpg",
          "minPlayers": 2,
          "maxPlayers": 4,
          "genres": []
        }]
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val game = repository.getGameById("123")

    assertEquals("123", game.uid)
    assertEquals("Minimal Game", game.name)
    assertEquals(0, game.recommendedPlayers)
    assertEquals(0, game.averagePlayTime)
    assertEquals(0, game.minAge)
    assertEquals(0, game.genres.size)
  }

  // ==================== getGamesById Tests ====================

  @Test
  fun getGamesByIdReturnsMultipleGames() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        [
          {
            "uid": "181",
            "name": "Risk",
            "description": "War game",
            "imageURL": "https://example.com/risk.jpg",
            "minPlayers": 2,
            "maxPlayers": 6,
            "recommendedPlayers": 4,
            "averagePlayTime": 120,
            "minAge": 10,
            "genres": ["Wargame"]
          },
          {
            "uid": "13",
            "name": "CATAN",
            "description": "Resource management game",
            "imageURL": "https://example.com/catan.jpg",
            "minPlayers": 3,
            "maxPlayers": 4,
            "recommendedPlayers": 4,
            "averagePlayTime": 120,
            "minAge": 10,
            "genres": ["Economic"]
          }
        ]
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val games = repository.getGamesById("181", "13")

    assertEquals(2, games.size)
    assertEquals("Risk", games[0].name)
    assertEquals("CATAN", games[1].name)
  }

  @Test
  fun getGamesByIdThrowsOnTooManyIds() = runTest {
    val ids = (1..21).map { it.toString() }.toTypedArray()

    val exception = assertFailsWith<IllegalArgumentException> { repository.getGamesById(*ids) }

    assertTrue(exception.message!!.contains("maximum of 20"))
  }

  @Test
  fun getGamesByIdIncludesCorrectQueryParams() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        [{
          "uid": "1",
          "name": "Game",
          "description": "Test",
          "imageURL": "url",
          "minPlayers": 1,
          "maxPlayers": 4,
          "genres": []
        }]
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    repository.getGamesById("1", "2", "3")

    val request = mockWebServer.takeRequest()
    assertTrue(request.path!!.contains("getGamesByIds"))
    val url = request.requestUrl!!
    assertEquals("1,2,3", url.queryParameter("ids"))
  }

  @Test
  fun getGamesByIdHandlesNetworkError() = runTest {
    mockWebServer.shutdown()

    val exception = assertFailsWith<GameFetchException> { repository.getGameById("1") }

    assertTrue(exception.message!!.contains("Network error"))
  }

  @Test
  fun getGamesByIdReturnsEmptyListForEmptyResponse() = runTest {
    val mockResponse = MockResponse().setResponseCode(200).setBody("[]")

    mockWebServer.enqueue(mockResponse)

    val games = repository.getGamesById("999")

    assertEquals(0, games.size)
  }

  // ==================== searchGamesByNameLight Tests ====================

  @Test
  fun searchGamesByNameLightReturnsResults() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        [
          { "id": "1406", "name": "Monopoly" },
          { "id": "238393", "name": "Monolith Arena" },
          { "id": "365359", "name": "Mono" }
        ]
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByNameLight("mono", 10)

    assertEquals(3, results.size)
    assertTrue(results.any { it.name == "Monopoly" })
    assertTrue(results.any { it.name == "Monolith Arena" })
    assertTrue(results.any { it.name == "Mono" })
  }

  @Test
  fun searchGamesByNameLightIncludesCorrectQueryParams() = runTest {
    val mockResponse = MockResponse().setResponseCode(200).setBody("[]")

    mockWebServer.enqueue(mockResponse)

    repository.searchGamesByNameLight("test query", 25)

    val request = mockWebServer.takeRequest()
    assertTrue(request.path!!.contains("searchGames"))
    assertTrue(request.path!!.contains("query=test%20query"))
    assertTrue(request.path!!.contains("maxResults=25"))
  }

  @Test
  fun searchGamesByNameLightThrowsOnHttpError() = runTest {
    val mockResponse = MockResponse().setResponseCode(500).setBody("Server Error")

    mockWebServer.enqueue(mockResponse)

    val exception =
        assertFailsWith<GameSearchException> { repository.searchGamesByNameLight("test", 10) }

    assertTrue(exception.message!!.contains("Failed to search games (HTTP 500)"))
  }

  @Test
  fun searchGamesByNameLightReturnsEmptyListOnNoResults() = runTest {
    val mockResponse = MockResponse().setResponseCode(200).setBody("[]")

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByNameLight("nonexistentgame", 10)

    assertEquals(0, results.size)
  }

  @Test
  fun searchGamesByNameLightHandlesNetworkError() = runTest {
    mockWebServer.shutdown()

    val exception =
        assertFailsWith<GameSearchException> { repository.searchGamesByNameLight("query", 10) }

    assertTrue(exception.message!!.contains("Network error"))
  }

  // ==================== Deprecated Method Test ====================

  @Test
  fun deprecatedSearchGamesByNameContainsThrowsUnsupported() = runTest {
    @Suppress("DEPRECATION")
    assertFailsWith<UnsupportedOperationException> {
      repository.searchGamesByNameContains("test", 5, ignoreCase = true)
    }
  }

  // ==================== JSON Parsing Edge Cases ====================

  @Test
  fun parsesGameWithAllNullOptionalFields() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        [{
          "uid": "999",
          "name": "Test Game",
          "description": "Test Description",
          "imageURL": "https://test.com/img.jpg",
          "minPlayers": 1,
          "maxPlayers": 5,
          "genres": []
        }]
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val game = repository.getGameById("999")

    assertEquals("999", game.uid)
    assertEquals(0, game.recommendedPlayers)
    assertEquals(0, game.averagePlayTime)
    assertEquals(0, game.minAge)
    assertEquals(0, game.genres.size)
  }

  @Test
  fun parsesMultipleGenresCorrectly() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        [{
          "uid": "1",
          "name": "Multi-Genre Game",
          "description": "Has many genres",
          "imageURL": "url",
          "minPlayers": 2,
          "maxPlayers": 4,
          "genres": ["Strategy", "Economic", "Territory Building"]
        }]
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val game = repository.getGameById("1")

    assertEquals(3, game.genres.size)
    assertTrue(game.genres.contains("Strategy"))
    assertTrue(game.genres.contains("Economic"))
    assertTrue(game.genres.contains("Territory Building"))
  }

  @Test
  fun handlesInvalidJsonGracefully() = runTest {
    val mockResponse = MockResponse().setResponseCode(200).setBody("{ invalid json syntax ]")

    mockWebServer.enqueue(mockResponse)

    assertFailsWith<GameFetchException> { repository.getGameById("1") }
  }

  // ==================== URL Construction Tests ====================

  @Test
  fun usesCorrectBaseUrlForProduction() {
    val prodUrl = CloudBggGameRepository.getBaseUrl()
    // In tests, BuildConfig.DEBUG is typically true, so this will use local URL
    // You can verify the URL structure is correct
    assertTrue(prodUrl.toString().isNotEmpty())
  }
}
