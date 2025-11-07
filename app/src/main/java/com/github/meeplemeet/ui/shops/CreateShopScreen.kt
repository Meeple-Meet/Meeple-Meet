// This file was initially done by hand and
// then improved and refactored using ChatGPT-5 Extend Thinking
// Docstrings were generated using copilot from Android studio
package com.github.meeplemeet.ui.shops

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.CreateShopViewModel
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.ui.components.ActionBar
import com.github.meeplemeet.ui.components.AvailabilitySection
import com.github.meeplemeet.ui.components.CollapsibleSection
import com.github.meeplemeet.ui.components.GameListSection
import com.github.meeplemeet.ui.components.GameStockPicker
import com.github.meeplemeet.ui.components.OpeningHoursEditor
import com.github.meeplemeet.ui.components.RequiredInfoSection
import com.github.meeplemeet.ui.components.ShopFormTestTags
import com.github.meeplemeet.ui.components.ShopFormUi
import com.github.meeplemeet.ui.components.emptyWeek
import com.github.meeplemeet.ui.components.isValidEmail
import com.github.meeplemeet.ui.shops.AddShopUi.Strings
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

  // Reuse shared section suffixes
  const val SECTION_HEADER_SUFFIX = ShopFormTestTags.SECTION_HEADER_SUFFIX
  const val SECTION_TITLE_SUFFIX = ShopFormTestTags.SECTION_TITLE_SUFFIX
  const val SECTION_TOGGLE_SUFFIX = ShopFormTestTags.SECTION_TOGGLE_SUFFIX
  const val SECTION_DIVIDER_SUFFIX = ShopFormTestTags.SECTION_DIVIDER_SUFFIX
  const val SECTION_CONTENT_SUFFIX = ShopFormTestTags.SECTION_CONTENT_SUFFIX

  const val SECTION_REQUIRED = "section_required"

  // Reuse shared field tags
  const val FIELD_SHOP = ShopFormTestTags.FIELD_SHOP
  const val FIELD_EMAIL = ShopFormTestTags.FIELD_EMAIL
  const val FIELD_ADDRESS = ShopFormTestTags.FIELD_ADDRESS
  const val FIELD_PHONE = ShopFormTestTags.FIELD_PHONE
  const val FIELD_LINK = ShopFormTestTags.FIELD_LINK

  const val SPACER_AFTER_REQUIRED = "spacer_after_required"

  const val SECTION_AVAILABILITY = "section_availability"
  const val AVAILABILITY_LIST = ShopFormTestTags.AVAILABILITY_LIST
  const val AVAILABILITY_DIVIDER_PREFIX = ShopFormTestTags.AVAILABILITY_DIVIDER_PREFIX
  const val SPACER_AFTER_AVAILABILITY = "spacer_after_availability"

  const val SECTION_GAMES = "section_games"
  const val GAMES_ADD_LABEL = "games_add_label"
  const val GAMES_EMPTY_TEXT = "games_empty_text"
  const val GAMES_ADD_BUTTON = "games_add_button"

  const val OPENING_HOURS_DIALOG_WRAPPER = ShopFormTestTags.OPENING_HOURS_DIALOG_WRAPPER
  const val GAME_STOCK_DIALOG_WRAPPER = ShopFormTestTags.GAME_STOCK_DIALOG_WRAPPER

  const val BOTTOM_SPACER = "bottom_spacer"
}

/* ================================================================================================
 * UI Defaults
 * ================================================================================================ */
private object AddShopUi {
  // Reuse shared dimensions
  object Dimensions {
    val contentHPadding = ShopFormUi.Dimensions.contentHPadding
    val contentVPadding = ShopFormUi.Dimensions.contentVPadding
    val sectionSpace = ShopFormUi.Dimensions.sectionSpace
    val bottomSpacer = ShopFormUi.Dimensions.bottomSpacer
    val betweenControls = ShopFormUi.Dimensions.betweenControls
  }

  object Strings {
    const val REQUIREMENTS_SECTION = "Required Info"
    const val SECTION_AVAILABILITY = "Availability"
    const val SECTION_GAMES = "Games in stock"

    const val LABEL_SHOP = "Shop"
    const val PLACEHOLDER_SHOP = "Shop name"

    const val LabelEmail = "Email"
    const val PlaceholderEmail = "Email"

    const val LabelPhone = "Contact info"
    const val PlaceholderPhone = "Phone number"

    const val LabelLink = "Link"
    const val PlaceholderLink = "Website/Instagram link"

    const val PlaceholderLocation = "Search locations…"

    const val BtnAddGame = "Add game"
    const val EmptyGames = "No games selected yet."
    const val ErrorValidation = "Validation error"
    const val ErrorCreate = "Failed to create shop"
  }
}

/* ================================================================================================
 * Screen
 * ================================================================================================ */

/**
 * Composable function representing the Create Shop screen.
 *
 * @param owner The account of the shop owner.
 * @param onBack Callback function to be invoked when the back navigation is triggered.
 * @param onCreated Callback function to be invoked when the shop is successfully created, receives
 *   the shop ID.
 * @param viewModel The ViewModel managing the state and logic for creating a shop.
 */
@Composable
fun CreateShopScreen(
    owner: Account,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateShopViewModel
) {
  val ui by viewModel.gameUIState.collectAsState()
  val locationUi by viewModel.locationUIState.collectAsState()

  AddShopContent(
      onBack = onBack,
      onCreated = onCreated,
      onCreate = { name, email, address, week, stock ->
        try {
          val shop =
              viewModel.createShop(
                  owner = owner,
                  name = name,
                  phone = "",
                  email = email,
                  website = "",
                  address = address,
                  openingHours = week,
                  gameCollection = stock)
          shop.id
        } catch (e: IllegalArgumentException) {
          throw e
        } catch (e: Exception) {
          throw e
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
 * @param onCreated Callback function to be invoked when the shop is successfully created, receives
 *   the shop ID.
 * @param onCreate Suspend function to handle the creation of a shop with provided details, returns
 *   shop ID.
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
    onCreated: (String) -> Unit,
    onCreate:
        suspend (
            name: String,
            email: String,
            address: Location,
            week: List<OpeningHours>,
            stock: List<Pair<Game, Int>>) -> String,
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
              Text(
                  MeepleMeetScreen.CreateShop.title,
                  modifier = Modifier.testTag(CreateShopScreenTestTags.TITLE))
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
            onPrimary = {
              val addr = selectedLocation ?: Location(name = addressText)
              scope.launch {
                try {
                  val shopId = onCreate(shopName, email, addr, week, stock)
                  onCreated(shopId)
                } catch (e: IllegalArgumentException) {
                  snackbarHost.showSnackbar(e.message ?: Strings.ErrorValidation)
                } catch (e: Exception) {
                  snackbarHost.showSnackbar(Strings.ErrorCreate)
                }
              }
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
                    title = Strings.REQUIREMENTS_SECTION,
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
                    title = Strings.SECTION_AVAILABILITY,
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
                    title = Strings.SECTION_GAMES,
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
