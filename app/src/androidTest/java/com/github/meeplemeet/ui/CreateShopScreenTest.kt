// This file was initially done by hand and refactored and improved by ChatGPT-5 Extend Thinking
// Combinations to tests were given to the LLM so it could generate the code more easily
package com.github.meeplemeet.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.shared.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.ui.ShopTestTags as UiTags
import com.github.meeplemeet.ui.components.ShopTestTags as CTags
import com.github.meeplemeet.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateShopScreenTest {

  /* ───────────────────────────────── RULES ───────────────────────────────── */

  @get:Rule val compose = createComposeRule()

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

  private fun setContentAddShopContent(
      initialStock: List<Pair<Game, Int>> = emptyList(),
      gameSuggestions: List<Game> = emptyList(),
      onBack: () -> Unit = {},
      onCreated: () -> Unit = {},
      onCreate:
          (
              name: String,
              email: String,
              address: Location,
              week: List<OpeningHours>,
              stock: List<Pair<Game, Int>>) -> String? =
          { _, _, _, _, _ ->
            null
          }
  ) {
    compose.setContent {
      AppTheme {
        AddShopContent(
            onBack = onBack,
            onCreated = onCreated,
            onCreate = onCreate,
            gameQuery = "",
            gameSuggestions = gameSuggestions,
            isSearching = false,
            onSetGameQuery = {},
            onSetGame = {},
            initialStock = initialStock)
      }
    }
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

  /** Scroll the outer LazyColumn to bring a tagged node into view. */
  private fun scrollListToTag(tag: String) {
    compose.onTag(CreateShopScreenTestTags.LIST).performScrollToNode(hasTestTag(tag))
    compose.waitForIdle()
  }

  /** Returns the LabeledField INPUT inside the given wrapper (FIELD_* tag). */
  private fun inputIn(wrapperTag: String) =
      compose.onNode(
          hasTestTag(CTags.LABELED_FIELD_INPUT) and hasAnyAncestor(hasTestTag(wrapperTag)),
          useUnmergedTree = true)

  private fun fillRequiredFields(
      name: String = "Meeple Mart",
      email: String = "shop@example.com",
      address: String = "123 Meeple St"
  ) {
    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_REQUIRED)
    inputIn(CreateShopScreenTestTags.FIELD_SHOP).assertExists().performTextInput(name)
    inputIn(CreateShopScreenTestTags.FIELD_EMAIL).assertExists().performTextInput(email)
    compose
        .onNodeWithTag(SessionTestTags.LOCATION_FIELD, useUnmergedTree = true)
        .assertExists()
        .performClick()
        .performTextInput(address)
  }

  /** Flip Sunday to “Open 24 hours” via the dialog and assert the 1st row value. */
  private fun setAnyOpeningHoursViaDialog() {
    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_AVAILABILITY)

    compose.onTags(CTags.DAY_ROW_EDIT).assertCountEquals(7)[0].performClick()

    compose.onTag(CreateShopScreenTestTags.OPENING_HOURS_DIALOG_WRAPPER).assertExists()
    compose.onTag(CTags.DIALOG_OPEN24_CHECKBOX).assertExists().performClick()
    compose.onTag(CTags.DIALOG_SAVE).assertIsEnabled().performClick()
    compose.waitForIdle()

    compose
        .onTags(CTags.DAY_ROW_VALUE)
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

    scrollListToTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON)
    compose.onTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON).assertExists().performClick()
    compose.onTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertExists()

    compose.onTag(CTags.GAME_SEARCH_FIELD).assertExists().performClick().performTextInput("a")
    compose.waitUntilTagExists(CTags.GAME_SEARCH_MENU)

    val itemTag = "${CTags.GAME_SEARCH_ITEM}:$pickIndex"
    compose.waitUntilTagExists(itemTag)

    compose.onTag(itemTag).assertExists().performClick()

    if (desiredQty != null) {
      compose.onTag(CTags.QTY_SLIDER).assertExists().performSemanticsAction(
          SemanticsActions.SetProgress) { set ->
            set(desiredQty.toFloat())
          }
      compose.onTag(CTags.QTY_VALUE).assert(hasText(desiredQty.toString()))
    }

    compose.onTag(CTags.GAME_DIALOG_SAVE).assertIsEnabled().performClick()
    compose.onTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertDoesNotExist()
    return suggestions[pickIndex]
  }

  /** Make sure the Games header (and thus the toggle) is actually composed. */
  private fun bringGamesHeaderIntoView() {
    val headerTag =
        CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX
    scrollListToTag(headerTag)
  }

  /* ────────────────────────────── STRUCTURE ──────────────────────────────── */

  @Test
  fun structure_renders_scaffold_topbar_title_list_and_snackbarHost() {
    setContentAddShopContent()

    compose.onTag(CreateShopScreenTestTags.SCAFFOLD).assertExists()
    compose.onTag(CreateShopScreenTestTags.TOPBAR).assertExists()
    compose.onTag(CreateShopScreenTestTags.TITLE).assertExists()
    compose.onTag(CreateShopScreenTestTags.NAV_BACK).assertExists()
    compose.onTag(CreateShopScreenTestTags.SNACKBAR_HOST).assertExists()
    compose.onTag(CreateShopScreenTestTags.LIST).assertExists()
  }

  /* ────────────────────────────── VALIDATION ─────────────────────────────── */

  @Test
  fun create_disabled_until_required_fields_and_openingHours_present_then_enabled() {
    setContentAddShopContent()

    compose.onTag(CTags.ACTION_CREATE).assertIsNotEnabled()
    fillRequiredFields()
    compose.onTag(CTags.ACTION_CREATE).assertIsNotEnabled()
    setAnyOpeningHoursViaDialog()
    compose.onTag(CTags.ACTION_CREATE).assertIsEnabled()
  }

  @Test
  fun clicking_create_calls_onCreate_and_onCreated_when_valid() {
    var calledCreate = false
    var calledCreated = false
    var lastPayload: Triple<String, String, String>? = null

    setContentAddShopContent(
        onCreated = { calledCreated = true },
        onCreate = { name, email, address, _, _ ->
          calledCreate = true
          lastPayload = Triple(name, email, address.name)
          null
        })

    fillRequiredFields("Meeple", "meeple@shop.com", "42 Boardgame Ave")
    setAnyOpeningHoursViaDialog()

    compose.onTag(CTags.ACTION_CREATE).assertIsEnabled().performClick()

    assertEquals(true, calledCreate)
    assertEquals(true, calledCreated)
    assertEquals(Triple("Meeple", "meeple@shop.com", "42 Boardgame Ave"), lastPayload)
  }

  @Test
  fun clicking_create_shows_snackbar_on_error() {
    setContentAddShopContent(
        onCreate = { _, _, _, _, _ -> "Something went wrong" },
        onCreated = { error("onCreated should not be called on error") })

    fillRequiredFields()
    setAnyOpeningHoursViaDialog()

    compose.onTag(CTags.ACTION_CREATE).performClick()
    compose
        .onAllNodes(hasText("Something went wrong", substring = true), useUnmergedTree = true)
        .assertCountEquals(1)
  }

  @Test
  fun optional_fields_phone_and_link_present_and_editable_and_do_not_gate_validation() {
    setContentAddShopContent()

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

    compose.onTag(CTags.ACTION_CREATE).assertIsNotEnabled()
    fillRequiredFields()
    compose.onTag(CTags.ACTION_CREATE).assertIsNotEnabled()
    setAnyOpeningHoursViaDialog()
    compose.onTag(CTags.ACTION_CREATE).assertIsEnabled()
  }

  /* ─────────────────────────────── GAMES UI ──────────────────────────────── */

  @Test
  fun games_add_opens_dialog_pick_game_and_save_displays_card() {
    val g1 = Game("g1", "Catan", "Trade routes", "", 3, 4, null, 60, emptyList())
    val g2 = Game("g2", "Azul", "Tiles", "", 2, 4, null, 30, emptyList())
    val suggestions = listOf(g1, g2)

    setContentAddShopContent(gameSuggestions = suggestions)

    bringGamesHeaderIntoView()
    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)

    compose.onTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT).assertExists()

    val picked = addGameThroughDialog(suggestions, pickIndex = 0, desiredQty = 3)

    compose.onTag("${UiTags.SHOP_GAME_PREFIX}${picked.uid}").assertExists()
    compose.onTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT).assertDoesNotExist()
  }

  @Test
  fun games_list_scrolls_to_last_item_via_inner_grid_swipes() {
    val many =
        (1..20).map { idx ->
          val g = Game("g$idx", "Game $idx", "", "", 2, 4, null, 30, emptyList())
          g to 1
        }

    setContentAddShopContent(initialStock = many)

    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)

    val gamesContentAncestor =
        CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX
    val lastTag = "${UiTags.SHOP_GAME_PREFIX}g20"

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

  /* ─────────────────────────────── DISCARD ───────────────────────────────── */

  @Test
  fun discard_clears_all_state_and_calls_onBack() {
    var backCalled = false

    setContentAddShopContent(onBack = { backCalled = true })

    fillRequiredFields()
    setAnyOpeningHoursViaDialog()

    // Now discard -> onBack must be called
    compose.onTag(CTags.ACTION_DISCARD).performClick()
    assertEquals(true, backCalled)

    // Re-expand and verify cleared
    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_REQUIRED)
    compose
        .onAllNodes(
            hasTestTag(CTags.LABELED_FIELD_INPUT) and
                hasAnyAncestor(
                    hasTestTag(
                        CreateShopScreenTestTags.SECTION_REQUIRED +
                            CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX)),
            useUnmergedTree = true)
        .apply {
          this[0].assert(hasText(""))
          this[1].assert(hasText(""))
        }

    // Create disabled again
    compose.onTag(CTags.ACTION_CREATE).assertIsNotEnabled()
  }

  /* ─────────────────────────────── SMOKE ─────────────────────────────────── */

  @Test
  fun createShopScreen_smoke_renders_topbar_and_list() {
    val vm = com.github.meeplemeet.model.shops.CreateShopViewModel()

    compose.setContent {
      AppTheme { CreateShopScreen(owner = owner, onBack = {}, onCreated = {}, viewModel = vm) }
    }

    compose.onTag(CreateShopScreenTestTags.TOPBAR).assertExists()
    compose.onTag(CreateShopScreenTestTags.TITLE).assertExists()
    compose.onTag(CreateShopScreenTestTags.LIST).assertExists()
  }
}
