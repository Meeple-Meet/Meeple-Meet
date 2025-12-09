// This file was initially done by hand and
// then improved and refactored using ChatGPT-5 Extend Thinking
// Docstrings were generated using copilot from Android studio
package com.github.meeplemeet.ui.shops

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.CreateShopViewModel
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.ui.LocalFocusableFieldObserver
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.components.ActionBar
import com.github.meeplemeet.ui.components.AvailabilitySection
import com.github.meeplemeet.ui.components.CollapsibleSection
import com.github.meeplemeet.ui.components.CreateShopFormState
import com.github.meeplemeet.ui.components.EditableGameItem
import com.github.meeplemeet.ui.components.EditableImageCarousel
import com.github.meeplemeet.ui.components.GameImageListSection
import com.github.meeplemeet.ui.components.GameItemImage
import com.github.meeplemeet.ui.components.GameStockPicker
import com.github.meeplemeet.ui.components.ImageCarousel
import com.github.meeplemeet.ui.components.OpeningHoursEditor
import com.github.meeplemeet.ui.components.RequiredInfoSection
import com.github.meeplemeet.ui.components.ShopFormTestTags
import com.github.meeplemeet.ui.components.ShopFormUi
import com.github.meeplemeet.ui.components.emptyWeek
import com.github.meeplemeet.ui.components.isValidEmail
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.theme.AppColors

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
    const val SECTION_TOGGLE_SUFFIX = ShopFormTestTags.SECTION_TOGGLE_SUFFIX
    const val SECTION_CONTENT_SUFFIX = ShopFormTestTags.SECTION_CONTENT_SUFFIX

    const val SECTION_REQUIRED = "section_required"

    // Reuse shared field tags
    const val FIELD_SHOP = ShopFormTestTags.FIELD_SHOP
    const val FIELD_EMAIL = ShopFormTestTags.FIELD_EMAIL
    const val FIELD_PHONE = ShopFormTestTags.FIELD_PHONE
    const val FIELD_LINK = ShopFormTestTags.FIELD_LINK

    const val SECTION_AVAILABILITY = "section_availability"

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
        val contentHPadding = ShopFormUi.Dim.contentHPadding
        val contentVPadding = ShopFormUi.Dim.contentVPadding
        val bottomSpacer = ShopFormUi.Dim.bottomSpacer
        val betweenControls = ShopFormUi.Dim.betweenControls
    }

    object Strings {
        const val REQUIREMENTS_SECTION = "Required Info"
        const val SECTION_AVAILABILITY = "Availability"
        const val SECTION_GAMES = "Game Vitrine"

        const val BTN_ADD_GAME = "Add game"
        const val EMPTY_GAMES = "No games selected yet."
        const val ERROR_VALIDATION = "Validation error"
        const val ERROR_CREATE = "Failed to create shop"
    }
}

const val IMAGE_COUNT = 10

