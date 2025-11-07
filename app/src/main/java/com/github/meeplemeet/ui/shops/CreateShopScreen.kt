// This file was initially done by hand and
// then improved and refactored using ChatGPT-5 Extend Thinking
// Docstrings were generated using copilot from Android studio
package com.github.meeplemeet.ui.shops

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.CreateShopViewModel
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.components.ActionBar
import com.github.meeplemeet.ui.components.DayRow
import com.github.meeplemeet.ui.components.GameListSection
import com.github.meeplemeet.ui.components.GameStockDialog
import com.github.meeplemeet.ui.components.LabeledField
import com.github.meeplemeet.ui.components.OpeningHoursDialog
import com.github.meeplemeet.ui.sessions.LocationSearchBar
import com.github.meeplemeet.ui.shops.AddShopUi.Strings
import java.text.DateFormatSymbols
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/* ================================================================================================
 * Test tags
 * ================================================================================================ */
object CreateShopScreenTestTags {
  const val SCAFFOLD = "add_shop_scaffold"
  const val TOPBAR = "add_shop_topbar"
  const val TITLE = "add_shop_title"
  const val NAV_BACK = "add_shop_nav_back"
  const val SNACKBAR_HOST = "add_shop_snackbar_host"
  const val LIST = "add_shop_list"

  const val SECTION_HEADER_SUFFIX = "_header"
  const val SECTION_TITLE_SUFFIX = "_title"
  const val SECTION_TOGGLE_SUFFIX = "_toggle"
  const val SECTION_DIVIDER_SUFFIX = "_divider"
  const val SECTION_CONTENT_SUFFIX = "_content"

  const val SECTION_REQUIRED = "section_required"
  const val FIELD_SHOP = "field_shop_name"
  const val FIELD_EMAIL = "field_email"
  const val FIELD_ADDRESS = "field_address"
  const val FIELD_PHONE = "field_phone"
  const val FIELD_LINK = "field_link"
  const val SPACER_AFTER_REQUIRED = "spacer_after_required"

  const val SECTION_AVAILABILITY = "section_availability"
  const val AVAILABILITY_LIST = "availability_list"
  const val AVAILABILITY_DIVIDER_PREFIX = "availability_divider_"
  const val SPACER_AFTER_AVAILABILITY = "spacer_after_availability"

  const val SECTION_GAMES = "section_games"
  const val GAMES_ADD_LABEL = "games_add_label"
  const val GAMES_EMPTY_TEXT = "games_empty_text"
  const val GAMES_ADD_BUTTON = "games_add_button"

  const val OPENING_HOURS_DIALOG_WRAPPER = "opening_hours_dialog_wrapper"
  const val GAME_STOCK_DIALOG_WRAPPER = "game_stock_dialog_wrapper"

  const val BOTTOM_SPACER = "bottom_spacer"
}

/* ================================================================================================
 * UI Defaults
 * ================================================================================================ */
private object AddShopUi {
  object Dimensions {
    val contentHPadding = 16.dp
    val contentVPadding = 8.dp
    val sectionSpace = 12.dp
    val bottomSpacer = 100.dp
    val betweenControls = 6.dp
  }

  object Strings {
    const val ScreenTitle = "Add Shop"
    const val RequirementsSection = "Required Info"
    const val SectionAvailability = "Availability"
    const val SectionGames = "Games in stock"

    const val LabelShop = "Shop"
    const val PlaceholderShop = "Shop name"

    const val LabelEmail = "Email"
    const val PlaceholderEmail = "Email"

    const val LabelPhone = "Contact info"
    const val PlaceholderPhone = "Phone number"

    const val LabelLink = "Link"
    const val PlaceholderLink = "Website/Instagram link"

    const val BtnAddGame = "Add game"
    const val EmptyGames = "No games selected yet."
    const val ErrorValidation = "Validation error"
    const val ErrorCreate = "Failed to create shop"

    const val Collapse = "Collapse"
    const val Expand = "Expand"

