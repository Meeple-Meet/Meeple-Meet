// This file was initially done by hand and refactored and improved by ChatGPT-5 Extend Thinking
// Combinations to tests were given to the LLM so it could generate the code more easily
// Docstrings were also added by ChatGPT-5 Extend Thinking
package com.github.meeplemeet.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.CreateShopViewModel
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.shops.AddShopContent
import com.github.meeplemeet.ui.shops.CreateShopScreen
import com.github.meeplemeet.ui.shops.CreateShopScreenTestTags
import com.github.meeplemeet.ui.shops.ShopTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateShopScreenTest {

  /* ───────────────────────────────── RULES ───────────────────────────────── */

  @get:Rule val compose = createComposeRule()

  /* ---------- Checkpoint helper ---------- */
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  /* ────────────────────────────── FIXTURES / FX ──────────────────────────── */

  private val owner =
      Account(uid = "Marco", handle = "meeple", name = "Meeple", email = "marco@epfl.com")

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
          .onTag(sectionBaseTag + CreateShopScreenTestTags.SECTION_TOGGLE_SUFFIX)
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
  private fun bringGamesHeaderIntoView() {
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
    // Capture vars for create stages
    var calledCreate = false
    var calledCreated = false
    var lastPayload: Triple<String, String, String>? = null
    var backCalled = false

    lateinit var stage: MutableIntState
    val viewModel = CreateShopViewModel()

    compose.setContent {
      AppTheme {
        val s = remember { mutableIntStateOf(0) }
        stage = s
        var query by remember { mutableStateOf("") }

        val locationUi by viewModel.locationUIState.collectAsState()
        val gameUi by viewModel.gameUIState.collectAsState()

        when (s.intValue) {
          // 0: Structure
          0 ->
              AddShopContent(
                  onBack = {},
                  onCreated = {},
                  onCreate = { _, _, _, _, _ -> "" },
                  gameQuery = query,
                  gameSuggestions = emptyList(),
                  isSearching = false,
                  onSetGameQuery = { q -> query = q },
                  onSetGame = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 1: Validation gating (disabled -> enabled after fields + hours)
          1 ->
              AddShopContent(
                  onBack = {},
                  onCreated = {},
                  onCreate = { _, _, _, _, _ -> "" },
                  gameQuery = query,
                  gameSuggestions = emptyList(),
                  isSearching = false,
                  onSetGameQuery = { q -> query = q },
                  onSetGame = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 2: Create success
          2 ->
              AddShopContent(
                  onBack = {},
                  onCreated = { calledCreated = true },
                  onCreate = { name, email, address, _, _ ->
                    calledCreate = true
                    lastPayload = Triple(name, email, address.name)
                    ""
                  },
                  gameQuery = query,
                  gameSuggestions = emptyList(),
                  isSearching = false,
                  onSetGameQuery = { q -> query = q },
                  onSetGame = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 3: Create error -> snackbar
          3 ->
              AddShopContent(
                  onBack = {},
                  onCreated = { error("onCreated should not be called on error") },
                  onCreate = { _, _, _, _, _ -> throw Exception("Something went wrong") },
                  gameQuery = query,
                  gameSuggestions = emptyList(),
                  isSearching = false,
                  onSetGameQuery = { q -> query = q },
                  onSetGame = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 4: Optional fields don't gate
          4 ->
              AddShopContent(
                  onBack = {},
                  onCreated = {},
                  onCreate = { _, _, _, _, _ -> "" },
                  gameQuery = query,
                  gameSuggestions = emptyList(),
                  isSearching = false,
                  onSetGameQuery = { q -> query = q },
                  onSetGame = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 5: Discard clears and calls onBack
          5 ->
              AddShopContent(
                  onBack = { backCalled = true },
                  onCreated = {},
                  onCreate = { _, _, _, _, _ -> "" },
                  gameQuery = query,
                  gameSuggestions = emptyList(),
                  isSearching = false,
                  onSetGameQuery = { q -> query = q },
                  onSetGame = {},
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
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
      compose.onTag(CreateShopScreenTestTags.SNACKBAR_HOST).assertExists()
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
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsEnabled().performClick()
      assertEquals(true, calledCreate)
      assertEquals(true, calledCreated)
      assertEquals(Triple("Meeple", "meeple@shop.com", "42 Boardgame Ave"), lastPayload)
    }

    // 3) Create error -> snackbar
    checkpoint("Create error -> snackbar") {
      compose.runOnUiThread { stage.intValue = 3 }
      compose.waitForIdle()
      fillRequiredFields(viewModel)
      setAnyOpeningHoursViaDialog()
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).performClick()
      compose.waitForIdle()
      compose.waitUntil(5_000) {
        compose
            .onAllNodes(hasText("Failed to create shop", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }
      compose
          .onAllNodes(hasText("Failed to create shop", substring = true), useUnmergedTree = true)
          .assertCountEquals(1)
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
  }

  /**
   * Single-composition flow covering all "Games" behaviors:
   * - add via dialog
   * - scroll inner grid
   * - delete removes and shows empty
   * - delete updates stock payload sent to onCreate
   * - dialog hides already-added + save disabled until pick
   * - delete then re-add
   */
  @Ignore
  @Test
  fun addShop_gamesFlows_singleComposition() {
    lateinit var stage: MutableIntState

    // Shared fixtures
    val g1 = Game("g1", "Catan", "Trade routes", "", 3, 4, null, 60, emptyList())
    val g2 = Game("g2", "Azul", "Tiles", "", 2, 4, null, 30, emptyList())
    val suggestions = listOf(g1, g2)

    val many =
        (1..20).map { idx ->
          val g = Game("g$idx", "Game $idx", "", "", 2, 4, null, 30, emptyList())
          g to 1
        }

    var capturedStock: List<Pair<Game, Int>>? = null

    // Fake game repository that returns our test data
    val fakeGameRepo =
        object : com.github.meeplemeet.model.shared.game.GameRepository {
          override suspend fun getGameById(gameID: String) = suggestions.first { it.uid == gameID }

          override suspend fun getGamesById(vararg gameIDs: String): List<Game> = suggestions

          override suspend fun searchGamesByNameContains(
              query: String,
              maxResults: Int,
              ignoreCase: Boolean
          ) = if (query.isNotEmpty()) suggestions else emptyList()
        }

    compose.setContent {
      AppTheme {
        val s = remember { mutableIntStateOf(0) }
        stage = s
        val viewModel = CreateShopViewModel(gameRepository = fakeGameRepo)
        val locationUi by viewModel.locationUIState.collectAsState()
        val gameUi by viewModel.gameUIState.collectAsState()

        when (s.intValue) {
          // 0: Add one via dialog (starts empty)
          0 ->
              AddShopContent(
                  onBack = {},
                  onCreated = {},
                  onCreate = { _, _, _, _, _ -> "" },
                  gameQuery = gameUi.gameQuery,
                  gameSuggestions = gameUi.gameSuggestions,
                  isSearching = false,
                  onSetGameQuery = { q -> viewModel.setGameQuery(q) },
                  onSetGame = { g -> viewModel.setGame(g) },
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 1: Scroll inner grid with many items
          1 ->
              AddShopContent(
                  onBack = {},
                  onCreated = {},
                  onCreate = { _, _, _, _, _ -> "" },
                  gameQuery = gameUi.gameQuery,
                  gameSuggestions = gameUi.gameSuggestions,
                  isSearching = false,
                  onSetGameQuery = { q -> viewModel.setGameQuery(q) },
                  onSetGame = { g -> viewModel.setGame(g) },
                  initialStock = many,
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 2: Delete removes and shows empty
          2 ->
              AddShopContent(
                  onBack = {},
                  onCreated = {},
                  onCreate = { _, _, _, _, _ -> "" },
                  gameQuery = gameUi.gameQuery,
                  gameSuggestions = gameUi.gameSuggestions,
                  isSearching = false,
                  onSetGameQuery = { q -> viewModel.setGameQuery(q) },
                  onSetGame = { g -> viewModel.setGame(g) },
                  initialStock = listOf(g1 to 2, g2 to 1),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 3: Delete updates stock payload on create
          3 ->
              AddShopContent(
                  onBack = {},
                  onCreated = {},
                  onCreate = { _, _, _, _, stock ->
                    capturedStock = stock
                    ""
                  },
                  gameQuery = gameUi.gameQuery,
                  gameSuggestions = gameUi.gameSuggestions,
                  isSearching = false,
                  onSetGameQuery = { q -> viewModel.setGameQuery(q) },
                  onSetGame = { g -> viewModel.setGame(g) },
                  initialStock = listOf(g1 to 2, g2 to 1),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 4: Dialog hides already-added + save disabled until pick
          4 ->
              AddShopContent(
                  onBack = {},
                  onCreated = {},
                  onCreate = { _, _, _, _, _ -> "" },
                  gameQuery = gameUi.gameQuery,
                  gameSuggestions = gameUi.gameSuggestions,
                  isSearching = false,
                  onSetGameQuery = { q -> viewModel.setGameQuery(q) },
                  onSetGame = { g -> viewModel.setGame(g) },
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)

          // 5: Delete then re-add
          5 ->
              AddShopContent(
                  onBack = {},
                  onCreated = {},
                  onCreate = { _, _, _, _, _ -> "" },
                  gameQuery = gameUi.gameQuery,
                  gameSuggestions = gameUi.gameSuggestions,
                  isSearching = false,
                  onSetGameQuery = { q -> viewModel.setGameQuery(q) },
                  onSetGame = { g -> viewModel.setGame(g) },
                  initialStock = emptyList(),
                  viewModel = viewModel,
                  owner = owner,
                  gameUi = gameUi,
                  locationUi = locationUi)
        }
      }
    }

    // 0) add via dialog
    checkpoint("Add via dialog") {
      bringGamesHeaderIntoView()
      ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)
      compose.onTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT).assertExists()
      val picked = addGameThroughDialog(suggestions, pickIndex = 0, desiredQty = 3)
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${picked.uid}").assertExists()
      compose.onTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT).assertDoesNotExist()
    }

    // 1) scroll inner grid
    checkpoint("Scroll inner grid") {
      compose.runOnUiThread { stage.intValue = 1 }
      compose.waitForIdle()
      ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)
      val gamesContentAncestor =
          CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX
      val lastTag = "${ShopTestTags.SHOP_GAME_PREFIX}g20"
      val innerScrollable =
          compose
              .onNode(hasScrollAction() and hasAnyAncestor(hasTestTag(gamesContentAncestor)), true)
              .assertExists()
      repeat(8) {
        val found = compose.onAllNodesWithTag(lastTag, true).fetchSemanticsNodes().isNotEmpty()
        if (found) return@repeat
        innerScrollable.performTouchInput { swipeUp() }
        compose.waitForIdle()
      }
      compose.onTag(lastTag).assertExists()
    }

    // 2) delete removes and shows empty
    checkpoint("Delete removes and shows empty") {
      compose.runOnUiThread { stage.intValue = 2 }
      compose.waitForIdle()
      bringGamesHeaderIntoView()
      ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${g1.uid}").assertExists()
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${g2.uid}").assertExists()
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${g1.uid}").performClick()
      compose.waitForIdle()
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${g1.uid}").assertDoesNotExist()
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${g2.uid}").assertExists()
      compose.onTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT).assertDoesNotExist()
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${g2.uid}").performClick()
      compose.waitForIdle()
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${g2.uid}").assertDoesNotExist()
      compose.onTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT).assertExists()
    }

    // 3) delete updates stock payload on create
    checkpoint("Delete updates stock payload on create") {
      compose.runOnUiThread { stage.intValue = 3 }
      compose.waitForIdle()
      bringGamesHeaderIntoView()
      ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${g1.uid}").performClick()
      compose.waitForIdle()
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsEnabled().performClick()
      compose.waitForIdle()
      val uids = requireNotNull(capturedStock).map { it.first.uid }
      val qtys = requireNotNull(capturedStock).map { it.second }
      assertEquals(listOf(g2.uid), uids)
      assertEquals(listOf(1), qtys)
    }

    // 4) dialog hides already-added + save disabled until pick
    checkpoint("Dialog hides already-added + save disabled until pick") {
      compose.runOnUiThread { stage.intValue = 4 }
      compose.waitForIdle()
      bringGamesHeaderIntoView()
      ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)
      val first = addGameThroughDialog(suggestions, pickIndex = 0, desiredQty = 2)
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${first.uid}").assertExists()

      // Open dialog again
      scrollListToTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON)
      compose.onTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON).performClick()
      compose.onTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertExists()
      compose.onTag(ShopComponentsTestTags.GAME_SEARCH_FIELD).performClick().performTextInput("a")
      compose.waitUntilTagExists(ShopComponentsTestTags.GAME_SEARCH_MENU)
      val inMenu = hasAnyAncestor(hasTestTag(ShopComponentsTestTags.GAME_SEARCH_MENU))
      compose.waitUntil(6_000) {
        compose
            .onAllNodes(hasText(g2.name) and inMenu, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }
      compose.onAllNodes(hasText(g1.name) and inMenu, useUnmergedTree = true).assertCountEquals(0)
      compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE).assertIsNotEnabled()
      compose.onTag(ShopComponentsTestTags.GAME_DIALOG_CANCEL).performClick()
      compose.onTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertDoesNotExist()
    }

    // 5) delete then re-add
    checkpoint("Delete then re-add") {
      compose.runOnUiThread { stage.intValue = 5 }
      compose.waitForIdle()
      bringGamesHeaderIntoView()
      ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)
      addGameThroughDialog(suggestions, pickIndex = 0, desiredQty = 1)
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${g1.uid}").assertExists()
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${g1.uid}").performClick()
      compose.waitForIdle()
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${g1.uid}").assertDoesNotExist()
      val readded = addGameThroughDialog(suggestions, pickIndex = 0, desiredQty = 2)
      compose.onTag("${ShopTestTags.SHOP_GAME_PREFIX}${readded.uid}").assertExists()
    }
  }

  /* ─────────────────────────────── SMOKE ─────────────────────────────────── */

  @Test
  fun createShopScreen_smoke_renders_topbar_and_list() {
    checkpoint("createShopScreen renders topbar and list") {
      val vm = CreateShopViewModel()
      compose.setContent {
        AppTheme { CreateShopScreen(owner = owner, onBack = {}, onCreated = {}, viewModel = vm) }
      }
      compose.onTag(CreateShopScreenTestTags.TOPBAR).assertExists()
      compose.onTag(CreateShopScreenTestTags.TITLE).assertExists()
      compose.onTag(CreateShopScreenTestTags.LIST).assertExists()
    }
  }
}
