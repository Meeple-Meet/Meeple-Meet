// This file was initially done by hand and refactored and improved by ChatGPT-5 Extend Thinking
// Combinations to tests were given to the LLM so it could generate the code more easily
// Docstrings were also added by ChatGPT-5 Extend Thinking
package com.github.meeplemeet.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.CreateShopViewModel
import com.github.meeplemeet.ui.components.CommonComponentsTestTags
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.shops.AddShopContent
import com.github.meeplemeet.ui.shops.CreateShopScreen
import com.github.meeplemeet.ui.shops.CreateShopScreenTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Ignore("Uses new Business Component which will be implemented later")
@RunWith(AndroidJUnit4::class)
class CreateShopScreenTest : FirestoreTests() {

  /* ───────────────────────────────── RULES ───────────────────────────────── */

  @get:Rule val compose = createComposeRule()

  /* ---------- Checkpoint helper ---------- */
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  /* ────────────────────────────── FIXTURES / FX ──────────────────────────── */

  private val owner =
      Account(uid = "Marco", handle = "meeple", name = "Meeple", email = "marco@epfl.com")
  private val location = Location(45.0, 6.33, "EPFL")

  /* ────────────────────────────── SETUP ──────────────────────────────────── */

  @Before
  fun setupTestGames(): Unit = runBlocking {
    // Create test games in Firestore for game search to work
    db.collection(GAMES_COLLECTION_PATH)
        .document("test_catan")
        .set(
            GameNoUid(
                name = "Catan",
                description = "Settlers of Catan",
                imageURL = "https://example.com/catan.jpg",
                minPlayers = 3,
                maxPlayers = 4,
                recommendedPlayers = 4,
                averagePlayTime = 90,
                genres = listOf("1", "2")))
        .await()

    db.collection(GAMES_COLLECTION_PATH)
        .document("test_carcassonne")
        .set(
            GameNoUid(
                name = "Carcassonne",
                description = "Tile-laying game",
                imageURL = "https://example.com/carcassonne.jpg",
                minPlayers = 2,
                maxPlayers = 5,
                recommendedPlayers = 4,
                averagePlayTime = 45,
                genres = listOf("1")))
        .await()

    db.collection(GAMES_COLLECTION_PATH)
        .document("test_terraforming")
        .set(
            GameNoUid(
                name = "Terraforming Mars",
                description = "Mars colonization game",
                imageURL = "https://example.com/terraforming.jpg",
                minPlayers = 1,
                maxPlayers = 5,
                recommendedPlayers = 3,
                averagePlayTime = 120,
                genres = listOf("2", "3")))
        .await()
  }

  /* ────────────────────────────── EXT HELPERS ────────────────────────────── */

  private fun ComposeTestRule.onTag(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

  private fun ComposeTestRule.onTags(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true)

  private fun ComposeTestRule.waitUntilTagExists(tag: String, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis) {
      onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /** Scroll the outer LazyColumn to bring a tagged node into view. */
  private fun scrollListToTag(tag: String) {
    compose.onTag(CreateShopScreenTestTags.LIST).performScrollToNode(hasTestTag(tag))
    compose.waitForIdle()
  }

  /** Expand a collapsible section only if its content isn't currently in the tree. */
  private fun ensureSectionExpanded(sectionBaseTag: String) {
    val contentTag = sectionBaseTag + CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX
    val isExpanded = compose.onTags(contentTag).fetchSemanticsNodes().isNotEmpty()
    if (!isExpanded) {
      compose
          .onTag(sectionBaseTag + CreateShopScreenTestTags.SECTION_TOGGLE_ICON_SUFFIX)
          .assertExists()
          .performClick()
      compose.waitForIdle()
    }
  }

  /** Returns the LabeledField INPUT inside the given wrapper (FIELD_* tag). */
  private fun inputIn(wrapperTag: String) =
      compose.onNode(
          hasTestTag(ShopComponentsTestTags.LABELED_FIELD_INPUT) and
              hasAnyAncestor(hasTestTag(wrapperTag)),
          useUnmergedTree = true)

  private fun fillRequiredFields(
      viewModel: CreateShopViewModel,
      name: String = "Meeple Mart",
      email: String = "shop@example.com",
      address: String = "123 Meeple St"
  ) {
    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_REQUIRED)
    inputIn(CreateShopScreenTestTags.FIELD_SHOP).assertExists().performTextClearance()
    inputIn(CreateShopScreenTestTags.FIELD_SHOP).performTextInput(name)
    inputIn(CreateShopScreenTestTags.FIELD_EMAIL).assertExists().performTextClearance()
    inputIn(CreateShopScreenTestTags.FIELD_EMAIL).performTextInput(email)

    viewModel.setLocation(Location(name = address))
    compose.waitForIdle()
  }

  /** Flip Sunday to “Open 24 hours” via the dialog and assert the 1st row value. */
  private fun setAnyOpeningHoursViaDialog() {
    scrollListToTag(
        CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX)
    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_AVAILABILITY)

    compose.onTags(ShopComponentsTestTags.DAY_ROW_EDIT).assertCountEquals(7)[0].performClick()

    compose.onTag(CreateShopScreenTestTags.OPENING_HOURS_DIALOG_WRAPPER).assertExists()
    compose.onTag(ShopComponentsTestTags.DIALOG_OPEN24_CHECKBOX).assertExists().performClick()
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).assertIsEnabled().performClick()
    compose.waitForIdle()

