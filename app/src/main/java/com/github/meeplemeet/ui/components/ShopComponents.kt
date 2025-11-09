// This file was initially done by hand and
// then improved and refactored using ChatGPT-5 Extend Thinking
// Docstrings were generated using copilot from Android studio
@file:Suppress("FunctionName")

package com.github.meeplemeet.ui.components

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopSearchViewModel
import com.github.meeplemeet.model.shops.TimeSlot
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* =============================================================================
 * Test tags
 * ============================================================================= */

object ShopComponentsTestTags {
  // Section header
  const val SECTION_HEADER = "shop_section_header"
  const val SECTION_HEADER_LABEL = "shop_section_header_label"
  const val SECTION_HEADER_DIVIDER = "shop_section_header_divider"

  fun sectionHeader(title: String) = "$SECTION_HEADER:$title"

  // LabeledField
  const val LABELED_FIELD_CONTAINER = "shop_labeled_field"
  const val LABELED_FIELD_INPUT = "shop_labeled_field_input"

  fun labeledField(label: String) = "$LABELED_FIELD_CONTAINER:$label"

  // TimeField
  const val TIME_FIELD_CONTAINER = "shop_time_field"
  const val TIME_FIELD_CARD = "shop_time_field_card"
  const val TIME_FIELD_LABEL = "shop_time_field_label"
  const val TIME_FIELD_VALUE = "shop_time_field_value"

  fun timeField(label: String) = "$TIME_FIELD_CONTAINER:$label"

  // Day row
  const val DAY_ROW = "shop_day_row"
  const val DAY_ROW_NAME = "shop_day_row_name"
  const val DAY_ROW_VALUE = "shop_day_row_value"
  const val DAY_ROW_EDIT = "shop_day_row_edit"

  fun dayRow(name: String) = "$DAY_ROW:$name"

  // Hour row
  const val HOUR_ROW = "shop_hour_row"
  const val HOUR_ROW_REMOVE = "shop_hour_row_remove"
  const val HOUR_ROW_OPEN_FIELD = "shop_hour_row_open_field"
  const val HOUR_ROW_CLOSE_FIELD = "shop_hour_row_close_field"

  fun hourRow(index: Int) = "$HOUR_ROW:$index"

  // Days selector
  const val DAYS_SELECTOR = "shop_days_selector"
  const val DAY_CHIP = "shop_day_chip"

  fun dayChip(index: Int) = "$DAY_CHIP:$index"

  // Opening hours dialog
  const val DIALOG = "shop_opening_hours_dialog"
  const val DIALOG_TITLE = "shop_opening_hours_title"
  const val DIALOG_DAYS = "shop_opening_hours_days"
  const val DIALOG_OPEN24_CHECKBOX = "shop_open_24_checkbox"
  const val DIALOG_CLOSED_CHECKBOX = "shop_closed_checkbox"
  const val DIALOG_OPEN24_ROW = "shop_open_24_row"
  const val DIALOG_CLOSED_ROW = "shop_closed_row"
  const val DIALOG_INTERVALS = "shop_intervals"
  const val DIALOG_ADD_HOURS = "shop_add_hours_button"
  const val DIALOG_ERROR = "shop_dialog_error"
  const val DIALOG_SAVE = "shop_dialog_save"
  const val DIALOG_CANCEL = "shop_dialog_cancel"

  // Action bar
  const val ACTION_BAR = "shop_action_bar"
  const val ACTION_DISCARD = "shop_action_discard"
  const val ACTION_CREATE = "shop_action_create"
  const val ACTION_SAVE = "shop_action_save"

  // Search field internals
  const val GAME_SEARCH_FIELD = "shop_game_search_field"
  const val GAME_SEARCH_CLEAR = "shop_game_search_clear"
  const val GAME_SEARCH_PROGRESS = "shop_game_search_progress"
  const val GAME_SEARCH_MENU = "shop_game_search_menu"
  const val GAME_SEARCH_ITEM = "shop_game_search_item"

  // Quantity slider
  const val QTY_CONTAINER = "shop_qty_container"
  const val QTY_LABEL = "shop_qty_label"
  const val QTY_MINUS_BUTTON = "shop_qty_minus_button"
  const val QTY_INPUT_FIELD = "shop_qty_input_field"
  const val QTY_PLUS_BUTTON = "shop_qty_plus_button"

  // Game stock dialog
  const val GAME_DIALOG_TITLE = "shop_game_dialog_title"
  const val GAME_DIALOG_BODY = "shop_game_dialog_body"
  const val GAME_DIALOG_SLIDER = "shop_game_dialog_slider"
  const val GAME_DIALOG_SAVE = "shop_game_dialog_save"
  const val GAME_DIALOG_CANCEL = "shop_game_dialog_cancel"
  const val GAME_DIALOG_HELPER = "shop_game_dialog_helper"

