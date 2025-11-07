package com.github.meeplemeet.ui.shops
// Github copilot was used for this file

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.ui.components.ActionBar
import com.github.meeplemeet.ui.components.AvailabilitySection
import com.github.meeplemeet.ui.components.CollapsibleSection
import com.github.meeplemeet.ui.components.EditableGameItem
import com.github.meeplemeet.ui.components.GameStockPicker
import com.github.meeplemeet.ui.components.OpeningHoursEditor
import com.github.meeplemeet.ui.components.RequiredInfoSection
import com.github.meeplemeet.ui.components.ShopFormTestTags
import com.github.meeplemeet.ui.components.ShopFormUi
import com.github.meeplemeet.ui.components.ShopUiDefaults
import com.github.meeplemeet.ui.components.emptyWeek
import kotlinx.coroutines.launch

/* ================================================================================================
 * Test tags
 * ================================================================================================ */
object EditShopScreenTestTags {
  const val SCAFFOLD = "edit_shop_scaffold"
  const val TOPBAR = "edit_shop_topbar"
  const val TITLE = "edit_shop_title"
  const val NAV_BACK = "edit_shop_nav_back"
  const val SNACKBAR_HOST = "edit_shop_snackbar_host"
  const val LIST = "edit_shop_list"

  // Reuse shared section suffixes
  const val SECTION_HEADER_SUFFIX = ShopFormTestTags.SECTION_HEADER_SUFFIX
  const val SECTION_TOGGLE_SUFFIX = ShopFormTestTags.SECTION_TOGGLE_SUFFIX
  const val SECTION_CONTENT_SUFFIX = ShopFormTestTags.SECTION_CONTENT_SUFFIX

  const val SECTION_REQUIRED = "section_required"

  // Reuse shared field tags
  const val FIELD_SHOP = ShopFormTestTags.FIELD_SHOP
  const val FIELD_EMAIL = ShopFormTestTags.FIELD_EMAIL
  const val FIELD_PHONE = ShopFormTestTags.FIELD_PHONE
  const val FIELD_LINK = ShopFormTestTags.FIELD_LINK

  const val SPACER_AFTER_REQUIRED = "spacer_after_required"

  const val SECTION_AVAILABILITY = "section_availability"
  const val SPACER_AFTER_AVAILABILITY = "spacer_after_availability"

  const val SECTION_GAMES = "section_games"
  const val GAMES_ADD_LABEL = "games_add_label"
  const val GAMES_EMPTY_TEXT = "games_empty_text"
  const val GAMES_ADD_BUTTON = "games_add_button"
  const val GAME_STOCK_DIALOG_WRAPPER = ShopFormTestTags.GAME_STOCK_DIALOG_WRAPPER

  const val BOTTOM_SPACER = "bottom_spacer"
}

/* ================================================================================================
 * UI Defaults
 * ================================================================================================ */
private object EditShopUi {
  // Reuse shared dimensions
  object Dimensions {
    val contentHPadding = ShopFormUi.Dimensions.contentHPadding
    val contentVPadding = ShopFormUi.Dimensions.contentVPadding
    val sectionSpace = ShopFormUi.Dimensions.sectionSpace
    val bottomSpacer = ShopFormUi.Dimensions.bottomSpacer
    val betweenControls = ShopFormUi.Dimensions.betweenControls
  }

  object Strings {
    const val SCREEN_TITLE = "Edit Shop"
    const val SECTION_REQUIRED = "Required Info"
    const val SECTION_AVAILABILITY = "Availability"
    const val SECTION_GAMES = "Games in stock"

    const val BTN_ADD_GAME = "Add game"
    const val EMPTY_GAMES = "No games selected yet."
    const val ERROR_VALIDATION = "Validation error"
    const val ERROR_SAVE = "Failed to save shop"
  }
}

/* ================================================================================================
 * Screen
 * ================================================================================================ */

/**
 * Composable function representing the Edit Shop screen.
 *
 * @param shopId The ID of the shop to edit.
 * @param owner The account of the shop owner.
 * @param onBack Callback function to be invoked when the back navigation is triggered.
 * @param onSaved Callback function to be invoked when the shop is successfully saved.
 * @param viewModel The ViewModel managing the state and logic for editing a shop.
 */
