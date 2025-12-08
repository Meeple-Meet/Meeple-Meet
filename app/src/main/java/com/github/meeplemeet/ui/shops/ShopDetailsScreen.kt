package com.github.meeplemeet.ui.shops
// Github copilot was used for this file

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.ui.LocalFocusableFieldObserver
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.components.ActionBar
import com.github.meeplemeet.ui.components.AvailabilitySection
import com.github.meeplemeet.ui.components.CollapsibleSection
import com.github.meeplemeet.ui.components.ConfirmationDialog
import com.github.meeplemeet.ui.components.EditableGameItem
import com.github.meeplemeet.ui.components.GameStockPicker
import com.github.meeplemeet.ui.components.GamePickerActions
import com.github.meeplemeet.ui.components.ImageCarousel
import com.github.meeplemeet.ui.components.OpeningHoursEditor
import com.github.meeplemeet.ui.components.RequiredInfoSection
import com.github.meeplemeet.ui.components.ShopFormActions
import com.github.meeplemeet.ui.components.ShopFormTestTags
import com.github.meeplemeet.ui.components.ShopFormUi
import com.github.meeplemeet.ui.components.ShopUiDefaults
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.launch

/* ================================================================================================
 * Test tags
 * ================================================================================================ */
object EditShopScreenTestTags {
  const val SCAFFOLD = "edit_shop_scaffold"
  const val TOPBAR = "edit_shop_topbar"
  const val TITLE = "edit_shop_title"
  const val NAV_BACK = "edit_shop_nav_back"
  const val DELETE_BUTTON = "edit_shop_delete_button"
  const val DELETE_DIALOG = "edit_shop_delete_dialog"
  const val DELETE_CONFIRM = "edit_shop_delete_confirm"
  const val DELETE_CANCEL = "edit_shop_delete_cancel"
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
    val contentHPadding = ShopFormUi.Dim.contentHPadding
    val contentVPadding = ShopFormUi.Dim.contentVPadding
    val sectionSpace = ShopFormUi.Dim.sectionSpace
    val bottomSpacer = ShopFormUi.Dim.bottomSpacer
    val betweenControls = ShopFormUi.Dim.betweenControls
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

    const val DELETE_DIALOG_TITLE = "Delete Shop"
    const val DELETE_DIALOG_MESSAGE =
        "Are you sure you want to delete this shop? This action cannot be undone."
    const val DELETE_CONFIRM = "Delete"
    const val DELETE_CANCEL = "Cancel"
  }
}

/* ================================================================================================
 * Screen
 * ================================================================================================ */

/**
 * Composable function representing the Edit Shop screen.
 *
 * @param shop The shop to edit.
 * @param owner The account of the shop owner.
 * @param onBack Callback function to be invoked when the back navigation is triggered.
 * @param onSaved Callback function to be invoked when the shop is successfully saved.
 * @param onDelete Callback function to be invoked when the shop is deleted.
 * @param viewModel The ViewModel managing the state and logic for editing a shop.
 */