// Callback type for shop creation, kept explicit but readable.
private typealias CreateShopHandler = suspend (
    name: String,
    email: String,
    address: Location,
    week: List<OpeningHours>,
    stock: List<Pair<Game, Int>>
) -> String

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
    online: Boolean,
    userLocation: Location?,
    viewModel: CreateShopViewModel = viewModel()
) {
    val gameUi by viewModel.gameUIState.collectAsState()
    val locationUi by viewModel.locationUIState.collectAsState()

    // Set default location when offline
    LaunchedEffect(online, userLocation) {
        if (!online && userLocation != null && locationUi.selectedLocation == null) {
            viewModel.setLocation(userLocation)
        }
    }

    AddShopContent(
        onBack = onBack,
        gameUi = gameUi,
        locationUi = locationUi,
        online = online,
        viewModel = viewModel,
        owner = owner
    )
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
 * @param gameUi The current game UI state.
 * @param locationUi The current location UI state.
 * @param onSetGameQuery Callback function to update the game search query.
 * @param onSetGame Callback function to set the selected game.
 * @param initialStock Initial list of games and their quantities in stock.
 * @param viewModel The ViewModel managing the state and logic for creating a shop.
 * @param owner The account of the shop owner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShopContent(
    onBack: () -> Unit,
    gameUi: GameUIState,
    locationUi: LocationUIState,
    online: Boolean,
    initialStock: List<Pair<Game, Int>> = emptyList(),
    viewModel: CreateShopViewModel,
    owner: Account
) {
    val state =
        rememberCreateShopFormState(
            initialStock = initialStock,
            onSetGameQuery = viewModel::setGameQuery,
            onSetGame = viewModel::setGame
        )
    val hasOpeningHours by remember(state.week) { derivedStateOf { state.week.any { it.hours.isNotEmpty() } } }

    val snackbarHostState = remember { SnackbarHostState() }

    // Sync addressText with locationUi.locationQuery when typing
    LaunchedEffect(locationUi.locationQuery) {
        val sel = locationUi.selectedLocation
        if (sel != null && locationUi.locationQuery != sel.name) {
            val typed = locationUi.locationQuery
            viewModel.clearLocationSearch()
            if (typed.isNotBlank()) viewModel.setLocationQuery(typed)
        }
    }

    var isInputFocused by remember { mutableStateOf(false) }
    var focusedFieldTokens by remember { mutableStateOf(emptySet<Any>()) }

    CompositionLocalProvider(
        LocalFocusableFieldObserver provides { token, focused ->
            focusedFieldTokens =
                if (focused) focusedFieldTokens + token else focusedFieldTokens - token
            isInputFocused = focusedFieldTokens.isNotEmpty()
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            MeepleMeetScreen.CreateShop.title,
                            modifier = Modifier.testTag(CreateShopScreenTestTags.TITLE)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag(CreateShopScreenTestTags.NAV_BACK)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    modifier = Modifier.testTag(CreateShopScreenTestTags.TOPBAR)
                )
            },
            snackbarHost = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.testTag(CreateShopScreenTestTags.SNACKBAR_HOST)
                )
            },
            bottomBar = {
                val shouldHide = UiBehaviorConfig.hideBottomBarWhenInputFocused

                val isValid by
                remember(state.shopName, state.email, locationUi.selectedLocation, hasOpeningHours) {
                    derivedStateOf {
                        state.shopName.isNotBlank() &&
                                isValidEmail(state.email) &&
                                locationUi.selectedLocation != null &&
                                hasOpeningHours
                    }
                }
                if (!(shouldHide && isInputFocused)) {
                    ActionBar(
                        onDiscard = { state.onDiscard(onBack) },
                        onPrimary = {
                                    viewModel.createShop(
                                        owner = owner,
                                        name = state.shopName,
                                        email = state.email,
                                        phone = state.phone,
                                        website = state.website,
                                        address = locationUi.selectedLocation ?: Location(),
                                        openingHours = state.week,
                                        gameCollection = state.stock,
                                        photoCollectionUrl = state.photoCollectionUrl
                                    )
                                    onBack()
                        },
                        enabled = isValid
                    )
                }
            },
            modifier = Modifier.testTag(CreateShopScreenTestTags.SCAFFOLD)
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .testTag(CreateShopScreenTestTags.LIST),
                contentPadding =
                    PaddingValues(
                        horizontal = AddShopUi.Dimensions.contentHPadding,
                        vertical = AddShopUi.Dimensions.contentVPadding
                    )
            ) {
                item {     if (online) {
                    EditableImageCarousel(
                        photoCollectionUrl = state.photoCollectionUrl,
                        spacesCount = IMAGE_COUNT,
                        setPhotoCollectionUrl = { state.photoCollectionUrl = it })
                } else
                    ImageCarousel(
                        photoCollectionUrl = state.photoCollectionUrl,
                        maxNumberOfImages = IMAGE_COUNT,
                        editable = false)}
                item {
                    ShopInfoSection(
                        state = state,
                        viewModel = viewModel,
                        owner = owner,
                        online = online,
                        locationUi = locationUi
                    )
                }
                item { ShopAvailabilitySection(state) }
                item { ShopGamesSection(state, online) }
                item {
                    Spacer(
                        Modifier
                            .height(AddShopUi.Dimensions.bottomSpacer)
                            .testTag(CreateShopScreenTestTags.BOTTOM_SPACER)
                    )
                }
            }
        }
    }

    OpeningHoursEditor(
        show = state.showHoursDialog,
        day = state.editingDay,
        week = state.week,
        onWeekChange = { state.week = it },
        onDismiss = { state.showHoursDialog = false }
    )

    GameStockPicker(
        owner = owner,
        shop = null,
        viewModel = viewModel,
        gameUIState = gameUi,
        state = state)
}

