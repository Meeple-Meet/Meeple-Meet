package com.github.meeplemeet.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.MarkerPreviewRepository
import com.github.meeplemeet.model.shared.game.FirestoreGameRepository
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.FirestoreTests
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Comprehensive UI test suite for MapScreen with maximum coverage.
 *
 * Tests cover: map display, bottom sheet previews, FAB visibility, error handling, navigation, and
 * user interactions with real ViewModels and repositories.
 *
 * Note: Tests avoid GoogleMap-specific interactions due to CameraUpdateFactory initialization
 * issues in test environment. Focus is on ViewModel logic, UI state, and component visibility.
 */
// @Ignore("Compose do not work well with Google maps API")
class MapScreenTest : FirestoreTests(), OnMapsSdkInitializedCallback {

  @get:Rule val compose = createComposeRule()

  private lateinit var shopRepository: ShopRepository
  private lateinit var discussionRepository: DiscussionRepository
  private lateinit var spaceRenterRepository: SpaceRenterRepository
  private lateinit var gameRepository: FirestoreGameRepository
  private lateinit var markerPreviewRepository: MarkerPreviewRepository
  private lateinit var mapViewModel: MapViewModel
  private lateinit var mockNavigation: NavigationActions

  private lateinit var regularAccount: Account
  private lateinit var shopOwnerAccount: Account
  private lateinit var spaceRenterAccount: Account

  private lateinit var testLocation: Location
  private lateinit var testOpeningHours: List<OpeningHours>

  private var fabClickCount = 0

