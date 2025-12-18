package com.github.meeplemeet.integration

import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.map.MapUIState
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.cluster.Cluster
import com.github.meeplemeet.model.map.cluster.ClusterManager
import com.github.meeplemeet.model.map.cluster.ClusterStrategy
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive integration test suite for MapViewModel with clustering support.
 *
 * Tests cover:
 * - Geo query lifecycle with debounced updates
 * - Clustering with cache invalidation
 * - Cluster selection and preview loading
 * - Filter updates and cache behavior
 * - Zoom level changes
 * - Error handling
 */
class MapViewModelTest : FirestoreTests() {
  private lateinit var testAccount1: Account
  private lateinit var testAccount2: Account
  private lateinit var testLocation: Location
  private lateinit var testLocationNearby: Location
  private lateinit var testLocationFar: Location
  private lateinit var testOpeningHours: List<OpeningHours>

  // Test cluster strategies
  private val singleClusterStrategy = ClusterStrategy { items, _ ->
    if (items.isEmpty()) emptyList()
    else {
      val centerLat = items.map { it.lat }.average()
      val centerLng = items.map { it.lng }.average()
      listOf(Cluster(centerLat, centerLng, items))
    }
  }

  private val noClusterStrategy = ClusterStrategy { items, _ ->
    items.map { Cluster(it.lat, it.lng, listOf(it)) }
  }

  @Before
  fun setup() {
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

  // ============================================================================
  // BASIC STATE & INITIALIZATION TESTS
  // ============================================================================

  @Test
  fun initialState_isEmpty() {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))
    val state = viewModel.uiState.value