  // Game list
  const val SHOP_GAME_PREFIX = "SHOP_GAME_"
  const val SHOP_GAME_DELETE = "shop_game_delete"
  const val SHOP_GAME_MINUS_BUTTON = "shop_game_minus_button"
  const val SHOP_GAME_QTY_INPUT = "shop_game_qty_input"
  const val SHOP_GAME_PLUS_BUTTON = "shop_game_plus_button"
}

/* =============================================================================
 * MAGIC NUMBERS & DEFAULTS
 * ============================================================================= */

object ShopUiDefaults {

  object DaysMagicNumbers {
    val short = listOf("S", "M", "T", "W", "T", "F", "S")
  }

  object TimeMagicNumbers {
    const val OPEN24_START = "00:00"
    const val OPEN24_END = "23:59"

    const val DEFAULT_START_HOUR = 7
    const val DEFAULT_START_MIN = 30
    const val DEFAULT_END_HOUR = 20
    const val DEFAULT_END_MIN = 0

    const val DISPLAY_PATTERN_12 = "h:mm a"

    fun formatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DISPLAY_PATTERN_12, locale)

    val defaultStart: LocalTime = LocalTime.of(DEFAULT_START_HOUR, DEFAULT_START_MIN)
    val defaultEnd: LocalTime = LocalTime.of(DEFAULT_END_HOUR, DEFAULT_END_MIN)

    val open24Start: LocalTime = LocalTime.of(0, 0)
    val open24End: LocalTime = LocalTime.of(23, 59)
  }

  object DimensionsMagicNumbers {
    // Spacing
    val space4 = 4.dp
    val space6 = 6.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp

    // Component sizes
    val timeFieldHeight = 56.dp
    val removeIconTouch = 40.dp
    val dayChip = 35.dp
    val actionBarPadding = 16.dp
    val actionBarElevation = 3.dp
    val sectionHeaderDivider = 1.dp
  }

  object StringsMagicNumbers {
    // Section header
    const val REQUIRED_INFO = "Required Info"

    // Generic
    const val BTN_SAVE = "Save"
    const val BTN_CANCEL = "Cancel"
    const val BTN_DISCARD = "Discard"
    const val BTN_CREATE = "Create"

    // LabeledField
    const val LABEL_SHOP = "Shop"
    const val PLACEHOLDER_SHOP = "Shop name"

    // Time field
    const val OPEN_TIME = "Open time"
    const val CLOSE_TIME = "Close time"

    // Day row
    const val EDIT_HOURS = "Edit hours"

    // Days selector / dialog
    const val DIALOG_TITLE = "Select days & time"
    const val OPEN_24 = "Open 24 hours"
    const val CLOSED = "Closed"
    const val ADD_HOURS = "Add hours"
    const val INVALID_TIME_RANGES = "Invalid time ranges."

    // Quantity
    const val QUANTITY = "Quantity"

    // Game stock dialog
    const val GAME_DIALOG_TITLE = "Add game in stock"
    const val DUPLICATE_GAME = "This game is already in stock."
  }

  object RangesMagicNumbers {
    val qtyGameDialog: IntRange = 1..40
  }
}

/* =============================================================================
 * Utilities
 * ============================================================================= */

/**
 * Formats a LocalTime object into a display string.
 *
 * @return The formatted time string.
 * @receiver The LocalTime object to format.
 */
private fun LocalTime.display(): String = format(ShopUiDefaults.TimeMagicNumbers.formatter())

/**
 * Parses a raw time string into a LocalTime object.
 *
 * @param raw The raw time string to parse.
 * @return The corresponding LocalTime object.
 */
private fun parseToLocalTime(raw: String): LocalTime {
  val s = raw.trim()
  val lower = s.lowercase(Locale.getDefault())

  if (lower.contains("am") || lower.contains("pm")) {
    val normalized =
        lower
            .replace("am", " am")
            .replace("pm", " pm")
            .replace(Regex("\\s+"), " ")
            .trim()
            .uppercase(Locale.getDefault())
    return LocalTime.parse(normalized, ShopUiDefaults.TimeMagicNumbers.formatter())
  }

  return runCatching {
        val (h, mRest) = s.split(":")
        LocalTime.of(h.toInt(), mRest.take(2).toInt())
      }
      .getOrElse {
        LocalTime.parse(
            s.uppercase(Locale.getDefault()), ShopUiDefaults.TimeMagicNumbers.formatter())
      }
}

/**
 * Checks if the provided hours represent a 24-hour open schedule.
 *
 * @param hours A list of TimeSlot objects.
 * @return True if the shop is open 24 hours, false otherwise.
 */
private fun isOpen24(hours: List<TimeSlot>): Boolean =
    hours.size == 1 &&
        hours.first().open == ShopUiDefaults.TimeMagicNumbers.OPEN24_START &&
        hours.first().close == ShopUiDefaults.TimeMagicNumbers.OPEN24_END

/**
 * Validates that the provided time intervals do not overlap and that each end time is after its
 * corresponding start time.
 *
 * @param intervals A list of pairs representing start and end times.
 * @return A sorted list of valid time intervals.
 * @throws IllegalArgumentException if any interval is invalid or if intervals overlap.
 */
