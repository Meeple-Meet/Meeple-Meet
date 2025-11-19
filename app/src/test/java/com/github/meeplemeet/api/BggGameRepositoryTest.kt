// Test suite initially generated with Claude 4.5, following Meeple Meet's global test architecture.
// Then, the test suite was manually reviewed, cleaned up, and debugged.
package com.github.meeplemeet.api

import com.github.meeplemeet.model.GameSearchException
import com.github.meeplemeet.model.shared.game.BggGameRepository
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

class BggGameRepositoryTest {

  private lateinit var mockWebServer: MockWebServer
  private lateinit var repository: BggGameRepository

  @Before
  fun setup() {
    mockWebServer = MockWebServer()
    mockWebServer.start()

    val client = OkHttpClient()
    val baseUrl = mockWebServer.url("/xmlapi2")
    repository = BggGameRepository(client, baseUrl)
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
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="181">
            <thumbnail>https://cf.geekdo-images.com/example.jpg</thumbnail>
            <name type="primary" value="Risk" />
            <description>A classic war game.</description>
            <minplayers value="2" />
            <maxplayers value="6" />
            <poll-summary name="suggested_numplayers">
              <result name="bestwith" value="Best with 4 players" />
            </poll-summary>
            <playingtime value="120" />
            <minage value="10" />
            <link type="boardgamecategory" value="Territory Building" />
            <link type="boardgamecategory" value="Wargame" />
          </item>
        </items>
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
    val mockResponse = MockResponse().setResponseCode(500)
    mockWebServer.enqueue(mockResponse)

    val exception = assertFailsWith<GameSearchException> { repository.getGameById("181") }

    assertTrue(exception.message!!.contains("BGG game request failed"))
  }

  @Test
  fun getGameByIdThrowsExceptionOnMissingFields() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="999">
            <thumbnail>https://example.com/img.jpg</thumbnail>
            <name type="primary" value="Incomplete Game" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val exception = assertFailsWith<GameSearchException> { repository.getGameById("999") }

