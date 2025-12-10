package com.github.meeplemeet.ui.shops
// Github copilot was used for this file

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.ui.LocalFocusableFieldObserver
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.components.ActionBar
import com.github.meeplemeet.ui.components.ConfirmationDialog
import com.github.meeplemeet.ui.components.EditableImageCarousel
import com.github.meeplemeet.ui.components.EditableGameItem
import com.github.meeplemeet.ui.components.GameStockPicker
import com.github.meeplemeet.ui.components.ImageCarousel
import com.github.meeplemeet.ui.components.OpeningHoursEditor
import com.github.meeplemeet.ui.components.ShopFormTestTags
import com.github.meeplemeet.ui.components.ShopFormUi
import com.github.meeplemeet.ui.components.ShopUiDefaults
import com.github.meeplemeet.ui.theme.Dimensions

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
  }

  object Strings {
    const val SCREEN_TITLE = "Edit Shop"
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
  val context = LocalContext.current

  LaunchedEffect(shop) { viewModel.initialize(shop) }

  // Automatically initialize the selected location if not already set
  LaunchedEffect(shop.address) {
    if (locationUi.selectedLocation == null && shop.address != Location()) {
      viewModel.setLocation(shop.address)
    }
  }

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
              context = context,
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
    gameUi: GameUIState,
    locationUi: LocationUIState,
    viewModel: EditShopViewModel,
    owner: Account
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  // Initialize state with loaded shop data
  val state =
      rememberCreateShopFormState(
          initialShop = shop,
          onSetGameQuery = viewModel::setGameQuery,
          onSetGame = viewModel::setGame)
  // Initialize state with loaded shop data or default values
  var photoCollectionUrl by rememberSaveable(shop) { mutableStateOf(shop.photoCollectionUrl) }

  LaunchedEffect(shop.photoCollectionUrl) {
    photoCollectionUrl = shop.photoCollectionUrl ?: emptyList()
  }
  var showDeleteDialog by remember { mutableStateOf(false) }

  LaunchedEffect(locationUi.locationQuery) {
    val sel = locationUi.selectedLocation
    if (sel != null && locationUi.locationQuery != sel.name) {
      val typed = locationUi.locationQuery
      viewModel.clearLocationSearch()
      if (typed.isNotBlank()) viewModel.setLocationQuery(typed)
    }
  }

  val hasOpeningHours by
      remember(state.week) { derivedStateOf { state.week.any { it.hours.isNotEmpty() } } }
  val isValid by
      remember(state.shopName, state.email, locationUi.selectedLocation, hasOpeningHours) {
        derivedStateOf {
          state.shopName.isNotBlank() &&
              state.email.isNotBlank() &&
              locationUi.selectedLocation != null &&
              hasOpeningHours
        }
      }

  // Sync addressText with locationUi.locationQuery when typing
  LaunchedEffect(locationUi.locationQuery) {
    if (locationUi.locationQuery.isNotEmpty() && state.addressText != locationUi.locationQuery) {
      state.addressText = locationUi.locationQuery
    }
  }

  var isInputFocused by remember { mutableStateOf(false) }
  var focusedFieldTokens by remember { mutableStateOf(emptySet<Any>()) }

  var isSaving by remember { mutableStateOf(false) }

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
                  snackbarHostState,
                  modifier = Modifier.testTag(EditShopScreenTestTags.SNACKBAR_HOST))
            },
            bottomBar = {
              val shouldHide = UiBehaviorConfig.hideBottomBarWhenInputFocused
              if (!(shouldHide && isInputFocused))
                  ActionBar(
                      onDiscard = onBack,
                      onPrimary = {
                        viewModel.updateShop(
                            shop = shop,
                            requester = owner,
                            owner = owner,
                            name = state.shopName,
                            phone = state.phone,
                            email = state.email,
                            website = state.website,
                            address = locationUi.selectedLocation ?: Location(),
                            openingHours = state.week,
                            gameCollection = state.stock,
                            photoCollectionUrl = state.photoCollectionUrl)
                        onSaved()
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
                      if (online) {
                        EditableImageCarousel(
                            photoCollectionUrl = state.photoCollectionUrl,
                            spacesCount = IMAGE_COUNT,
                            setPhotoCollectionUrl = { state.photoCollectionUrl = it })
                      } else
                          ImageCarousel(
                              photoCollectionUrl = state.photoCollectionUrl,
                              maxNumberOfImages = IMAGE_COUNT,
                              editable = false)
                    }
                    item {
                      ShopInfoSection(
                          state = state,
                          viewModel = viewModel,
                          owner = owner,
                          online = online,
                          locationUi = locationUi)
                    }
                    item { ShopAvailabilitySection(state) }
                    item { ShopGamesSection(state, online, viewModel) }
                    item {
                      Spacer(
                          Modifier.height(EditShopUi.Dimensions.bottomSpacer)
                              .testTag(EditShopScreenTestTags.BOTTOM_SPACER))
                    }
                  }
            }
      }
      if (isSaving) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .clickable(enabled = true, onClick = {}),
            contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
      }
    }

  OpeningHoursEditor(
      show = state.showHoursDialog,
      day = state.editingDay,
      week = state.week,
      onWeekChange = { state.week = it },
      onDismiss = { state.showHoursDialog = false })

  GameStockPicker(
      owner = owner, shop = shop, viewModel = viewModel, gameUIState = gameUi, state = state)

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
