// This file was initially done by hand and refactored and improved by ChatGPT-5 Extend Thinking
// Combinations to tests were given to the LLM so it could generate the code more easily
package com.github.meeplemeet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.space_renter.SpaceRenterTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShopComponentsTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

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
    val game1 = Game("1", "Catan", "", "", 3, 4, null, 60, genres = emptyList())
    val game2 = Game("2", "Carcassonne", "", "", 2, 5, null, 35, genres = emptyList())
    val game3 = Game("3", "Azul", "", "", 2, 4, null, 30, genres = emptyList())
  }

  private class FakeClipboardManager : ClipboardManager {
    var copiedText: AnnotatedString? = null

    override fun getText(): AnnotatedString? = copiedText

    override fun setText(annotatedString: AnnotatedString) {
      copiedText = annotatedString
    }
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

    checkpoint("SectionHeader renders label and divider") {
      val title = ShopUiDefaults.StringsMagicNumbers.REQUIRED_INFO
      compose.onTag(ShopComponentsTestTags.sectionHeader(title)).assertExists().assertIsDisplayed()
      compose.onTag(ShopComponentsTestTags.SECTION_HEADER_LABEL).assert(hasText(title))
      compose.onTag(ShopComponentsTestTags.SECTION_HEADER_DIVIDER).assertExists()
    }

    checkpoint("LabeledField accepts and displays text") {
      compose.runOnUiThread { stage.intValue = 1 }
      compose.waitForIdle()
      compose.onTag(ShopComponentsTestTags.LABELED_FIELD_INPUT).performTextInput("Meeple Market")
      compose.onTag(ShopComponentsTestTags.LABELED_FIELD_INPUT).assertTextEquals("Meeple Market")
    }

    checkpoint("TimeField shows label, value and reacts to clicks") {
      compose.runOnUiThread { stage.intValue = 2 }
      compose.waitForIdle()
      compose
          .onTag(ShopComponentsTestTags.TIME_FIELD_LABEL)
          .assert(hasText(ShopUiDefaults.StringsMagicNumbers.OPEN_TIME))
      compose.onTag(ShopComponentsTestTags.TIME_FIELD_VALUE).assertTextEquals(Fx.t07_30.fmt12())
      compose.onTag(ShopComponentsTestTags.TIME_FIELD_CARD).performClick()
      assert(clicks == 1)
    }

    checkpoint("DayRow forwards click and edit") {
      compose.runOnUiThread { stage.intValue = 3 }
      compose.waitForIdle()
      compose.onTag(ShopComponentsTestTags.dayRow("Tuesday")).performClick()
      compose.onTag(ShopComponentsTestTags.DAY_ROW_EDIT).performClick()
      assert(edits == 2)
    }

    checkpoint("HourRow shows both times and buttons trigger callbacks") {
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
    }

    checkpoint("DaysSelector renders 7 chips and toggles selection") {
      compose.runOnUiThread { stage.intValue = 5 }
      compose.waitForIdle()
      (0..6).forEach { idx -> compose.onTag(ShopComponentsTestTags.dayChip(idx)).assertExists() }
      compose.onTag(ShopComponentsTestTags.dayChip(0)).performClick()
      compose.onTag(ShopComponentsTestTags.dayChip(1)).performClick()
      compose.onTag(ShopComponentsTestTags.dayChip(6)).performClick()
    }
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

    checkpoint("Open24 preset hides intervals and saves as open24") {
      compose.onTag(ShopComponentsTestTags.DIALOG_OPEN24_CHECKBOX).assertIsOn()
      compose.onTag(ShopComponentsTestTags.DIALOG_INTERVALS).assertDoesNotExist()
      compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()
    }

    checkpoint("Closed toggle hides intervals and turns off open24") {
      compose.runOnUiThread { stage.intValue = 1 }
      compose.waitForIdle()
      compose.onTag(ShopComponentsTestTags.DIALOG_INTERVALS).assertExists()
      compose.onTag(ShopComponentsTestTags.DIALOG_CLOSED_CHECKBOX).performClick()
      compose.onTag(ShopComponentsTestTags.DIALOG_INTERVALS).assertDoesNotExist()
      compose.onTag(ShopComponentsTestTags.DIALOG_OPEN24_CHECKBOX).assertIsOff()
    }

    checkpoint("Intervals are sorted on save") {
      compose.runOnUiThread { stage.intValue = 2 }
      compose.waitForIdle()
      compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()
      assert(sorted.map { it.first } == listOf(LocalTime.of(7, 30), LocalTime.of(14, 0)))
    }

    checkpoint("Overlapping intervals show error; removing extra interval allows save") {
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
    }

    checkpoint("Multi-select days propagates all selected indices") {
      compose.runOnUiThread { stage.intValue = 4 }
      compose.waitForIdle()
      compose.onTag(ShopComponentsTestTags.dayChip(2)).performClick()
      compose.onTag(ShopComponentsTestTags.dayChip(5)).performClick()
      compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()
      assert(savedDays.containsAll(listOf(1, 2, 5)))
    }

    checkpoint("Provided overlapping hours are rejected on save") {
      compose.runOnUiThread { stage.intValue = 5 }
      compose.waitForIdle()
      compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).performClick()
      compose.onTag(ShopComponentsTestTags.DIALOG_ERROR).assertExists().assertIsDisplayed()
      assert(!savedFlag)
    }
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

    checkpoint("Enabled state allows clicks on both buttons") {
      compose.onTag(ShopComponentsTestTags.ACTION_BAR).assertExists()
      compose.onTag(ShopComponentsTestTags.ACTION_DISCARD).performClick()
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).performClick()
      assert(discard == 1 && create == 1)
    }

    checkpoint("Disabled state prevents primary button click") {
      compose.runOnUiThread { enabledState.value = false }
      compose.waitForIdle()
      compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled().performClick()
      assert(create == 1)
    }
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
            showButtons = true,
            onDelete = { deleted = it })
      }
    }

    checkpoint("GameItem without count does not show 0 badge") {
      compose
          .onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game1.uid}")
          .assertExists()
          .assertIsDisplayed()
      compose.onText("0").assertDoesNotExist()
    }

    checkpoint("GameItem with count is clickable and delete works") {
      val g2Tag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game2.uid}"
      compose.onTag(g2Tag).assertExists().assertIsDisplayed().performClick()
      assert(clicked == 1)

      compose
          .onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${Fx.game2.uid}")
          .assertExists()
          .performClick()
      assert(deleted?.uid == Fx.game2.uid)
    }
  }

  /** 6) GameListSection */
  @Test
  fun gameListSection_combined_behaviors() {
    val removed = mutableListOf<String>()
    val clicks = mutableListOf<String>()
    lateinit var stage: MutableIntState
    lateinit var setter: (List<Pair<Game, Int>>) -> Unit

    val g4 = Game("4", "7 Wonders", "", "", 3, 7, null, 45, genres = emptyList())
    val input = listOf(Fx.game1 to 1, Fx.game2 to 2, Fx.game3 to 3, g4 to 4)

    setContentThemed {
      val s = remember { mutableIntStateOf(0) }
      stage = s
      when (s.intValue) {
        // Stage 0: list, no delete
        0 ->
            GameListSection(
                games = input, clickableGames = false, title = "Inventory", showButtons = true,)

        // Stage 1: list, delete buttons enabled
        // PARENT OWNS THE LIST and mutates it when onDelete fires
        1 -> {
          var source by remember { mutableStateOf(input) }
          GameListSection(
              games = source,
              clickableGames = false,
              title = "Inventory",
              showButtons = true,
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
                showButtons = true,
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
                  showButtons = true)
            }
      }
    }

    checkpoint("List mode shows all games without delete buttons") {
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game1.uid}").assertExists()
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game2.uid}").assertExists()
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game3.uid}").assertExists()
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${g4.uid}").assertExists()
      compose
          .onAllNodesWithTag(ShopComponentsTestTags.SHOP_GAME_DELETE, useUnmergedTree = true)
          .assertCountEquals(0)
    }

    checkpoint("List mode with delete bubbles removes on click") {
      compose.runOnUiThread { stage.intValue = 1 }
      compose.waitForIdle()

      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${Fx.game2.uid}").assertExists()
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${Fx.game3.uid}").assertExists()

      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${Fx.game2.uid}").performClick()
      compose.waitForIdle()
      compose
          .onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game2.uid}")
          .assertDoesNotExist()
      assert(removed.contains(Fx.game2.uid))
    }

    checkpoint("Clickable cards propagate clicks in onClick callback") {
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
    }

    checkpoint("Parent-driven resync replaces list content") {
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

      compose
          .onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game2.uid}")
          .assertDoesNotExist()
      compose
          .onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game3.uid}")
          .assertDoesNotExist()
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${Fx.game1.uid}").assertExists()
      compose.onTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${g4.uid}").assertExists()
    }
  }

  /** 7) AvailabilitySection */
  @Test
  fun availabilitySection_shopDetails_behaviours_todayAndMissingToday() {
    val today = Calendar.getInstance()[Calendar.DAY_OF_WEEK] - 1

    // Full week with a single simple interval
    val week: List<OpeningHours> =
        (0..6).map { d ->
          OpeningHours(day = d, hours = listOf(TimeSlot(open = "09:00", close = "17:00")))
        }

    val weekWithoutToday: List<OpeningHours> = week.filter { it.day != today }

    // Backing state for the composable - only call setContent once
    val openingState = mutableStateOf(week)

    setContentThemed { AvailabilitySectionWithChevron(openingHours = openingState.value) }

    val prefix = ShopComponentsTestTags.SHOP_DAY_PREFIX

    // Open sheet and wait until at least one day row is mounted inside the sheet
    compose.onNodeWithTag(SpaceRenterTestTags.AVAILABILITY_HEADER).assertExists().performClick()
    compose.waitForIdle()
    val anotherDay = if (today == 0) 1 else 0
    compose.waitUntil(timeoutMillis = 5_000) {
      runCatching {
            compose.onNodeWithTag("${prefix}${anotherDay}", useUnmergedTree = true).assertExists()
            true
          }
          .isSuccess
    }

    // Assert all expected day rows exist for the full week
    week.forEach { entry ->
      val tag = "${prefix}${entry.day}"
      checkpoint("Availability day exists: $tag") {
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
      }
    }

    // Close sheet (button text is "Close" per strings)
    compose
        .onNodeWithText(ShopUiDefaults.StringsMagicNumbers.BOTTOM_SHEET_CONFIRM_BUTTON_TEXT)
        .performClick()
    compose.waitForIdle()

    // Update the backing state on the UI thread to remove today's entry (no second setContent)
    compose.runOnIdle { openingState.value = weekWithoutToday }

    // Re-open sheet and wait until it is mounted again
    compose.onNodeWithTag(SpaceRenterTestTags.AVAILABILITY_HEADER).performClick()
    compose.waitForIdle()
    compose.waitUntil(timeoutMillis = 5_000) {
      runCatching {
            compose.onNodeWithTag("${prefix}${anotherDay}", useUnmergedTree = true).assertExists()
            true
          }
          .isSuccess
    }

    // Assert today row does not exist
    checkpoint("Today missing when not provided") {
      compose.onNodeWithTag("${prefix}${today}", useUnmergedTree = true).assertDoesNotExist()
    }
  }

  /** 8) ContactSection & ContactRow */
  @Test
  fun contactSection_and_contactRow_behaviour() {
    val name = "Meeple Meet"
    val address = "123 Meeple St, Boardgame City"
    val email = "info@meeplehaven.com"
    val phone = "123-456-7890"
    val website = "www.meeplemeet.com"

    val multiLineText = "Line 1\nLine 2"
    val fakeClipboard = FakeClipboardManager()
    lateinit var stage: MutableIntState

    setContentThemed {
      val s = remember { mutableIntStateOf(0) }
      stage = s
      when (s.intValue) {
        // 0: full ContactSection with all fields
        0 ->
            ContactSection(
                name = name,
                address = address,
                email = email,
                phone = phone,
                website = website,
            )

        // 1: ContactSection with empty optional phone & website
        1 ->
            ContactSection(
                name = name,
                address = address,
                email = email,
                phone = "",
                website = "",
            )

        // 2: raw ContactRow with multiline text + fake clipboard
        2 ->
            androidx.compose.runtime.CompositionLocalProvider(
                LocalClipboardManager provides fakeClipboard) {
                  ContactRow(
                      icon = Icons.Filled.Phone,
                      text = multiLineText,
                      textTag = ShopComponentsTestTags.SHOP_PHONE_TEXT,
                      buttonTag = ShopComponentsTestTags.SHOP_PHONE_BUTTON,
                  )
                }
      }
    }

    checkpoint("ContactSection renders name and all non-empty contact fields") {
      compose.runOnUiThread { stage.intValue = 0 }
      compose.waitForIdle()

      // Name as section title
      compose.onText(name).assertExists().assertIsDisplayed()

      // Tags are now directly on the Text nodes, so assert text on the tagged node
      compose
          .onTag(ShopComponentsTestTags.SHOP_ADDRESS_TEXT)
          .assertExists()
          .assert(hasText(address))

      compose.onTag(ShopComponentsTestTags.SHOP_EMAIL_TEXT).assertExists().assert(hasText(email))

      compose.onTag(ShopComponentsTestTags.SHOP_PHONE_TEXT).assertExists().assert(hasText(phone))

      compose
          .onTag(ShopComponentsTestTags.SHOP_WEBSITE_TEXT)
          .assertExists()
          .assert(hasText(website))
    }

    checkpoint("ContactSection hides empty optional phone and website") {
      compose.runOnUiThread { stage.intValue = 1 }
      compose.waitForIdle()

      compose
          .onAllNodesWithTag(ShopComponentsTestTags.SHOP_PHONE_TEXT, useUnmergedTree = true)
          .assertCountEquals(0)
      compose
          .onAllNodesWithTag(ShopComponentsTestTags.SHOP_WEBSITE_TEXT, useUnmergedTree = true)
          .assertCountEquals(0)

      // Required fields still present
      compose.onTag(ShopComponentsTestTags.SHOP_ADDRESS_TEXT).assertExists()
      compose.onTag(ShopComponentsTestTags.SHOP_EMAIL_TEXT).assertExists()
    }

    checkpoint("ContactRow ellipsizes visually and copies full text to clipboard") {
      compose.runOnUiThread { stage.intValue = 2 }
      compose.waitForIdle()

      // Single Text node with the full multi-line string ("Line 1\nLine 2")
      compose
          .onTag(ShopComponentsTestTags.SHOP_PHONE_TEXT)
          .assertExists()
          .assertIsDisplayed()
          .assertTextEquals(multiLineText)

      // Copy via button uses the full text, not a truncated part
      compose.onTag(ShopComponentsTestTags.SHOP_PHONE_BUTTON).performClick()
      assert(fakeClipboard.copiedText?.text == multiLineText)
    }
  }
}