    assertTrue(exception.message!!.contains("Failed to parse the game with id '999'"))
  }

  // ==================== getGamesByIdWithErrors Tests ====================

  @Test
  fun getGamesByIdWithErrorsReturnsMultipleGames() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="181">
            <thumbnail>https://example.com/risk.jpg</thumbnail>
            <name type="primary" value="Risk" />
            <description>War game</description>
            <minplayers value="2" />
            <maxplayers value="6" />
            <playingtime value="120" />
            <minage value="10" />
          </item>
          <item type="boardgame" id="13">
            <thumbnail>https://example.com/catan.jpg</thumbnail>
            <name type="primary" value="CATAN" />
            <description>Resource management game</description>
            <minplayers value="3" />
            <maxplayers value="4" />
            <poll-summary name="suggested_numplayers">
              <result name="bestwith" value="Best with 4 players" />
            </poll-summary>
            <playingtime value="120" />
            <minage value="10" />
            <link type="boardgamecategory" value="Economic" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val result = repository.getGamesByIdWithErrors("181", "13")

    assertEquals(2, result.games.size)
    assertEquals(0, result.errors.size)
    assertEquals("Risk", result.games[0].name)
    assertEquals("CATAN", result.games[1].name)
  }

  @Test
  fun getGamesByIdWithErrorsReturnsPartialResults() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="181">
            <thumbnail>https://example.com/risk.jpg</thumbnail>
            <name type="primary" value="Risk" />
            <description>War game</description>
            <minplayers value="2" />
            <maxplayers value="6" />
            <playingtime value="120" />
            <minage value="10" />
          </item>
          <item type="boardgame" id="999">
            <thumbnail>https://example.com/broken.jpg</thumbnail>
            <name type="primary" value="Broken Game" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val result = repository.getGamesByIdWithErrors("181", "999")

    assertEquals(1, result.games.size)
    assertEquals(1, result.errors.size)
    assertEquals("Risk", result.games[0].name)
    assertTrue(result.errors[0].message!!.contains("description"))
  }

  @Test
  fun getGamesByIdWithErrorsThrowsOnEmptyIdList() = runTest {
    val result = repository.getGamesByIdWithErrors()

    assertEquals(0, result.games.size)
    assertEquals(0, result.errors.size)
  }

  @Test
  fun getGamesByIdWithErrorsThrowsOnTooManyIds() = runTest {
    val ids = (1..21).map { it.toString() }.toTypedArray()

    val exception =
        assertFailsWith<IllegalArgumentException> { repository.getGamesByIdWithErrors(*ids) }

    assertTrue(exception.message!!.contains("maximum of 20"))
  }

  @Test
  fun getGamesByIdWithErrorsThrowsWhenAllGamesFail() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="999">
            <thumbnail>https://example.com/broken.jpg</thumbnail>
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val exception =
        assertFailsWith<GameSearchException> { repository.getGamesByIdWithErrors("999") }

    assertTrue(exception.message!!.contains("Failed to parse the game with id '999'."))
  }

  // ==================== searchGamesByName Tests ====================

  @Test
  fun searchGamesByNameReturnsResults() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="338" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1406">
            <name type="primary" value="Monopoly" />
          </item>
          <item type="boardgame" id="238393">
            <name type="primary" value="Monolith Arena" />
          </item>
          <item type="boardgame" id="365359">
            <name type="primary" value="Mono" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("mono", 10, ignoreCase = true)

    assertEquals(3, results.size)
    assertTrue(results.any { it.name == "Monopoly" })
    assertTrue(results.any { it.name == "Monolith Arena" })
    assertTrue(results.any { it.name == "Mono" })
  }

  @Test
  fun searchGamesByNameRanksExactMatchFirst() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="3" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Monopoly Arena" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Mono" />
          </item>
          <item type="boardgame" id="3">
            <name type="primary" value="Monopoly" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("mono", 10, ignoreCase = true)

    // "Mono" should be ranked first (exact match)
    assertEquals("Mono", results[0].name)
  }

  @Test
  fun searchGamesByNameRespectsMaxResults() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="5" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Card Game 1" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Card Game 2" />
          </item>
          <item type="boardgame" id="3">
            <name type="primary" value="Card Game 3" />
          </item>
          <item type="boardgame" id="4">
            <name type="primary" value="Card Game 4" />
          </item>
          <item type="boardgame" id="5">
            <name type="primary" value="Card Game 5" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("card", 3, ignoreCase = true)

    assertEquals(3, results.size)
  }

  @Test
  fun searchGamesByNameThrowsExceptionOnHttpError() = runTest {
    val mockResponse = MockResponse().setResponseCode(500)
    mockWebServer.enqueue(mockResponse)

    val exception =
        assertFailsWith<GameSearchException> {
          repository.searchGamesByName("test", 10, ignoreCase = true)
        }

    assertTrue(exception.message!!.contains("BGG search request failed"))
  }

  @Test
  fun searchGamesByNameReturnsEmptyListOnNoResults() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="0" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("nonexistentgame", 10, ignoreCase = true)

    assertEquals(0, results.size)
  }

  @Test
  fun searchGamesByNameIgnoresItemsWithoutPrimaryName() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="2" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Valid Game" />
          </item>
          <item type="boardgame" id="2">
            <name type="alternate" value="Invalid Game" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("game", 10, ignoreCase = true)

    assertEquals(1, results.size)
    assertEquals("Valid Game", results[0].name)
  }

  // ==================== Deprecated (unsupported) methods tests ====================
  @Test
  fun deprecatedGetGamesByIdThrowsUnsupported() = runTest {
    @Suppress("DEPRECATION")
    assertFailsWith<UnsupportedOperationException> { repository.getGamesById("181", "13") }
  }

  @Test
  fun deprecatedSearchGamesByNameContainsThrowsUnsupported() = runTest {
    @Suppress("DEPRECATION")
    assertFailsWith<UnsupportedOperationException> {
      repository.searchGamesByNameContains("risk", 5, ignoreCase = true)
    }
  }

  // ==================== Search Ranking Tests ====================

  @Test
  fun searchRankingPrioritizesExactMatch() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="3" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Catan Expansion" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Catan" />
          </item>
          <item type="boardgame" id="3">
            <name type="primary" value="Star Catan" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("Catan", 10, ignoreCase = true)

    // Exact match "Catan" should be first
    assertEquals("Catan", results[0].name)
  }

  @Test
  fun searchRankingPrioritizesEarlierSubstringMatch() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="3" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="The Great Card Game" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Card Game Master" />
          </item>
          <item type="boardgame" id="3">
            <name type="primary" value="Advanced Card Strategies" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("Card", 10, ignoreCase = true)

    // "Card Game Master" should come before "The Great Card Game"
    // because "Card" appears at index 0 vs index 10
    assertEquals("Card Game Master", results[0].name)
  }

  @Test
  fun searchRankingUsesLevenshteinAsFallback() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="3" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Monopoly" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Monop" />
          </item>
          <item type="boardgame" id="3">
            <name type="primary" value="Monopo" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("Mono", 10, ignoreCase = true)

    // All start with "Mono", so Levenshtein distance decides:
    // "Monop" (distance 1) should come before "Monopo" (distance 2) and "Monopoly" (distance 4)
    assertEquals("Monop", results[0].name)
    assertEquals("Monopo", results[1].name)
    assertEquals("Monopoly", results[2].name)
  }

  @Test
  fun searchRankingIsCaseInsensitive() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="2" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="RISK" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Risk Game" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("risk", 10, ignoreCase = true)

    // "RISK" should be ranked first (exact match, case-insensitive)
    assertEquals("RISK", results[0].name)
  }

  @Test
  fun searchRankingIsCaseSensitiveWhenRequested() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="2" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="RISK" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Risk" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("Risk", 10, ignoreCase = false)

    // "Risk" should be ranked first (exact match, case-sensitive)
    assertEquals("Risk", results[0].name)
  }

  // ==================== XML Parsing Tests ====================

  @Test
  fun parseThingsHandlesRecommendedPlayersFromPollSummary() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="13">
            <thumbnail>https://example.com/catan.jpg</thumbnail>
            <name type="primary" value="CATAN" />
            <description>Settle the island</description>
            <minplayers value="3" />
            <maxplayers value="4" />
            <poll-summary name="suggested_numplayers">
              <result name="bestwith" value="Best with 4 players" />
            </poll-summary>
            <playingtime value="120" />
            <minage value="10" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val game = repository.getGameById("13")

    assertEquals(4, game.recommendedPlayers)
  }

  @Test
  fun parseThingsHandlesMultipleGenres() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="181">
            <thumbnail>https://example.com/risk.jpg</thumbnail>
            <name type="primary" value="Risk" />
            <description>War game</description>
            <minplayers value="2" />
            <maxplayers value="6" />
            <playingtime value="120" />
            <minage value="10" />
            <link type="boardgamecategory" value="Territory Building" />
            <link type="boardgamecategory" value="Wargame" />
            <link type="boardgamecategory" value="Strategy" />
            <link type="boardgamemechanic" value="Dice Rolling" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val game = repository.getGameById("181")

    assertEquals(3, game.genres.size)
    assertTrue(game.genres.contains("Territory Building"))
    assertTrue(game.genres.contains("Wargame"))
    assertTrue(game.genres.contains("Strategy"))
    // Should not contain mechanics
    assertTrue(!game.genres.contains("Dice Rolling"))
  }

  @Test
  fun parseThingsExtractsNumberFromBestWithText() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="999">
            <thumbnail>https://example.com/game.jpg</thumbnail>
            <name type="primary" value="Test Game" />
            <description>A test</description>
            <minplayers value="2" />
            <maxplayers value="8" />
            <poll-summary name="suggested_numplayers">
              <result name="bestwith" value="Best with 5 players" />
            </poll-summary>
            <playingtime value="60" />
            <minage value="12" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val game = repository.getGameById("999")

    assertEquals(5, game.recommendedPlayers)
  }

  @Test
  fun parseSearchResultsIgnoresMissingIds() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="2" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Valid Game" />
          </item>
          <item type="boardgame">
            <name type="primary" value="No ID Game" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("game", 10, ignoreCase = true)

    assertEquals(1, results.size)
    assertEquals("1", results[0].id)
  }

  @Test
  fun parseSearchResultsIgnoresMissingValues() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="2" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Valid Game" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.searchGamesByName("game", 10, ignoreCase = true)

    assertEquals(1, results.size)
    assertEquals("Valid Game", results[0].name)
  }

  // ==================== Edge Cases & Error Handling ====================

  @Test
  fun getGameByIdHandlesOptionalFieldsGracefully() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="123">
            <thumbnail>https://example.com/img.jpg</thumbnail>
            <name type="primary" value="Minimal Game" />
            <description>A game with minimal data</description>
            <minplayers value="2" />
            <maxplayers value="4" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val game = repository.getGameById("123")

    assertEquals("123", game.uid)
    assertEquals("Minimal Game", game.name)
    assertEquals(null, game.recommendedPlayers)
    assertEquals(null, game.averagePlayTime)
    assertEquals(null, game.minAge)
    assertEquals(0, game.genres.size)
  }

  @Test
  fun apiCallIncludesUserAgentHeader() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="181">
            <thumbnail>https://example.com/risk.jpg</thumbnail>
            <name type="primary" value="Risk" />
            <description>War game</description>
            <minplayers value="2" />
            <maxplayers value="6" />
            <playingtime value="120" />
            <minage value="10" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    repository.getGameById("181")

    val request = mockWebServer.takeRequest()
    assertTrue(request.headers["User-Agent"]!!.contains("MeepleMeet"))
  }

  // ==================== Cache tests ====================

  @Test
  fun searchCacheHitAvoidsSecondHttpCall() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="1" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="900">
            <name type="primary" value="Cached Search Game" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    // first call should hit network
    val first = repository.searchGamesByName("cached", 10, ignoreCase = true)
    // second call should be served from cache -> no new server request
    val second = repository.searchGamesByName("cached", 10, ignoreCase = true)

    assertEquals(1, mockWebServer.requestCount, "Search should hit network only once due to cache")
    assertEquals(first, second)
  }

  @Test
  fun searchCacheExpiresAfterTtl() = runTest {
    // repo with very short TTL for searches
    val client = OkHttpClient()
    val baseUrl = mockWebServer.url("/xmlapi2")
    val shortTtlRepo = BggGameRepository(client, baseUrl, null, searchesTtlMs = 10L)

    val mockResponse1 =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="1" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="901">
            <name type="primary" value="Expiring Game" />
          </item>
        </items>
      """
                    .trimIndent())
    val mockResponse2 = mockResponse1.clone()

    mockWebServer.enqueue(mockResponse1)
    val r1 = shortTtlRepo.searchGamesByName("exp", 10, ignoreCase = true)
    assertEquals(1, mockWebServer.requestCount)

    // wait for TTL to pass (small real sleep)
    Thread.sleep(20)

    mockWebServer.enqueue(mockResponse2)
    val r2 = shortTtlRepo.searchGamesByName("exp", 10, ignoreCase = true)

    assertEquals(2, mockWebServer.requestCount, "Search should re-query after TTL expiry")
    assertEquals(r1, r2)
  }

  @Test
  fun gamesCacheFullHitDoesNotCallNetworkAgain() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1001">
            <thumbnail>https://example.com/1001.jpg</thumbnail>
            <name type="primary" value="Cached Game One" />
            <description>desc</description>
            <minplayers value="1" />
            <maxplayers value="4" />
          </item>
        </items>
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    // First fetch -> network
    val res1 = repository.getGamesByIdWithErrors("1001")
    assertEquals(1, mockWebServer.requestCount)
    assertEquals(1, res1.games.size)
    // Second fetch -> must be served from cache
    val res2 = repository.getGamesByIdWithErrors("1001")
    assertEquals(1, mockWebServer.requestCount, "Second fetch must not trigger network (cached)")
    assertEquals(res1.games[0].uid, res2.games[0].uid)
  }

  @Test
  fun gamesCachePartialFetchOnlyMissingIds() = runTest {
    // first: fetch id "2001" to populate cache
    val response2001 =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="2001">
            <thumbnail>https://example.com/2001.jpg</thumbnail>
            <name type="primary" value="Game2001" />
            <description>d</description>
            <minplayers value="2" />
            <maxplayers value="5" />
          </item>
        </items>
      """
                    .trimIndent())
    mockWebServer.enqueue(response2001)
    repository.getGamesByIdWithErrors("2001")
    assertEquals(1, mockWebServer.requestCount)

    // now ask for ("2001","2002") -> repository should only request missing "2002"
    val response2002 =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="2002">
            <thumbnail>https://example.com/2002.jpg</thumbnail>
            <name type="primary" value="Game2002" />
            <description>d2</description>
            <minplayers value="1" />
            <maxplayers value="3" />
          </item>
        </items>
      """
                    .trimIndent())
    mockWebServer.enqueue(response2002)

    val combined = repository.getGamesByIdWithErrors("2001", "2002")
    // total requests should be 2 (one for initial 2001, one for the missing 2002)
    assertEquals(2, mockWebServer.requestCount)
    // Ensure ordering matches requested IDs
    assertEquals(listOf("2001", "2002"), combined.games.map { it.uid })
  }

  @Test
  fun getGamesByIdWithErrorsRespectsRequestedOrderEvenIfResponseDifferentOrder() = runTest {
    // server returns B then A (reverse), client asked for A,B -> result must be [A,B]
    val responseReversed =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="B">
            <thumbnail>https://example.com/b.jpg</thumbnail>
            <name type="primary" value="GameB" />
            <description>B</description>
            <minplayers value="1" />
            <maxplayers value="2" />
          </item>
          <item type="boardgame" id="A">
            <thumbnail>https://example.com/a.jpg</thumbnail>
            <name type="primary" value="GameA" />
            <description>A</description>
            <minplayers value="1" />
            <maxplayers value="2" />
          </item>
        </items>
      """
                    .trimIndent())
    mockWebServer.enqueue(responseReversed)

    val res = repository.getGamesByIdWithErrors("A", "B")
    assertEquals(listOf("A", "B"), res.games.map { it.uid })
  }

  @Test
  fun gamesCacheLruEvictionWorks() = runTest {
    // repo with maxCachedGames = 2
    val client = OkHttpClient()
    val baseUrl = mockWebServer.url("/xmlapi2")
    val smallCacheRepo = BggGameRepository(client, baseUrl, null, maxCachedGames = 2)

    // enqueue 1,2,3, then 1 again
    fun thingBody(id: String) =
        """
      <?xml version="1.0" encoding="UTF-8"?>
      <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
        <item type="boardgame" id="$id">
          <thumbnail>https://example.com/$id.jpg</thumbnail>
          <name type="primary" value="G$id" />
          <description>d$id</description>
          <minplayers value="1" />
          <maxplayers value="4" />
        </item>
      </items>
    """
            .trimIndent()

    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(thingBody("1")))
    smallCacheRepo.getGamesByIdWithErrors("1")
    assertEquals(1, mockWebServer.requestCount)

    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(thingBody("2")))
    smallCacheRepo.getGamesByIdWithErrors("2")
    assertEquals(2, mockWebServer.requestCount)

    // now cache contains [1,2] (1 is LRU-oldest)
    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(thingBody("3")))
    smallCacheRepo.getGamesByIdWithErrors("3")
    assertEquals(3, mockWebServer.requestCount)

    // id "1" should have been evicted. fetching it triggers a network call again
    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(thingBody("1")))
    smallCacheRepo.getGamesByIdWithErrors("1")
    assertEquals(4, mockWebServer.requestCount)
  }

  @Test
  fun gamesCacheExpiresAfterTtl() = runTest {
    val client = OkHttpClient()
    val baseUrl = mockWebServer.url("/xmlapi2")
    val shortTtlRepo = BggGameRepository(client, baseUrl, null, gamesTtlMs = 10L)

    val body =
        """
      <?xml version="1.0" encoding="UTF-8"?>
      <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
        <item type="boardgame" id="3001">
          <thumbnail>https://example.com/3001.jpg</thumbnail>
          <name type="primary" value="G3001" />
          <description>d</description>
          <minplayers value="1" />
          <maxplayers value="2" />
        </item>
      </items>
    """
            .trimIndent()

    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(body))
    shortTtlRepo.getGamesByIdWithErrors("3001")
    assertEquals(1, mockWebServer.requestCount)

    // let TTL expire
    Thread.sleep(20)

    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(body))
    shortTtlRepo.getGamesByIdWithErrors("3001")
    assertEquals(2, mockWebServer.requestCount, "Cache entry expired; network should be hit again")
  }
}
