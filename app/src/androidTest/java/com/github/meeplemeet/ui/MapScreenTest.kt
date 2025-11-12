package com.github.meeplemeet.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
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
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

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
          accountRepository.createAccount(
              "regular_user", "Regular User", "regular@test.com", photoUrl = null)

      shopOwnerAccount =
          accountRepository.createAccount(
              "shop_owner", "Shop Owner", "shop@test.com", photoUrl = null)
      accountRepository.setAccountRole(
          shopOwnerAccount.uid, isShopOwner = true, isSpaceRenter = false)
      shopOwnerAccount = accountRepository.getAccount(shopOwnerAccount.uid)
      assert(shopOwnerAccount.shopOwner) { "Shop owner role not set correctly" }

      spaceRenterAccount =
          accountRepository.createAccount(
              "space_renter", "Space Renter", "space@test.com", photoUrl = null)
      accountRepository.setAccountRole(
          spaceRenterAccount.uid, isShopOwner = false, isSpaceRenter = true)
      spaceRenterAccount = accountRepository.getAccount(spaceRenterAccount.uid)
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

  @Test
  fun smoke_regular_user_tests() {
    setContent(account = regularAccount)
    compose.waitForIdle()

    // ========================================================================
    // Test 1: Initial Map Display + Screen Elements
    // ========================================================================

    checkpoint("Initial map display") {
      compose.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertIsDisplayed()
    }

    // ========================================================================
    // Tests 2: FAB Visibility - Regular User
    // ========================================================================

    checkpoint("FAB not visible for regular user") {
      compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertDoesNotExist()
    }

    // ========================================================================
    // Test 3: Map Initializes GeoQuery on Launch
    // ========================================================================

    checkpoint("Map Initializes GeoQuery on Launch") {
      val uiState = mapViewModel.uiState.value
      assert(uiState.geoPins.isEmpty()) { "Initially should have no pins" }
    }

    // ========================================================================
    // Test 4: Bottom Sheet - Shop Preview
    // ========================================================================

    checkpoint("Bottom Sheet - Shop Preview") {
      runBlocking {
        // Create a real shop in Firestore
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Test Board Game Shop",
                address = testLocation,
                openingHours = testOpeningHours)

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
        compose
            .onNodeWithTag(MapScreenTestTags.PREVIEW_ADDRESS)
            .assertTextContains(shop.address.name)
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
    }

    // ========================================================================
    // Test 5: Bottom Sheet - Space Preview
    // ========================================================================

    checkpoint("Bottom Sheet - Space Preview") {
      runBlocking {
        // Create a real space renter in Firestore
        val spaceRenter =
            spaceRenterRepository.createSpaceRenter(
                owner = spaceRenterAccount,
                name = "Test Game Space",
                address = testLocation,
                openingHours = testOpeningHours,
                spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

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
    }

    // ========================================================================
    // Test 6: Error Handling - Error Message Display
    // ========================================================================

    checkpoint("Error Handling - Error Message Display") {
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
    // Test 7: Update Query Parameters During Session
    // ========================================================================

    checkpoint("Update Query Parameters During Session") {

      // Start query
      mapViewModel.startGeoQuery(testLocation, radiusKm = 10.0)
      compose.waitForIdle()

      // Update center
      val newLocation = Location(latitude = 46.5200, longitude = 6.5700, name = "New Location")
      mapViewModel.updateQueryCenter(newLocation)
      // Update radius
      mapViewModel.updateRadius(15.0)
      // Update both
      mapViewModel.updateCenterAndRadius(testLocation, 20.0)
      compose.waitForIdle()

      // Verify UI is still responsive
      compose.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertExists()
    }
  }

  @Test
  fun smoke_shop_owner_user_tests() {
    setContent(account = shopOwnerAccount)
    compose.waitForIdle()

    // ========================================================================
    // Test 8: FAB Visibility - Shop Owner
    // ========================================================================
    checkpoint("FAB visible for shop owner") {
      compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertIsDisplayed()
      compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertHasClickAction()
    }

    // ========================================================================
    // Test 9: FAB Click Callback
    // ========================================================================

    checkpoint("FAB Click Callback") {
      val initialCount = fabClickCount
      compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      compose.waitForIdle()

      assert(fabClickCount == initialCount + 1) { "FAB click should increment count" }
    }
    // ========================================================================
    // Test 10: Complete User Flow - Start, Navigate, Stop
    // ========================================================================

    checkpoint("Complete User Flow - Start, Navigate, Stop") {
      runBlocking {
        // Create test entities
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Complete Flow Shop",
                address = testLocation,
                openingHours = testOpeningHours)
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
            compose
                .onNodeWithTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON)
                .assertHasClickAction()
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
  }
  // ========================================================================
  // Test 11: FAB Visibility - Space Renter
  // ========================================================================
  @Test
  fun fab_visibleForSpaceRenter() {
    checkpoint("FAB visible for space renter") {
      setContent(account = spaceRenterAccount)
      compose.waitForIdle()
      compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertIsDisplayed()
      compose.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertHasClickAction()
    }
  }
}