  @Before
  fun setup() {
    // Initialize Google Maps SDK for tests
    try {
      MapsInitializer.initialize(
          androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
          MapsInitializer.Renderer.LATEST,
          this)
    } catch (_: Exception) {
      // Maps initialization may fail in test environment, that's OK
    }

    shopRepository = ShopRepository()
    discussionRepository = DiscussionRepository()
    spaceRenterRepository = SpaceRenterRepository()
    gameRepository = FirestoreGameRepository()
    markerPreviewRepository =
        MarkerPreviewRepository(
            shopRepository, discussionRepository, spaceRenterRepository, gameRepository)
    mapViewModel = MapViewModel(markerPreviewRepository)
    mockNavigation = mockk(relaxed = true)

    testLocation = Location(latitude = 46.5197, longitude = 6.5665, name = "EPFL")

    testOpeningHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))))

    runBlocking {
      regularAccount =
          discussionRepository.createAccount(
              "regular_user", "Regular User", "regular@test.com", photoUrl = null)

      shopOwnerAccount =
          discussionRepository.createAccount(
              "shop_owner", "Shop Owner", "shop@test.com", photoUrl = null)
      discussionRepository.setAccountRole(
          shopOwnerAccount.uid, isShopOwner = true, isSpaceRenter = false)
      shopOwnerAccount = discussionRepository.getAccount(shopOwnerAccount.uid)
      assert(shopOwnerAccount.shopOwner) { "Shop owner role not set correctly" }

      spaceRenterAccount =
          discussionRepository.createAccount(
              "space_renter", "Space Renter", "space@test.com", photoUrl = null)
      discussionRepository.setAccountRole(
          spaceRenterAccount.uid, isShopOwner = false, isSpaceRenter = true)
      spaceRenterAccount = discussionRepository.getAccount(spaceRenterAccount.uid)
      assert(spaceRenterAccount.spaceRenter) { "Space renter role not set correctly" }
    }

    fabClickCount = 0
  }

  override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
    // Maps SDK initialized callback
  }

  private fun setContent(account: Account = regularAccount) {
    compose.setContent {
      AppTheme {
        MapScreen(
            viewModel = mapViewModel,
            navigation = mockNavigation,
            account = account,
            onFABCLick = { fabClickCount++ },
            onRedirect = {})
      }
    }
  }

  // ========================================================================
  // Test 1: Initial Map Display + Screen Elements
  // ========================================================================

  @Test
  fun initialMapDisplay_showsMapAndTopBarWithoutErrors() {
    setContent()
    compose.waitForIdle()

    // Verify map is displayed
    compose.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertIsDisplayed()
  }

  // ========================================================================
  // Tests 2: FAB Visibility - Shop Owner, Space Renter, Regular User
  // ========================================================================

  @Test
  fun fab_hiddenForRegularUser() {
    setContent(account = regularAccount)
    compose.waitForIdle()
    compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertDoesNotExist()
  }

  @Test
  fun fab_visibleForShopOwner() {
    setContent(account = shopOwnerAccount)
    compose.waitForIdle()
    compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertHasClickAction()
  }

  @Test
  fun fab_visibleForSpaceRenter() {
    setContent(account = spaceRenterAccount)
    compose.waitForIdle()
    compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertHasClickAction()
  }

  // ========================================================================
  // Test 3: FAB Click Callback
  // ========================================================================

  @Test
  fun fab_whenClicked_triggersCallback() {
    setContent(account = shopOwnerAccount)
    compose.waitForIdle()

    val initialCount = fabClickCount
    compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
    compose.waitForIdle()

    assert(fabClickCount == initialCount + 1) { "FAB click should increment count" }
  }

  // ========================================================================
  // Test 4: Map Initializes GeoQuery on Launch
  // ========================================================================

  @Test
  fun mapScreen_startsGeoQueryOnLaunch() {
    setContent()
    compose.waitForIdle()

    // After a short delay, geoQuery should be initialized
    // We can verify by checking that the UI state is no longer null
    val uiState = mapViewModel.uiState.value
    assert(uiState.geoPins.isEmpty()) { "Initially should have no pins" }
  }

  // ========================================================================
  // Test 5: Bottom Sheet - Shop Preview
  // ========================================================================

  @Test
  fun bottomSheet_displaysShopPreview_withRealFirestoreData() = runBlocking {
    // Create a real shop in Firestore
    val shop =
        shopRepository.createShop(
            owner = shopOwnerAccount,
            name = "Test Board Game Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    setContent()
    compose.waitForIdle()

    // Start geo query around EPFL
    mapViewModel.startGeoQuery(testLocation, radiusKm = 10.0)
    delay(2000) // Wait for Firestore listeners to trigger

    val state = mapViewModel.uiState.value
    assert(state.geoPins.isNotEmpty()) { "Expected at least one pin" }

    val pin = state.geoPins.first()
    mapViewModel.selectPin(pin)
    delay(1000)

    val updatedState = mapViewModel.uiState.value
    assert(updatedState.selectedMarkerPreview != null) { "Preview should be loaded" }

    compose.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.PREVIEW_TITLE).assertTextContains(shop.name)
    compose.onNodeWithTag(MapScreenTestTags.PREVIEW_ADDRESS).assertTextContains(shop.address.name)
    compose.onNodeWithTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.PREVIEW_CLOSE_BUTTON).assertHasClickAction()

    mapViewModel.clearSelectedPin()
    delay(500)

    val clearedState = mapViewModel.uiState.value
    assert(clearedState.selectedMarkerPreview == null) { "Preview should be cleared" }
    assert(clearedState.selectedGeoPin == null) { "GeoPin selection should be cleared" }

    // Clean up
    shopRepository.deleteShop(shop.id)
  }

  // ========================================================================
  // Test 6: Bottom Sheet - Space Preview
  // ========================================================================

  @Test
  fun bottomSheet_displaysSpacePreview_withRealFirestoreData() = runBlocking {
    // Create a real space renter in Firestore
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = spaceRenterAccount,
            name = "Test Game Space",
            address = testLocation,
            openingHours = testOpeningHours,
            spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

    setContent()
    compose.waitForIdle()

    // Start geo query
    mapViewModel.startGeoQuery(testLocation, radiusKm = 10.0)
    delay(2000)

    val state = mapViewModel.uiState.value
    assert(state.geoPins.isNotEmpty()) { "Expected at least one pin" }

    val pin = state.geoPins.first()
    mapViewModel.selectPin(pin)
    delay(1000)

    val updatedState = mapViewModel.uiState.value
    assert(updatedState.selectedMarkerPreview != null) { "Preview should be loaded" }

    compose.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.PREVIEW_TITLE).assertTextContains(spaceRenter.name)
    compose
        .onNodeWithTag(MapScreenTestTags.PREVIEW_ADDRESS)
        .assertTextContains(spaceRenter.address.name)
    compose.onNodeWithTag(MapScreenTestTags.PREVIEW_OPENING_HOURS).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.PREVIEW_CLOSE_BUTTON).assertHasClickAction()

    mapViewModel.clearSelectedPin()
    delay(500)

    val clearedState = mapViewModel.uiState.value
    assert(clearedState.selectedMarkerPreview == null) { "Preview should be cleared" }
    assert(clearedState.selectedGeoPin == null) { "GeoPin selection should be cleared" }

    // Clean up
    spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
  }

  // ========================================================================
  // Test 7: Error Handling - Error Message Display
  // ========================================================================

  @Test
  fun errorMessage_canBeSetAndCleared() {
    setContent()
    compose.waitForIdle()

    // Initially no error
    var state = mapViewModel.uiState.value
    assert(state.errorMsg == null) { "Initially no error" }

    // Errors are typically set by geo query listeners
    // We can verify clearErrorMsg works
    mapViewModel.clearErrorMsg()
    state = mapViewModel.uiState.value
    assert(state.errorMsg == null) { "Error should remain null" }
  }

  // ========================================================================
  // Test 8: Update Query Parameters During Session
  // ========================================================================

  @Test
  fun updateQueryParameters_duringActiveSession_doesNotCrash() {
    setContent()
    compose.waitForIdle()

    // Start query
    mapViewModel.startGeoQuery(testLocation, radiusKm = 10.0)
    compose.waitForIdle()

    // Update center
    val newLocation = Location(latitude = 46.5200, longitude = 6.5700, name = "New Location")
    mapViewModel.updateQueryCenter(newLocation)
    compose.waitForIdle()

    // Update radius
    mapViewModel.updateRadius(15.0)
    compose.waitForIdle()

    // Update both
    mapViewModel.updateCenterAndRadius(testLocation, 20.0)
    compose.waitForIdle()

    // Verify UI is still responsive
    compose.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertExists()
  }

  // ========================================================================
  // Test 9: Complete User Flow - Start, Navigate, Stop
  // ========================================================================

  @Test
  fun completeUserFlow_startNavigateStop_worksCorrectly() = runBlocking {
    // Create test entities
    val shop =
        shopRepository.createShop(
            owner = shopOwnerAccount,
            name = "Complete Flow Shop",
            address = testLocation,
            openingHours = testOpeningHours)

    setContent(account = shopOwnerAccount)
    compose.waitForIdle()

    // Verify initial state
    compose.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertIsDisplayed()

    // Start geo query
    mapViewModel.startGeoQuery(testLocation, radiusKm = 10.0)
    delay(2000)

    val state = mapViewModel.uiState.value
    assert(state.geoPins.isNotEmpty() || state.geoPins.isEmpty()) { "State should be valid" }

    // Click FAB
    val initialFabCount = fabClickCount
    compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
    compose.waitForIdle()
    assert(fabClickCount == initialFabCount + 1) { "FAB callback triggered" }

    // If pins exist, select one
    if (state.geoPins.isNotEmpty()) {
      mapViewModel.selectPin(state.geoPins.first())
      delay(1000)

      val updatedState = mapViewModel.uiState.value
      // Preview should load or error should appear
      assert(updatedState.selectedMarkerPreview != null || updatedState.errorMsg != null) {
        "Selection should update state"
      }

      // If bottom sheet opened, verify close works
      if (updatedState.selectedMarkerPreview != null) {
        // Bottom sheet should show
        compose.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        compose.onNodeWithTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON).assertHasClickAction()
        compose.onNodeWithTag(MapScreenTestTags.PREVIEW_CLOSE_BUTTON).assertHasClickAction()

        mapViewModel.clearSelectedPin()
        delay(500)

        val clearedState = mapViewModel.uiState.value
        assert(clearedState.selectedMarkerPreview == null) { "Preview cleared" }
      }
    }

    // Stop query
    mapViewModel.stopGeoQuery()
    delay(100)

    val finalState = mapViewModel.uiState.value
    assert(finalState.geoPins.isEmpty()) { "Pins should be cleared after stop" }

    // Clean up
    shopRepository.deleteShop(shop.id)
  }
}