private fun validateIntervals(
    intervals: List<Pair<LocalTime, LocalTime>>
): List<Pair<LocalTime, LocalTime>> {
  val cleaned = intervals.filter { (s, e) -> e.isAfter(s) }
  require(cleaned.size == intervals.size) { "End time must be after start time." }
  val sorted = cleaned.sortedBy { it.first }
  for (i in 1 until sorted.size) {
    require(sorted[i].first.isAfter(sorted[i - 1].second)) { "Time ranges must not overlap." }
  }
  return sorted
}

/**
 * Shows a time picker dialog.
 *
 * @param context The context to use for the dialog.
 * @param initial The initial time to display.
 * @param onTimePicked Callback invoked with the selected time.
 */
private fun showTimePicker(
    context: Context,
    initial: LocalTime,
    onTimePicked: (LocalTime) -> Unit
) {
  val is24h = DateFormat.is24HourFormat(context)
  TimePickerDialog(
          context,
          { _, h, m -> onTimePicked(LocalTime.of(h, m)) },
          initial.hour,
          initial.minute,
          is24h)
      .show()
}

/**
 * Toggles the presence of a day in the set.
 *
 * @param day The day to toggle.
 * @return A new set with the day toggled.
 */
private fun Set<Int>.toggled(day: Int): Set<Int> = if (day in this) this - day else this + day

/**
 * Generates the initial list of time intervals based on the current opening hours and 24-hour
 * status.
 *
 * @param current The current OpeningHours object.
 * @param is24h Boolean indicating if the shop is open 24 hours.
 * @return A list of pairs representing start and end times.
 */
private fun initialIntervals(
    current: OpeningHours,
    is24h: Boolean
): List<Pair<LocalTime, LocalTime>> =
    when {
      is24h ->
          listOf(
              ShopUiDefaults.TimeMagicNumbers.open24Start to
                  ShopUiDefaults.TimeMagicNumbers.open24End)
      current.hours.isEmpty() ->
          listOf(
              ShopUiDefaults.TimeMagicNumbers.defaultStart to
                  ShopUiDefaults.TimeMagicNumbers.defaultEnd)
      else ->
          current.hours
              .mapNotNull { slot ->
                runCatching {
                      val st =
                          slot.open?.let(::parseToLocalTime)
                              ?: ShopUiDefaults.TimeMagicNumbers.defaultStart
                      val en = slot.close?.let(::parseToLocalTime) ?: st.plusHours(1)
                      st to en
                    }
                    .getOrNull()
              }
              .ifEmpty {
                listOf(
                    ShopUiDefaults.TimeMagicNumbers.defaultStart to
                        ShopUiDefaults.TimeMagicNumbers.defaultEnd)
              }
    }

/* =============================================================================
 * Components
 * ============================================================================= */

/**
 * A composable function that displays a section header with a title and an underline.
 *
 * @param title The title of the section header.
 * @param modifier The modifier to be applied to the section header.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
  val density = LocalDensity.current
  var textWidth by remember { mutableStateOf(0.dp) }

  Column(modifier = modifier.testTag(ShopComponentsTestTags.sectionHeader(title))) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        onTextLayout = { layout -> textWidth = with(density) { layout.size.width.toDp() } },
        modifier = Modifier.testTag(ShopComponentsTestTags.SECTION_HEADER_LABEL))
    Spacer(Modifier.height(ShopUiDefaults.DimensionsMagicNumbers.space6))
    HorizontalDivider(
        modifier = Modifier.width(textWidth).testTag(ShopComponentsTestTags.SECTION_HEADER_DIVIDER),
        thickness = ShopUiDefaults.DimensionsMagicNumbers.sectionHeaderDivider,
        color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(ShopUiDefaults.DimensionsMagicNumbers.space8))
  }
}

/**
 * A composable function that displays a labeled text field with a placeholder.
 *
 * @param label The label for the text field.
 * @param placeholder The placeholder text for the text field.
 * @param value The current value of the text field.
 * @param onValueChange A callback function that is invoked when the value of the text field
 *   changes.
 * @param modifier The modifier to be applied to the text field.
 * @param keyboardType The type of keyboard to be used for the text field.
 * @param singleLine A boolean indicating whether the text field should be single line or not.
 * @param minLines The minimum number of lines for the text field.
 */
@Composable
fun LabeledField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
  Box(modifier.fillMaxWidth().testTag(ShopComponentsTestTags.labeledField(label))) {
    OutlinedTextField(
        label = { Text(label) },
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = keyboardType),
        placeholder = { Text(placeholder) },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag(ShopComponentsTestTags.LABELED_FIELD_INPUT))
  }
}

/**
 * A composable function that displays a time field with a label and a clickable card.
 *
 * @param label The label for the time field.
 * @param value The current value of the time field.
 * @param onClick A callback function that is invoked when the time field is clicked.
 * @param modifier The modifier to be applied to the time field.
 */