/* ================================================================================================
 * Sections
 * ================================================================================================ */

@Composable
private fun ShopInfoSection(
    state: CreateShopFormState,
    viewModel: CreateShopViewModel,
    online: Boolean,
    owner: Account,
    locationUi: LocationUIState
) {
    // This is a purely UI-level draft, not persisted and only used to feed the form component.
    val draftShop =
        Shop(
            id = "",
            owner = owner,
            name = state.shopName,
            phone = state.phone,
            email = state.email,
            website = state.website,
            address = locationUi.selectedLocation ?: Location(),
            openingHours = state.week,
            gameCollection = state.stock,
            photoCollectionUrl = state.photoCollectionUrl
        )

    CollapsibleSection(
        title = AddShopUi.Strings.REQUIREMENTS_SECTION,
        initiallyExpanded = true,
        content = {
            RequiredInfoSection(
                shop = draftShop,
                actions = state,
                viewModel = viewModel,
                online = online,
                owner = owner
            )
        },
        testTag = CreateShopScreenTestTags.SECTION_REQUIRED
    )
}

@Composable
private fun ShopAvailabilitySection(state: CreateShopFormState) {
    CollapsibleSection(
        title = AddShopUi.Strings.SECTION_AVAILABILITY,
        initiallyExpanded = false,
        content = {
            AvailabilitySection(
                week = state.week,
                onEdit = { day ->
                    state.editingDay = day
                    state.showHoursDialog = true
                }
            )
        },
        testTag = CreateShopScreenTestTags.SECTION_AVAILABILITY
    )
}

@Composable
private fun ShopGamesSection(state: CreateShopFormState, online: Boolean) {
    CollapsibleSection(
        title = AddShopUi.Strings.SECTION_GAMES,
        initiallyExpanded = state.stock.isEmpty(),
        content = {
            if (online) {
            Button(
                shape = RoundedCornerShape(4.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AppColors.secondary,
                        disabledContainerColor = AppColors.secondary,
                        contentColor = AppColors.focus,
                        disabledContentColor = AppColors.focus
                    ),
                onClick = {
                    state.onSetGameQuery("")
                    state.showGameDialog = true
                },
                modifier = Modifier
                    .testTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON)
                    .fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(AddShopUi.Dimensions.betweenControls))
                Text(
                    AddShopUi.Strings.BTN_ADD_GAME,
                    modifier = Modifier.testTag(CreateShopScreenTestTags.GAMES_ADD_LABEL)
                )
            } }

//            GamesSection(
//                stock = state.stock,
//                onQuantityChange = { game, qty -> state.updateStockQuantity(game, qty) },
//                onDelete = { game -> state.removeFromStock(game) }
//            )

            GameImageListSection(
                games = state.stock,
                clickableGames = true,
                editable = true,
                title = ""
            )
        },
        testTag = CreateShopScreenTestTags.SECTION_GAMES
    )
}

/**
 * Composable function representing the games section of the Add Shop screen.
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
            verticalArrangement = Arrangement.spacedBy(AddShopUi.Dimensions.betweenControls),
            contentPadding = PaddingValues(bottom = AddShopUi.Dimensions.contentVPadding),
            modifier = Modifier.heightIn(max = 500.dp)
        ) {
            items(items = stock, key = { it.first.uid }) { (game, count) ->
                GameItemImage(
                    game = game,
                    count = count,
                    editable = true,
                    clickable = false,
                )
            }
        }
    } else {
        Text(
            AddShopUi.Strings.EMPTY_GAMES,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(CreateShopScreenTestTags.GAMES_EMPTY_TEXT)
        )
    }
}

/* ================================================================================================
 * State Holder
 * ================================================================================================ */

@Composable
fun rememberCreateShopFormState(
    initialStock: List<Pair<Game, Int>> = emptyList(),
    onSetGameQuery: (String) -> Unit,
    onSetGame: (Game) -> Unit
): CreateShopFormState {
    return remember {
        CreateShopFormState(
            initialStock = initialStock,
            initialWeek = emptyWeek(),
            onSetGameQueryCallback = onSetGameQuery,
            onSetGameCallback = onSetGame
        )
    }
}
