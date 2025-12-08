// This file contains shared components, test tags, and utilities used by both
// CreateShopScreen and ShopDetailsEditScreen
// Github copilot was used for this file

package com.github.meeplemeet.ui.components

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopSearchViewModel
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.sessions.SessionTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import java.text.DateFormatSymbols
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ================================================================================================
 * Shared Test Tags
 * ================================================================================================ */
object ShopFormTestTags {
  const val SECTION_HEADER_SUFFIX = "_header"
  const val SECTION_TITLE_SUFFIX = "_title"
  const val SECTION_TOGGLE_SUFFIX = "_toggle"
  const val SECTION_DIVIDER_SUFFIX = "_divider"
  const val SECTION_CONTENT_SUFFIX = "_content"

  const val FIELD_SHOP = "field_shop_name"
  const val FIELD_EMAIL = "field_email"
  const val FIELD_ADDRESS = "field_address"
  const val FIELD_PHONE = "field_phone"
  const val FIELD_LINK = "field_link"

  const val AVAILABILITY_LIST = "availability_list"
  const val AVAILABILITY_DIVIDER_PREFIX = "availability_divider_"

  const val OPENING_HOURS_DIALOG_WRAPPER = "opening_hours_dialog_wrapper"
  const val GAME_STOCK_DIALOG_WRAPPER = "game_stock_dialog_wrapper"
}

/* ================================================================================================
 * Shared UI Defaults
 * ================================================================================================ */
object ShopFormUi {
  object Dim {
    val contentHPadding = Dimensions.Padding.extraLarge
    val contentVPadding = Dimensions.Padding.medium
    val sectionSpace = Dimensions.Padding.large
    val bottomSpacer = Dimensions.ContainerSize.bottomSpacer
    val betweenControls = Dimensions.Padding.mediumSmall
  }

  object Strings {
    const val SHOP_LABEL = "Shop"
    const val SHOP_PLACEHOLDER = "Shop name"

    const val EMAIL_LABEL = "Email"
    const val EMAIL_PLACEHOLDER = "Email"

    const val PHONE_LABEL = "Contact info"
    const val PHONE_PLACEHOLDER = "Phone number"

    const val LINK_LABEL = "Link"
    const val LINK_PLACEHOLDER = "Website/Instagram link"

    const val COLLAPSE = "Collapse"
    const val EXPAND = "Expand"

    const val CLOSED_LABEL = "Closed"
    const val OPEN24_LABEL = "Open 24 hours"

    const val ERROR_EMAIL_MSG = "Enter a valid email address."
  }

  val dayNames: List<String> by lazy {
    val weekdays = DateFormatSymbols().weekdays
    (0..6).map { idx -> weekdays.getOrNull(idx + 1) ?: "Day ${idx + 1}" }
  }
}

/* ================================================================================================
 * Time utilities
 * ================================================================================================ */
object TimeUi {
  const val OPEN24_START = "00:00"
  const val OPEN24_END = "23:59"

  fun fmt12(locale: Locale = Locale.getDefault()): DateTimeFormatter =
      DateTimeFormatter.ofPattern("h:mm a", locale)
}

/**
 * Formats a LocalTime object into "HH:mm" string format.
 *
 * @return A string representation of the time in "HH:mm" format.
 * @receiver The LocalTime object to format.
 */
fun LocalTime.hhmm(): String = "%02d:%02d".format(hour, minute)

/**
 * Tries to parse a time string into a LocalTime object.
 *
 * Supports both 12-hour (with AM/PM) and 24-hour formats.
 *
 * @return A LocalTime object if parsing is successful, null otherwise.
 * @receiver The time string to parse.
 */
fun String.tryParseTime(): LocalTime? =
    runCatching {
          val lower = lowercase(Locale.getDefault())
          if (lower.contains("am") || lower.contains("pm")) {
            LocalTime.parse(
                replace("am", " AM", ignoreCase = true)
                    .replace("pm", " PM", ignoreCase = true)
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .uppercase(Locale.getDefault()),
                TimeUi.fmt12())
          } else {
            val (h, mRest) = split(":")
            LocalTime.of(h.toInt(), mRest.take(2).toInt())
          }
        }
        .getOrNull()

/**
 * Converts a list of TimeSlot objects into a human-readable string format.
 *
 * @param hours List of TimeSlot objects representing opening hours.
 * @return A string representation of the opening hours.
 */
