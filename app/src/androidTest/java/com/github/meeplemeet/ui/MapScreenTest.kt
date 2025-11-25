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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.cluster.Cluster
import com.github.meeplemeet.model.map.cluster.ClusterManager
import com.github.meeplemeet.model.map.cluster.ClusterStrategy
import com.github.meeplemeet.model.map.cluster.DistanceBasedClusterStrategy
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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DEFAULT_TEST_KM = 10.0

/**
 * Comprehensive UI test suite for MapScreen with clustering support.
 *
 * Tests cover:
 * - Basic UI structure and FAB behavior
 * - Clustering display and interactions
 * - Single pin and cluster bottom sheets
 * - Filter functionality with clustering
 * - Zoom level effects on clustering
 * - Session filtering by participant
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

  // Cluster strategies for testing
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
    try {
      MapsInitializer.initialize(
          InstrumentationRegistry.getInstrumentation().targetContext,
          MapsInitializer.Renderer.LATEST,
          this)
    } catch (_: Exception) {}

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

  /**
   * Tests basic UI structure, FAB behavior, filters, and creation dialog. Uses no clustering
   * strategy to test individual pins.
   */
  @Test
  fun test_basicUI_FAB_and_filters() {
    val viewModel = MapViewModel(clusterManager = ClusterManager(noClusterStrategy))

    composeRule.setContent {
      val trigger = renderTrigger
      AppTheme {
        key(trigger) {
          MapScreen(
              viewModel = viewModel,
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

    Thread.sleep(500)

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

    checkpoint("fab_shopOwnerOnly_directlyCallsCallbackWithShopType") {
      currentAccountState.value = shopOwnerAccount
      fabClickCount = 0
      lastFabClickType = null
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      composeRule.waitForIdle()

      assertEquals(1, fabClickCount)
      assertEquals(PinType.SHOP, lastFabClickType)
      composeRule.onNodeWithTag(MapScreenTestTags.ADD_CHOOSE_DIALOG).assertDoesNotExist()
    }

    checkpoint("fab_spaceRenterOnly_directlyCallsCallbackWithSpaceType") {
      currentAccountState.value = spaceRenterAccount
      fabClickCount = 0
      lastFabClickType = null
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      composeRule.waitForIdle()

      assertEquals(1, fabClickCount)
      assertEquals(PinType.SPACE, lastFabClickType)
    }

    checkpoint("fab_bothRoles_showsDialogForSelection") {
      currentAccountState.value = bothRolesAccount
      fabClickCount = 0
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.ADD_CHOOSE_DIALOG).assertIsDisplayed()
      assertEquals(0, fabClickCount)
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

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SPACE_CHIP).performClick()
      composeRule.waitForIdle()
    }

    checkpoint("filterIntegration_hidesAndShowsPins") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Filter Test Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        val space =
            spaceRenterRepository.createSpaceRenter(
                owner = spaceRenterAccount,
                name = "Filter Test Space",
                address = testLocation,
                openingHours = testOpeningHours,
                spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

        refreshContent()

        viewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val initialClusters = viewModel.getClusters()
        assertTrue(initialClusters.size >= 2)

        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_BUTTON).performClick()
        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
          !viewModel.uiState.value.activeFilters.contains(PinType.SHOP)
        }

        val filteredClusters = viewModel.getClusters()
        assertTrue(
            filteredClusters.all { cluster ->
              cluster.items.all { it.geoPin.type != PinType.SHOP }
            })

        shopRepository.deleteShop(shop.id)
        spaceRenterRepository.deleteSpaceRenter(space.id)
      }
    }
  }

  /**
   * Tests clustering display, single pin selection, and cluster interactions. Tests with both
   * single cluster strategy and no cluster strategy.
   */
  @Test
  fun test_clustering_and_singlePin_interactions() {
    // First part: test with no clustering (individual pins)
    val noClusterViewModel = MapViewModel(clusterManager = ClusterManager(noClusterStrategy))

    composeRule.setContent {
      val trigger = renderTrigger
      AppTheme {
        key(trigger) {
          MapScreen(
              viewModel = noClusterViewModel,
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

    checkpoint("singlePin_displaysMarkerPreviewSheet") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Single Pin Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        noClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = noClusterViewModel.getClusters()
        val cluster = clusters.find { it.items.size == 1 && it.items[0].geoPin.uid == shop.id }
        assertNotNull(cluster)
        assertEquals(1, cluster!!.items.size)

        // Click on the single pin
        noClusterViewModel.selectPin(cluster.items[0])
        delay(1000)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_TITLE).assertTextContains(shop.name)
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_ADDRESS)
            .assertTextContains(testLocation.name)
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_OPENING_HOURS).assertIsDisplayed()
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_CLOSE_BUTTON).assertHasClickAction()

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("singlePin_closeButton_closesSheet") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Close Test Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        noClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = noClusterViewModel.getClusters()
        val cluster = clusters.find { it.items[0].geoPin.uid == shop.id }
        assertNotNull(cluster)

        noClusterViewModel.selectPin(cluster!!.items[0])
        delay(1000)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_CLOSE_BUTTON).performClick()
        delay(500)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertDoesNotExist()

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("singlePin_viewDetailsButton_callsRedirect") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Redirect Test Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        noClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = noClusterViewModel.getClusters()
        val cluster = clusters.find { it.items[0].geoPin.uid == shop.id }
        assertNotNull(cluster)

        noClusterViewModel.selectPin(cluster!!.items[0])
        delay(1000)

        lastRedirect = null
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON).performClick()
        composeRule.waitForIdle()

        assertEquals(shop.id, lastRedirect)

        shopRepository.deleteShop(shop.id)
      }
    }

    checkpoint("sessionPin_displaysCorrectPreview") {
      runBlocking {
        db.collection(GAMES_COLLECTION_PATH)
            .document("test_game_session_pin")
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

        val testGame = gameRepository.getGameById("test_game_session_pin")

        val discussion =
            discussionRepository.createDiscussion(
                "Session Preview Test", "Test discussion", regularAccount.uid)

        sessionRepository.createSession(
            discussion.uid,
            "Session Preview Test",
            testGame.uid,
            Timestamp.now(),
            testLocation,
            regularAccount.uid)

        refreshContent()

        noClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = noClusterViewModel.getClusters()
        val cluster = clusters.find { it.items[0].geoPin.uid == discussion.uid }
        assertNotNull(cluster)

        noClusterViewModel.selectPin(cluster!!.items[0])
        delay(1500)

        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_TITLE)
            .assertTextContains("Session Preview Test")
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_GAME)
            .assertTextContains("Catan", substring = true)
        composeRule.onNodeWithTag(MapScreenTestTags.PREVIEW_DATE).assertExists()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        discussionRepository.deleteDiscussion(context, discussion)
      }
    }

    checkpoint("sessionPin_onlyVisibleToParticipants") {
      runBlocking {
        db.collection(GAMES_COLLECTION_PATH)
            .document("test_game_private")
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

        val testGame = gameRepository.getGameById("test_game_private")

        val discussion =
            discussionRepository.createDiscussion(
                "Private Session", "Private test", shopOwnerAccount.uid)

        sessionRepository.createSession(
            discussion.uid,
            "Private Session",
            testGame.uid,
            Timestamp.now(),
            testLocation,
            shopOwnerAccount.uid)

        refreshContent(regularAccount)

        noClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = noClusterViewModel.getClusters()
        val sessionCluster = clusters.find { it.items[0].geoPin.uid == discussion.uid }
        assertNull(sessionCluster)

        noClusterViewModel.stopGeoQuery()

        val ownerViewModel = MapViewModel(clusterManager = ClusterManager(noClusterStrategy))
        ownerViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = shopOwnerAccount.uid)
        delay(1500)

        val ownerClusters = ownerViewModel.getClusters()
        val ownerSessionCluster = ownerClusters.find { it.items[0].geoPin.uid == discussion.uid }
        assertNotNull(ownerSessionCluster)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        discussionRepository.deleteDiscussion(context, discussion)
      }
    }
  }

  /**
   * Tests cluster sheet display, cluster item selection, and zoom level effects. Uses single
   * cluster strategy to force clustering.
   */
  @Test
  fun test_clusterSheet_items_and_zoomEffects() {
    val singleClusterViewModel =
        MapViewModel(clusterManager = ClusterManager(singleClusterStrategy))

    composeRule.setContent {
      val trigger = renderTrigger
      AppTheme {
        key(trigger) {
          MapScreen(
              viewModel = singleClusterViewModel,
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

    checkpoint("cluster_displaysClusterSheet") {
      runBlocking {
        val shop1 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Cluster Shop 1",
                address = testLocation,
                openingHours = testOpeningHours)

        val shop2 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Cluster Shop 2",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        singleClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = singleClusterViewModel.getClusters()
        assertEquals(1, clusters.size)
        assertTrue(clusters[0].items.size >= 2)

        // Select the cluster
        singleClusterViewModel.selectCluster(clusters[0])
        delay(1500)

        composeRule.onNodeWithTag(MapScreenTestTags.CLUSTER_SHEET).assertIsDisplayed()

        // Verify cluster items are displayed
        composeRule
            .onNodeWithTag(MapScreenTestTags.getTestTagForClusterItem(shop1.id))
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(MapScreenTestTags.getTestTagForClusterItem(shop2.id))
            .assertIsDisplayed()

        shopRepository.deleteShop(shop1.id)
        shopRepository.deleteShop(shop2.id)
      }
    }

    checkpoint("clusterSheet_loadingState_displayed") {
      runBlocking {
        val shop1 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Loading Cluster Shop 1",
                address = testLocation,
                openingHours = testOpeningHours)

        val shop2 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Loading Cluster Shop 2",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        singleClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = singleClusterViewModel.getClusters()
        singleClusterViewModel.selectCluster(clusters[0])

        composeRule.waitForIdle()
        delay(100)

        val loadingState = singleClusterViewModel.uiState.value
        assertTrue(loadingState.isLoadingPreview || loadingState.selectedClusterPreviews != null)

        shopRepository.deleteShop(shop1.id)
        shopRepository.deleteShop(shop2.id)
      }
    }

    checkpoint("clusterItem_click_transitionsToSinglePinSheet") {
      runBlocking {
        val shop1 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Item Click Shop 1",
                address = testLocation,
                openingHours = testOpeningHours)

        val shop2 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Item Click Shop 2",
                address = testLocation,
                openingHours = testOpeningHours)

        refreshContent()

        singleClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = singleClusterViewModel.getClusters()
        singleClusterViewModel.selectCluster(clusters[0])
        delay(1500)

        composeRule.onNodeWithTag(MapScreenTestTags.CLUSTER_SHEET).assertIsDisplayed()

        // Click on first cluster item
        composeRule
            .onNodeWithTag(MapScreenTestTags.getTestTagForClusterItem(shop1.id))
            .performClick()
        delay(500)

        // Should now show single pin preview sheet
        composeRule.onNodeWithTag(MapScreenTestTags.CLUSTER_SHEET).assertDoesNotExist()
        composeRule.onNodeWithTag(MapScreenTestTags.MARKER_PREVIEW_SHEET).assertIsDisplayed()
        composeRule
            .onNodeWithTag(MapScreenTestTags.PREVIEW_TITLE)
            .assertTextContains("Item Click Shop 1")

        shopRepository.deleteShop(shop1.id)
        shopRepository.deleteShop(shop2.id)
      }
    }

    checkpoint("clusterSheet_displaysMultipleTypes") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Mixed Cluster Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        val space =
            spaceRenterRepository.createSpaceRenter(
                owner = spaceRenterAccount,
                name = "Mixed Cluster Space",
                address = testLocation,
                openingHours = testOpeningHours,
                spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

        refreshContent()

        singleClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = singleClusterViewModel.getClusters()
        assertTrue(clusters[0].items.size >= 2)

        singleClusterViewModel.selectCluster(clusters[0])
        delay(1500)

        composeRule.onNodeWithTag(MapScreenTestTags.CLUSTER_SHEET).assertIsDisplayed()
        composeRule
            .onNodeWithTag(MapScreenTestTags.getTestTagForClusterItem(shop.id))
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(MapScreenTestTags.getTestTagForClusterItem(space.id))
            .assertIsDisplayed()

        shopRepository.deleteShop(shop.id)
        spaceRenterRepository.deleteSpaceRenter(space.id)
      }
    }

    checkpoint("multipleMarkers_displayedAsClusters") {
      runBlocking {
        val shops = mutableListOf<String>()
        repeat(5) { i ->
          val shop =
              shopRepository.createShop(
                  owner = shopOwnerAccount,
                  name = "Multi Shop $i",
                  address = testLocation,
                  openingHours = testOpeningHours)
          shops.add(shop.id)
        }

        refreshContent()

        singleClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val clusters = singleClusterViewModel.getClusters()
        assertEquals(1, clusters.size)
        assertTrue(clusters[0].items.size >= 5)

        shops.forEach { shopRepository.deleteShop(it) }
      }
    }

    checkpoint("filter_affectsClusterContents") {
      runBlocking {
        val shop =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Filter Cluster Shop",
                address = testLocation,
                openingHours = testOpeningHours)

        val space =
            spaceRenterRepository.createSpaceRenter(
                owner = spaceRenterAccount,
                name = "Filter Cluster Space",
                address = testLocation,
                openingHours = testOpeningHours,
                spaces = listOf(Space(seats = 10, costPerHour = 25.0)))

        refreshContent()

        singleClusterViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val allTypeClusters = singleClusterViewModel.getClusters()
        assertTrue(allTypeClusters[0].items.size >= 2)

        // Filter to only shops
        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_BUTTON).performClick()
        composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SPACE_CHIP).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
          !singleClusterViewModel.uiState.value.activeFilters.contains(PinType.SPACE)
        }

        val shopOnlyClusters = singleClusterViewModel.getClusters()
        assertTrue(shopOnlyClusters[0].items.all { it.geoPin.type == PinType.SHOP })

        shopRepository.deleteShop(shop.id)
        spaceRenterRepository.deleteSpaceRenter(space.id)
      }
    }

    checkpoint("zoomLevel_affectsClustering") {
      runBlocking {
        // Shops are spaced by ~500m around center
        val shop1 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Zoom Test Shop 1",
                address = Location(testLocation.latitude + 0.0000, testLocation.longitude, "Z1"),
                openingHours = testOpeningHours)

        val shop2 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Zoom Test Shop 2",
                address = Location(testLocation.latitude + 0.0045, testLocation.longitude, "Z2"),
                openingHours = testOpeningHours)

        val shop3 =
            shopRepository.createShop(
                owner = shopOwnerAccount,
                name = "Zoom Test Shop 3",
                address = Location(testLocation.latitude, testLocation.longitude + 0.0045, "Z3"),
                openingHours = testOpeningHours)

        refreshContent()

        // Use actual simplified viewmodel for distance-based clustering strategy
        val mapViewModel =
            MapViewModel(
                clusterManager =
                    ClusterManager(
                        DistanceBasedClusterStrategy(
                            baseThresholdKm = 1.0,
                            zoomToThreshold = { zoom -> if (zoom <= 10f) 1.0 else 0.1 })))

        mapViewModel.startGeoQuery(
            testLocation, radiusKm = DEFAULT_TEST_KM, currentUserId = regularAccount.uid)
        delay(1500)

        val initialZoom = 8f // Threshold = 1.0 km
        val newZoom = 14f // Threshold = 0.1 km

        mapViewModel.updateZoomLevel(initialZoom)
        delay(500)
        val clustersAtInitialZoom = mapViewModel.getClusters()

        // Change zoom level
        mapViewModel.updateZoomLevel(newZoom)
        delay(500)
        val clustersAtNewZoom = mapViewModel.getClusters()

        val state = mapViewModel.uiState.value
        assertEquals(newZoom, state.currentZoomLevel)

        // Test clustering granularity
        assertEquals(1, clustersAtInitialZoom.size)
        assertEquals(3, clustersAtNewZoom.size)

        shopRepository.deleteShop(shop1.id)
        shopRepository.deleteShop(shop2.id)
        shopRepository.deleteShop(shop3.id)
      }
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
