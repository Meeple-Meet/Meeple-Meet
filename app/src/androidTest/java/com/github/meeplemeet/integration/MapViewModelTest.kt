package com.github.meeplemeet.integration

import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.map.MapUIState
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive integration test suite for MapViewModel using real Firestore repositories.
 *
 * Tests cover:
 * - Geo query lifecycle with real pins
 * - UI-side type filtering
 * - Session participant filtering
 * - Pin selection and preview loading
 * - Query parameter updates
 * - Error handling
 */
class MapViewModelTest : FirestoreTests() {
  private lateinit var viewModel: MapViewModel
  private lateinit var testAccount1: Account
  private lateinit var testAccount2: Account
  private lateinit var testLocation: Location
  private lateinit var testLocationNearby: Location
  private lateinit var testLocationFar: Location
  private lateinit var testOpeningHours: List<OpeningHours>

  @Before
  fun setup() {
    viewModel = MapViewModel()

    runBlocking {
      testAccount1 =
          accountRepository.createAccount("user1", "User One", "user1@test.com", photoUrl = null)
      testAccount2 =
          accountRepository.createAccount("user2", "User Two", "user2@test.com", photoUrl = null)
    }

    testLocation = Location(46.5197, 6.5665, "EPFL")
    testLocationNearby = Location(46.5200, 6.5670, "Near EPFL")
    testLocationFar = Location(46.2044, 6.1432, "Geneva")

    testOpeningHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("10:00", "17:00"))),
            OpeningHours(day = 7, hours = listOf(TimeSlot("10:00", "17:00"))))
  }

  @Test
  fun initialState_isEmpty() {
    val state = viewModel.uiState.value

    assertEquals(MapUIState(), state)
    assertTrue(state.allGeoPins.isEmpty())
    assertTrue(state.geoPins.isEmpty())
    assertEquals(PinType.entries.toSet(), state.activeFilters)
    assertNull(state.errorMsg)
    assertNull(state.selectedMarkerPreview)
    assertNull(state.selectedGeoPin)
  }

  @Test
  fun startGeoQuery_loadsShopPins() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Board Game Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    val state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())
    assertTrue(state.geoPins.isNotEmpty())

    val shopPin = state.geoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(shopPin)
    assertEquals(PinType.SHOP, shopPin!!.geoPin.type)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun startGeoQuery_loadsSpacePins() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Game Space",
            address = testLocation,
            openingHours = testOpeningHours,
            spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    val state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    val spacePin = state.geoPins.find { it.geoPin.uid == spaceRenter.id }
    assertNotNull(spacePin)
    assertEquals(PinType.SPACE, spacePin!!.geoPin.type)

    spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
  }

  @Test
  fun startGeoQuery_loadsSessionPinsOnlyForParticipant() = runBlocking {
    // create a game entry
    db.collection(GAMES_COLLECTION_PATH)
        .document("test_game_chess_mv")
        .set(
            GameNoUid(
                name = "Chess",
                description = "Strategy board game",
                imageURL = "https://example.com/chess.jpg",
                minPlayers = 2,
                maxPlayers = 2,
                recommendedPlayers = null,
                averagePlayTime = null,
                genres = emptyList()))
        .await()

    val testGame = gameRepository.getGameById("test_game_chess_mv")

    // create discussion then create session linked to it
    val discussion =
        discussionRepository.createDiscussion(
            "Chess Night Discussion", "Test discussion for chess session", testAccount1.uid)

    // create session via discussion (sessionRepository.createSession(discussionId,...))
    sessionRepository.createSession(
        discussion.uid,
        "Chess Night",
        testGame.uid,
        Timestamp.now(),
        testLocation,
        testAccount1.uid)

    // User1 is creator / participant -> should see the session pin (search by discussion.uid)
    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    val sessionPin = state.geoPins.find { it.geoPin.uid == discussion.uid } // note: discussion.uid
    assertNotNull(sessionPin)
    assertEquals(PinType.SESSION, sessionPin!!.geoPin.type)

    viewModel.stopGeoQuery()

    // User2 (not participant) should not see the session pin
    val viewModel2 = MapViewModel()
    viewModel2.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount2.uid)
    delay(100)

    state = viewModel2.uiState.value
    val sessionPin2 = state.geoPins.find { it.geoPin.uid == discussion.uid }
    assertNull(sessionPin2)

    // cleanup
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    discussionRepository.deleteDiscussion(context, discussion)
  }

  @Test
  fun startGeoQuery_loadsMultiplePinTypes() = runBlocking {
    // create game + discussion+session
    db.collection(GAMES_COLLECTION_PATH)
        .document("test_game_catan_mv")
        .set(
            GameNoUid(
                name = "Catan",
                description = "Trading and building board game",
                imageURL = "https://example.com/catan.jpg",
                minPlayers = 3,
                maxPlayers = 4,
                recommendedPlayers = null,
                averagePlayTime = null,
                genres = emptyList()))
        .await()

    val testGame = gameRepository.getGameById("test_game_catan_mv")

    val discussion =
        discussionRepository.createDiscussion(
            "Catan Night Discussion", "Discussion for Catan session test", testAccount1.uid)

    sessionRepository.createSession(
        discussion.uid, "Game Night", testGame.uid, Timestamp.now(), testLocation, testAccount1.uid)

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Space",
            address = testLocation,
            openingHours = testOpeningHours,
            spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    val state = viewModel.uiState.value
    assertTrue(state.allGeoPins.size >= 3)

    val shopPin = state.geoPins.find { it.geoPin.type == PinType.SHOP }
    val spacePin = state.geoPins.find { it.geoPin.type == PinType.SPACE }
    val sessionPin = state.geoPins.find { it.geoPin.type == PinType.SESSION }

    assertNotNull(shopPin)
    assertNotNull(spacePin)
    assertNotNull(sessionPin)

    shopRepository.deleteShop(shop.id)
    spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
    // cleanup discussion which contains the session link
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    discussionRepository.deleteDiscussion(context, discussion)
  }

  @Test
  fun startGeoQuery_doesNotLoadPinsOutsideRadius() = runBlocking {
    val farShop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Far Shop",
            address = testLocationFar,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    val state = viewModel.uiState.value
    val farPin = state.geoPins.find { it.geoPin.uid == farShop.id }
    assertNull(farPin)

    shopRepository.deleteShop(farShop.id)
  }

  @Test
  fun stopGeoQuery_clearsAllPins() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    viewModel.stopGeoQuery()

    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isEmpty())
    assertTrue(state.geoPins.isEmpty())

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun updateFilters_filtersGeoPinsProperty() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Space",
            address = testLocation,
            openingHours = testOpeningHours,
            spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    assertTrue(state.allGeoPins.size >= 2)
    assertTrue(state.geoPins.size >= 2)

    viewModel.updateFilters(setOf(PinType.SHOP))
    delay(100)

    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.size >= 2)
    assertTrue(state.geoPins.all { it.geoPin.type == PinType.SHOP })
    assertTrue(state.geoPins.none { it.geoPin.type == PinType.SPACE })

    viewModel.updateFilters(setOf(PinType.SPACE))
    delay(100)

    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.size >= 2)
    assertTrue(state.geoPins.all { it.geoPin.type == PinType.SPACE })
    assertTrue(state.geoPins.none { it.geoPin.type == PinType.SHOP })

    viewModel.updateFilters(setOf(PinType.SHOP, PinType.SPACE))
    delay(100)

    state = viewModel.uiState.value
    assertTrue(state.geoPins.size >= 2)

    shopRepository.deleteShop(shop.id)
    spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
  }

  @Test
  fun updateFilters_withEmptySet_hidesAllPins() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())
    assertTrue(state.geoPins.isNotEmpty())

    viewModel.updateFilters(emptySet())
    delay(100)

    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())
    assertTrue(state.geoPins.isEmpty())

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun selectPin_loadsShopPreview() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Chess Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    val state = viewModel.uiState.value
    val shopPin = state.geoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(shopPin)

    viewModel.selectPin(shopPin!!)
    delay(1000)

    val updatedState = viewModel.uiState.value
    assertNotNull(updatedState.selectedMarkerPreview)
    assertNotNull(updatedState.selectedGeoPin)
    assertEquals(shop.id, updatedState.selectedGeoPin!!.uid)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun selectPin_loadsSpacePreview() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Game Hall",
            address = testLocation,
            openingHours = testOpeningHours,
            spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    val state = viewModel.uiState.value
    val spacePin = state.geoPins.find { it.geoPin.uid == spaceRenter.id }
    assertNotNull(spacePin)

    viewModel.selectPin(spacePin!!)
    delay(1000)

    val updatedState = viewModel.uiState.value
    assertNotNull(updatedState.selectedMarkerPreview)
    assertEquals(spaceRenter.id, updatedState.selectedGeoPin!!.uid)

    spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
  }

  @Test
  fun selectPin_loadsSessionPreview() = runBlocking {
    // create game entry
    db.collection(GAMES_COLLECTION_PATH)
        .document("test_game_chess_select")
        .set(
            GameNoUid(
                name = "Chess",
                description = "Strategy board game",
                imageURL = "https://example.com/chess.jpg",
                minPlayers = 2,
                maxPlayers = 2,
                recommendedPlayers = null,
                averagePlayTime = null,
                genres = emptyList()))
        .await()

    val testGame = gameRepository.getGameById("test_game_chess_select")

    // create discussion then session via discussion
    val discussion =
        discussionRepository.createDiscussion(
            "Chess Tournament Discussion", "Test discussion for tournament", testAccount1.uid)

    sessionRepository.createSession(
        discussion.uid,
        "Chess Tournament",
        testGame.uid,
        Timestamp.now(),
        testLocation,
        testAccount1.uid)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    val state = viewModel.uiState.value
    val sessionPin = state.geoPins.find { it.geoPin.uid == discussion.uid }

    assertNotNull(sessionPin)

    viewModel.selectPin(sessionPin!!)
    delay(1000)

    val updatedState = viewModel.uiState.value
    assertNotNull(updatedState.selectedMarkerPreview)
    assertEquals(discussion.uid, updatedState.selectedGeoPin!!.uid)

    // cleanup
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    discussionRepository.deleteDiscussion(context, discussion)
  }

  @Test
  fun clearSelectedPin_removesSelection() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    val state = viewModel.uiState.value
    val shopPin = state.geoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(shopPin)

    viewModel.selectPin(shopPin!!)
    delay(1000)

    var updatedState = viewModel.uiState.value
    assertNotNull(updatedState.selectedMarkerPreview)

    viewModel.clearSelectedPin()

    updatedState = viewModel.uiState.value
    assertNull(updatedState.selectedMarkerPreview)
    assertNull(updatedState.selectedGeoPin)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun updateQueryCenter_keepsQueryAlive() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    val initialCount = state.allGeoPins.size
    assertTrue(initialCount > 0)

    viewModel.updateQueryCenter(testLocationNearby)
    delay(1000)

    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun updateRadius_keepsQueryAlive() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    viewModel.updateRadius(15.0)
    delay(1000)

    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun updateCenterAndRadius_keepsQueryAlive() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    viewModel.updateCenterAndRadius(testLocationNearby, 15.0)
    delay(1000)

    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun geoQuery_onKeyMoved_updatesLocation() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Moving Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    val initialPin = state.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(initialPin)
    assertEquals(testLocation.latitude, initialPin!!.location.latitude, 0.0001)
    assertEquals(testLocation.longitude, initialPin.location.longitude, 0.0001)

    shopRepository.updateShop(shop.id, address = testLocationNearby)
    delay(100)

    state = viewModel.uiState.value
    val updatedPin = state.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(updatedPin)
    assertEquals(testLocationNearby.latitude, updatedPin!!.location.latitude, 0.0001)
    assertEquals(testLocationNearby.longitude, updatedPin.location.longitude, 0.0001)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun geoQuery_onKeyExited_removesPinWhenMovedOutOfRadius() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Exiting Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    val initialPin = state.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(initialPin)

    shopRepository.updateShop(shop.id, address = testLocationFar)
    delay(100)

    state = viewModel.uiState.value
    val exitedPin = state.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNull(exitedPin)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun geoQuery_onKeyExited_removesPinWhenDeleted() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Temporary Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    val initialPin = state.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(initialPin)

    shopRepository.deleteShop(shop.id)
    delay(100)

    state = viewModel.uiState.value
    val deletedPin = state.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNull(deletedPin)
  }

  @Test
  fun clearErrorMsg_removesError() {
    viewModel.clearErrorMsg()

    val state = viewModel.uiState.value
    assertNull(state.errorMsg)
  }

  @Test
  fun multipleQueryCycles_workCorrectly() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    viewModel.stopGeoQuery()
    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isEmpty())

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun stopGeoQuery_canBeCalledMultipleTimes() {
    viewModel.stopGeoQuery()
    viewModel.stopGeoQuery()
    viewModel.stopGeoQuery()

    val state = viewModel.uiState.value
    assertNotNull(state)
    assertTrue(state.allGeoPins.isEmpty())
  }

  @Test
  fun startGeoQuery_restartingQueryClearsPreviousState() = runBlocking {
    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 1",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    val shop2 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 2",
            address = testLocationFar,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocationFar, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    state = viewModel.uiState.value
    val pin1 = state.allGeoPins.find { it.geoPin.uid == shop1.id }
    val pin2 = state.allGeoPins.find { it.geoPin.uid == shop2.id }
    assertNull(pin1)
    assertNotNull(pin2)

    shopRepository.deleteShop(shop1.id)
    shopRepository.deleteShop(shop2.id)
  }

  @Test
  fun geoQuery_dynamicallyLoadsNewPins() = runBlocking {
    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    var state = viewModel.uiState.value
    val initialCount = state.allGeoPins.size

    val newShop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "New Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    delay(100)

    state = viewModel.uiState.value
    assertTrue(state.allGeoPins.size > initialCount)
    val newPin = state.allGeoPins.find { it.geoPin.uid == newShop.id }
    assertNotNull(newPin)

    shopRepository.deleteShop(newShop.id)
  }

  @Test
  fun updateFilters_doesNotAffectAllGeoPins() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Space",
            address = testLocation,
            openingHours = testOpeningHours,
            spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(100)

    val initialState = viewModel.uiState.value
    val initialAllCount = initialState.allGeoPins.size

    viewModel.updateFilters(setOf(PinType.SHOP))
    delay(100)

    val filteredState = viewModel.uiState.value
    assertEquals(initialAllCount, filteredState.allGeoPins.size)
    assertTrue(filteredState.geoPins.size < initialAllCount)

    shopRepository.deleteShop(shop.id)
    spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
  }
}