fun humanize(hours: List<TimeSlot>): String =
    when {
      hours.isEmpty() -> ShopFormUi.Strings.CLOSED_LABEL
      hours.size == 1 &&
          hours[0].open == TimeUi.OPEN24_START &&
          hours[0].close == TimeUi.OPEN24_END -> ShopFormUi.Strings.OPEN24_LABEL
      else -> {
        // Sort by opening time
        val sorted = hours.sortedBy { ts -> ts.open?.tryParseTime() ?: LocalTime.MAX }
        sorted.joinToString("\n") { slot ->
          val s = slot.open?.let { it.tryParseTime()?.format(TimeUi.fmt12()) ?: it } ?: "-"
          val e = slot.close?.let { it.tryParseTime()?.format(TimeUi.fmt12()) ?: it } ?: "-"
          "$s - $e"
        }
      }
    }

/* ================================================================================================
 * Helpers
 * ================================================================================================ */

/** Returns an empty list of opening hours for each day of the week. */
fun emptyWeek(): List<OpeningHours> =
    List(7) { day -> OpeningHours(day = day, hours = emptyList()) }

/**
 * Validates if the provided email string is in a valid email format.
 *
 * @param email The email string to validate.
 * @return True if the email is valid, false otherwise.
 */
fun isValidEmail(email: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(email).matches()

/* ================================================================================================
 * Shared Composable Components
 * ================================================================================================ */

/**
 * Composable function representing the required information section.
 *
 * @param shopName The current value of the shop name field.
 * @param onShopName Callback function to update the shop name.
 * @param email The current value of the email field.
 * @param onEmail Callback function to update the email.
 * @param phone The current value of the phone field.
 * @param onPhone Callback function to update the phone.
 * @param link The current value of the link field.
 * @param onLink Callback function to update the link.
 * @param onPickLocation Callback function to handle location selection.
 */
@Composable
fun RequiredInfoSection(
    shop: Shop?,
    shopName: String,
    onShopName: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    phone: String,
    onPhone: (String) -> Unit,
    link: String,
    onLink: (String) -> Unit,
    onPickLocation: (Location) -> Unit,
    viewModel: ShopSearchViewModel,
    owner: Account
) {
  Box(Modifier.testTag(ShopFormTestTags.FIELD_SHOP)) {
    LabeledField(
        label = ShopFormUi.Strings.SHOP_LABEL,
        placeholder = ShopFormUi.Strings.SHOP_PLACEHOLDER,
        value = shopName,
        onValueChange = onShopName)
  }
  Box(Modifier.testTag(ShopFormTestTags.FIELD_EMAIL)) {
    LabeledField(
        label = ShopFormUi.Strings.EMAIL_LABEL,
        placeholder = ShopFormUi.Strings.EMAIL_PLACEHOLDER,
        value = email,
        onValueChange = onEmail,
        keyboardType = KeyboardType.Email)
  }
  val showEmailError = email.isNotEmpty() && !isValidEmail(email)
  if (showEmailError) {
    Text(
        text = ShopFormUi.Strings.ERROR_EMAIL_MSG,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall)
  }
  Box(Modifier.testTag(ShopFormTestTags.FIELD_PHONE)) {
    LabeledField(
        label = ShopFormUi.Strings.PHONE_LABEL,
        placeholder = ShopFormUi.Strings.PHONE_PLACEHOLDER,
        value = phone,
        onValueChange = onPhone,
        keyboardType = KeyboardType.Phone)
  }

  Box(Modifier.testTag(ShopFormTestTags.FIELD_LINK)) {
    LabeledField(
        label = ShopFormUi.Strings.LINK_LABEL,
        placeholder = ShopFormUi.Strings.LINK_PLACEHOLDER,
        value = link,
        onValueChange = onLink,
        keyboardType = KeyboardType.Uri)
  }

  Box(Modifier.testTag(ShopFormTestTags.FIELD_ADDRESS)) {
    ShopLocationSearchBar(
        owner,
        shop,
        viewModel,
        inputFieldTestTag = SessionTestTags.LOCATION_FIELD,
        dropdownItemTestTag = SessionTestTags.LOCATION_FIELD_ITEM)
  }
}

/**
 * Composable function representing the availability section.
 *
 * @param week List of opening hours for each day of the week.
 * @param onEdit Callback function to handle editing of opening hours for a specific day.
 */
@Composable
fun AvailabilitySection(week: List<OpeningHours>, onEdit: (Int) -> Unit) {
  Column(Modifier.testTag(ShopFormTestTags.AVAILABILITY_LIST)) {
    week.forEach { oh ->
      val day = oh.day
      DayRow(
          dayName = ShopFormUi.dayNames[day], value = humanize(oh.hours), onEdit = { onEdit(day) })
      HorizontalDivider(
          modifier = Modifier.testTag(ShopFormTestTags.AVAILABILITY_DIVIDER_PREFIX + day),
          thickness = Dimensions.DividerThickness.thin)
    }
  }
  Spacer(Modifier.height(Dimensions.Spacing.small))
}

/**
 * Composable function representing a collapsible section with a title, optional header, and
 * content.
 *
 * @param title The title of the section.
 * @param initiallyExpanded Boolean indicating whether the section is initially expanded.
 * @param header Optional composable function for the header content.
 * @param content Composable function for the main content of the section.
 * @param testTag Optional test tag for the section.
 */
@Composable
fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = true,
    header: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    testTag: String? = null,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
  val (isExpanded, setExpanded) =
      if (expanded != null && onExpandedChange != null) {
        expanded to onExpandedChange
      } else {
        var localExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
        localExpanded to { v: Boolean -> localExpanded = v }
      }

  val arrowRotation by
      animateFloatAsState(
          targetValue = if (isExpanded) Dimensions.Angles.expanded else Dimensions.Angles.collapsed,
          label = "arrow")

  Column(Modifier.fillMaxWidth()) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(top = Dimensions.Padding.medium).let { m ->
              if (testTag != null) m.testTag(testTag + ShopFormTestTags.SECTION_HEADER_SUFFIX)
              else m
            },
        verticalAlignment = Alignment.CenterVertically) {
          Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              modifier =
                  Modifier.weight(1f).let { m ->
                    if (testTag != null) m.testTag(testTag + ShopFormTestTags.SECTION_TITLE_SUFFIX)
                    else m
                  })

          header?.invoke(this)

          IconButton(
              onClick = { setExpanded(!isExpanded) },
              modifier =
                  Modifier.let { m ->
                    if (testTag != null) m.testTag(testTag + ShopFormTestTags.SECTION_TOGGLE_SUFFIX)
                    else m
                  }) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription =
                        if (isExpanded) ShopFormUi.Strings.COLLAPSE else ShopFormUi.Strings.EXPAND,
                    modifier = Modifier.rotate(arrowRotation))
              }
        }

    HorizontalDivider(
        thickness = Dimensions.DividerThickness.standard,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier =
            Modifier.padding(bottom = Dimensions.Spacing.large).let { m ->
              if (testTag != null) m.testTag(testTag + ShopFormTestTags.SECTION_DIVIDER_SUFFIX)
              else m
            })

    AnimatedVisibility(visible = isExpanded) {
      Column(
          Modifier.padding(top = Dimensions.Spacing.none).let { m ->
            if (testTag != null) m.testTag(testTag + ShopFormTestTags.SECTION_CONTENT_SUFFIX) else m
          },
          content = content)
    }
  }
}

