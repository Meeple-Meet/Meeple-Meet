package com.github.meeplemeet.integration

import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.game.GameSearchResult
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shared.location.LocationRepository
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopSearchViewModel
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// ------------------ Fake repositories with call counters ------------------

class CountingGameRepository : GameRepository {
  var callCount = 0

  override suspend fun searchGamesByName(query: String, maxResults: Int): List<GameSearchResult> {
    callCount++
    return emptyList()
  }

  override suspend fun getGameById(gameID: String): Game = error("Unused")

  override suspend fun getGamesById(vararg gameIDs: String): List<Game> = error("Unused")
}

class CountingLocationRepository : LocationRepository {
  var callCount = 0

  override suspend fun search(query: String): List<Location> {
    callCount++
    return emptyList()
  }
}

// ------------------ Testable ViewModel with injected repos ------------------

class TestableShopSearchViewModel(gameRepo: GameRepository, locationRepo: LocationRepository) :
    ShopSearchViewModel(gameRepo, locationRepo)

class ShopSearchViewModelTest : FirestoreTests() {
  private lateinit var gameRepo: GameRepository
  private lateinit var locationRepo: LocationRepository
  private lateinit var shopViewModel: ShopSearchViewModel
  @OptIn(ExperimentalCoroutinesApi::class) private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var shop: Shop
  private lateinit var owner: Account
  private lateinit var intruder: Account
  private lateinit var game: Game
  private lateinit var location: Location

  @OptIn(ExperimentalCoroutinesApi::class)
  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)

    gameRepo = CountingGameRepository()
    locationRepo = CountingLocationRepository()
    shopViewModel = TestableShopSearchViewModel(gameRepo, locationRepo)

    runBlocking {
      owner = accountRepository.createAccount("owner", "Owner", "owner@shop.com", null)
      intruder = accountRepository.createAccount("intruder", "Intruder", "intruder@shop.com", null)

      game = Game("g1", "Catan", "Catan game", "url.com", 2, 8, null, null, null, emptyList())
      location = Location(latitude = 46.5197, longitude = 6.5665, name = "EPFL")

      val openingHours =
          listOf(
              OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))))

      shop =
          shopRepository.createShop(
              owner = owner, name = "Shop", address = location, openingHours = openingHours)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ------------------ Permission checks ------------------

  @Test(expected = PermissionDeniedException::class)
  fun setGameThrowsIfNotOwner() {
    shopViewModel.setGame(shop, intruder, GameSearchResult("", ""))
  }

  @Test(expected = PermissionDeniedException::class)
  fun setGameQueryThrowsIfNotOwner() {
    shopViewModel.setGameQuery(shop, intruder, "Catan")
  }

  @Test(expected = PermissionDeniedException::class)
  fun setLocationThrowsIfNotOwner() {
    shopViewModel.setLocation(shop, intruder, location)
  }

  @Test(expected = PermissionDeniedException::class)
  fun setLocationQueryThrowsIfNotOwner() {
    shopViewModel.setLocationQuery(shop, intruder, "EPFL")
  }

  // ------------------ Authorized calls ------------------

  @Test
  fun setGameSucceedsIfOwner() {
    shopViewModel.setGame(shop, owner, GameSearchResult("", ""))
  }

  @Test
  fun setGameQuerySucceedsIfOwner() {
    shopViewModel.setGameQuery(shop, owner, "Catan")
  }

  @Test
  fun setLocationSucceedsIfOwner() {
    shopViewModel.setLocation(shop, owner, location)
  }

  @Test
  fun setLocationQuerySucceedsIfOwner() {
    shopViewModel.setLocationQuery(shop, owner, "EPFL")
  }

  // ------------------ Debounce calls ------------------

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun gameQueryIsDebounced() = runTest {
    repeat(10) { shopViewModel.setGameQuery(shop, owner, "Catan") }

    testScheduler.advanceUntilIdle()
    assertEquals(1, (gameRepo as CountingGameRepository).callCount)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun locationQueryIsDebounced() = runTest {
    repeat(10) { shopViewModel.setLocationQuery(shop, owner, "EPFL") }

    testScheduler.advanceUntilIdle()
    assertEquals(1, (locationRepo as CountingLocationRepository).callCount)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun fetchGames_allBehaviors() = runTest {
    val testGame1 = Game("g1", "Catan", "", "", 3, 4, null, 90, null, emptyList())
    val testGame2 = Game("g2", "Pandemic", "", "", 2, 4, null, 45, null, emptyList())

    var getByIdCallCount = 0
    val fakeRepo =
        object : GameRepository {
          override suspend fun searchGamesByName(query: String, maxResults: Int) =
              emptyList<GameSearchResult>()

          override suspend fun getGameById(gameID: String) = error("Not used")

          override suspend fun getGamesById(vararg gameIDs: String): List<Game> {
            getByIdCallCount++
            return gameIDs.mapNotNull { id ->
              when (id) {
                "g1" -> testGame1
                "g2" -> testGame2
                else -> null
              }
            }
          }
        }

    val vm = TestableShopSearchViewModel(fakeRepo, locationRepo)

    // Test 1: fetchGames populates map
    vm.fetchGames(listOf("g1", "g2"))
    testScheduler.advanceUntilIdle()
    assertEquals(2, vm.fetchedGames.value.size)
    assertEquals("Catan", vm.fetchedGames.value["g1"]?.name)

    // Test 2: Already-fetched games are skipped
    vm.fetchGames(listOf("g1"))
    testScheduler.advanceUntilIdle()
    assertEquals(1, getByIdCallCount) // Still only 1 call

    // Test 3: clearFetchedGames works
    vm.clearFetchedGames()
    assert(vm.fetchedGames.value.isEmpty())

    // Test 4: Empty list doesn't crash
    vm.fetchGames(emptyList())
    testScheduler.advanceUntilIdle()

    // Test 5: Too many IDs throws
    var threwException = false
    try {
      vm.fetchGames((1..21).map { "game$it" })
    } catch (e: IllegalArgumentException) {
      threwException = true
    }
    assert(threwException)
  }
}