@Composable
fun TimeField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier = modifier.testTag(ShopComponentsTestTags.timeField(label))) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(ShopComponentsTestTags.TIME_FIELD_LABEL))
    OutlinedCard(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = ShopUiDefaults.DimensionsMagicNumbers.space4)
                .height(ShopUiDefaults.DimensionsMagicNumbers.timeFieldHeight)
                .testTag(ShopComponentsTestTags.TIME_FIELD_CARD),
        shape = MaterialTheme.shapes.medium) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(ShopComponentsTestTags.TIME_FIELD_VALUE))
          }
        }
  }
}

/* =============================================================================
 * Rows & selectors
 * ============================================================================= */

/**
 * A composable function that displays a row representing a day with its name, value, and an edit
 * button.
 *
 * @param dayName The name of the day.
 * @param value The value associated with the day.
 * @param onEdit A callback function that is invoked when the edit button is clicked.
 * @param modifier The modifier to be applied to the day row.
 */
@Composable
fun DayRow(dayName: String, value: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clickable(onClick = onEdit)
              .testTag(ShopComponentsTestTags.dayRow(dayName)),
      verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = dayName,
            modifier = Modifier.weight(1f).testTag(ShopComponentsTestTags.DAY_ROW_NAME),
            style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            modifier =
                Modifier.weight(2f)
                    .padding(end = ShopUiDefaults.DimensionsMagicNumbers.space8)
                    .testTag(ShopComponentsTestTags.DAY_ROW_VALUE),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
        IconButton(
            onClick = onEdit, modifier = Modifier.testTag(ShopComponentsTestTags.DAY_ROW_EDIT)) {
              Icon(
                  imageVector = Icons.Outlined.Edit,
                  contentDescription = ShopUiDefaults.StringsMagicNumbers.EDIT_HOURS)
            }
      }
}

/**
 * A composable function that displays a row for selecting opening and closing hours, along with a
 * remove button.
 *
 * @param start The starting time.
 * @param end The ending time.
 * @param onPickStart A callback function that is invoked when the start time field is clicked.
 * @param onPickEnd A callback function that is invoked when the end time field is clicked.
 * @param onRemove A callback function that is invoked when the remove button is clicked.
 * @param rowIndex An optional index for testing purposes.
 */
@Composable
fun HourRow(
    start: LocalTime,
    end: LocalTime,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onRemove: () -> Unit,
    rowIndex: Int? = null
) {
  val labelTopSpace =
      with(LocalDensity.current) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp() +
            ShopUiDefaults.DimensionsMagicNumbers.space4
      }

  Row(
      verticalAlignment = Alignment.Top,
      modifier =
          Modifier.fillMaxWidth()
              .then(
                  if (rowIndex != null) Modifier.testTag(ShopComponentsTestTags.hourRow(rowIndex))
                  else Modifier.testTag(ShopComponentsTestTags.HOUR_ROW))) {
        TimeField(
            label = ShopUiDefaults.StringsMagicNumbers.OPEN_TIME,
            value = start.display(),
            onClick = onPickStart,
            modifier = Modifier.weight(1f).testTag(ShopComponentsTestTags.HOUR_ROW_OPEN_FIELD))
        Spacer(Modifier.width(ShopUiDefaults.DimensionsMagicNumbers.space12))
        TimeField(
            label = ShopUiDefaults.StringsMagicNumbers.CLOSE_TIME,
            value = end.display(),
            onClick = onPickEnd,
            modifier = Modifier.weight(1f).testTag(ShopComponentsTestTags.HOUR_ROW_CLOSE_FIELD))
        Spacer(Modifier.width(ShopUiDefaults.DimensionsMagicNumbers.space4))

        Column(
            modifier = Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Spacer(Modifier.height(labelTopSpace))
              Box(
                  modifier =
                      Modifier.height(ShopUiDefaults.DimensionsMagicNumbers.timeFieldHeight)
                          .fillMaxWidth(),
                  contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = onRemove,
                        modifier =
                            Modifier.size(ShopUiDefaults.DimensionsMagicNumbers.removeIconTouch)
                                .testTag(ShopComponentsTestTags.HOUR_ROW_REMOVE)) {
                          Icon(Icons.Filled.Close, contentDescription = "Remove interval")
                        }
                  }
            }
      }
}

/**
 * A composable function that displays a selector for days of the week.
 *
 * @param selected A set of selected day indices (0 for Sunday, 1 for Monday, etc.).
 * @param onToggle A callback function that is invoked when a day is toggled.
 */
@Composable
fun DaysSelector(selected: Set<Int>, onToggle: (Int) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(vertical = ShopUiDefaults.DimensionsMagicNumbers.space4)
              .testTag(ShopComponentsTestTags.DAYS_SELECTOR),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically) {
        ShopUiDefaults.DaysMagicNumbers.short.forEachIndexed { idx, short ->
          val isSel = idx in selected

          FilterChip(
              selected = isSel,
              onClick = { onToggle(idx) },
              shape = CircleShape,
              modifier =
                  Modifier.size(ShopUiDefaults.DimensionsMagicNumbers.dayChip)
                      .testTag(ShopComponentsTestTags.dayChip(idx)),
              label = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                  Text(
                      text = short,
                      textAlign = TextAlign.Center,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.SemiBold)
                }
              },
              colors =
                  FilterChipDefaults.filterChipColors(
                      containerColor = Color.Transparent,
                      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      selectedContainerColor = MaterialTheme.colorScheme.inversePrimary,
                      selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
              border =
                  FilterChipDefaults.filterChipBorder(
                      enabled = true,
                      selected = isSel,
                      borderColor = MaterialTheme.colorScheme.outlineVariant,
                      selectedBorderColor = MaterialTheme.colorScheme.inversePrimary))
        }
      }
}