/**
 * Composable function representing the opening hours editor dialog.
 *
 * @param show Boolean indicating whether to show the dialog.
 * @param day The day of the week being edited.
 * @param week List of opening hours for each day of the week.
 * @param onWeekChange Callback function to update the opening hours for the week.
 * @param onDismiss Callback function to dismiss the dialog.
 */
@Composable
fun OpeningHoursEditor(
    show: Boolean,
    day: Int?,
    week: List<OpeningHours>,
    onWeekChange: (List<OpeningHours>) -> Unit,
    onDismiss: () -> Unit
) {
  if (!show || day == null) return
  Box(Modifier.testTag(ShopFormTestTags.OPENING_HOURS_DIALOG_WRAPPER)) {
    OpeningHoursDialog(
        initialSelectedDays = setOf(day),
        current = week[day],
        onDismiss = onDismiss,
        onSave = { selectedDays, closed, open24, intervals ->
          val encoded: List<TimeSlot> =
              when {
                closed -> emptyList()
                open24 -> listOf(TimeSlot(TimeUi.OPEN24_START, TimeUi.OPEN24_END))
                else -> intervals.map { TimeSlot(it.first.hhmm(), it.second.hhmm()) }
              }
          val copy = week.toMutableList()
          selectedDays.forEach { d -> copy[d] = OpeningHours(day = d, hours = encoded) }
          onWeekChange(copy)
          onDismiss()
        })
  }
}

/**
 * Composable function representing the game stock picker dialog.
 *
 * @param show Boolean indicating whether to show the dialog.
 * @param stock List of pairs containing games and their quantities in stock.
 * @param onStockChange Callback function to update the stock list.
 * @param gameQuery The current query string for searching games.
 * @param gameSuggestions List of game suggestions based on the current query.
 * @param isSearching Boolean indicating if a search operation is in progress.
 * @param qty The quantity of the picked game.
 * @param onQtyChange Callback function to update the quantity of the picked game.
 * @param onSetGameQuery Callback function to update the game search query.
 * @param onSetGame Callback function to set the selected game.
 * @param onDismiss Callback function to dismiss the dialog.
 */