    assertEquals(MapUIState(), state)
    assertTrue(state.allGeoPins.isEmpty())
    assertEquals(PinType.entries.toSet(), state.activeFilters)
    assertNull(state.errorMsg)
    assertNull(state.selectedMarkerPreview)
    assertNull(state.selectedPin)
    assertNull(state.selectedClusterPreviews)
    assertNull(state.clusterCache)
    assertEquals(0, state.cacheVersion)
  }

  @Test
  fun initialClusters_isEmpty() {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))
    val clusters = viewModel.getClusters(testAccount1)

    assertTrue(clusters.isEmpty())
  }

  // ============================================================================
  // GEO QUERY LIFECYCLE TESTS
  // ============================================================================

  @Test
  fun startGeoQuery_loadsShopPins() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Board Game Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isNotEmpty())

    val shopPin = state.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(shopPin)
    assertEquals(PinType.SHOP, shopPin!!.geoPin.type)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun startGeoQuery_loadsSessionPinsOnlyForParticipant() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

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
    val discussion =
        discussionRepository.createDiscussion(
            "Chess Night Discussion", "Test discussion for chess session", testAccount1.uid)

    sessionRepository.createSession(
        discussion.uid,
        "Chess Night",
        testGame.uid,
        testGame.name,
        Timestamp.now(),
        testLocation,
        testAccount1.uid)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val sessionPin = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == discussion.uid }
    assertNotNull(sessionPin)
    assertEquals(PinType.SESSION, sessionPin!!.geoPin.type)

    viewModel.stopGeoQuery()

    val viewModel2 = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))
    viewModel2.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount2.uid)
    delay(3000)

    val sessionPin2 = viewModel2.uiState.value.allGeoPins.find { it.geoPin.uid == discussion.uid }
    assertNull(sessionPin2)

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    discussionRepository.deleteDiscussion(context, discussion)
  }

  @Test
  fun stopGeoQuery_clearsAllPins() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    assertTrue(viewModel.uiState.value.allGeoPins.isNotEmpty())

    viewModel.stopGeoQuery()

    val state = viewModel.uiState.value
    assertTrue(state.allGeoPins.isEmpty())
    assertNull(state.clusterCache)

    shopRepository.deleteShop(shop.id)
  }

  // ============================================================================
  // DEBOUNCING TESTS
  // ============================================================================

  @Test
  fun geoQuery_debouncesBatchUpdates() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)

    val shops = mutableListOf<String>()
    repeat(5) { i ->
      val shop =
          shopRepository.createShop(
              owner = testAccount1,
              name = "Shop $i",
              address = testLocation,
              openingHours = testOpeningHours)
      shops.add(shop.id)
    }

    delay(3000)
    val stateAfterDebounce = viewModel.uiState.value
    assertTrue(stateAfterDebounce.allGeoPins.size >= 5)

    shops.forEach { shopRepository.deleteShop(it) }
  }

  // ============================================================================
  // CLUSTERING TESTS
  // ============================================================================

  @Test
  fun getClusters_withSingleClusterStrategy_returnsOneCluster() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 1",
            address = testLocation,
            openingHours = testOpeningHours)

    val shop2 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 2",
            address = testLocationNearby,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val clusters = viewModel.getClusters(testAccount1)

    assertEquals(1, clusters.size)
    assertTrue(clusters[0].items.size >= 2)

    shopRepository.deleteShop(shop1.id)
    shopRepository.deleteShop(shop2.id)
  }

  @Test
  fun getClusters_withNoClusterStrategy_returnsIndividualClusters() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(noClusterStrategy))

    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 1",
            address = testLocation,
            openingHours = testOpeningHours)

    val shop2 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 2",
            address = testLocationNearby,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val clusters = viewModel.getClusters(testAccount1)

    assertTrue(clusters.size >= 2)
    assertTrue(clusters.all { it.items.size == 1 })

    shopRepository.deleteShop(shop1.id)
    shopRepository.deleteShop(shop2.id)
  }

  @Test
  fun getClusters_cacheIsValid_returnsCachedResult() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val clusters1 = viewModel.getClusters(testAccount1)
    val state1 = viewModel.uiState.value
    assertNotNull(state1.clusterCache)

    val cacheVersion = state1.cacheVersion

    val clusters2 = viewModel.getClusters(testAccount1)
    val state2 = viewModel.uiState.value

    assertEquals(cacheVersion, state2.clusterCache!!.version)
    assertEquals(clusters1, clusters2)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun getClusters_afterFilterChange_invalidatesCache() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    viewModel.getClusters(testAccount1)
    val version1 = viewModel.uiState.value.cacheVersion

    viewModel.updateFilters(setOf(PinType.SHOP))

    val version2 = viewModel.uiState.value.cacheVersion
    assertTrue(version2 > version1)

    viewModel.getClusters(testAccount1)
    val cache2 = viewModel.uiState.value.clusterCache
    assertNotNull(cache2)
    assertEquals(version2, cache2!!.version)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun getClusters_afterZoomChange_invalidatesCache() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    viewModel.getClusters(testAccount1)
    val version1 = viewModel.uiState.value.cacheVersion

    viewModel.updateZoomLevel(16f)

    val version2 = viewModel.uiState.value.cacheVersion
    assertTrue(version2 > version1)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun getClusters_afterPinAdded_invalidatesCache() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 1",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    viewModel.getClusters(testAccount1)
    val version1 = viewModel.uiState.value.cacheVersion

    val shop2 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 2",
            address = testLocationNearby,
            openingHours = testOpeningHours)

    delay(3000)

    val version2 = viewModel.uiState.value.cacheVersion
    assertTrue(version2 > version1)

    shopRepository.deleteShop(shop1.id)
    shopRepository.deleteShop(shop2.id)
  }

  // ============================================================================
  // FILTER TESTS
  // ============================================================================

  @Test
  fun updateFilters_filtersCorrectly() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(noClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    val space =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Space",
            address = testLocationNearby,
            openingHours = testOpeningHours,
            spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    viewModel.updateFilters(setOf(PinType.SHOP))
    val clusters = viewModel.getClusters(testAccount1)

    assertTrue(clusters.all { cluster -> cluster.items.all { it.geoPin.type == PinType.SHOP } })

    shopRepository.deleteShop(shop.id)
    spaceRenterRepository.deleteSpaceRenter(space.id)
  }

  @Test
  fun updateFilters_withEmptySet_yieldsEmptyClusters() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    assertTrue(viewModel.getClusters(testAccount1).isNotEmpty())

    viewModel.updateFilters(emptySet())

    val clusters = viewModel.getClusters(testAccount1)
    assertTrue(clusters.isEmpty())

    shopRepository.deleteShop(shop.id)
  }

  // ============================================================================
  // OWNERSHIP FILTER TESTS
  // ============================================================================

  @Test
  fun setShowOwnedBusinessesOnly_invalidatesCache() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    viewModel.getClusters(testAccount1)
    val version1 = viewModel.uiState.value.cacheVersion

    viewModel.setShowOwnedBusinessesOnly(true)

    val version2 = viewModel.uiState.value.cacheVersion
    assertTrue(version2 > version1)
    assertTrue(viewModel.uiState.value.showOwnedBusinessesOnly)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun setShowOwnedBusinessesOnly_filtersNonOwnedBusinesses() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(noClusterStrategy))

    // Create shop owned by testAccount1
    val ownedShop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "My Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    // Create shop owned by testAccount2
    val otherShop =
        shopRepository.createShop(
            owner = testAccount2,
            name = "Other Shop",
            address = testLocationNearby,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    // Both shops visible initially
    val allClusters = viewModel.getClusters(testAccount1)
    assertTrue(allClusters.flatMap { it.items }.any { it.geoPin.uid == ownedShop.id })
    assertTrue(allClusters.flatMap { it.items }.any { it.geoPin.uid == otherShop.id })

    // Enable owned-only filter
    viewModel.setShowOwnedBusinessesOnly(true)

    // Only owned shop visible
    val ownedClusters = viewModel.getClusters(testAccount1)
    assertTrue(ownedClusters.flatMap { it.items }.any { it.geoPin.uid == ownedShop.id })
    assertFalse(ownedClusters.flatMap { it.items }.any { it.geoPin.uid == otherShop.id })

    shopRepository.deleteShop(ownedShop.id)
    shopRepository.deleteShop(otherShop.id)
  }

  @Test
  fun setShowOwnedBusinessesOnly_sessionsAlwaysVisible() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(noClusterStrategy))

    // Create shop owned by testAccount2
    val otherShop =
        shopRepository.createShop(
            owner = testAccount2,
            name = "Other Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    // Create session
    db.collection(GAMES_COLLECTION_PATH)
        .document("test_game_owned_session")
        .set(
            GameNoUid(
                name = "Catan",
                description = "Strategy board game",
                imageURL = "https://example.com/catan.jpg",
                minPlayers = 3,
                maxPlayers = 4,
                recommendedPlayers = null,
                averagePlayTime = null,
                genres = emptyList()))
        .await()

    val testGame = gameRepository.getGameById("test_game_owned_session")
    val discussion =
        discussionRepository.createDiscussion("Test Session", "Test discussion", testAccount1.uid)

    sessionRepository.createSession(
        discussion.uid,
        "Test Session",
        testGame.uid,
        testGame.name,
        Timestamp.now(),
        testLocation,
        testAccount1.uid)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    // Enable owned-only filter
    viewModel.setShowOwnedBusinessesOnly(true)

    // Session still visible, other shop not visible
    val clusters = viewModel.getClusters(testAccount1)
    assertTrue(clusters.flatMap { it.items }.any { it.geoPin.uid == discussion.uid })
    assertFalse(clusters.flatMap { it.items }.any { it.geoPin.uid == otherShop.id })

    shopRepository.deleteShop(otherShop.id)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    discussionRepository.deleteDiscussion(context, discussion)
  }

  @Test
  fun setShowOwnedBusinessesOnly_worksWithSpaces() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(noClusterStrategy))

    // Set testAccount1 as space renter
    accountRepository.setAccountRole(testAccount1.uid, isShopOwner = false, isSpaceRenter = true)
    val updatedAccount1 = accountRepository.getAccount(testAccount1.uid)

    // Create space owned by testAccount1
    val ownedSpace =
        spaceRenterRepository.createSpaceRenter(
            owner = updatedAccount1,
            name = "My Space",
            address = testLocation,
            openingHours = testOpeningHours,
            spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

    // Create space owned by testAccount2
    accountRepository.setAccountRole(testAccount2.uid, isShopOwner = false, isSpaceRenter = true)
    val updatedAccount2 = accountRepository.getAccount(testAccount2.uid)

    val otherSpace =
        spaceRenterRepository.createSpaceRenter(
            owner = updatedAccount2,
            name = "Other Space",
            address = testLocationNearby,
            openingHours = testOpeningHours,
            spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    // Both spaces visible initially
    val allClusters = viewModel.getClusters(updatedAccount1)
    assertTrue(allClusters.flatMap { it.items }.any { it.geoPin.uid == ownedSpace.id })
    assertTrue(allClusters.flatMap { it.items }.any { it.geoPin.uid == otherSpace.id })

    // Enable owned-only filter
    viewModel.setShowOwnedBusinessesOnly(true)

    // Only owned space visible
    val ownedClusters = viewModel.getClusters(updatedAccount1)
    assertTrue(ownedClusters.flatMap { it.items }.any { it.geoPin.uid == ownedSpace.id })
    assertFalse(ownedClusters.flatMap { it.items }.any { it.geoPin.uid == otherSpace.id })

    spaceRenterRepository.deleteSpaceRenter(ownedSpace.id)
    spaceRenterRepository.deleteSpaceRenter(otherSpace.id)
  }

  @Test
  fun setShowOwnedBusinessesOnly_disablingShowsAllAgain() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(noClusterStrategy))

    val ownedShop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "My Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    val otherShop =
        shopRepository.createShop(
            owner = testAccount2,
            name = "Other Shop",
            address = testLocationNearby,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    // Enable filter
    viewModel.setShowOwnedBusinessesOnly(true)
    val ownedClusters = viewModel.getClusters(testAccount1)
    assertFalse(ownedClusters.flatMap { it.items }.any { it.geoPin.uid == otherShop.id })

    // Disable filter
    viewModel.setShowOwnedBusinessesOnly(false)
    val allClusters = viewModel.getClusters(testAccount1)
    assertTrue(allClusters.flatMap { it.items }.any { it.geoPin.uid == ownedShop.id })
    assertTrue(allClusters.flatMap { it.items }.any { it.geoPin.uid == otherShop.id })

    shopRepository.deleteShop(ownedShop.id)
    shopRepository.deleteShop(otherShop.id)
  }

  // ============================================================================
  // SINGLE PIN SELECTION TESTS
  // ============================================================================

  @Test
  fun selectPin_loadsShopPreview() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Chess Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val shopPin = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(shopPin)

    viewModel.selectPin(shopPin!!)
    delay(3000)

    val state = viewModel.uiState.value
    assertNotNull(state.selectedMarkerPreview)
    assertNotNull(state.selectedPin)
    assertEquals(shop.id, state.selectedPin!!.geoPin.uid)
    assertFalse(state.isLoadingPreview)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun clearSelectedPin_removesSelection() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val shopPin = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop.id }
    viewModel.selectPin(shopPin!!)
    delay(3000)

    assertNotNull(viewModel.uiState.value.selectedMarkerPreview)

    viewModel.clearSelectedPin()

    val state = viewModel.uiState.value
    assertNull(state.selectedMarkerPreview)
    assertNull(state.selectedPin)

    shopRepository.deleteShop(shop.id)
  }

  // ============================================================================
  // CLUSTER SELECTION TESTS
  // ============================================================================

  @Test
  fun selectCluster_loadsAllPreviews() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 1",
            address = testLocation,
            openingHours = testOpeningHours)

    val shop2 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 2",
            address = testLocationNearby,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val clusters = viewModel.getClusters(testAccount1)
    assertTrue(clusters.isNotEmpty())

    val cluster = clusters[0]
    assertTrue(cluster.items.size >= 2)

    viewModel.selectCluster(cluster)
    delay(4500)

    val state = viewModel.uiState.value
    assertNotNull(state.selectedClusterPreviews)
    assertTrue(state.selectedClusterPreviews!!.size >= 2)
    assertFalse(state.isLoadingPreview)

    shopRepository.deleteShop(shop1.id)
    shopRepository.deleteShop(shop2.id)
  }

  @Test
  fun selectPinFromCluster_transitionsToSingleSelection() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 1",
            address = testLocation,
            openingHours = testOpeningHours)

    val shop2 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 2",
            address = testLocationNearby,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val cluster = viewModel.getClusters(testAccount1)[0]
    viewModel.selectCluster(cluster)
    delay(4500)

    val clusterState = viewModel.uiState.value
    assertNotNull(clusterState.selectedClusterPreviews)
    assertTrue(clusterState.selectedClusterPreviews!!.isNotEmpty())

    val (pin, preview) = clusterState.selectedClusterPreviews!![0]
    viewModel.selectPinFromCluster(pin, preview)

    val finalState = viewModel.uiState.value
    assertNull(finalState.selectedClusterPreviews)
    assertNotNull(finalState.selectedPin)
    assertNotNull(finalState.selectedMarkerPreview)
    assertEquals(pin.geoPin.uid, finalState.selectedPin!!.geoPin.uid)

    shopRepository.deleteShop(shop1.id)
    shopRepository.deleteShop(shop2.id)
  }

  @Test
  fun clearSelectedCluster_removesClusterSelection() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 1",
            address = testLocation,
            openingHours = testOpeningHours)

    val shop2 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop 2",
            address = testLocationNearby,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val cluster = viewModel.getClusters(testAccount1)[0]
    viewModel.selectCluster(cluster)
    delay(4500)

    assertNotNull(viewModel.uiState.value.selectedClusterPreviews)

    viewModel.clearSelectedCluster()

    val state = viewModel.uiState.value
    assertNull(state.selectedClusterPreviews)

    shopRepository.deleteShop(shop1.id)
    shopRepository.deleteShop(shop2.id)
  }

  // ============================================================================
  // QUERY UPDATE TESTS
  // ============================================================================

  @Test
  fun updateQueryCenter_triggersNewPinLoad() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop Near EPFL",
            address = testLocation,
            openingHours = testOpeningHours)

    val shop2 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop in Geneva",
            address = testLocationFar,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val pin1 = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop1.id }
    val pin2 = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop2.id }

    assertNotNull(pin1)
    assertNull(pin2)

    viewModel.updateQueryCenter(testLocationFar)
    delay(3000)

    val pin1After = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop1.id }
    val pin2After = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop2.id }

    assertNull(pin1After)
    assertNotNull(pin2After)

    shopRepository.deleteShop(shop1.id)
    shopRepository.deleteShop(shop2.id)
  }

  @Test
  fun updateRadius_loadsAdditionalPins() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop",
            address = Location(46.00, 6.00, "Far away"),
            openingHours = testOpeningHours)

    // Start query with radius smaller than distance to shop
    viewModel.startGeoQuery(
        Location(45.00, 5.00, "Center"), radiusKm = 50.0, currentUserId = testAccount1.uid)
    delay(3000)

    val pinBefore = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNull(pinBefore)

    // Increase radius to include shop
    viewModel.updateRadius(200.0)
    delay(3000)

    val pinAfter = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(pinAfter)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun updateRadius_reducingRadiusRemovesPins() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shopNear =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop Near",
            address = Location(45.01, 5.01, "Near center"),
            openingHours = testOpeningHours)

    val shopFar =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop Far",
            address = Location(46.00, 6.00, "Far away"),
            openingHours = testOpeningHours)

    // Start query large enough to include both
    viewModel.startGeoQuery(
        Location(45.00, 5.00, "Center"), radiusKm = 200.0, currentUserId = testAccount1.uid)
    delay(3000)

    val nearBefore = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shopNear.id }
    val farBefore = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shopFar.id }

    assertNotNull(nearBefore)
    assertNotNull(farBefore)

    // Reduce radius to only include near shop
    viewModel.updateRadius(50.0)
    delay(3000)

    val nearAfter = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shopNear.id }
    val farAfter = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shopFar.id }

    assertNotNull(nearAfter)
    assertNull(farAfter)

    shopRepository.deleteShop(shopNear.id)
    shopRepository.deleteShop(shopFar.id)
  }

  @Test
  fun updateCenterAndRadius_updatesQueryCorrectly() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shopEPFL =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop EPFL",
            address = testLocation,
            openingHours = testOpeningHours)

    val shopGeneva =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop Geneva",
            address = testLocationFar,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 5.0, currentUserId = testAccount1.uid)
    delay(3000)

    val epflBefore = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shopEPFL.id }
    val genevaBefore = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shopGeneva.id }

    assertNotNull(epflBefore)
    assertNull(genevaBefore)

    viewModel.updateCenterAndRadius(testLocationFar, 10.0)
    delay(3000)

    val epflAfter = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shopEPFL.id }
    val genevaAfter = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shopGeneva.id }

    assertNull(epflAfter)
    assertNotNull(genevaAfter)

    shopRepository.deleteShop(shopEPFL.id)
    shopRepository.deleteShop(shopGeneva.id)
  }

  // ============================================================================
  // PIN MOVEMENT & DELETION TESTS
  // ============================================================================

  @Test
  fun geoQuery_onKeyMoved_updatesLocation() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Moving Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    val initialPin = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(initialPin)
    assertEquals(testLocation.latitude, initialPin!!.location.latitude, 0.0001)

    shopRepository.updateShop(shop.id, address = testLocationNearby)
    delay(3000)

    val updatedPin = viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop.id }
    assertNotNull(updatedPin)
    assertEquals(testLocationNearby.latitude, updatedPin!!.location.latitude, 0.0001)

    shopRepository.deleteShop(shop.id)
  }

  @Test
  fun geoQuery_onKeyExited_removesPinWhenDeleted() = runBlocking {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Temporary Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    viewModel.startGeoQuery(testLocation, radiusKm = 10.0, currentUserId = testAccount1.uid)
    delay(3000)

    assertNotNull(viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop.id })

    shopRepository.deleteShop(shop.id)
    delay(3000)

    assertNull(viewModel.uiState.value.allGeoPins.find { it.geoPin.uid == shop.id })
  }

  // ============================================================================
  // ERROR HANDLING & LIFECYCLE TESTS
  // ============================================================================

  @Test
  fun clearErrorMsg_removesError() {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    viewModel.clearErrorMsg()

    val state = viewModel.uiState.value
    assertNull(state.errorMsg)
  }

  @Test
  fun stopGeoQuery_canBeCalledMultipleTimes() {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    viewModel.stopGeoQuery()
    viewModel.stopGeoQuery()
    viewModel.stopGeoQuery()

    val state = viewModel.uiState.value
    assertNotNull(state)
    assertTrue(state.allGeoPins.isEmpty())
  }

  @Test
  fun updateZoomLevel_updatesStateCorrectly() {
    val viewModel = MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    assertEquals(14f, viewModel.uiState.value.currentZoomLevel)

    viewModel.updateZoomLevel(16f)

    assertEquals(16f, viewModel.uiState.value.currentZoomLevel)
  }
}
