package com.github.meeplemeet.api

import com.github.meeplemeet.model.FirebaseFunctionsWrapper
import com.github.meeplemeet.model.GameFetchException
import com.github.meeplemeet.model.GameSearchException
import com.github.meeplemeet.model.shared.game.CloudBggGameRepository
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import io.mockk.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class CloudBggGameRepositoryTest {

  private lateinit var mockFunctionsWrapper: FirebaseFunctionsWrapper
  private lateinit var repository: CloudBggGameRepository

  @Before
  fun setup() {
    mockFunctionsWrapper = mockk(relaxed = true)
    repository = CloudBggGameRepository(mockFunctionsWrapper)
  }

  @After
  fun teardown() {
    clearAllMocks()
  }

  // ==================== Helper Functions ====================

  private fun <T> mockSuccessfulCall(data: T): Task<T> {
    return Tasks.forResult(data)
  }

  private fun <T> mockFailedCall(exception: Exception): Task<T> {
    return Tasks.forException(exception)
  }

  // ==================== getGameById Tests ====================

  @Test
  fun getGameByIdReturnsValidGame() = runTest {
    val responseData =
        listOf(
            mapOf(
                "uid" to "181",
                "name" to "Risk",
                "description" to "A classic war game.",
                "imageURL" to "https://cf.geekdo-images.com/example.jpg",
                "minPlayers" to 2,
                "maxPlayers" to 6,
                "recommendedPlayers" to 4,
                "averagePlayTime" to 120,
                "minAge" to 10,
                "genres" to listOf("Territory Building", "Wargame")))

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

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
  fun getGameByIdThrowsExceptionOnCloudFunctionError() = runTest {
    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockFailedCall(RuntimeException("Cloud Function failed"))

    val exception = assertFailsWith<GameFetchException> { repository.getGameById("181") }

    assertTrue(exception.message!!.contains("Failed calling Cloud Function"))
  }

  @Test
  fun getGameByIdHandlesNullableFields() = runTest {
    val responseData =
        listOf(
            mapOf(
                "uid" to "123",
                "name" to "Minimal Game",
                "description" to "A game with minimal data",
                "imageURL" to "https://example.com/img.jpg",
                "minPlayers" to 2,
                "maxPlayers" to 4,
                "genres" to emptyList<String>()))

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

    val game = repository.getGameById("123")

    assertEquals("123", game.uid)
    assertEquals("Minimal Game", game.name)
    assertNull(game.recommendedPlayers)
    assertNull(game.averagePlayTime)
    assertNull(game.minAge)
    assertEquals(0, game.genres.size)
  }

  // ==================== getGamesById Tests ====================

  @Test
  fun getGamesByIdReturnsMultipleGames() = runTest {
    val responseData =
        listOf(
            mapOf(
                "uid" to "181",
                "name" to "Risk",
                "description" to "War game",
                "imageURL" to "https://example.com/risk.jpg",
                "minPlayers" to 2,
                "maxPlayers" to 6,
                "recommendedPlayers" to 4,
                "averagePlayTime" to 120,
                "minAge" to 10,
                "genres" to listOf("Wargame")),
            mapOf(
                "uid" to "13",
                "name" to "CATAN",
                "description" to "Resource management game",
                "imageURL" to "https://example.com/catan.jpg",
                "minPlayers" to 3,
                "maxPlayers" to 4,
                "recommendedPlayers" to 4,
                "averagePlayTime" to 120,
                "minAge" to 10,
                "genres" to listOf("Economic")))

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

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
  fun getGamesByIdPassesCorrectParameters() = runTest {
    val responseData =
        listOf(
            mapOf(
                "uid" to "1",
                "name" to "Game",
                "description" to "Test",
                "imageURL" to "url",
                "minPlayers" to 1,
                "maxPlayers" to 4,
                "genres" to emptyList<String>()))

    val capturedParams = slot<Any>()
    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", capture(capturedParams)) } returns
        mockSuccessfulCall(responseData)

    repository.getGamesById("1", "2", "3")

    verify { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) }

    val params = capturedParams.captured as Map<*, *>
    assertEquals(listOf("1", "2", "3"), params["ids"])
  }

  @Test
  fun getGamesByIdReturnsEmptyListForEmptyResponse() = runTest {
    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(emptyList<Any>())

    val games = repository.getGamesById("999")

    assertEquals(0, games.size)
  }

  @Test
  fun getGamesByIdThrowsOnInvalidResponseType() = runTest {
    // Return a list with null elements to trigger parsing error
    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(listOf(null))

    val exception = assertFailsWith<GameFetchException> { repository.getGameById("1") }

    assertTrue(exception.message!!.contains("Failed parsing JSON"))
  }

  // ==================== searchGamesByName Tests ====================

  @Test
  fun searchGamesByNameReturnsResults() = runTest {
    val responseData =
        listOf(
            mapOf("id" to "1406", "name" to "Monopoly"),
            mapOf("id" to "238393", "name" to "Monolith Arena"),
            mapOf("id" to "365359", "name" to "Mono"))

    every { mockFunctionsWrapper.call<List<*>>("searchGames", any()) } returns
        mockSuccessfulCall(responseData)

    val results = repository.searchGamesByName("mono", 10)

    assertEquals(3, results.size)
    assertTrue(results.any { it.name == "Monopoly" })
    assertTrue(results.any { it.name == "Monolith Arena" })
    assertTrue(results.any { it.name == "Mono" })
  }

  @Test
  fun searchGamesByNamePassesCorrectParameters() = runTest {
    every { mockFunctionsWrapper.call<List<*>>("searchGames", any()) } returns
        mockSuccessfulCall(emptyList<Any>())

    repository.searchGamesByName("test query", 25)

    val capturedParams = slot<Any>()
    verify { mockFunctionsWrapper.call<List<*>>("searchGames", capture(capturedParams)) }

    val params = capturedParams.captured as Map<*, *>
    assertEquals("test query", params["query"])
    assertEquals(25, params["maxResults"])
  }

  @Test
  fun searchGamesByNameThrowsOnCloudFunctionError() = runTest {
    every { mockFunctionsWrapper.call<List<*>>("searchGames", any()) } returns
        mockFailedCall(RuntimeException("Cloud Function failed"))

    val exception =
        assertFailsWith<GameSearchException> { repository.searchGamesByName("test", 10) }

    assertTrue(exception.message!!.contains("Failed calling Cloud Function"))
  }

  @Test
  fun searchGamesByNameReturnsEmptyListOnNoResults() = runTest {
    every { mockFunctionsWrapper.call<List<*>>("searchGames", any()) } returns
        mockSuccessfulCall(emptyList<Any>())

    val results = repository.searchGamesByName("nonexistentgame", 10)

    assertEquals(0, results.size)
  }

  @Test
  fun searchGamesByNameThrowsOnInvalidResponseType() = runTest {
    // Return a list with null elements to trigger parsing error
    every { mockFunctionsWrapper.call<List<*>>("searchGames", any()) } returns
        mockSuccessfulCall(listOf(null))

    val exception =
        assertFailsWith<GameSearchException> { repository.searchGamesByName("query", 10) }

    assertTrue(exception.message!!.contains("Failed parsing JSON"))
  }

  // ==================== JSON Parsing Edge Cases ====================

  @Test
  fun parsesGameWithAllNullOptionalFields() = runTest {
    val responseData =
        listOf(
            mapOf(
                "uid" to "999",
                "name" to "Test Game",
                "description" to "Test Description",
                "imageURL" to "https://test.com/img.jpg",
                "minPlayers" to 1,
                "maxPlayers" to 5,
                "genres" to emptyList<String>()))

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

    val game = repository.getGameById("999")

    assertEquals("999", game.uid)
    assertNull(game.recommendedPlayers)
    assertNull(game.averagePlayTime)
    assertNull(game.minAge)
    assertEquals(0, game.genres.size)
  }

  @Test
  fun parsesMultipleGenresCorrectly() = runTest {
    val responseData =
        listOf(
            mapOf(
                "uid" to "1",
                "name" to "Multi-Genre Game",
                "description" to "Has many genres",
                "imageURL" to "url",
                "minPlayers" to 2,
                "maxPlayers" to 4,
                "genres" to listOf("Strategy", "Economic", "Territory Building")))

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

    val game = repository.getGameById("1")

    assertEquals(3, game.genres.size)
    assertTrue(game.genres.contains("Strategy"))
    assertTrue(game.genres.contains("Economic"))
    assertTrue(game.genres.contains("Territory Building"))
  }

  @Test
  fun handlesInvalidJsonGracefully() = runTest {
    // Simulate malformed data that can't be parsed
    val responseData = listOf("invalid json structure")

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

    assertFailsWith<GameFetchException> { repository.getGameById("1") }
  }

  // ==================== Edge Cases ====================

  @Test
  fun getGamesByIdWithSingleIdCallsCorrectEndpoint() = runTest {
    val responseData =
        listOf(
            mapOf(
                "uid" to "999",
                "name" to "Single Game",
                "description" to "Test",
                "imageURL" to "url",
                "minPlayers" to 1,
                "maxPlayers" to 2,
                "genres" to emptyList<String>()))

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

    val game = repository.getGameById("999")

    verify(exactly = 1) { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) }
    assertEquals("Single Game", game.name)
  }

  @Test
  fun getGamesByIdWithMaximumAllowedIds() = runTest {
    val ids = (1..20).map { it.toString() }.toTypedArray()
    val responseData =
        (1..20).map { i ->
          mapOf(
              "uid" to i.toString(),
              "name" to "Game $i",
              "description" to "Test",
              "imageURL" to "url",
              "minPlayers" to 2,
              "maxPlayers" to 4,
              "genres" to emptyList<String>())
        }

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

    val games = repository.getGamesById(*ids)

    assertEquals(20, games.size)
    verify(exactly = 1) { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) }
  }

  @Test
  fun searchGamesByNameWithSingleResult() = runTest {
    val responseData = listOf(mapOf("id" to "123", "name" to "Unique Game"))

    every { mockFunctionsWrapper.call<List<*>>("searchGames", any()) } returns
        mockSuccessfulCall(responseData)

    val results = repository.searchGamesByName("unique", 1)

    assertEquals(1, results.size)
    assertEquals("123", results[0].id)
    assertEquals("Unique Game", results[0].name)
  }

  @Test
  fun parsesGameWithPartialOptionalFields() = runTest {
    val responseData =
        listOf(
            mapOf(
                "uid" to "456",
                "name" to "Partial Game",
                "description" to "Has some optional fields",
                "imageURL" to "https://test.com/img.jpg",
                "minPlayers" to 2,
                "maxPlayers" to 5,
                "recommendedPlayers" to 3,
                "minAge" to 8,
                // averagePlayTime is missing
                "genres" to listOf("Strategy")))

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

    val game = repository.getGameById("456")

    assertEquals("456", game.uid)
    assertEquals(3, game.recommendedPlayers)
    assertEquals(8, game.minAge)
    assertNull(game.averagePlayTime)
    assertEquals(1, game.genres.size)
  }

  @Test
  fun handlesCloudFunctionExceptionWithDetailedMessage() = runTest {
    val detailedException = RuntimeException("Detailed error: connection timeout")
    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockFailedCall(detailedException)

    val exception = assertFailsWith<GameFetchException> { repository.getGameById("1") }

    assertTrue(exception.message!!.contains("Detailed error"))
    assertNotNull(exception.cause)
  }

  @Test
  fun searchGamesByNameHandlesExceptionWithCause() = runTest {
    val originalException = RuntimeException("Network issue")
    every { mockFunctionsWrapper.call<List<*>>("searchGames", any()) } returns
        mockFailedCall(originalException)

    val exception = assertFailsWith<GameSearchException> { repository.searchGamesByName("test", 5) }

    assertNotNull(exception.cause)
    assertTrue(exception.message!!.contains("Failed calling Cloud Function"))
    assertTrue(exception.cause?.message!!.contains("Network issue"))
  }

  @Test
  fun getGamesByIdHandlesParsingExceptionGracefully() = runTest {
    // Response with invalid structure that will fail JSON parsing
    val responseData =
        listOf(
            mapOf("invalid_field" to "value") // Missing required fields
            )

    every { mockFunctionsWrapper.call<List<*>>("getGamesByIds", any()) } returns
        mockSuccessfulCall(responseData)

    val exception = assertFailsWith<GameFetchException> { repository.getGameById("1") }

    assertTrue(exception.message!!.contains("Failed parsing JSON"))
  }

  @Test
  fun searchGamesByNameHandlesParsingException() = runTest {
    val responseData =
        listOf(
            mapOf("wrong_key" to "value") // Missing 'id' and 'name' fields
            )

    every { mockFunctionsWrapper.call<List<*>>("searchGames", any()) } returns
        mockSuccessfulCall(responseData)

    val exception = assertFailsWith<GameSearchException> { repository.searchGamesByName("test", 5) }

    assertTrue(exception.message!!.contains("Failed parsing JSON"))
  }
}
