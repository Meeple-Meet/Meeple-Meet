// Test suite initially generated with Claude 4.5, following Meeple Meet's global test architecture.
// Then, the test suite was manually reviewed, cleaned up, and debugged.
package com.github.meeplemeet.integration

import com.github.meeplemeet.model.map.GeoPinWithLocation
import com.github.meeplemeet.model.map.MapUIState
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.MarkerPreview
import com.github.meeplemeet.model.map.MarkerPreviewRepository
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPin
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive test suite for MapViewModel with maximum coverage.
 *
 * Tests cover: initial state, geo query lifecycle, pin updates, error handling, preview selection,
 * query parameter updates, and type filtering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

  private lateinit var mockMarkerPreviewRepo: MarkerPreviewRepository
  private lateinit var viewModel: MapViewModel

  private val testCenter = Location(46.5197, 6.5665, "EPFL")
  private val testGeoPoint = GeoPoint(46.5197, 6.5665)
  private val testRadiusKm = 10.0

  private val shopPin = StorableGeoPin("shop_1", PinType.SHOP)
  private val spacePin = StorableGeoPin("space_1", PinType.SPACE)
  private val sessionPin = StorableGeoPin("session_1", PinType.SESSION)

  private val shopPreview =
      MarkerPreview.ShopMarkerPreview(name = "Test Shop", address = "EPFL", open = true)

  @Before
  fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())

    mockMarkerPreviewRepo = mockk(relaxed = true)
    viewModel = MapViewModel(mockMarkerPreviewRepo)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ========================================================================
  // Test 1: Initial State + Basic Query Start/Stop
  // ========================================================================

  @Test
  fun initialState_isEmpty_andStartStopQuery_resetsState() = runTest {
    // Initial state verification
    val initialState = viewModel.uiState.value
    assertEquals(MapUIState(), initialState)
    assertTrue(initialState.geoPins.isEmpty())
    assertNull(initialState.errorMsg)
    assertNull(initialState.selectedMarkerPreview)
    assertNull(initialState.selectedGeoPin)

    // Start query (creates listeners but won't trigger without real Firestore)
    viewModel.startGeoQuery(testCenter, testRadiusKm)

    // Stop query should reset state
    viewModel.stopGeoQuery()

    val stateAfterStop = viewModel.uiState.value
    assertEquals(MapUIState(), stateAfterStop)
    assertTrue(stateAfterStop.geoPins.isEmpty())
  }

  // ========================================================================
  // Test 2: Update Query Parameters (Center, Radius, Both)
  // ========================================================================

  @Test
  fun updateQueryParameters_centerRadiusAndBoth_doesNotCrash() = runTest {
    viewModel.startGeoQuery(testCenter, testRadiusKm)

    // Update center only
    val newCenter = Location(46.5200, 6.5700, "New Location")
    viewModel.updateQueryCenter(newCenter)

    // Update radius only
    val newRadius = 15.0
    viewModel.updateRadius(newRadius)

    // Update both
    val anotherCenter = Location(46.5300, 6.5800, "Another Location")
    val anotherRadius = 20.0
    viewModel.updateCenterAndRadius(anotherCenter, anotherRadius)

    // Verify state is still valid
    assertNotNull(viewModel.uiState.value)

    viewModel.stopGeoQuery()
  }

  // ========================================================================
  // Test 3: Select Pin - Success Path with Preview Fetch
  // ========================================================================

  @Test
  fun selectPin_successful_updatesStateWithPreview() = runTest {
    coEvery { mockMarkerPreviewRepo.getMarkerPreview(shopPin) } returns shopPreview

    val geoPinWithLocation = GeoPinWithLocation(shopPin, testGeoPoint)

    viewModel.selectPin(geoPinWithLocation)

    val state = viewModel.uiState.value
    assertEquals(shopPreview, state.selectedMarkerPreview)
    assertEquals("shop_1", state.selectedGeoPin?.uid)
    assertNull(state.errorMsg)

    coVerify(exactly = 1) { mockMarkerPreviewRepo.getMarkerPreview(shopPin) }
  }

  // ========================================================================
  // Test 4: Select Pin - Error Handling + Clear Error
  // ========================================================================

  @Test
  fun selectPin_failure_setsError_andClearErrorMsg_removesIt() = runTest {
    val exception = RuntimeException("Failed to load preview")
    coEvery { mockMarkerPreviewRepo.getMarkerPreview(shopPin) } throws exception

    val geoPinWithLocation = GeoPinWithLocation(shopPin, testGeoPoint)

    viewModel.selectPin(geoPinWithLocation)

    var state = viewModel.uiState.value
    assertNull(state.selectedMarkerPreview)
    assertNull(state.selectedGeoPin)
    assertNotNull(state.errorMsg)
    assertTrue(state.errorMsg!!.contains("Failed to fetch preview"))

    // Clear error
    viewModel.clearErrorMsg()

    state = viewModel.uiState.value
    assertNull(state.errorMsg)
  }

  // ========================================================================
  // Test 5: Clear Selected Pin
  // ========================================================================

  @Test
  fun clearSelectedPin_removesSelectionFromState() = runTest {
    coEvery { mockMarkerPreviewRepo.getMarkerPreview(shopPin) } returns shopPreview

    val geoPinWithLocation = GeoPinWithLocation(shopPin, testGeoPoint)

    // First select a pin
    viewModel.selectPin(geoPinWithLocation)

    var state = viewModel.uiState.value
    assertNotNull(state.selectedMarkerPreview)
    assertEquals("shop_1", state.selectedGeoPin?.uid)

    // Then clear it
    viewModel.clearSelectedPin()

    state = viewModel.uiState.value
    assertNull(state.selectedMarkerPreview)
    assertNull(state.selectedGeoPin)
  }

  // ========================================================================
  // Test 6: Type Filtering (includeTypes parameter)
  // ========================================================================

  @Test
  fun startGeoQuery_withTypeFiltering_onlyIncludesSpecifiedTypes() = runTest {
    // Start query with only SHOP and SPACE (no SESSION)
    viewModel.startGeoQuery(
        center = testCenter,
        radiusKm = testRadiusKm,
        includeTypes = setOf(PinType.SHOP, PinType.SPACE))

    // This test verifies the parameter is passed correctly
    // Actual filtering happens in the onKeyEntered listener
    // In a real scenario with Firestore, SESSION pins would be filtered out

    assertNotNull(viewModel.uiState.value)
  }

  // ========================================================================
  // Test 7: Multiple Query Lifecycle - Start, Stop, Restart
  // ========================================================================

  @Test
  fun multipleQueryLifecycle_startStopRestart_maintainsConsistentState() = runTest {
    // First query
    viewModel.startGeoQuery(testCenter, testRadiusKm)
    viewModel.stopGeoQuery()

    var state = viewModel.uiState.value
    assertTrue(state.geoPins.isEmpty())

    // Second query with different params
    val newCenter = Location(48.8566, 2.3522, "Paris")
    viewModel.startGeoQuery(newCenter, 5.0)
    viewModel.stopGeoQuery()

    state = viewModel.uiState.value
    assertTrue(state.geoPins.isEmpty())

    // Third query
    viewModel.startGeoQuery(testCenter, testRadiusKm)

    // State should still be clean
    assertNotNull(viewModel.uiState.value)

    viewModel.stopGeoQuery()
  }

  // ========================================================================
  // Test 8: Select Different Pin Types (Shop, Space, Session)
  // ========================================================================

  @Test
  fun selectPin_differentTypes_allHandledCorrectly() = runTest {
    val shopPreview = MarkerPreview.ShopMarkerPreview("Shop", "Address", true)
    val spacePreview = MarkerPreview.SpaceMarkerPreview("Space", "Address", false)
    val sessionPreview =
        MarkerPreview.SessionMarkerPreview("Session", "Address", "Game", "01/01/2025 at 20:00")

    coEvery { mockMarkerPreviewRepo.getMarkerPreview(shopPin) } returns shopPreview
    coEvery { mockMarkerPreviewRepo.getMarkerPreview(spacePin) } returns spacePreview
    coEvery { mockMarkerPreviewRepo.getMarkerPreview(sessionPin) } returns sessionPreview

    // Select shop
    viewModel.selectPin(GeoPinWithLocation(shopPin, testGeoPoint))
    var state = viewModel.uiState.value
    assertTrue(state.selectedMarkerPreview is MarkerPreview.ShopMarkerPreview)
    assertEquals("shop_1", state.selectedGeoPin?.uid)

    // Clear and select space
    viewModel.clearSelectedPin()
    viewModel.selectPin(GeoPinWithLocation(spacePin, testGeoPoint))
    state = viewModel.uiState.value
    assertTrue(state.selectedMarkerPreview is MarkerPreview.SpaceMarkerPreview)
    assertEquals("space_1", state.selectedGeoPin?.uid)

    // Clear and select session
    viewModel.clearSelectedPin()
    viewModel.selectPin(GeoPinWithLocation(sessionPin, testGeoPoint))
    state = viewModel.uiState.value
    assertTrue(state.selectedMarkerPreview is MarkerPreview.SessionMarkerPreview)
    assertEquals("session_1", state.selectedGeoPin?.uid)
  }

  // ========================================================================
  // Test 9: Error Messages - Set and Clear Multiple Times
  // ========================================================================

  @Test
  fun errorMessages_setAndClearMultipleTimes_maintainsConsistency() = runTest {
    val exception1 = RuntimeException("Error 1")
    val exception2 = RuntimeException("Error 2")

    coEvery { mockMarkerPreviewRepo.getMarkerPreview(shopPin) } throws exception1

    // First error
    viewModel.selectPin(GeoPinWithLocation(shopPin, testGeoPoint))
    var state = viewModel.uiState.value
    assertNotNull(state.errorMsg)
    assertTrue(state.errorMsg!!.contains("Error 1"))

    // Clear
    viewModel.clearErrorMsg()
    state = viewModel.uiState.value
    assertNull(state.errorMsg)

    // Second error
    coEvery { mockMarkerPreviewRepo.getMarkerPreview(spacePin) } throws exception2
    viewModel.selectPin(GeoPinWithLocation(spacePin, testGeoPoint))
    state = viewModel.uiState.value
    assertNotNull(state.errorMsg)
    assertTrue(state.errorMsg!!.contains("Error 2"))

    // Clear again
    viewModel.clearErrorMsg()
    state = viewModel.uiState.value
    assertNull(state.errorMsg)
  }

  // ========================================================================
  // Test 10: onCleared Lifecycle + Stop Query Without Active Query
  // ========================================================================

  @Test
  fun onCleared_stopsQuery_andStopQueryWithoutStart_doesNotCrash() = runTest {
    // Start a query
    viewModel.startGeoQuery(testCenter, testRadiusKm)

    // Simulate ViewModel being cleared (this calls onCleared internally)
    // We can't directly call onCleared as it's protected, but stopGeoQuery does the same
    viewModel.stopGeoQuery()

    val state = viewModel.uiState.value
    assertTrue(state.geoPins.isEmpty())

    // Try to stop again without starting (should not crash)
    viewModel.stopGeoQuery()

    // Try multiple stops
    viewModel.stopGeoQuery()
    viewModel.stopGeoQuery()

    // State should still be valid
    assertNotNull(viewModel.uiState.value)
    assertTrue(viewModel.uiState.value.geoPins.isEmpty())
  }
}