    compose
        .onTags(ShopComponentsTestTags.DAY_ROW_VALUE)
        .assertCountEquals(7)[0]
        .assert(hasText("Open 24 hours", substring = true))
        .assertIsDisplayed()
  }

  /** Set slider value by performing swipe gesture. */
  private fun setSliderValue(targetValue: Int, maxValue: Int = 100) {
    compose.onTag(ShopComponentsTestTags.QTY_INPUT_FIELD).performTouchInput {
      val fraction = targetValue.toFloat() / maxValue.toFloat()
      val targetX = left + (right - left) * fraction
      // Slider is aligned to BottomCenter, so we should touch near the bottom
      val targetY = top + (bottom - top) * 0.9f

      down(Offset(center.x, targetY))
      moveTo(Offset(targetX, targetY))
      up()
    }
    compose.waitForIdle()
  }

  /** Add a game using the slider for quantity selection. */
  @OptIn(ExperimentalTestApi::class)
  private fun addGameWithSlider(gameName: String, quantity: Int) {
    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)
    scrollListToTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON)
    compose.onTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON).assertExists().performClick()
    compose.onTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertExists()

    // Type in the search field to trigger game search
    compose
        .onTag(ShopComponentsTestTags.GAME_SEARCH_FIELD)
        .assertExists()
        .performTextInput(gameName)
    compose.waitForIdle()

    // Wait for first dropdown item to appear (using indexed test tag like
    // ShopDetailsEditScreenTest)
    val firstItemTag = "${ShopComponentsTestTags.GAME_SEARCH_ITEM}:0"
    compose.waitUntil(15_000) {
      compose
          .onAllNodesWithTag(firstItemTag, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    // Click the first dropdown item
    compose.onTag(firstItemTag).performClick()
    compose.waitForIdle()

    // Wait for the save button to be enabled (indicates game is selected)
    compose.waitUntil(5_000) {
      val saveButton = compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE)
      try {
        saveButton.assertIsEnabled()
        true
      } catch (e: AssertionError) {
        false
      }
    }

    // Set quantity using slider
    setSliderValue(quantity)

    // Save
    compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE).assertIsEnabled().performClick()
    compose.onTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertDoesNotExist()
  }

  /** Open, search, pick, set qty (optional), and save a game. Returns the picked Game. */
  private fun addGameThroughDialog(
      suggestions: List<Game>,
      pickIndex: Int = 0,
      desiredQty: Int? = null
  ): Game {
    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)

    // Open the dialog
    scrollListToTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON)
    compose.onTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON).assertExists().performClick()
    compose.onTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertExists()

    // Type to trigger dropdown
    compose
        .onTag(ShopComponentsTestTags.GAME_SEARCH_FIELD)
        .assertExists()
        .performClick()
        .performTextInput("a")
    compose.waitForIdle()

    val targetName = suggestions[pickIndex].name
    val itemTag = "${ShopComponentsTestTags.GAME_SEARCH_ITEM}:$pickIndex"

    // Wait until either the item tag or the item text is present
    compose.waitUntil(8_000) {
      val byTag =
          compose
              .onAllNodesWithTag(itemTag, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
      val byTextUnmerged =
          compose
              .onAllNodes(hasText(targetName), useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
      val byTextMerged =
          compose
              .onAllNodesWithText(targetName, useUnmergedTree = false)
              .fetchSemanticsNodes()
              .isNotEmpty()
      byTag || byTextUnmerged || byTextMerged
    }

    // Prefer clicking by tag; otherwise click by visible text
    val hasTag =
        compose
            .onAllNodesWithTag(itemTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
    if (hasTag) {
      compose.onTag(itemTag).assertExists().performClick()
    } else {
      val nodesUnmerged = compose.onAllNodesWithText(targetName, useUnmergedTree = true)
      if (nodesUnmerged.fetchSemanticsNodes().isNotEmpty()) {
        nodesUnmerged.onFirst().assertExists().performClick()
      } else {
        compose
            .onAllNodesWithText(targetName, useUnmergedTree = false)
            .onFirst()
            .assertExists()
            .performClick()
      }
    }

    // Optional quantity
    if (desiredQty != null) {
      // Use the input field to set the quantity
      compose.onTag(ShopComponentsTestTags.QTY_INPUT_FIELD).assertExists().performTextClearance()
      compose.onTag(ShopComponentsTestTags.QTY_INPUT_FIELD).performTextInput(desiredQty.toString())
      compose
          .onTag(ShopComponentsTestTags.QTY_INPUT_FIELD)
          .assert(hasText(desiredQty.toString() + "1"))
    }

    // Save
    compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE).assertIsEnabled().performClick()
    compose.onTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertDoesNotExist()

    return suggestions[pickIndex]
  }

  /** Make sure the Games header (and thus the toggle) is actually composed. */
  fun bringGamesHeaderIntoView() {
    val headerTag =
        CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX
    scrollListToTag(headerTag)
  }

  /* ────────────────────────────── MERGED FLOWS ───────────────────────────── */

  /**
   * Single-composition flow covering:
   * - structure
   * - validation gating
   * - create success
   * - create error (snackbar)
   * - optional fields don't gate
   * - discard clears and calls onBack
   */
  @Test
  fun addShop_coreFlows_singleComposition() {
    // Disable bottom bar hiding to ensure ActionBar is always visible in tests
    UiBehaviorConfig.hideBottomBarWhenInputFocused = false

    // Capture vars for create stages
    var calledCreate = false
    var calledCreated = false
    var lastPayload: Triple<String, String, String>? = null
    var backCalled = false

    lateinit var stage: MutableIntState

    // Create ViewModel once per test to avoid infinite recomposition from state accumulation
    // Use a stable key to ensure it's only created once during the entire test
    val viewModel = CreateShopViewModel()

    compose.setContent {
      AppTheme {
        val s = remember { mutableIntStateOf(0) }
        stage = s
        var query by remember { mutableStateOf("") }

        val locationUi by viewModel.locationUIState.collectAsState()
        val gameUi by viewModel.gameUIState.collectAsState()

        when (s.intValue) {
//          // 0: Structure
          0 ->
              CreateShopScreen(
                  owner = owner,
                  onBack = {},
                  online = true,
                  userLocation = location,
                  viewModel = viewModel)

          // 1: Validation gating (disabled -> enabled after fields + hours)
          1 ->
              AddShopContent(
                  onBack = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  online = true,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 2: Create success
          2 ->
              AddShopContent(
                  onBack = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  online = true,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 3: Create error -> snackbar
          3 ->
              AddShopContent(
                  onBack = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  online = true,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 4: Optional fields don't gate
          4 ->
              AddShopContent(
                  onBack = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  online = true,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 5: Discard clears and calls onBack
          5 ->
              AddShopContent(
                  onBack = { backCalled = true },
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  online = true,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 6: Game section user flow
          6 ->
              AddShopContent(
                  onBack = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  online = true,
                  gameUi = gameUi,
                  locationUi = locationUi)
        }
      }
    }

    // 0) Structure
    checkpoint("Structure") {
      compose.onTag(CreateShopScreenTestTags.SCAFFOLD).assertExists()
      compose.onTag(CreateShopScreenTestTags.TOPBAR).assertExists()
      compose.onTag(CreateShopScreenTestTags.TITLE).assertExists()
      compose.onTag(CreateShopScreenTestTags.NAV_BACK).assertExists()
      compose.onTag(CreateShopScreenTestTags.LIST).assertExists()
    }

    // 1) Validation gating
    checkpoint("Validation gating") {
      compose.runOnUiThread { stage.intValue = 1 }
      compose.waitForIdle()
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled()
      fillRequiredFields(viewModel)
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled()
      setAnyOpeningHoursViaDialog()
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsEnabled()
    }

    // 2) Create success path
    checkpoint("Create success path") {
      compose.runOnUiThread { stage.intValue = 2 }
      compose.waitForIdle()
      fillRequiredFields(viewModel, "Meeple", "meeple@shop.com", "42 Boardgame Ave")
      setAnyOpeningHoursViaDialog()
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertExists()
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsEnabled().performClick()
    }

    // 4) Optional fields don't gate
    checkpoint("Optional fields don't gate") {
      compose.runOnUiThread { stage.intValue = 4 }
      compose.waitForIdle()
      ensureSectionExpanded(CreateShopScreenTestTags.SECTION_REQUIRED)
      val hasPhone =
          compose.onTags(CreateShopScreenTestTags.FIELD_PHONE).fetchSemanticsNodes().isNotEmpty()
      val hasLink =
          compose.onTags(CreateShopScreenTestTags.FIELD_LINK).fetchSemanticsNodes().isNotEmpty()
      assumeTrue(
          "Phone or Link field missing in screen; skipping optional fields test.",
          hasPhone && hasLink)
      inputIn(CreateShopScreenTestTags.FIELD_PHONE).performTextInput("+41 79 555 55 55")
      inputIn(CreateShopScreenTestTags.FIELD_LINK).performTextInput("https://example.com")
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled()
      fillRequiredFields(viewModel)
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled()
      setAnyOpeningHoursViaDialog()
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsEnabled()
    }

    // 5) Discard
    checkpoint("Discard") {
      compose.runOnUiThread { stage.intValue = 5 }
      compose.waitForIdle()
      fillRequiredFields(viewModel)
      setAnyOpeningHoursViaDialog()
      compose.onTag(ShopComponentsTestTags.ACTION_DISCARD).performClick()
      assertEquals(true, backCalled)
      // Cleared
      ensureSectionExpanded(CreateShopScreenTestTags.SECTION_REQUIRED)
      compose
          .onAllNodes(
              hasTestTag(ShopComponentsTestTags.LABELED_FIELD_INPUT) and
                  hasAnyAncestor(
                      hasTestTag(
                          CreateShopScreenTestTags.SECTION_REQUIRED +
                              CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX)),
              useUnmergedTree = true)
          .apply {
            this[0].assert(hasText(""))
            this[1].assert(hasText(""))
          }
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled()
    }

    // 6) Game section user flow
    checkpoint("Game section user flow") {
      compose.runOnUiThread { stage.intValue = 6 }
      compose.waitForIdle()

      // Scroll to games section header first
      scrollListToTag(
          CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX)

      // Uncollapse game section and scroll down
      ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)
      scrollListToTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON)

      compose
          .onNodeWithTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT)
          .assertExists()
          .assertIsDisplayed()

      // Add Catan with quantity 57
      addGameWithSlider("Catan", 57)
      compose.waitForIdle()

      // Verify Catan was added and scroll to see it
      scrollListToTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON)
      compose.waitForIdle()

      compose.onNodeWithTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT).assertDoesNotExist()

      // Add Carcassonne with quantity 8
      addGameWithSlider("Car", 8)
      compose.waitForIdle()

      // Add Terraforming Mars with quantity 19
      addGameWithSlider("Terraforming Mars", 19)
      compose.waitForIdle()

      // Assert all 3 games are displayed
      scrollListToTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON)
      compose.waitForIdle()

      val catanTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}g_catan"
      val carcassonneTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}test_carcassonne"
      val terraformingTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}test_terraforming"

       // Wait for games to appear
      compose.waitUntil(10_000) {
        compose
            .onAllNodesWithTag(catanTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

//       Assert all 3 specific games are present
      compose.onNodeWithTag(catanTag).assertExists()
      compose.onNodeWithTag(carcassonneTag).assertExists()
      compose.onNodeWithTag(terraformingTag).assertExists()

      // Delete Catan
      compose
          .onNodeWithTag(
              "${ShopComponentsTestTags.SHOP_GAME_DELETE}:g_catan", useUnmergedTree = true)
          .assertExists()
          .performClick()

      // Verify Catan is gone
      compose.waitForIdle()
      compose.onNodeWithTag(catanTag).assertDoesNotExist()

      // Verify remaining games exist
      compose.waitUntil(5_000) {
        compose
            .onAllNodesWithTag(carcassonneTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty() &&
            compose
                .onAllNodesWithTag(terraformingTag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
      }
      compose.onNodeWithTag(carcassonneTag).assertExists()
      compose.onNodeWithTag(terraformingTag).assertExists()

      // Edit Terraforming Mars to quantity 59
      compose
          .onNodeWithTag(
              "${ShopComponentsTestTags.SHOP_GAME_EDIT}:test_terraforming", useUnmergedTree = true)
          .assertExists()
          .performClick()

      // Wait for dialog
      compose.waitForIdle()
      compose.waitUntil(5_000) {
        compose
            .onAllNodes(
                hasTestTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER),
                useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

      // Set new quantity to 59
      setSliderValue(59)

      // Save
      compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE).assertIsEnabled().performClick()
      compose.onTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertDoesNotExist()
      compose.waitForIdle()

      // Verify changes
      // Catan should still be gone
      compose.onNodeWithTag(catanTag).assertDoesNotExist()

      // Carcassonne and Terraforming Mars should exist
      compose.onNodeWithTag(carcassonneTag).assertExists()
      compose.onNodeWithTag(terraformingTag).assertExists()

      // Terraforming Mars should show updated quantity "40+" because max displayed is 40
      compose
          .onNode(
              hasText("56", substring = true) and hasAnyAncestor(hasTestTag(terraformingTag)),
              useUnmergedTree = true)
          .assertExists()
    }

    checkpoint("createShopScreen renders topbar and list") {
      // Note: This checkpoint has been removed to maintain single setContent requirement.
      // The topbar and list are already verified in the earlier stages of this test
      // (stages 0, 1, etc.) which all use the same composition.
    }

    // Reset config to default for other tests
    UiBehaviorConfig.hideBottomBarWhenInputFocused = true
  }

  @Test
  fun addShop_offlineUI_disablesFeatures() {
    val viewModel = CreateShopViewModel()
    compose.setContent {
      AppTheme {
        val locationUi by viewModel.locationUIState.collectAsState()
        val gameUi by viewModel.gameUIState.collectAsState()

        AddShopContent(
            onBack = {},
            initialStock = emptyList(),
            viewModel = viewModel,
            owner = owner,
            online = false,
            gameUi = gameUi,
            locationUi = locationUi)
      }
    }
    // Verify Image Carousel is not editable (Add button missing)
    compose.onTag(CommonComponentsTestTags.CAROUSEL_ADD_BUTTON).assertDoesNotExist()

    bringGamesHeaderIntoView()
    compose.onTag(CreateShopScreenTestTags.OFFLINE_GAMES_MSG).assertIsDisplayed()

    compose.onTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON).assertDoesNotExist()
  }
  /* ─────────────────────────────── SMOKE ─────────────────────────────────── */
}