@Composable
fun GameStockPicker(
    owner: Account,
    shop: Shop?,
    viewModel: ShopSearchViewModel,
    gameUIState: GameUIState,
    show: Boolean,
    stock: List<Pair<Game, Int>>,
    onStockChange: (List<Pair<Game, Int>>) -> Unit,
    qty: Int,
    onQtyChange: (Int) -> Unit,
    onSetGameQuery: (String) -> Unit,
    onSetGame: (Game) -> Unit,
    onDismiss: () -> Unit
) {
  if (!show) return

  val existing = remember(stock) { stock.map { it.first.uid }.toSet() }
  Box(Modifier.testTag(ShopFormTestTags.GAME_STOCK_DIALOG_WRAPPER)) {
    GameStockDialog(
        owner,
        shop,
        viewModel = viewModel,
        gameUIState = gameUIState,
        onQueryChange = onSetGameQuery,
        quantity = qty,
        onQuantityChange = onQtyChange,
        existingIds = existing,
        onDismiss = {
          onDismiss()
          onQtyChange(1)
          onSetGameQuery("")
        },
        onSave = {
          gameUIState.fetchedGame?.let { g ->
            onStockChange((stock + (g to qty)).distinctBy { it.first.uid })
          }
          onQtyChange(1)
          onSetGameQuery("")
          onDismiss()
        })
  }
}

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

        Spacer(Modifier.height(Dimensions.Spacing.large))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
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

            FocusableInputField(
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
        containerColor = AppColors.primary,
        title = {
            Box(
                Modifier.fillMaxWidth()
                    .testTag(ShopComponentsTestTags.GAME_DIALOG_TITLE)
                    .background(AppColors.primary),
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
                    Spacer(Modifier.height(Dimensions.Padding.mediumSmall))
                    Text(
                        ShopUiDefaults.StringsMagicNumbers.DUPLICATE_GAME,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(ShopComponentsTestTags.GAME_DIALOG_HELPER))
                }

                Spacer(Modifier.height(Dimensions.Spacing.extraLarge))

                GameAddUI(
                    value = quantity,
                    onValueChange = onQuantityChange,
                    modifier =
                        Modifier.testTag(ShopComponentsTestTags.GAME_DIALOG_SLIDER)
                            .background(AppColors.primary))
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
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
        modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
            contentPadding = PaddingValues(bottom = Dimensions.Spacing.extraLarge),
            modifier = Modifier.heightIn(max = Dimensions.ContainerSize.maxListHeight)) {
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
        Row(
            modifier = Modifier.padding(Dimensions.Padding.medium),
            verticalAlignment = Alignment.CenterVertically) {

            // Icon + badge
            Box(
                modifier = Modifier.size(Dimensions.IconSize.huge),
                contentAlignment = Alignment.Center) {
                BadgedBox(
                    badge = {
                        // Only show badge if count > 0
                        if (count > 0) {
                            val max = ShopUiDefaults.RangesMagicNumbers.qtyGameDialog.last
                            val label = if (count > max) "$max+" else count.toString()
                            Badge(
                                modifier =
                                    Modifier.offset(
                                        x = Dimensions.BadgeSize.offsetX,
                                        y = Dimensions.BadgeSize.offsetY)
                                        .defaultMinSize(
                                            minWidth = Dimensions.BadgeSize.minSize,
                                            minHeight = Dimensions.BadgeSize.minSize),
                                containerColor = MaterialTheme.colorScheme.inversePrimary) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = Dimensions.Numbers.singleLine,
                                    softWrap = false,
                                    modifier =
                                        Modifier.padding(horizontal = Dimensions.Spacing.small))
                            }
                        }
                    }) {
                    Icon(Icons.Filled.VideogameAsset, contentDescription = null)
                }
            }

            Spacer(Modifier.width(Dimensions.Spacing.medium))

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
                    Icon(
                        Icons.Filled.Delete, contentDescription = "Remove ${game.name} from list")
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
        Row(
            Modifier.padding(Dimensions.Padding.medium),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                game.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)) {
                IconButton(
                    onClick = { onQuantityChange(game, (count - 1).coerceAtLeast(0)) },
                    enabled = count > 0,
                    modifier =
                        Modifier.testTag(ShopComponentsTestTags.SHOP_GAME_MINUS_BUTTON)) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                }
                FocusableInputField(
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
                        Modifier.width(Dimensions.ComponentWidth.inputFieldWidth)
                            .testTag(ShopComponentsTestTags.SHOP_GAME_QTY_INPUT))
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