@Composable
fun ShopDetailsScreen(
    owner: Account,
    shop: Shop,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDelete: () -> Unit = {},
    online: Boolean,
    viewModel: EditShopViewModel = viewModel()
) {
  val gameUi by viewModel.gameUIState.collectAsState()
  val locationUi by viewModel.locationUIState.collectAsState()

  EditShopContent(
      shop = shop,
      onBack = onBack,
      onSaved = onSaved,
      onSave = {
          loadedShop,
          requester,
          name,
          email,
          phone,
          website,
          address,
          week,
          stock,
          photoCollectionUrl ->
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
              gameCollection = stock,
              photoCollectionUrl = photoCollectionUrl)
          null
        } catch (e: IllegalArgumentException) {
          e.message ?: EditShopUi.Strings.ERROR_VALIDATION
        } catch (_: Exception) {
          EditShopUi.Strings.ERROR_SAVE
        }
      },
      gameUi = gameUi,
      locationUi = locationUi,
      gameQuery = gameUi.gameQuery,
      gameSuggestions = gameUi.gameSuggestions,
      isSearching = gameUi.isSearching,
      onSetGameQuery = viewModel::setGameQuery,
      onSetGame = viewModel::setGame,
      viewModel = viewModel,
      online = online,
      owner = owner,
  )
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
    shop: Shop,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    online: Boolean,
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
            stock: List<Pair<Game, Int>>,
            photoCollectionUrl: List<String>) -> String?,
    gameUi: GameUIState,
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

  // Initialize state with loaded shop data or default values
  var photoCollectionUrl by rememberSaveable(shop) { mutableStateOf(shop.photoCollectionUrl) }
  var shopName by rememberSaveable(shop) { mutableStateOf(shop.name) }
  var email by rememberSaveable(shop) { mutableStateOf(shop.email) }
  var addressText by rememberSaveable(shop) { mutableStateOf(shop.address.name) }
  var phone by rememberSaveable(shop) { mutableStateOf(shop.phone) }
  var link by rememberSaveable(shop) { mutableStateOf(shop.website) }

  var week by remember(shop) { mutableStateOf(shop.openingHours) }

  var editingDay by remember { mutableStateOf<Int?>(null) }
  var showHoursDialog by remember { mutableStateOf(false) }

  var showGameDialog by remember { mutableStateOf(false) }
  var qty by rememberSaveable { mutableIntStateOf(1) }
  var stock by remember(shop) { mutableStateOf(shop.gameCollection) }

  var showDeleteDialog by remember { mutableStateOf(false) }

  val hasOpeningHours by remember(week) { derivedStateOf { week.any { it.hours.isNotEmpty() } } }
  val isValid by
      remember(shopName, email, locationUi.selectedLocation, hasOpeningHours) {
        derivedStateOf {
          shopName.isNotBlank() &&
              email.isNotBlank() &&
              locationUi.selectedLocation != null &&
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
    onBack()
  }

  val shopFormActions = object : ShopFormActions {
    override fun onNameChange(name: String) { shopName = name }
    override fun onEmailChange(newEmail: String) { email = newEmail }
    override fun onPhoneChange(newPhone: String) { phone = newPhone }
    override fun onWebsiteChange(website: String) { link = website }
    override fun onLocationChange(location: Location) { addressText = location.name }
  }

  val gamePickerActions = object : GamePickerActions {
    override fun onStockChange(newStock: List<Pair<Game, Int>>) { stock = newStock }
    override fun onQtyChange(newQty: Int) { qty = newQty }
    override fun onSetGameQuery(query: String) { onSetGameQuery(query) }
    override fun onSetGame(game: Game) { onSetGame(game) }
    override fun onDismiss() { showGameDialog = false }
  }

  var isInputFocused by remember { mutableStateOf(false) }
  var focusedFieldTokens by remember { mutableStateOf(emptySet<Any>()) }

  CompositionLocalProvider(
      LocalFocusableFieldObserver provides
          { token, focused ->
            focusedFieldTokens =
                if (focused) focusedFieldTokens + token else focusedFieldTokens - token
            isInputFocused = focusedFieldTokens.isNotEmpty()
          }) {
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
                        onClick = onBack,
                        modifier = Modifier.testTag(EditShopScreenTestTags.NAV_BACK)) {
                          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                  },
                  actions = {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag(EditShopScreenTestTags.DELETE_BUTTON)) {
                          Icon(Icons.Filled.Delete, contentDescription = "Delete shop")
                        }
                  },
                  modifier = Modifier.testTag(EditShopScreenTestTags.TOPBAR))
            },
            snackbarHost = {
              SnackbarHost(
                  snackbarHost, modifier = Modifier.testTag(EditShopScreenTestTags.SNACKBAR_HOST))
            },
            bottomBar = {
              val shouldHide = UiBehaviorConfig.hideBottomBarWhenInputFocused
              if (!(shouldHide && isInputFocused))
                  ActionBar(
                      onDiscard = { onDiscard() },
                      onPrimary = {
                        val addr = locationUi.selectedLocation ?: Location()
                        val err =
                            onSave(
                                shop,
                                shop.owner,
                                shopName,
                                email,
                                phone,
                                link,
                                addr,
                                week,
                                stock,
                                photoCollectionUrl)
                        if (err == null) onSaved()
                        else scope.launch { snackbarHost.showSnackbar(err) }
                      },
                      enabled = isValid,
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
                      ImageCarousel(
                          photoCollectionUrl = photoCollectionUrl,
                          maxNumberOfImages = IMAGE_COUNT,
                          onAdd = { path, index ->
                            photoCollectionUrl =
                                if (index < photoCollectionUrl.size &&
                                    photoCollectionUrl[index].isNotEmpty()) {
                                  photoCollectionUrl.mapIndexed { i, old ->
                                    if (i == index) path else old
                                  }
                                } else {
                                  photoCollectionUrl + path
                                }
                          },
                          onRemove = { url ->
                            photoCollectionUrl = photoCollectionUrl.filter { it != url }
                          },
                          editable = true)
                    }
                    item {
                      CollapsibleSection(
                          title = EditShopUi.Strings.SECTION_REQUIRED,
                          initiallyExpanded = true,
                          content = {
                            RequiredInfoSection(
                                shop = shop,
                                actions = shopFormActions,
                                viewModel = viewModel,
                                online = online,
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
      }

  OpeningHoursEditor(
      show = showHoursDialog,
      day = editingDay,
      week = week,
      onWeekChange = { week = it },
      onDismiss = { showHoursDialog = false })

//  GameStockPicker(
//      owner = owner,
//      shop = shop,
//      viewModel = viewModel,
//      gameUIState = gameUi,
//      state = gamePickerActions)

  ConfirmationDialog(
      show = showDeleteDialog,
      title = EditShopUi.Strings.DELETE_DIALOG_TITLE,
      message = EditShopUi.Strings.DELETE_DIALOG_MESSAGE,
      confirmText = EditShopUi.Strings.DELETE_CONFIRM,
      cancelText = EditShopUi.Strings.DELETE_CANCEL,
      onConfirm = {
        showDeleteDialog = false
        viewModel.deleteShop(shop, owner)
        onBack()
        onBack()
      },
      onDismiss = { showDeleteDialog = false },
      dialogTestTag = EditShopScreenTestTags.DELETE_DIALOG,
      confirmTestTag = EditShopScreenTestTags.DELETE_CONFIRM,
      cancelTestTag = EditShopScreenTestTags.DELETE_CANCEL)
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
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
        contentPadding = PaddingValues(bottom = Dimensions.Padding.extraLarge),
        modifier = Modifier.heightIn(max = Dimensions.ContainerSize.maxListHeight)) {
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