/* =============================================================================
 * Dialogs
 * ============================================================================= */

/**
 * A composable function that displays a dialog for selecting opening hours.
 *
 * @param initialSelectedDays A set of initially selected day indices.
 * @param current The current OpeningHours object.
 * @param onDismiss A callback function that is invoked when the dialog is dismissed.
 * @param onSave A callback function that is invoked when the save button is clicked, with the
 *   selected days, closed status, 24-hour status, and time intervals as parameters.
 */
@Composable
fun OpeningHoursDialog(
    initialSelectedDays: Set<Int>,
    current: OpeningHours,
    onDismiss: () -> Unit,
    onSave:
        (
            selectedDays: Set<Int>,
            closed: Boolean,
            open24: Boolean,
            intervals: List<Pair<LocalTime, LocalTime>>) -> Unit
) {
  val context = LocalContext.current

  var selectedDays by remember(initialSelectedDays) { mutableStateOf(initialSelectedDays) }
  var isClosed by remember(current) { mutableStateOf(current.hours.isEmpty()) }
  var is24h by remember(current) { mutableStateOf(isOpen24(current.hours)) }
  var intervals by remember(current, is24h) { mutableStateOf(initialIntervals(current, is24h)) }

  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
      onDismissRequest = onDismiss,
      shape = MaterialTheme.shapes.extraLarge,
      title = {
        Box(
            Modifier.fillMaxWidth().testTag(ShopComponentsTestTags.DIALOG_TITLE),
            contentAlignment = Alignment.Center) {
              Text(
                  ShopUiDefaults.StringsMagicNumbers.DIALOG_TITLE,
                  style = MaterialTheme.typography.headlineSmall,
                  textAlign = TextAlign.Center)
            }
      },
      text = {
        Column(Modifier.fillMaxWidth().testTag(ShopComponentsTestTags.DIALOG)) {
          Column(Modifier.testTag(ShopComponentsTestTags.DIALOG_DAYS)) {
            DaysSelector(
                selected = selectedDays, onToggle = { d -> selectedDays = selectedDays.toggled(d) })
          }

          Spacer(Modifier.height(ShopUiDefaults.DimensionsMagicNumbers.space12))

          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).testTag(ShopComponentsTestTags.DIALOG_OPEN24_ROW),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                  Checkbox(
                      checked = is24h,
                      onCheckedChange = {
                        is24h = it
                        if (it) {
                          isClosed = false
                          intervals =
                              listOf(
                                  ShopUiDefaults.TimeMagicNumbers.open24Start to
                                      ShopUiDefaults.TimeMagicNumbers.open24End)
                        }
                      },
                      modifier = Modifier.testTag(ShopComponentsTestTags.DIALOG_OPEN24_CHECKBOX))
                  Spacer(Modifier.width(ShopUiDefaults.DimensionsMagicNumbers.space8))
                  Text(ShopUiDefaults.StringsMagicNumbers.OPEN_24, maxLines = 1)
                }
            Row(
                modifier = Modifier.weight(1f).testTag(ShopComponentsTestTags.DIALOG_CLOSED_ROW),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                  Checkbox(
                      checked = isClosed,
                      onCheckedChange = {
                        isClosed = it
                        if (it) {
                          is24h = false
                          intervals =
                              listOf(
                                  ShopUiDefaults.TimeMagicNumbers.defaultStart to
                                      ShopUiDefaults.TimeMagicNumbers.defaultEnd)
                        }
                      },
                      modifier = Modifier.testTag(ShopComponentsTestTags.DIALOG_CLOSED_CHECKBOX))
                  Spacer(Modifier.width(ShopUiDefaults.DimensionsMagicNumbers.space8))
                  Text(ShopUiDefaults.StringsMagicNumbers.CLOSED, maxLines = 1)
                }
          }

          Spacer(Modifier.height(ShopUiDefaults.DimensionsMagicNumbers.space8))

          AnimatedVisibility(visible = !isClosed && !is24h) {
            Column(Modifier.testTag(ShopComponentsTestTags.DIALOG_INTERVALS)) {
              intervals.forEachIndexed { idx, (start, end) ->
                HourRow(
                    start = start,
                    end = end,
                    onPickStart = {
                      showTimePicker(context, start) { t ->
                        intervals = intervals.toMutableList().apply { this[idx] = t to end }
                      }
                    },
                    onPickEnd = {
                      showTimePicker(context, end) { t ->
                        intervals = intervals.toMutableList().apply { this[idx] = start to t }
                      }
                    },
                    onRemove = {
                      intervals = intervals.toMutableList().apply { removeAt(idx) }
                      if (intervals.isEmpty()) {
                        intervals =
                            listOf(
                                ShopUiDefaults.TimeMagicNumbers.defaultStart to
                                    ShopUiDefaults.TimeMagicNumbers.defaultEnd)
                      }
                    },
                    rowIndex = idx)
                Spacer(Modifier.height(ShopUiDefaults.DimensionsMagicNumbers.space8))
              }

              TextButton(
                  onClick = {
                    val defaultStart = ShopUiDefaults.TimeMagicNumbers.defaultStart
                    val defaultEnd = ShopUiDefaults.TimeMagicNumbers.defaultEnd
                    intervals = intervals + (defaultStart to defaultEnd)
                  },
                  contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                  modifier = Modifier.testTag(ShopComponentsTestTags.DIALOG_ADD_HOURS)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(ShopUiDefaults.DimensionsMagicNumbers.space8))
                    Text(ShopUiDefaults.StringsMagicNumbers.ADD_HOURS)
                  }
            }
          }

          errorText?.let {
            Spacer(Modifier.height(ShopUiDefaults.DimensionsMagicNumbers.space8))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(ShopComponentsTestTags.DIALOG_ERROR))
          }
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(ShopComponentsTestTags.DIALOG_CANCEL)) {
              Text(ShopUiDefaults.StringsMagicNumbers.BTN_CANCEL)
            }
      },
      confirmButton = {
        TextButton(
            onClick = {
              try {
                val open24Start = ShopUiDefaults.TimeMagicNumbers.open24Start
                val open24End = ShopUiDefaults.TimeMagicNumbers.open24End
                val open24Pair = open24Start to open24End

                // If user entered a single interval with same start/end => treat as open 24
                val normalizedIntervals =
                    if (!isClosed &&
                        !is24h &&
                        intervals.size == 1 &&
                        intervals[0].first == intervals[0].second) {
                      listOf(open24Pair)
                    } else intervals

                val payload =
                    when {
                      isClosed -> emptyList()
                      is24h -> listOf(open24Pair)
                      else -> validateIntervals(normalizedIntervals)
                    }

                // Compute final open24 flag from the normalized payload
                val open24Final =
                    payload.size == 1 &&
                        payload[0].first == open24Start &&
                        payload[0].second == open24End

                errorText = null
                onSave(selectedDays, isClosed, open24Final, payload)
              } catch (e: IllegalArgumentException) {
                errorText = e.message ?: ShopUiDefaults.StringsMagicNumbers.INVALID_TIME_RANGES
              }
            },
            modifier = Modifier.testTag(ShopComponentsTestTags.DIALOG_SAVE)) {
              Text(ShopUiDefaults.StringsMagicNumbers.BTN_SAVE)
            }
      })
}