@Composable
fun EditShopScreen(
    owner: Account,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditShopViewModel
) {
  val ui by viewModel.gameUIState.collectAsState()
  val shop by viewModel.shop.collectAsState()
  val locationUi by viewModel.locationUIState.collectAsState()

  EditShopContent(
      shop = shop,
      onBack = onBack,
      onSaved = onSaved,
      onSave = { loadedShop, requester, name, email, phone, website, address, week, stock ->
        try {
          viewModel.updateShop(
              shop = loadedShop,
              requester = requester,
              owner = owner,
              name = name,
              phone = phone,
              email = email,
              website = website,
              address = address,
              openingHours = week,
              gameCollection = stock)
          null
        } catch (e: IllegalArgumentException) {
          e.message ?: EditShopUi.Strings.ERROR_VALIDATION
        } catch (_: Exception) {
          EditShopUi.Strings.ERROR_SAVE
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
 * Composable function representing the content of the Edit Shop screen.
 *
 * @param shop The shop to edit, or null if still loading.
 * @param onBack Callback function to be invoked when the back navigation is triggered.
 * @param onSaved Callback function to be invoked when the shop is successfully saved.
 * @param onSave Callback function to handle the saving of a shop with provided details.
 * @param gameQuery The current query string for searching games.
 * @param gameSuggestions List of game suggestions based on the current query.
 * @param isSearching Boolean indicating if a search operation is in progress.
 * @param onSetGameQuery Callback function to update the game search query.
 * @param onSetGame Callback function to set the selected game.
 * @param initialStock Initial list of games and their quantities in stock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShopContent(
    shop: Shop?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onSave:
        (
            shop: Shop,
            requester: Account,
            name: String,
            email: String,
            phone: String,
            website: String,
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
    viewModel: EditShopViewModel,
    owner: Account
) {
  val snackbarHost = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val showError: (String) -> Unit = { msg -> scope.launch { snackbarHost.showSnackbar(msg) } }

  // Initialize state with loaded shop data or default values
  var shopName by rememberSaveable(shop) { mutableStateOf(shop?.name ?: "") }
  var email by rememberSaveable(shop) { mutableStateOf(shop?.email ?: "") }
  var addressText by rememberSaveable(shop) { mutableStateOf(shop?.address?.name ?: "") }
  var selectedLocation by remember(shop) { mutableStateOf(shop?.address) }
  var phone by rememberSaveable(shop) { mutableStateOf(shop?.phone ?: "") }
  var link by rememberSaveable(shop) { mutableStateOf(shop?.website ?: "") }

  var week by remember(shop) { mutableStateOf(shop?.openingHours ?: emptyWeek()) }

  var editingDay by remember { mutableStateOf<Int?>(null) }
  var showHoursDialog by remember { mutableStateOf(false) }

  var showGameDialog by remember { mutableStateOf(false) }
  var qty by rememberSaveable { mutableStateOf(1) }
  var picked by remember { mutableStateOf<Game?>(null) }
  var stock by remember(shop) { mutableStateOf(shop?.gameCollection ?: initialStock) }

  val hasOpeningHours by remember(week) { derivedStateOf { week.any { it.hours.isNotEmpty() } } }
  val isValid by
      remember(shopName, email, addressText, hasOpeningHours) {
        derivedStateOf {
          shopName.isNotBlank() && email.isNotBlank() && addressText.isNotBlank() && hasOpeningHours
        }
      }

  // Sync addressText with locationUi.locationQuery when typing
  LaunchedEffect(locationUi.locationQuery) {
    if (locationUi.locationQuery.isNotEmpty() && addressText != locationUi.locationQuery) {
      addressText = locationUi.locationQuery
    }
  }

  fun onDiscard() {
    onBack()
  }

  Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  EditShopUi.Strings.SCREEN_TITLE,
                  modifier = Modifier.testTag(EditShopScreenTestTags.TITLE))
            },
            navigationIcon = {
              IconButton(
                  onClick = onBack, modifier = Modifier.testTag(EditShopScreenTestTags.NAV_BACK)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                  }
            },
            modifier = Modifier.testTag(EditShopScreenTestTags.TOPBAR))
      },
      snackbarHost = {
        SnackbarHost(
            snackbarHost, modifier = Modifier.testTag(EditShopScreenTestTags.SNACKBAR_HOST))
      },
      bottomBar = {
        ActionBar(
            onDiscard = { onDiscard() },
            onPrimary = {
              if (shop != null) {
                val addr = selectedLocation ?: Location(name = addressText)
                val err = onSave(shop, shop.owner, shopName, email, phone, link, addr, week, stock)
                if (err == null) onSaved() else scope.launch { snackbarHost.showSnackbar(err) }
              }
            },
            enabled = isValid && shop != null,
            primaryButtonText = ShopUiDefaults.StringsMagicNumbers.BTN_SAVE)
      },
      modifier = Modifier.testTag(EditShopScreenTestTags.SCAFFOLD)) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).testTag(EditShopScreenTestTags.LIST),
            contentPadding =
                PaddingValues(
                    horizontal = EditShopUi.Dimensions.contentHPadding,
                    vertical = EditShopUi.Dimensions.contentVPadding)) {
              item {
                CollapsibleSection(
                    title = EditShopUi.Strings.SECTION_REQUIRED,
                    initiallyExpanded = true,
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
                    testTag = EditShopScreenTestTags.SECTION_REQUIRED)
              }

              item {
                Spacer(
                    Modifier.height(EditShopUi.Dimensions.sectionSpace)
                        .testTag(EditShopScreenTestTags.SPACER_AFTER_REQUIRED))
              }

              item {
                CollapsibleSection(
                    title = EditShopUi.Strings.SECTION_AVAILABILITY,
                    initiallyExpanded = true,
                    content = {
                      AvailabilitySection(
                          week = week,
                          onEdit = { day ->
                            editingDay = day
                            showHoursDialog = true
                          })
                    },
                    testTag = EditShopScreenTestTags.SECTION_AVAILABILITY)
              }

              item {
                Spacer(
                    Modifier.height(EditShopUi.Dimensions.sectionSpace)
                        .testTag(EditShopScreenTestTags.SPACER_AFTER_AVAILABILITY))
              }

              item {
                CollapsibleSection(
                    title = EditShopUi.Strings.SECTION_GAMES,
                    initiallyExpanded = true,
                    header = {
                      TextButton(
                          onClick = {
                            picked = null
                            onSetGameQuery("")
                            showGameDialog = true
                          },
                          modifier = Modifier.testTag(EditShopScreenTestTags.GAMES_ADD_BUTTON)) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(EditShopUi.Dimensions.betweenControls))
                            Text(
                                EditShopUi.Strings.BTN_ADD_GAME,
                                modifier = Modifier.testTag(EditShopScreenTestTags.GAMES_ADD_LABEL))
                          }
                    },
                    content = {
                      GamesSection(
                          stock = stock,
                          onQuantityChange = { game, newQuantity ->
                            stock =
                                stock.map { (g, qty) ->
                                  if (g.uid == game.uid) g to newQuantity else g to qty
                                }
                          },
                          onDelete = { gameToRemove ->
                            stock = stock.filterNot { it.first.uid == gameToRemove.uid }
                          })
                    },
                    testTag = EditShopScreenTestTags.SECTION_GAMES)
              }

              item {
                Spacer(
                    Modifier.height(EditShopUi.Dimensions.bottomSpacer)
                        .testTag(EditShopScreenTestTags.BOTTOM_SPACER))
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
 * Sections (Edit-specific)
 * ================================================================================================ */

/**
 * Composable function representing the games section of the Edit Shop screen.
 *
 * @param stock List of pairs containing games and their quantities in stock.
 * @param onQuantityChange Callback function to handle updating quantity of a game in the stock
 *   list.
 * @param onDelete Callback function to handle deletion of a game from the stock list.
 */
@Composable
private fun GamesSection(
    stock: List<Pair<Game, Int>>,
    onQuantityChange: (Game, Int) -> Unit,
    onDelete: (Game) -> Unit
) {
  if (stock.isNotEmpty()) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.heightIn(max = 600.dp)) {
          items(items = stock, key = { it.first.uid }) { (game, count) ->
            EditableGameItem(
                game = game,
                count = count,
                onQuantityChange = onQuantityChange,
                onDelete = onDelete)
          }
        }
  } else {
    Text(
        EditShopUi.Strings.EMPTY_GAMES,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(EditShopScreenTestTags.GAMES_EMPTY_TEXT))
  }
}