    const val ClosedMsg = "Closed"
    const val Open24Msg = "Open 24 hours"
  }

  val dayNames: List<String> by lazy {
    val weekdays = DateFormatSymbols().weekdays
    (0..6).map { idx -> weekdays.getOrNull(idx + 1) ?: "Day ${idx + 1}" }
  }
}

/* ================================================================================================
 * Time utilities
 * ================================================================================================ */
private object TimeUi {
  const val OPEN24_START = "00:00"
  const val OPEN24_END = "23:59"
  val fmt12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
}

/**
 * Formats a LocalTime object into "HH:mm" string format.
 *
 * @return A string representation of the time in "HH:mm" format.
 * @receiver The LocalTime object to format.
 */
private fun LocalTime.hhmm(): String = "%02d:%02d".format(hour, minute)

/**
 * Tries to parse a time string into a LocalTime object.
 *
 * Supports both 12-hour (with AM/PM) and 24-hour formats.
 *
 * @return A LocalTime object if parsing is successful, null otherwise.
 * @receiver The time string to parse.
 */
private fun String.tryParseTime(): LocalTime? =
    runCatching {
          val lower = lowercase(Locale.getDefault())
          if (lower.contains("am") || lower.contains("pm")) {
            LocalTime.parse(
                replace("am", " AM", ignoreCase = true)
                    .replace("pm", " PM", ignoreCase = true)
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .uppercase(Locale.getDefault()),
                TimeUi.fmt12)
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
private fun humanize(hours: List<TimeSlot>): String =
    when {
      hours.isEmpty() -> Strings.ClosedMsg
      hours.size == 1 &&
          hours[0].open == TimeUi.OPEN24_START &&
          hours[0].close == TimeUi.OPEN24_END -> Strings.Open24Msg
      else -> {
        // Sort by opening time
        val sorted = hours.sortedBy { ts -> ts.open?.tryParseTime() ?: LocalTime.MAX }
        sorted.joinToString("\n") { slot ->
          val s = slot.open?.let { it.tryParseTime()?.format(TimeUi.fmt12) ?: it } ?: "-"
          val e = slot.close?.let { it.tryParseTime()?.format(TimeUi.fmt12) ?: it } ?: "-"
          "$s - $e"
        }
      }
    }

/* ================================================================================================
 * Helpers
 * ================================================================================================ */

/** Returns an empty list of opening hours for each day of the week. */
private fun emptyWeek(): List<OpeningHours> =
    List(7) { day -> OpeningHours(day = day, hours = emptyList()) }

/**
 * Validates if the provided email string is in a valid email format.
 *
 * @param email The email string to validate.
 * @return True if the email is valid, false otherwise.
 */
private fun isValidEmail(email: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(email).matches()

/* ================================================================================================
 * Screen
 * ================================================================================================ */

/**
 * Composable function representing the Create Shop screen.
 *
 * @param owner The account of the shop owner.
 * @param onBack Callback function to be invoked when the back navigation is triggered.
 * @param onCreated Callback function to be invoked when the shop is successfully created.
 * @param viewModel The ViewModel managing the state and logic for creating a shop.
 */
@Composable
fun CreateShopScreen(
    owner: Account,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateShopViewModel
) {
  val ui by viewModel.gameUIState.collectAsState()
  val locationUi by viewModel.locationUIState.collectAsState()

  AddShopContent(
      onBack = onBack,
      onCreated = onCreated,
      onCreate = { name, email, address, week, stock ->
        try {
          viewModel.createShop(
              owner = owner,
              name = name,
              phone = "",
              email = email,
              website = "",
              address = address,
              openingHours = week,
              gameCollection = stock)
          null
        } catch (e: IllegalArgumentException) {
          e.message ?: Strings.ErrorValidation
        } catch (_: Exception) {
          Strings.ErrorCreate
        }
      },
      locationUi = locationUi,
      gameQuery = ui.gameQuery,
      gameSuggestions = ui.gameSuggestions,
      isSearching = ui.isSearching,
      onSetGameQuery = viewModel::setGameQuery,
      onSetGame = viewModel::setGame,
      viewModel = viewModel,
      owner = owner)
}

/* ================================================================================================
 * Content
 * ================================================================================================ */

/**
 * Composable function representing the content of the Add Shop screen.
 *
 * @param onBack Callback function to be invoked when the back navigation is triggered.
 * @param onCreated Callback function to be invoked when the shop is successfully created.
 * @param onCreate Callback function to handle the creation of a shop with provided details.
 * @param gameQuery The current query string for searching games.
 * @param gameSuggestions List of game suggestions based on the current query.
 * @param isSearching Boolean indicating if a search operation is in progress.
 * @param onSetGameQuery Callback function to update the game search query.
 * @param onSetGame Callback function to set the selected game.
 * @param initialStock Initial list of games and their quantities in stock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShopContent(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    onCreate:
        (
            name: String,
            email: String,
            address: Location,
            week: List<OpeningHours>,
            stock: List<Pair<Game, Int>>) -> String?,
    locationUi: LocationUIState,
    gameQuery: String,
    gameSuggestions: List<Game>,
    isSearching: Boolean,
    onSetGameQuery: (String) -> Unit,
    onSetGame: (Game) -> Unit,
    initialStock: List<Pair<Game, Int>> = emptyList(),
    viewModel: CreateShopViewModel,
    owner: Account
) {
  val snackbarHost = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val showError: (String) -> Unit = { msg -> scope.launch { snackbarHost.showSnackbar(msg) } }

  var shopName by rememberSaveable { mutableStateOf("") }
  var email by rememberSaveable { mutableStateOf("") }
  var addressText by rememberSaveable { mutableStateOf("") }
  var selectedLocation by remember { mutableStateOf<Location?>(null) }
  var phone by rememberSaveable { mutableStateOf("") }
  var link by rememberSaveable { mutableStateOf("") }

  var week by remember { mutableStateOf(emptyWeek()) }

  var editingDay by remember { mutableStateOf<Int?>(null) }
  var showHoursDialog by remember { mutableStateOf(false) }

  var showGameDialog by remember { mutableStateOf(false) }
  var qty by rememberSaveable { mutableIntStateOf(1) }
  var picked by remember { mutableStateOf<Game?>(null) }
  var stock by remember { mutableStateOf(initialStock) }

  val hasOpeningHours by remember(week) { derivedStateOf { week.any { it.hours.isNotEmpty() } } }
  val isValid by
      remember(shopName, email, addressText, hasOpeningHours) {
        derivedStateOf {
          shopName.isNotBlank() &&
              isValidEmail(email) &&
              addressText.isNotBlank() &&
              hasOpeningHours
        }
      }

  // Sync addressText with locationUi.locationQuery when typing
  LaunchedEffect(locationUi.locationQuery) {
    if (locationUi.locationQuery.isNotEmpty() && addressText != locationUi.locationQuery) {
      addressText = locationUi.locationQuery
    }
  }

  fun onDiscard() {
    shopName = ""
    email = ""
    addressText = ""
    selectedLocation = null
    phone = ""
    link = ""
    week = emptyWeek()
    editingDay = null
    showHoursDialog = false
    showGameDialog = false
    qty = 1
    picked = null
    stock = emptyList()
    onSetGameQuery("")
    onBack()
  }

  Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(Strings.ScreenTitle, modifier = Modifier.testTag(CreateShopScreenTestTags.TITLE))
            },
            navigationIcon = {
              IconButton(
                  onClick = onBack,
                  modifier = Modifier.testTag(CreateShopScreenTestTags.NAV_BACK)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                  }
            },
            modifier = Modifier.testTag(CreateShopScreenTestTags.TOPBAR))
      },
      snackbarHost = {
        SnackbarHost(
            snackbarHost, modifier = Modifier.testTag(CreateShopScreenTestTags.SNACKBAR_HOST))
      },
      bottomBar = {
        ActionBar(
            onDiscard = { onDiscard() },
            onCreate = {
              val addr = selectedLocation ?: Location(name = addressText)
              val err = onCreate(shopName, email, addr, week, stock)
              if (err == null) onCreated() else scope.launch { snackbarHost.showSnackbar(err) }
            },
            enabled = isValid)
      },
      modifier = Modifier.testTag(CreateShopScreenTestTags.SCAFFOLD)) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).testTag(CreateShopScreenTestTags.LIST),
            contentPadding =
                PaddingValues(
                    horizontal = AddShopUi.Dimensions.contentHPadding,
                    vertical = AddShopUi.Dimensions.contentVPadding)) {
              item {
                CollapsibleSection(
                    title = Strings.RequirementsSection,
                    initiallyExpanded = false,
                    content = {
                      RequiredInfoSection(
                          shopName = shopName,
                          onShopName = { shopName = it },
                          email = email,
                          onEmail = { email = it },
                          phone = phone,
                          onPhone = { phone = it },
                          link = link,
                          onLink = { link = it },
                          addressText = addressText,
                          onAddressText = { q ->
                            addressText = q
                            selectedLocation = null
                          },
                          onPickLocation = { loc ->
                            addressText = loc.name
                            selectedLocation = loc
                          },
                          locationUi = locationUi,
                          showError = showError,
                          viewModel = viewModel,
                          owner = owner)
                    },
                    testTag = CreateShopScreenTestTags.SECTION_REQUIRED)
              }

              item {
                Spacer(
                    Modifier.height(AddShopUi.Dimensions.sectionSpace)
                        .testTag(CreateShopScreenTestTags.SPACER_AFTER_REQUIRED))
              }

              item {
                CollapsibleSection(
                    title = Strings.SectionAvailability,
                    initiallyExpanded = false,
                    content = {
                      AvailabilitySection(
                          week = week,
                          onEdit = { day ->
                            editingDay = day
                            showHoursDialog = true
                          })
                    },
                    testTag = CreateShopScreenTestTags.SECTION_AVAILABILITY)
              }

              item {
                Spacer(
                    Modifier.height(AddShopUi.Dimensions.sectionSpace)
                        .testTag(CreateShopScreenTestTags.SPACER_AFTER_AVAILABILITY))
              }

              item {
                CollapsibleSection(
                    title = Strings.SectionGames,
                    initiallyExpanded = false,
                    header = {
                      TextButton(
                          onClick = {
                            picked = null
                            onSetGameQuery("")
                            showGameDialog = true
                          },
                          modifier = Modifier.testTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON)) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(AddShopUi.Dimensions.betweenControls))
                            Text(
                                Strings.BtnAddGame,
                                modifier =
                                    Modifier.testTag(CreateShopScreenTestTags.GAMES_ADD_LABEL))
                          }
                    },
                    content = {
                      GamesSection(
                          stock = stock,
                          onDelete = { gameToRemove ->
                            stock = stock.filterNot { it.first.uid == gameToRemove.uid }
                          })
                    },
                    testTag = CreateShopScreenTestTags.SECTION_GAMES)
              }

              item {
                Spacer(
                    Modifier.height(AddShopUi.Dimensions.bottomSpacer)
                        .testTag(CreateShopScreenTestTags.BOTTOM_SPACER))
              }
            }
      }

  OpeningHoursEditor(
      show = showHoursDialog,
      day = editingDay,
      week = week,
      onWeekChange = { week = it },
      onDismiss = { showHoursDialog = false })

  GameStockPicker(
      show = showGameDialog,
      stock = stock,
      onStockChange = { stock = it },
      gameQuery = gameQuery,
      gameSuggestions = gameSuggestions,
      isSearching = isSearching,
      picked = picked,
      onPickedChange = { picked = it },
      qty = qty,
      onQtyChange = { qty = it },
      onSetGameQuery = onSetGameQuery,
      onSetGame = onSetGame,
      onDismiss = { showGameDialog = false })
}

/* ================================================================================================
 * Sections
 * ================================================================================================ */

/**
 * Composable function representing the required information section of the Add Shop screen.
 *
 * @param shopName The current value of the shop name field.
 * @param onShopName Callback function to update the shop name.
 * @param email The current value of the email field.
 * @param onEmail Callback function to update the email.
 * @param phone The current value of the phone field.
 * @param onPhone Callback function to update the phone.
 * @param link The current value of the link field.
 * @param onLink Callback function to update the link.
 * @param addressText The current value of the address text field.
 * @param onAddressText Callback function to update the address text.
 * @param onPickLocation Callback function to handle location selection.
 */
@Composable
private fun RequiredInfoSection(
    shopName: String,
    onShopName: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    phone: String,
    onPhone: (String) -> Unit,
    link: String,
    onLink: (String) -> Unit,
    addressText: String,
    onAddressText: (String) -> Unit,
    onPickLocation: (Location) -> Unit,
    locationUi: LocationUIState,
    showError: (String) -> Unit = {},
    viewModel: CreateShopViewModel,
    owner: Account
) {
  Box(Modifier.testTag(CreateShopScreenTestTags.FIELD_SHOP)) {
    LabeledField(
        label = Strings.LabelShop,
        placeholder = Strings.PlaceholderShop,
        value = shopName,
        onValueChange = onShopName)
  }
  Box(Modifier.testTag(CreateShopScreenTestTags.FIELD_EMAIL)) {
    LabeledField(
        label = Strings.LabelEmail,
        placeholder = Strings.PlaceholderEmail,
        value = email,
        onValueChange = onEmail,
        keyboardType = KeyboardType.Email)
  }
  val showEmailError = email.isNotEmpty() && !isValidEmail(email)
  if (showEmailError) {
    Text(
        "Enter a valid email address.",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall)
  }
  Box(Modifier.testTag(CreateShopScreenTestTags.FIELD_PHONE)) {
    LabeledField(
        label = Strings.LabelPhone,
        placeholder = Strings.PlaceholderPhone,
        value = phone,
        onValueChange = onPhone,
        keyboardType = KeyboardType.Phone)
  }

  Box(Modifier.testTag(CreateShopScreenTestTags.FIELD_LINK)) {
    LabeledField(
        label = Strings.LabelLink,
        placeholder = Strings.PlaceholderLink,
        value = link,
        onValueChange = onLink,
        keyboardType = KeyboardType.Uri)
  }

  Box(Modifier.testTag(CreateShopScreenTestTags.FIELD_ADDRESS)) {
    LocationSearchBar(
        viewModel = viewModel,
        locationUi = locationUi,
        currentUser = owner,
        shop = null,
        onError = showError,
        onPick = onPickLocation)
  }
}

/**
 * Composable function representing the availability section of the Add Shop screen.
 *
 * @param week List of opening hours for each day of the week.
 * @param onEdit Callback function to handle editing of opening hours for a specific day.
 */
@Composable
private fun AvailabilitySection(week: List<OpeningHours>, onEdit: (Int) -> Unit) {
  Column(Modifier.testTag(CreateShopScreenTestTags.AVAILABILITY_LIST)) {
    week.forEach { oh ->
      val day = oh.day
      DayRow(
          dayName = AddShopUi.dayNames[day], value = humanize(oh.hours), onEdit = { onEdit(day) })
      HorizontalDivider(
          modifier = Modifier.testTag(CreateShopScreenTestTags.AVAILABILITY_DIVIDER_PREFIX + day))
    }
  }
  Spacer(Modifier.height(4.dp))
}

/**
 * Composable function representing the games section of the Add Shop screen.
 *
 * @param stock List of pairs containing games and their quantities in stock.
 * @param onDelete Callback function to handle deletion of a game from the stock list.
 */
@Composable
private fun GamesSection(stock: List<Pair<Game, Int>>, onDelete: (Game) -> Unit) {
  if (stock.isNotEmpty()) {
    GameListSection(
        hasDeleteButton = true,
        onDelete = onDelete,
        games = stock,
        clickableGames = false,
        modifier = Modifier.fillMaxWidth(),
    )
  } else {
    Text(
        Strings.EmptyGames,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT))
  }
}

/* ================================================================================================
 * Dialog wrappers
 * ================================================================================================ */

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
private fun OpeningHoursEditor(
    show: Boolean,
    day: Int?,
    week: List<OpeningHours>,
    onWeekChange: (List<OpeningHours>) -> Unit,
    onDismiss: () -> Unit
) {
  if (!show || day == null) return
  Box(Modifier.testTag(CreateShopScreenTestTags.OPENING_HOURS_DIALOG_WRAPPER)) {
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
 * @param picked The currently picked game.
 * @param onPickedChange Callback function to update the picked game.
 * @param qty The quantity of the picked game.
 * @param onQtyChange Callback function to update the quantity of the picked game.
 * @param onSetGameQuery Callback function to update the game search query.
 * @param onSetGame Callback function to set the selected game.
 * @param onDismiss Callback function to dismiss the dialog.
 */
@Composable
private fun GameStockPicker(
    show: Boolean,
    stock: List<Pair<Game, Int>>,
    onStockChange: (List<Pair<Game, Int>>) -> Unit,
    gameQuery: String,
    gameSuggestions: List<Game>,
    isSearching: Boolean,
    picked: Game?,
    onPickedChange: (Game?) -> Unit,
    qty: Int,
    onQtyChange: (Int) -> Unit,
    onSetGameQuery: (String) -> Unit,
    onSetGame: (Game) -> Unit,
    onDismiss: () -> Unit
) {
  if (!show) return

  val existing = remember(stock) { stock.map { it.first.uid }.toSet() }
  Box(Modifier.testTag(CreateShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER)) {
    GameStockDialog(
        query = gameQuery,
        onQueryChange = onSetGameQuery,
        results = gameSuggestions,
        isLoading = isSearching,
        onPickGame = { g ->
          onPickedChange(g)
          onSetGame(g)
        },
        selectedGame = picked,
        quantity = qty,
        onQuantityChange = onQtyChange,
        existingIds = existing,
        onDismiss = {
          onDismiss()
          onQtyChange(1)
          onPickedChange(null)
          onSetGameQuery("")
        },
        onSave = {
          picked?.let { g -> onStockChange((stock + (g to qty)).distinctBy { it.first.uid }) }
          onQtyChange(1)
          onPickedChange(null)
          onSetGameQuery("")
          onDismiss()
        })
  }
}

/* ================================================================================================
 * Collapsible section
 * ================================================================================================ */

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
private fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = true,
    header: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    testTag: String? = null
) {
  var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
  val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")

  Column(Modifier.fillMaxWidth()) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(top = 8.dp).let { m ->
              if (testTag != null)
                  m.testTag(testTag + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX)
              else m
            },
        verticalAlignment = Alignment.CenterVertically) {
          Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              modifier =
                  Modifier.weight(1f).let { m ->
                    if (testTag != null)
                        m.testTag(testTag + CreateShopScreenTestTags.SECTION_TITLE_SUFFIX)
                    else m
                  })
          header?.invoke(this)
          IconButton(
              onClick = { expanded = !expanded },
              modifier =
                  Modifier.let { m ->
                    if (testTag != null)
                        m.testTag(testTag + CreateShopScreenTestTags.SECTION_TOGGLE_SUFFIX)
                    else m
                  }) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) Strings.Collapse else Strings.Expand,
                    modifier = Modifier.rotate(arrowRotation))
              }
        }
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier =
            Modifier.padding(bottom = 12.dp).let { m ->
              if (testTag != null)
                  m.testTag(testTag + CreateShopScreenTestTags.SECTION_DIVIDER_SUFFIX)
              else m
            })

    AnimatedVisibility(visible = expanded) {
      Column(
          Modifier.padding(top = 0.dp).let { m ->
            if (testTag != null)
                m.testTag(testTag + CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX)
            else m
          },
          content = content)
    }
  }
}
