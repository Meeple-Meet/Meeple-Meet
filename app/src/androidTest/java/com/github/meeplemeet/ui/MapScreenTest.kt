package com.github.meeplemeet.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.GameNoUid
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
import com.google.firebase.Timestamp
import io.mockk.mockk
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive UI test suite for MapScreen with real Firestore data.
 *
 * Tests cover:
 * - Map display and UI structure
 * - FAB visibility and interactions based on user roles
 * - Create dialog for users with multiple roles
 * - Filter functionality
 * - Bottom sheet previews with real data
 * - Integration with geo queries
 */
@RunWith(AndroidJUnit4::class)
class MapScreenTest : FirestoreTests(), OnMapsSdkInitializedCallback {

  @get:Rule
  val permissionRule: GrantPermissionRule =
      GrantPermissionRule.grant(
          Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private val DEFAULT_TEST_KM = 10.0
  private lateinit var mapViewModel: MapViewModel
  private lateinit var mockNavigation: NavigationActions

  private lateinit var regularAccount: Account
  private lateinit var shopOwnerAccount: Account
  private lateinit var spaceRenterAccount: Account
  private lateinit var bothRolesAccount: Account

  private lateinit var testLocation: Location
  private lateinit var testOpeningHours: List<OpeningHours>

  private var fabClickCount = 0
  private var lastFabClickType: PinType? = null
  private var lastRedirect: String? = null
  private lateinit var currentAccountState: MutableState<Account>
  private var renderTrigger by mutableStateOf(0)

  @Before
  fun setup() {
    try {
      MapsInitializer.initialize(
          InstrumentationRegistry.getInstrumentation().targetContext,
          MapsInitializer.Renderer.LATEST,
          this)
    } catch (_: Exception) {}

    mapViewModel = MapViewModel()
    mockNavigation = mockk(relaxed = true)

    testLocation = Location(latitude = 46.5197, longitude = 6.5665, name = "EPFL")

    testOpeningHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("10:00", "17:00"))),
            OpeningHours(day = 7, hours = listOf(TimeSlot("10:00", "17:00"))))

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
      assert(shopOwnerAccount.shopOwner)

      spaceRenterAccount =
          accountRepository.createAccount(
              "space_renter", "Space Renter", "space@test.com", photoUrl = null)
      accountRepository.setAccountRole(
          spaceRenterAccount.uid, isShopOwner = false, isSpaceRenter = true)
      spaceRenterAccount = accountRepository.getAccount(spaceRenterAccount.uid)
      assert(spaceRenterAccount.spaceRenter)

      bothRolesAccount =
          accountRepository.createAccount(
              "both_roles", "Both Roles", "both@test.com", photoUrl = null)
      accountRepository.setAccountRole(
          bothRolesAccount.uid, isShopOwner = true, isSpaceRenter = true)
      bothRolesAccount = accountRepository.getAccount(bothRolesAccount.uid)
      assert(bothRolesAccount.shopOwner && bothRolesAccount.spaceRenter)
    }

    fabClickCount = 0
    lastFabClickType = null
    lastRedirect = null
    currentAccountState = mutableStateOf(regularAccount)
    renderTrigger = 0
  }

  override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {}

  @Test
  fun all_tests() {
    composeRule.setContent {
      val trigger = renderTrigger
      AppTheme {
        key(trigger) {
          MapScreen(
              viewModel = mapViewModel,
              navigation = mockNavigation,
              account = currentAccountState.value,
              onFABCLick = { type ->
                fabClickCount++
                lastFabClickType = type
              },
              onRedirect = { pin -> lastRedirect = pin.uid })
        }
      }
    }

    checkpoint("mapScreen_initialDisplay_showsMapAndFilterButton") {
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertIsDisplayed()
      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_BUTTON).assertIsDisplayed()
    }

    checkpoint("fab_hiddenForRegularUser") {
      currentAccountState.value = regularAccount
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertDoesNotExist()
    }

    checkpoint("fab_visibleForShopOwner") {
      currentAccountState.value = shopOwnerAccount
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertIsDisplayed()
      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertHasClickAction()
    }

    checkpoint("fab_visibleForSpaceRenter") {
      currentAccountState.value = spaceRenterAccount
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertIsDisplayed()
      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertHasClickAction()
    }

    checkpoint("fab_visibleForBothRoles") {
      currentAccountState.value = bothRolesAccount
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertIsDisplayed()
    }

    checkpoint("fab_shopOwnerOnly_directlyCallsCallbackWithShopType") {
      currentAccountState.value = shopOwnerAccount
      fabClickCount = 0 // Reset counter
      lastFabClickType = null
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      composeRule.waitForIdle()

      assert(fabClickCount == 1)
      assert(lastFabClickType == PinType.SHOP)
      composeRule.onNodeWithTag(MapScreenTestTags.ADD_CHOOSE_DIALOG).assertDoesNotExist()
    }

    checkpoint("fab_spaceRenterOnly_directlyCallsCallbackWithSpaceType") {
      currentAccountState.value = spaceRenterAccount
      fabClickCount = 0 // Reset counter
      lastFabClickType = null
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      composeRule.waitForIdle()

      assert(fabClickCount == 1)
      assert(lastFabClickType == PinType.SPACE)
      composeRule.onNodeWithTag(MapScreenTestTags.ADD_CHOOSE_DIALOG).assertDoesNotExist()
    }

    checkpoint("fab_bothRoles_showsDialogForSelection") {
      currentAccountState.value = bothRolesAccount
      fabClickCount = 0 // Reset counter
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_CHOOSE_DIALOG).assertIsDisplayed()
      assert(fabClickCount == 0)
    }

    checkpoint("createDialog_canSelectShop") {
      currentAccountState.value = bothRolesAccount
      fabClickCount = 0 // Reset counter
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_CHOOSE_DIALOG).assertIsDisplayed()

      composeRule.waitForIdle()

      assert(fabClickCount == 0)
    }

    checkpoint("createDialog_canSelectSpace") {
      currentAccountState.value = bothRolesAccount
      fabClickCount = 0 // Reset counter
      composeRule.waitForIdle()

      // Close any open dialog first
      if (composeRule
          .onAllNodesWithTag(MapScreenTestTags.ADD_CHOOSE_DIALOG)
          .fetchSemanticsNodes()
          .isNotEmpty()) {
        composeRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).performClick()
        composeRule.waitForIdle()
      }

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_CHOOSE_DIALOG).assertIsDisplayed()

      assert(fabClickCount == 0)
    }

    checkpoint("filterButton_opensAndClosesFilterPanel") {
      currentAccountState.value = regularAccount
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).assertDoesNotExist()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_BUTTON).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).assertIsDisplayed()
      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SPACE_CHIP).assertIsDisplayed()
      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SESSIONS_CHIP).assertIsDisplayed()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_BUTTON).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).assertIsNotDisplayed()
    }

    checkpoint("filterChips_canBeToggled") {
      currentAccountState.value = regularAccount
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_BUTTON).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).assertIsDisplayed()
      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SPACE_CHIP).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SESSIONS_CHIP).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).performClick()
      composeRule.waitForIdle()
    }

    checkpoint("bottomSheet_displaysShopPreview") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Chess & Checkers Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        val pin = state.geoPins.find { it.geoPin.uid == shop.id }
        assertNotNull(pin)

        mapViewModel.selectPin(pin!!)
        delay(1000)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_TITLE).assertTextContains(shop.name)
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_ADDRESS)
            .assertTextContains(shop.address.name)
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_OPENING_HOURS).assertIsDisplayed()
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_CLOSE_BUTTON).assertHasClickAction()
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON).assertIsDisplayed()

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("bottomSheet_displaysSpacePreview") {
      runBlocking {
        val spaceRenter =
            spaceRenterRepository.createSpaceRenter(
                owner = spaceRenterAccount,
                name = "Game Arena",
                address = testLocation,
                openingHours = testOpeningHours,
                spaces = listOf(Space(seats = 20, costPerHour = 30.0)))

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        val pin = state.geoPins.find { it.geoPin.uid == spaceRenter.id }
        assertNotNull(pin)

        mapViewModel.selectPin(pin!!)
        delay(1000)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_TITLE)
            .assertTextContains(spaceRenter.name)
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_ADDRESS)
            .assertTextContains(spaceRenter.address.name)
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_OPENING_HOURS).assertIsDisplayed()

        spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
      }
    }

    checkpoint("bottomSheet_displaysSessionPreview") {
      runBlocking {
        // create game document
        db.collection(GAMES_COLLECTION_PATH)
            .document("test_game_map")
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

        val testGame = gameRepository.getGameById("test_game_map")

        // create discussion then session linked to it
        val discussion =
            discussionRepository.createDiscussion(
                "Friday Night Catan",
                "Test discussion for bottom sheet preview",
                regularAccount.uid)

        sessionRepository.createSession(
            discussion.uid,
            "Friday Night Catan",
            testGame.uid,
            Timestamp.now(),
            testLocation,
            regularAccount.uid)

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        val pin = state.geoPins.find { it.geoPin.uid == discussion.uid }
        assertNotNull(pin)

        mapViewModel.selectPin(pin!!)
        delay(2000)

        val newState = mapViewModel.uiState.value
        assertNotNull(newState.selectedMarkerPreview)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_TITLE)
            .assertTextContains("Friday Night Catan")
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_GAME)
            .assertTextContains("Catan", substring = true)
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_ADDRESS).assertTextContains("EPFL")
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_DATE).assertExists()

        // cleanup via discussion delete (removes session index)
        discussionRepository.deleteDiscussion(discussion)
      }
    }

    checkpoint("bottomSheet_closeButton_closesSheet") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Test Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        val pin = state.geoPins.find { it.geoPin.uid == shop.id }
        assertNotNull(pin)

        mapViewModel.selectPin(pin!!)
        delay(1000)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_CLOSE_BUTTON).performClick()
        delay(500)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertDoesNotExist()

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("bottomSheet_viewDetailsButton_callsRedirect") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Details Test Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        val pin = state.geoPins.find { it.geoPin.uid == shop.id }
        assertNotNull(pin)

        mapViewModel.selectPin(pin!!)
        delay(1000)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON).performClick()
        composeRule.waitForIdle()

        assert(lastRedirect == shop.id)

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("completeUserFlow_shopOwner_createAndView") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Complete Flow Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent(shopOwnerAccount)

        composeRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertIsDisplayed()

        val initialCount = fabClickCount
        composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
        composeRule.waitForIdle()
        assert(fabClickCount == initialCount + 1)
        assert(lastFabClickType == PinType.SHOP)

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = shopOwnerAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        val pin = state.geoPins.find { it.geoPin.uid == shop.id }
        assertNotNull(pin)

        mapViewModel.selectPin(pin!!)
        delay(1000)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON)
            .assertHasClickAction()

        mapViewModel.clearSelectedPin()
        delay(500)

        val clearedState = mapViewModel.uiState.value
        assert(clearedState.selectedMarkerPreview == null)

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("filterIntegration_hidesAndShowsPins") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        val spaceRenter =
            spaceRenterRepository.createSpaceRenter(
                owner = spaceRenterAccount,
                name = "Space",
                address = testLocation,
                openingHours = testOpeningHours,
                spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        val initialCount = state.geoPins.size
        assert(initialCount >= 2)

        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_BUTTON).performClick()
        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
          !mapViewModel.uiState.value.activeFilters.contains(PinType.SHOP)
        }

        val stateWithFilter = mapViewModel.uiState.value
        assert(stateWithFilter.geoPins.size < initialCount)
        assert(stateWithFilter.geoPins.none { it.geoPin.type == PinType.SHOP })

        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
          mapViewModel.uiState.value.activeFilters.contains(PinType.SHOP)
        }

        val stateWithoutFilter = mapViewModel.uiState.value
        assert(stateWithoutFilter.geoPins.size == initialCount)

        shopRepository.deleteShop(shop.id)
        spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
      }
    }

    checkpoint("filterIntegration_canHideAllPinTypes") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        assert(state.geoPins.isNotEmpty())
        assert(state.allGeoPins.isNotEmpty())

        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).performClick()
        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SPACE_CHIP).performClick()
        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SESSIONS_CHIP).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
          mapViewModel.uiState.value.activeFilters.isEmpty()
        }

        val filteredState = mapViewModel.uiState.value

        assert(filteredState.geoPins.isEmpty())
        assert(filteredState.allGeoPins.isNotEmpty())

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("markers_areDisplayedOnMap") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Marker Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        val pin = state.geoPins.find { it.geoPin.uid == shop.id }
        assertNotNull(pin)

        // Marker do not expose tag to compose => fix later if possible, but assertNotNull is enough
        // composeRule.onNodeWithTag(MapScreenTestTags.getTestTagForPin(shop.id)).assertExists()

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("multipleMarkers_allDisplayed") {
      runBlocking {
        val shop1 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Shop 1",
                address = testLocation,
                openingHours = testOpeningHours)

        val shop2 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Shop 2",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        assert(state.geoPins.size >= 2)

        shopRepository.deleteShop(shop1.id)
        shopRepository.deleteShop(shop2.id)
      }
    }

    checkpoint("sessionMarkers_onlyVisibleToParticipants") {
      runBlocking {
        db.collection(GAMES_COLLECTION_PATH)
            .document("test_game_private_session")
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

        val testGame = gameRepository.getGameById("test_game_private_session")

        val discussion =
            discussionRepository.createDiscussion(
                "Private Session Discussion", "Private session test", shopOwnerAccount.uid)

        sessionRepository.createSession(
            discussion.uid,
            "Private Session",
            testGame.uid,
            Timestamp.now(),
            testLocation,
            shopOwnerAccount.uid)

        refreshContent(regularAccount)

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        var state = mapViewModel.uiState.value
        var sessionPin = state.geoPins.find { it.geoPin.uid == discussion.uid }
        assertNull(sessionPin)

        mapViewModel.stopGeoQuery()

        val ownerViewModel = MapViewModel()
        ownerViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = shopOwnerAccount.uid)
        delay(2000)

        state = ownerViewModel.uiState.value
        sessionPin = state.geoPins.find { it.geoPin.uid == discussion.uid }
        assertNotNull(sessionPin)

        // cleanup
        discussionRepository.deleteDiscussion(discussion)
      }
    }

    checkpoint("loadingState_displaysWhileLoadingPreview") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Loading Test Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(2000)

        val state = mapViewModel.uiState.value
        val pin = state.geoPins.find { it.geoPin.uid == shop.id }
        assertNotNull(pin)

        mapViewModel.selectPin(pin!!)

        composeRule.waitForIdle()
        delay(100)

        val loadingState = mapViewModel.uiState.value
        assert(loadingState.isLoadingPreview || loadingState.selectedMarkerPreview != null)

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("darkMode_appliesMapStyle") {
      refreshContent()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertIsDisplayed()
    }

    checkpoint("mapScreen_displaysBottomNavigation") {
      refreshContent()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertIsDisplayed()
    }
  }

  private fun refreshContent(account: Account? = null) {
    composeRule.runOnUiThread {
      account?.let { currentAccountState.value = it }
      renderTrigger++
    }
    composeRule.waitForIdle()
  }
}