/* =============================================================================
 * Footer action bar
 * ============================================================================= */

/**
 * A composable function that displays an action bar with discard and a primary button.
 * - Keeps existing test tags (ACTION_BAR, ACTION_DISCARD, ACTION_CREATE)
 * - primaryButtonText defaults to "Create"; pass BTN_SAVE for edit flows
 */
@Composable
fun ActionBar(
    onDiscard: () -> Unit,
    onPrimary: () -> Unit,
    enabled: Boolean,
    primaryButtonText: String = ShopUiDefaults.StringsMagicNumbers.BTN_CREATE,
) {
  Surface(
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = ShopUiDefaults.DimensionsMagicNumbers.actionBarElevation) {
        Row(
            Modifier.fillMaxWidth()
                .padding(ShopUiDefaults.DimensionsMagicNumbers.actionBarPadding)
                .testTag(ShopComponentsTestTags.ACTION_BAR),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              OutlinedButton(
                  onClick = onDiscard,
                  colors =
                      ButtonDefaults.outlinedButtonColors(
                          containerColor = MaterialTheme.colorScheme.outline,
                          contentColor = MaterialTheme.colorScheme.error),
                  modifier = Modifier.weight(1f).testTag(ShopComponentsTestTags.ACTION_DISCARD)) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(ShopUiDefaults.DimensionsMagicNumbers.space8))
                    Text(ShopUiDefaults.StringsMagicNumbers.BTN_DISCARD)
                  }

              val primaryColors =
                  if (enabled)
                      ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.secondary,
                          contentColor = MaterialTheme.colorScheme.onSecondary)
                  else
                      ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.outline,
                          contentColor = MaterialTheme.colorScheme.onSecondary)

              val primaryTag =
                  if (primaryButtonText.equals(
                      ShopUiDefaults.StringsMagicNumbers.BTN_SAVE, ignoreCase = true))
                      ShopComponentsTestTags.ACTION_SAVE
                  else ShopComponentsTestTags.ACTION_CREATE

              Button(
                  onClick = onPrimary,
                  enabled = enabled,
                  shape = RoundedCornerShape(20.dp),
                  colors = primaryColors,
                  modifier = Modifier.weight(1f).testTag(primaryTag)) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(ShopUiDefaults.DimensionsMagicNumbers.space8))
                    Text(primaryButtonText)
                  }
            }
      }
}

