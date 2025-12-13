package com.github.meeplemeet.integration

import com.github.meeplemeet.model.GameFetchException
import com.github.meeplemeet.model.shared.SearchViewModel
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.game.GameSearchResult
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shared.location.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeGameRepository : GameRepository {
  var searchCallCount = 0
  var getByIdCallCount = 0
  var searchResults = emptyList<GameSearchResult>()
  var gameToReturn: Game? = null
  var shouldThrowOnGetById = false

  override suspend fun searchGamesByName(query: String, maxResults: Int): List<GameSearchResult> {
    searchCallCount++
    return searchResults
  }

  override suspend fun getGameById(gameID: String): Game {
    getByIdCallCount++
    if (shouldThrowOnGetById) throw GameFetchException("Mock error")
    return gameToReturn ?: error("No game configured")
  }

  override suspend fun getGamesById(vararg gameIDs: String): List<Game> = error("Not used")
}

class FakeLocationRepository : LocationRepository {
  var searchCallCount = 0
  var searchResults = emptyList<Location>()

  override suspend fun search(query: String): List<Location> {
    searchCallCount++
    return searchResults
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var gameRepo: FakeGameRepository
  private lateinit var locationRepo: FakeLocationRepository
  private lateinit var viewModel: SearchViewModel

  private val testGame =
      Game(
          uid = "game1",
          name = "Catan",
          description = "Settlers of Catan",
          imageURL = "https://example.com/catan.jpg",
          minPlayers = 3,
          maxPlayers = 4,
          recommendedPlayers = 4,
          averagePlayTime = 90,
          minAge = 10,
          genres = listOf("strategy", "trading"))

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    gameRepo = FakeGameRepository()
    locationRepo = FakeLocationRepository()
    viewModel = SearchViewModel(gameRepo, locationRepo)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun searchViewModel_backgroundFetch_AndSearchLogic() = runTest {
    // Test 1: Background fetch with fetchFullGame=true
    gameRepo.gameToReturn = testGame
    viewModel.setGame(GameSearchResult("game1", "Catan"), fetchFullGame = true)

    assertNull(viewModel.gameUIState.value.fetchedGame) // Not yet fetched
    testScheduler.advanceUntilIdle()

    assertEquals(1, gameRepo.getByIdCallCount)
    assertNotNull(viewModel.gameUIState.value.fetchedGame)
    assertEquals("Catan", viewModel.gameUIState.value.fetchedGame?.name)

    // Test 2: fetchFullGame=false doesn't fetch
    gameRepo.getByIdCallCount = 0
    viewModel.setGame(GameSearchResult("game2", "Pandemic"), fetchFullGame = false)
    testScheduler.advanceUntilIdle()
    assertEquals(0, gameRepo.getByIdCallCount)

    // Test 3: Search is debounced
    gameRepo.searchResults = listOf(GameSearchResult("game1", "Catan"))
    gameRepo.searchCallCount = 0
    repeat(5) { viewModel.setGameQuery("C") }
    testScheduler.advanceUntilIdle()
    assertEquals(1, gameRepo.searchCallCount)

    // Test 4: Error handling
    gameRepo.shouldThrowOnGetById = true
    viewModel.setGame(GameSearchResult("game3", "Error"), fetchFullGame = true)
    testScheduler.advanceUntilIdle()
    assertNull(viewModel.gameUIState.value.fetchedGame)
    assertEquals("Failed to fetch game details", viewModel.gameUIState.value.gameFetchError)
  }
}
