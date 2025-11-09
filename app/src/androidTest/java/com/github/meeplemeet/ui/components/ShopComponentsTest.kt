// This file was initially done by hand and refactored and improved by ChatGPT-5 Extend Thinking
// Combinations to tests were given to the LLM so it could generate the code more easily
package com.github.meeplemeet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.ShopSearchViewModel
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import io.mockk.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShopComponentsTest {

  @get:Rule val compose = createComposeRule()

  /* ---------- Helpers ---------- */
  private fun ComposeTestRule.onTag(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

  private fun ComposeTestRule.onTags(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true)

  private fun ComposeTestRule.onText(text: String) = onNodeWithText(text, useUnmergedTree = true)

  private fun setContentThemed(content: @Composable () -> Unit) =
      compose.setContent { AppTheme(themeMode = ThemeMode.LIGHT) { content() } }

  private fun LocalTime.fmt12(): String = DateTimeFormatter.ofPattern("h:mm a").format(this)

  private object Fx {
    val t07_30 = LocalTime.of(7, 30)!!
    val t12_00 = LocalTime.of(12, 0)!!
    val openingDefault = OpeningHours(0, listOf(TimeSlot("07:30", "12:00")))
    val openingOpen24 = OpeningHours(0, listOf(TimeSlot("00:00", "23:59")))
    val game1 = Game("1", "Catan", "", "", 3, 4, null, 60, emptyList())
    val game2 = Game("2", "Carcassonne", "", "", 2, 5, null, 35, emptyList())
    val game3 = Game("3", "Azul", "", "", 2, 4, null, 30, emptyList())
  }

  /** 1) Lightweight stateless composables */
  @Test
  fun basicComponents_render_and_interact_fast() {
    var clicks = 0
    var edits = 0
    var selected by mutableStateOf(setOf(1, 3))
    var fieldValue by mutableStateOf("")
    lateinit var stage: MutableIntState

    setContentThemed {
      val s = remember { mutableIntStateOf(0) }
      stage = s
      when (s.intValue) {
        // 0: SectionHeader
        0 -> SectionHeader(ShopUiDefaults.StringsMagicNumbers.REQUIRED_INFO)

        // 1: LabeledField
        1 ->
            LabeledField(
                label = ShopUiDefaults.StringsMagicNumbers.LABEL_SHOP,
                placeholder = ShopUiDefaults.StringsMagicNumbers.PLACEHOLDER_SHOP,
                value = fieldValue,
                onValueChange = { fieldValue = it })

        // 2: TimeField
        2 ->
            TimeField(
                label = ShopUiDefaults.StringsMagicNumbers.OPEN_TIME,
                value = Fx.t07_30.fmt12(),
                onClick = { clicks++ })

        // 3: DayRow
        3 ->
            DayRow(
                dayName = "Tuesday",
                value = "7:30 AM - 12:00 PM\n2:00 PM - 8:00 PM",
                onEdit = { edits++ })

        // 4: HourRow
        4 ->
            HourRow(
                Fx.t07_30,
                Fx.t12_00,
                onPickStart = { clicks++ },
                onPickEnd = { clicks++ },
                onRemove = { clicks++ },
                rowIndex = 0)

        // 5: DaysSelector
        5 ->
            DaysSelector(
                selected = selected,
                onToggle = { d ->
                  selected =
                      selected.toMutableSet().apply { if (contains(d)) remove(d) else add(d) }
                })
      }
    }

    // Stage 0: SectionHeader
    val title = ShopUiDefaults.StringsMagicNumbers.REQUIRED_INFO
    compose.onTag(ShopComponentsTestTags.sectionHeader(title)).assertExists().assertIsDisplayed()
    compose.onTag(ShopComponentsTestTags.SECTION_HEADER_LABEL).assert(hasText(title))
    compose.onTag(ShopComponentsTestTags.SECTION_HEADER_DIVIDER).assertExists()

    // Stage 1: LabeledField
    compose.runOnUiThread { stage.intValue = 1 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.LABELED_FIELD_INPUT).performTextInput("Meeple Market")
    compose.onTag(ShopComponentsTestTags.LABELED_FIELD_INPUT).assertTextEquals("Meeple Market")

    // Stage 2: TimeField
    compose.runOnUiThread { stage.intValue = 2 }
    compose.waitForIdle()
    compose
        .onTag(ShopComponentsTestTags.TIME_FIELD_LABEL)
        .assert(hasText(ShopUiDefaults.StringsMagicNumbers.OPEN_TIME))
    compose.onTag(ShopComponentsTestTags.TIME_FIELD_VALUE).assertTextEquals(Fx.t07_30.fmt12())
    compose.onTag(ShopComponentsTestTags.TIME_FIELD_CARD).performClick()
    assert(clicks == 1)

    // Stage 3: DayRow
    compose.runOnUiThread { stage.intValue = 3 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.dayRow("Tuesday")).performClick()
    compose.onTag(ShopComponentsTestTags.DAY_ROW_EDIT).performClick()
    assert(edits == 2)

    // Stage 4: HourRow
    compose.runOnUiThread { stage.intValue = 4 }
    compose.waitForIdle()
    val values = compose.onTags(ShopComponentsTestTags.TIME_FIELD_VALUE)
    values.assertCountEquals(2)
    values[0].assertTextEquals(Fx.t07_30.fmt12())
    values[1].assertTextEquals(Fx.t12_00.fmt12())
    compose.onTag(ShopComponentsTestTags.HOUR_ROW_OPEN_FIELD).performClick()
    compose.onTag(ShopComponentsTestTags.HOUR_ROW_CLOSE_FIELD).performClick()
    compose.onTag(ShopComponentsTestTags.HOUR_ROW_REMOVE).performClick()
    assert(clicks == 4)

    // Stage 5: DaysSelector
    compose.runOnUiThread { stage.intValue = 5 }
    compose.waitForIdle()
    (0..6).forEach { idx -> compose.onTag(ShopComponentsTestTags.dayChip(idx)).assertExists() }
    compose.onTag(ShopComponentsTestTags.dayChip(0)).performClick()
    compose.onTag(ShopComponentsTestTags.dayChip(1)).performClick()
    compose.onTag(ShopComponentsTestTags.dayChip(6)).performClick()
  }

  /** 2) OpeningHoursDialog */
  @Test
  fun openingHoursDialog_coreFlows_singleComposition() {
    var sorted: List<Pair<LocalTime, LocalTime>> = emptyList()
    var saved = false
    var savedIntervals: List<Pair<LocalTime, LocalTime>> = emptyList()
    var savedDays: Set<Int> = emptySet()
    var savedFlag = false
    lateinit var stage: MutableIntState

    setContentThemed {
      val s = remember { mutableIntStateOf(0) }
      stage = s
      when (s.intValue) {
        // 0: Open24 – no intervals
        0 ->
            OpeningHoursDialog(
                initialSelectedDays = setOf(0),
                current = Fx.openingOpen24,
                onDismiss = {},
                onSave = { _, closed, open24, _ -> assert(!closed && open24) })

        // 1: Closed toggle hides intervals
        1 ->
            OpeningHoursDialog(
                initialSelectedDays = setOf(0),
                current = Fx.openingDefault,
                onDismiss = {},
                onSave = { _, _, _, _ -> })

        // 2: Sorting on save
        2 ->
            OpeningHoursDialog(
                initialSelectedDays = setOf(1),
                current =
                    OpeningHours(1, listOf(TimeSlot("14:00", "16:00"), TimeSlot("07:30", "08:30"))),
                onDismiss = {},
                onSave = { _, _, _, intervals -> sorted = intervals })

        // 3: Overlap -> error; remove -> save OK
        3 ->
            OpeningHoursDialog(
                initialSelectedDays = setOf(0),
                current = OpeningHours(0, listOf(TimeSlot("07:30", "12:00"))),
                onDismiss = {},
                onSave = { _, closed, open24, intervals ->
                  assert(!closed && !open24)
                  saved = true
                  savedIntervals = intervals
                })

        // 4: Multi-select days
        4 ->
            OpeningHoursDialog(
                initialSelectedDays = setOf(1),
                current = OpeningHours(1, listOf(TimeSlot("07:30", "12:00"))),
                onDismiss = {},
                onSave = { days, _, _, _ -> savedDays = days })

        // 5: Provided overlapping hours -> blocked
        5 ->
            OpeningHoursDialog(
                initialSelectedDays = setOf(3),
                current =
                    OpeningHours(3, listOf(TimeSlot("07:30", "12:00"), TimeSlot("11:00", "13:00"))),
                onDismiss = {},
                onSave = { _, _, _, _ -> savedFlag = true })
      }
    }

    // 0: Open24 – no intervals
    compose.onTag(ShopComponentsTestTags.DIALOG_OPEN24_CHECKBOX).assertIsOn()
    compose.onTag(ShopComponentsTestTags.DIALOG_INTERVALS).assertDoesNotExist()
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()

    // 1: Closed toggle hides intervals
    compose.runOnUiThread { stage.intValue = 1 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.DIALOG_INTERVALS).assertExists()
    compose.onTag(ShopComponentsTestTags.DIALOG_CLOSED_CHECKBOX).performClick()
    compose.onTag(ShopComponentsTestTags.DIALOG_INTERVALS).assertDoesNotExist()
    compose.onTag(ShopComponentsTestTags.DIALOG_OPEN24_CHECKBOX).assertIsOff()

    // 2: Sorting on save
    compose.runOnUiThread { stage.intValue = 2 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()
    assert(sorted.map { it.first } == listOf(LocalTime.of(7, 30), LocalTime.of(14, 0)))

    // 3: Overlap -> error; remove -> save OK
    compose.runOnUiThread { stage.intValue = 3 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.hourRow(0)).assertExists()
    compose.onTag(ShopComponentsTestTags.DIALOG_ADD_HOURS).performClick()
    compose.onTag(ShopComponentsTestTags.hourRow(1)).assertExists()
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()
    compose.onTag(ShopComponentsTestTags.DIALOG_ERROR).assertExists().assertIsDisplayed()
    assert(!saved)
    compose.onTags(ShopComponentsTestTags.HOUR_ROW_REMOVE).assertCountEquals(2)[1].performClick()
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()
    assert(saved && savedIntervals.isNotEmpty())

    // 4: Multi-select days
    compose.runOnUiThread { stage.intValue = 4 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.dayChip(2)).performClick()
    compose.onTag(ShopComponentsTestTags.dayChip(5)).performClick()
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()
    assert(savedDays.containsAll(listOf(1, 2, 5)))

    // 5: Provided overlapping hours -> blocked
    compose.runOnUiThread { stage.intValue = 5 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()
    compose.onTag(ShopComponentsTestTags.DIALOG_ERROR).assertExists().assertIsDisplayed()
    assert(!savedFlag)
  }

  /** 3) ActionBar */
  @Test
  fun actionBar_enabled_then_disabled_singleComposition() {
    var discard = 0
    var create = 0
    lateinit var enabledState: MutableState<Boolean>

    setContentThemed {
      val isEnabled = remember { mutableStateOf(true) }
      enabledState = isEnabled
      ActionBar(onDiscard = { discard++ }, onPrimary = { create++ }, enabled = isEnabled.value)
    }

    // enabled
    compose.onTag(ShopComponentsTestTags.ACTION_BAR).assertExists()
    compose.onTag(ShopComponentsTestTags.ACTION_DISCARD).performClick()
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).performClick()
    assert(discard == 1 && create == 1)

    // disabled
    compose.runOnUiThread { enabledState.value = false }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled().performClick()
    assert(create == 1)
  }

  /** 4) GameStockDialog - Rewritten for new API */
  @Test
  fun gameStockDialog_search_filter_duplicate_quantity_singleComposition() {
    val owner = Account(uid = "owner1", handle = "owner", name = "Owner", email = "owner@test")
    val shop = null // Can be null for testing
    var qty by mutableIntStateOf(2)
    lateinit var stage: MutableIntState

    // Create mutable state flows for each stage
    val gameUIStateFlow0 =
        MutableStateFlow(
            GameUIState(
                gameQuery = "",
                gameSuggestions = listOf(Fx.game1, Fx.game2, Fx.game3),
                fetchedGame = null,
                gameSearchError = null))
    val gameUIStateFlow1 =
        MutableStateFlow(
            GameUIState(
                gameQuery = "",
                gameSuggestions = listOf(Fx.game1, Fx.game3),
                fetchedGame = null,
                gameSearchError = null))
    val gameUIStateFlow2 =
        MutableStateFlow(
            GameUIState(
                gameQuery = "Az",
                gameSuggestions = emptyList(),
                fetchedGame = null,
                gameSearchError = null))
    val gameUIStateFlow3 =
        MutableStateFlow(
            GameUIState(
                gameQuery = "Catan",
                gameSuggestions = emptyList(),
                fetchedGame = Fx.game1,
                gameSearchError = null))
    val gameUIStateFlow4 =
        MutableStateFlow(
            GameUIState(
                gameQuery = "",
                gameSuggestions = emptyList(),
                fetchedGame = Fx.game1,
                gameSearchError = null))

    val mockViewModel0 = mockk<ShopSearchViewModel>(relaxed = true)
    every { mockViewModel0.gameUIState } returns gameUIStateFlow0

    val mockViewModel1 = mockk<ShopSearchViewModel>(relaxed = true)
    every { mockViewModel1.gameUIState } returns gameUIStateFlow1

    val mockViewModel2 = mockk<ShopSearchViewModel>(relaxed = true)
    every { mockViewModel2.gameUIState } returns gameUIStateFlow2

    val mockViewModel3 = mockk<ShopSearchViewModel>(relaxed = true)
    every { mockViewModel3.gameUIState } returns gameUIStateFlow3

    val mockViewModel4 = mockk<ShopSearchViewModel>(relaxed = true)
    every { mockViewModel4.gameUIState } returns gameUIStateFlow4

    setContentThemed {
      val s = remember { mutableIntStateOf(0) }
      stage = s
      when (s.intValue) {
        // 0: Filtering hides existing
        0 -> {
          val gameState by gameUIStateFlow0.collectAsState()
          GameStockDialog(
              owner = owner,
              shop = shop,
              viewModel = mockViewModel0,
              gameUIState = gameState,
              onQueryChange = { gameUIStateFlow0.value = gameState.copy(gameQuery = it) },
              quantity = 2,
              onQuantityChange = {},
              existingIds = setOf("2"),
              onDismiss = {},
              onSave = {})
        }

        // 1: Search -> pick -> save enabled
        1 -> {
          val gameState by gameUIStateFlow1.collectAsState()
          GameStockDialog(
              owner = owner,
              shop = shop,
              viewModel = mockViewModel1,
              gameUIState = gameState,
              onQueryChange = { gameUIStateFlow1.value = gameState.copy(gameQuery = it) },
              quantity = 2,
              onQuantityChange = {},
              existingIds = emptySet(),
              onDismiss = {},
              onSave = {})
        }

        // 2: Loading then clear
        2 -> {
          val gameState by gameUIStateFlow2.collectAsState()
          GameStockDialog(
              owner = owner,
              shop = shop,
              viewModel = mockViewModel2,
              gameUIState = gameState,
              onQueryChange = { gameUIStateFlow2.value = gameState.copy(gameQuery = it) },
              quantity = 2,
              onQuantityChange = {},
              existingIds = emptySet(),
              onDismiss = {},
              onSave = {})
        }

        // 3: Duplicate disables save
        3 -> {
          val gameState by gameUIStateFlow3.collectAsState()
          GameStockDialog(
              owner = owner,
              shop = shop,
              viewModel = mockViewModel3,
              gameUIState = gameState,
              onQueryChange = {},
              quantity = 2,
              onQuantityChange = {},
              existingIds = setOf(Fx.game1.uid),
              onDismiss = {},
              onSave = {})
        }

        // 4: Quantity slider & zero disables save
        4 -> {
          val gameState by gameUIStateFlow4.collectAsState()
          GameStockDialog(
              owner = owner,
              shop = shop,
              viewModel = mockViewModel4,
              gameUIState = gameState,
              onQueryChange = {},
              quantity = qty,
              onQuantityChange = { qty = it },
              existingIds = emptySet(),
              onDismiss = {},
              onSave = {})
        }
      }
    }

    // 0: Filtering hides existing - game2 (id="2") should be filtered out
    compose.onTag(ShopComponentsTestTags.GAME_SEARCH_FIELD).performClick().performTextInput("a")
    compose.onTag(ShopComponentsTestTags.GAME_SEARCH_MENU).assertExists().assertIsDisplayed()
    compose.onText("Carcassonne").assertDoesNotExist() // This is game2, should be filtered
    compose.onText("Catan").assertExists()
    compose.onText("Azul").assertExists()

    // 1: Search -> pick -> save enabled
    compose.runOnUiThread { stage.intValue = 1 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.GAME_SEARCH_FIELD).performClick().performTextInput("a")
    compose.onTag(ShopComponentsTestTags.GAME_SEARCH_MENU).assertExists()
    compose.onTag("${ShopComponentsTestTags.GAME_SEARCH_ITEM}:0").performClick()
    compose.runOnUiThread {
      gameUIStateFlow1.value = gameUIStateFlow1.value.copy(fetchedGame = Fx.game1)
    }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE).assertIsEnabled()

    // 2: Loading then clear
    compose.runOnUiThread { stage.intValue = 2 }
    compose.waitForIdle()
    // Simulate loading state
    compose.runOnUiThread { gameUIStateFlow2.value = gameUIStateFlow2.value.copy(gameQuery = "Az") }
    compose.waitForIdle()
    // Note: Loading indicator may not be present in new API without explicit loading state
    // Clear button should appear when there's a query
    compose.onTag(ShopComponentsTestTags.GAME_SEARCH_CLEAR).assertExists().performClick()
    compose.runOnUiThread { gameUIStateFlow2.value = gameUIStateFlow2.value.copy(gameQuery = "") }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.GAME_SEARCH_FIELD).assertTextEquals("")

    // 3: Duplicate disables save
    compose.runOnUiThread { stage.intValue = 3 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.GAME_DIALOG_HELPER).assertExists().assertIsDisplayed()
    compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE).assertIsNotEnabled()

    // 4: Quantity slider & zero disables save
    compose.runOnUiThread { stage.intValue = 4 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.QTY_INPUT_FIELD).performTextInput("10")
    compose.onTag(ShopComponentsTestTags.QTY_INPUT_FIELD).assertTextEquals("102")
    compose.runOnUiThread { qty = 0 }
    compose.waitForIdle()
    compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE).assertIsNotEnabled()
  }

  /** 5) GameItem */
  @Test
  fun gameItem_renders_badge_click_and_delete() {
    var clicked = 0
    var deleted: Game? = null

    setContentThemed {
      Column {
        // no delete, count==0 -> no badge text
        GameItem(
            game = Fx.game1,
            count = 0,
            clickable = false,
        )
        // clickable + delete bubble + count==1 -> badge "1" visible
        GameItem(
            game = Fx.game2,
            count = 1,
            clickable = true,
            onClick = { clicked++ },
            hasDeleteButton = true,
            onDelete = { deleted = it })
      }
    }

    // First card exists; no "0" badge text drawn anywhere
    compose
        .onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game1.uid}")
        .assertExists()
        .assertIsDisplayed()
    compose.onText("0").assertDoesNotExist()

    // Second card exists; has "1" badge text and is clickable
    val g2Tag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game2.uid}"
    compose.onTag(g2Tag).assertExists().assertIsDisplayed().performClick()
    assert(clicked == 1)

    // Delete bubble present and works
    compose
        .onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${Fx.game2.uid}")
        .assertExists()
        .performClick()
    assert(deleted?.uid == Fx.game2.uid)
  }

  /** 6) GameListSection */
  @Test
  fun gameListSection_combined_behaviors() {
    val removed = mutableListOf<String>()
    val clicks = mutableListOf<String>()
    lateinit var stage: MutableIntState
    lateinit var setter: (List<Pair<Game, Int>>) -> Unit

    val g4 = Game("4", "7 Wonders", "", "", 3, 7, null, 45, emptyList())
    val input = listOf(Fx.game1 to 1, Fx.game2 to 2, Fx.game3 to 3, g4 to 4)

    setContentThemed {
      val s = remember { mutableIntStateOf(0) }
      stage = s
      when (s.intValue) {
        // Stage 0: grid (two per row), no delete
        0 ->
            GameListSection(
                games = input, clickableGames = false, title = "Inventory", hasDeleteButton = false)

        // Stage 1: list (one per row), delete buttons enabled
        // PARENT OWNS THE LIST and mutates it when onDelete fires
        1 -> {
          var source by remember { mutableStateOf(input) }
          GameListSection(
              games = source,
              clickableGames = false,
              title = "Inventory",
              hasDeleteButton = true,
              onDelete = { g ->
                removed += g.uid
                source = source.filterNot { it.first.uid == g.uid }
              })
        }

        // Stage 2: clickable cards, no delete
        2 ->
            GameListSection(
                games = listOf(Fx.game1 to 1, Fx.game3 to 2),
                clickableGames = true,
                title = null,
                hasDeleteButton = false,
                onClick = { clicks += it.uid })

        // Stage 3: parent-driven updates (resync)
        3 ->
            run {
              var source by remember {
                mutableStateOf(listOf(Fx.game1 to 1, Fx.game2 to 2, Fx.game3 to 3, g4 to 1))
              }
              setter = { new -> source = new }
              GameListSection(
                  games = source,
                  clickableGames = false,
                  title = "Inventory",
                  hasDeleteButton = false)
            }
      }
    }

    // ---------- Stage 0: grid (no delete) ----------
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game1.uid}").assertExists()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game2.uid}").assertExists()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game3.uid}").assertExists()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${g4.uid}").assertExists()
    compose
        .onAllNodesWithTag(ShopComponentsTestTags.SHOP_GAME_DELETE, useUnmergedTree = true)
        .assertCountEquals(0)

    // ---------- Stage 1: list (with delete) ----------
    compose.runOnUiThread { stage.intValue = 1 }
    compose.waitForIdle()

    // Delete bubbles present
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${Fx.game2.uid}").assertExists()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${Fx.game3.uid}").assertExists()

    // Delete Fx.game2 -> parent removes -> node disappears + bubbled id recorded
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${Fx.game2.uid}").performClick()
    compose.waitForIdle()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game2.uid}").assertDoesNotExist()
    assert(removed.contains(Fx.game2.uid))

    // ---------- Stage 2: clickable cards ----------
    compose.runOnUiThread { stage.intValue = 2 }
    compose.waitForIdle()

    compose
        .onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game1.uid}")
        .assertExists()
        .performClick()
    compose
        .onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game3.uid}")
        .assertExists()
        .performClick()
    assert(clicks == listOf(Fx.game1.uid, Fx.game3.uid))

    // ---------- Stage 3: parent resync ----------
    compose.runOnUiThread { stage.intValue = 3 }
    compose.waitForIdle()

    // All four present initially
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game1.uid}").assertExists()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game2.uid}").assertExists()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game3.uid}").assertExists()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${g4.uid}").assertExists()

    // Parent drops two items -> resync
    compose.runOnUiThread { setter(listOf(Fx.game1 to 1, g4 to 1)) }
    compose.waitForIdle()

    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game2.uid}").assertDoesNotExist()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game3.uid}").assertDoesNotExist()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game1.uid}").assertExists()
    compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${g4.uid}").assertExists()
  }
}