/* =============================================================================
 * Game search + quantity
 * ============================================================================= */

/**
 * A composable function that displays a quantity input with +/- buttons and a label.
 *
 * @param value The current quantity value.
 * @param onValueChange A callback function that is invoked when the quantity value changes.
 * @param range The range of valid quantity values.
 * @param modifier The modifier to be applied to the quantity input.
 */
@Composable
fun GameAddUI(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.testTag(ShopComponentsTestTags.QTY_CONTAINER)) {
    Text(
        ShopUiDefaults.StringsMagicNumbers.QUANTITY,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.testTag(ShopComponentsTestTags.QTY_LABEL))

    Spacer(Modifier.height(12.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()) {
          IconButton(
              onClick = {
                val newValue = (value - 1)
                onValueChange(newValue)
              },
              enabled = value > 0,
              modifier = Modifier.testTag(ShopComponentsTestTags.QTY_MINUS_BUTTON)) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
              }

          OutlinedTextField(
              value = value.toString(),
              onValueChange = { newText ->
                val newValue = newText.toIntOrNull() ?: 0
                if (newValue > 0) {
                  onValueChange(newValue)
                }
              },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
              modifier = Modifier.weight(1f).testTag(ShopComponentsTestTags.QTY_INPUT_FIELD))

          IconButton(
              onClick = {
                val newValue = (value + 1)
                onValueChange(newValue)
              },
              enabled = true,
              modifier = Modifier.testTag(ShopComponentsTestTags.QTY_PLUS_BUTTON)) {
                Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
              }
        }
  }
}

/* =============================================================================
 * Game stock dialog
 * ============================================================================= */

/**
 * A composable function that displays a dialog for adding a game to stock with search and quantity
 * selection.
 *
 * @param onQueryChange A callback function that is invoked when the search query changes.
 * @param quantity The current quantity value.
 * @param onQuantityChange A callback function that is invoked when the quantity value changes.
 * @param existingIds A set of existing game IDs to prevent duplicates.
 * @param onDismiss A callback function that is invoked when the dialog is dismissed.
 * @param onSave A callback function that is invoked when the save button is clicked.
 */
@Composable
fun GameStockDialog(
    owner: Account,
    shop: Shop?,
    viewModel: ShopSearchViewModel,
    gameUIState: GameUIState,
    onQueryChange: (String) -> Unit,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    existingIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
  val selectedGame = gameUIState.fetchedGame
  val isDuplicate = selectedGame?.uid?.let { it in existingIds } ?: false

  AlertDialog(
      onDismissRequest = onDismiss,
      shape = MaterialTheme.shapes.extraLarge,
      title = {
        Box(
            Modifier.fillMaxWidth().testTag(ShopComponentsTestTags.GAME_DIALOG_TITLE),
            contentAlignment = Alignment.Center) {
              Text(
                  ShopUiDefaults.StringsMagicNumbers.GAME_DIALOG_TITLE,
                  style = MaterialTheme.typography.headlineSmall)
            }
      },
      text = {
        Column(Modifier.fillMaxWidth().testTag(ShopComponentsTestTags.GAME_DIALOG_BODY)) {
          ShopGameSearchBar(
              owner,
              shop,
              viewModel,
              gameUIState.fetchedGame,
              existingIds,
              inputFieldTestTag = ShopComponentsTestTags.GAME_SEARCH_FIELD,
              dropdownItemTestTag = ShopComponentsTestTags.GAME_SEARCH_ITEM)

          if (isDuplicate) {
            Spacer(Modifier.height(ShopUiDefaults.DimensionsMagicNumbers.space6))
            Text(
                ShopUiDefaults.StringsMagicNumbers.DUPLICATE_GAME,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(ShopComponentsTestTags.GAME_DIALOG_HELPER))
          }

          Spacer(Modifier.height(ShopUiDefaults.DimensionsMagicNumbers.space16))

          GameAddUI(
              value = quantity,
              onValueChange = onQuantityChange,
              modifier = Modifier.testTag(ShopComponentsTestTags.GAME_DIALOG_SLIDER))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(ShopComponentsTestTags.GAME_DIALOG_CANCEL)) {
              Text(ShopUiDefaults.StringsMagicNumbers.BTN_CANCEL)
            }
      },
      confirmButton = {
        TextButton(
            onClick = onSave,
            enabled = gameUIState.fetchedGame != null && !isDuplicate && quantity > 0,
            modifier = Modifier.testTag(ShopComponentsTestTags.GAME_DIALOG_SAVE)) {
              Text(ShopUiDefaults.StringsMagicNumbers.BTN_SAVE)
            }
      })
}

/* =============================================================================
 * Games: grid + item (with optional delete)
 * ============================================================================= */

/**
 * A composable function that displays a section of games in a grid or list format, with optional
 * title and delete buttons.
 *
 * @param games A list of pairs containing [Game] objects and their corresponding quantities.
 * @param modifier The modifier to be applied to the game list section.
 * @param clickableGames A boolean indicating whether the game items are clickable.
 * @param title An optional title for the game list section.
 * @param hasDeleteButton A boolean indicating whether the game items have delete buttons.
 * @param onClick A callback function that is invoked when a game item is clicked.
 * @param onDelete A callback function that is invoked when a game item is deleted.
 */
@Composable
fun GameListSection(
    games: List<Pair<Game, Int>>,
    modifier: Modifier = Modifier,
    clickableGames: Boolean = false,
    title: String? = null,
    hasDeleteButton: Boolean = false,
    onClick: (Game) -> Unit = {},
    onDelete: (Game) -> Unit = {},
) {
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
        if (title != null) {
          Text(
              title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
              textDecoration = TextDecoration.Underline)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.heightIn(max = 600.dp)) {
              items(items = games, key = { it.first.uid }) { (game, count) ->
                GameItem(
                    game = game,
                    count = count,
                    clickable = clickableGames,
                    onClick = onClick,
                    hasDeleteButton = hasDeleteButton,
                    onDelete = onDelete)
              }
            }
      }
}

/**
 * A composable function that displays a game item with its name, icon, quantity badge, and optional
 * delete button.
 *
 * @param game The [Game] object to be displayed.
 * @param count The quantity of the game.
 * @param modifier The modifier to be applied to the game item.
 * @param clickable A boolean indicating whether the game item is clickable.
 * @param onClick A callback function that is invoked when the game item is clicked.
 * @param hasDeleteButton A boolean indicating whether the game item has a delete button.
 * @param onDelete A callback function that is invoked when the delete button is clicked.
 */
@Composable
fun GameItem(
    game: Game,
    count: Int,
    modifier: Modifier = Modifier,
    clickable: Boolean = false,
    onClick: (Game) -> Unit = {},
    hasDeleteButton: Boolean = false,
    onDelete: (Game) -> Unit = {},
) {
  Card(
      modifier =
          modifier
              .fillMaxWidth()
              .testTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${game.uid}")
              .clickable(enabled = clickable, onClick = { onClick(game) }),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {

          // Icon + badge
          Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            BadgedBox(
                badge = {
                  // Only show badge if count > 0
                  if (count > 0) {
                    val max = ShopUiDefaults.RangesMagicNumbers.qtyGameDialog.last
                    val label = if (count > max) "$max+" else count.toString()
                    Badge(
                        modifier =
                            Modifier.offset(x = 8.dp, y = (-6).dp)
                                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp),
                        containerColor = MaterialTheme.colorScheme.inversePrimary) {
                          Text(
                              label,
                              style = MaterialTheme.typography.labelSmall,
                              maxLines = 1,
                              softWrap = false,
                              modifier = Modifier.padding(horizontal = 4.dp))
                        }
                  }
                }) {
                  Icon(Icons.Filled.VideogameAsset, contentDescription = null)
                }
          }

          Spacer(Modifier.width(8.dp))

          // Name centered
          Column(
              modifier = Modifier.weight(1f),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center) {
                Text(
                    game.name,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center)
              }

          // Optional delete
          if (hasDeleteButton) {
            IconButton(
                onClick = { onDelete(game) },
                modifier =
                    Modifier.testTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${game.uid}"),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)) {
                  Icon(Icons.Filled.Delete, contentDescription = "Remove ${game.name} from list")
                }
          }
        }
      }
}

/* =============================================================================
 * Games: editable item helper
 * ============================================================================= */

/** Editable game row used in edit screen (inline quantity +/- and delete). */
@Composable
fun EditableGameItem(
    game: Game,
    count: Int,
    onQuantityChange: (Game, Int) -> Unit,
    onDelete: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier =
          modifier.fillMaxWidth().testTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${game.uid}"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(
              game.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onQuantityChange(game, (count - 1).coerceAtLeast(0)) },
                    enabled = count > 0,
                    modifier = Modifier.testTag(ShopComponentsTestTags.SHOP_GAME_MINUS_BUTTON)) {
                      Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                    }
                OutlinedTextField(
                    value = count.toString(),
                    onValueChange = { newText ->
                      val newValue = newText.toIntOrNull() ?: 0
                      if (newValue >= 0) {
                        onQuantityChange(game, newValue)
                      }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                    modifier =
                        Modifier.width(80.dp).testTag(ShopComponentsTestTags.SHOP_GAME_QTY_INPUT))
                IconButton(
                    onClick = { onQuantityChange(game, count + 1) },
                    modifier = Modifier.testTag(ShopComponentsTestTags.SHOP_GAME_PLUS_BUTTON)) {
                      Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                    }
              }
          IconButton(
              onClick = { onDelete(game) },
              colors =
                  IconButtonDefaults.iconButtonColors(
                      contentColor = MaterialTheme.colorScheme.error),
              modifier =
                  Modifier.testTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${game.uid}")) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete game")
              }
        }
      }
}
