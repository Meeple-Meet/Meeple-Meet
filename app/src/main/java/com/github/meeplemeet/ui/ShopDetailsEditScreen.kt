package com.github.meeplemeet.ui
//Github copilot was used for this file
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.shared.Location
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.components.ActionBar
import com.github.meeplemeet.ui.components.DayRow
import com.github.meeplemeet.ui.components.EditableGameItem
import com.github.meeplemeet.ui.components.GameStockDialog
import com.github.meeplemeet.ui.components.LabeledField
import com.github.meeplemeet.ui.components.LocationSearchField
import com.github.meeplemeet.ui.components.OpeningHoursDialog
import com.github.meeplemeet.ui.components.ShopUiDefaults
import java.text.DateFormatSymbols
import java.time.LocalTime
import java.util.Locale
import kotlin.random.Random
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
private object EditShopUi {
    object Dimensions {
        val contentHPadding = 16.dp
        val contentVPadding = 8.dp
        val sectionSpace = 12.dp
        val bottomSpacer = 100.dp
        val betweenControls = 6.dp
    }

    object Strings {
        const val ScreenTitle = "Edit Shop"
        const val SectionRequired = "Required Info"
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

        const val PlaceholderLocation = "Search locations…"

        const val BtnAddGame = "Add game"
        const val EmptyGames = "No games selected yet."
        const val ErrorValidation = "Validation error"
        const val ErrorSave = "Failed to save shop"

        const val Collapse = "Collapse"
        const val Expand = "Expand"
    }

    val dayNames: List<String> by lazy {
        val weekdays = DateFormatSymbols().weekdays
        (0..6).map { idx -> weekdays.getOrNull(idx + 1) ?: "Day ${idx + 1}" }
    }
}

/* ================================================================================================
 * Helpers
 * ================================================================================================ */
private fun emptyWeek(): List<OpeningHours> =
    List(7) { day -> OpeningHours(day = day, hours = emptyList()) }

private fun mockLocationSuggestionsFrom(query: String, max: Int = 5): List<Location> {
    if (query.isBlank()) return emptyList()
    val rng = Random(query.hashCode())
    return List(max) { i ->
        val lat = rng.nextDouble(-90.0, 90.0)
        val lon = rng.nextDouble(-180.0, 180.0)
        Location(latitude = lat, longitude = lon, name = "$query #${i + 1}")
    }
}

// Local time helpers (renamed to avoid clashes with other files)
private fun LocalTime.toHhmm(): String = "%02d:%02d".format(hour, minute)

private fun String.tryParseTimeLocal(): LocalTime? =
    runCatching {
        val lower = lowercase(Locale.getDefault())
        if (lower.contains("am") || lower.contains("pm")) {
            LocalTime.parse(
                replace("am", " AM", ignoreCase = true)
                    .replace("pm", " PM", ignoreCase = true)
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .uppercase(Locale.getDefault()),
                ShopUiDefaults.TimeMagicNumbers.formatter())
        } else {
            val (h, mRest) = split(":")
            LocalTime.of(h.toInt(), mRest.take(2).toInt())
        }
    }.getOrNull()

private fun humanizeOpeningHours(hours: List<TimeSlot>): String =
    when {
        hours.isEmpty() -> "Closed"
        hours.size == 1 &&
                hours[0].open == ShopUiDefaults.TimeMagicNumbers.OPEN24_START &&
                hours[0].close == ShopUiDefaults.TimeMagicNumbers.OPEN24_END -> "Open 24 hours"
        else ->
            hours.joinToString("\n") { slot ->
                val s = slot.open?.let { it.tryParseTimeLocal()?.format(ShopUiDefaults.TimeMagicNumbers.formatter()) ?: it } ?: "-"
                val e = slot.close?.let { it.tryParseTimeLocal()?.format(ShopUiDefaults.TimeMagicNumbers.formatter()) ?: it } ?: "-"
                "$s - $e"
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
    shopId: String,
    owner: Account,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditShopViewModel
) {
    val ui by viewModel.gameUIState.collectAsState()
    val shop by viewModel.shop.collectAsState()

    // Load shop data when screen is first displayed
    LaunchedEffect(shopId) { viewModel.loadShop(shopId) }

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
                e.message ?: EditShopUi.Strings.ErrorValidation
            } catch (_: Exception) {
                EditShopUi.Strings.ErrorSave
            }
        },
        gameQuery = ui.gameQuery,
        gameSuggestions = ui.gameSuggestions,
        isSearching = ui.isSearching,
        onSetGameQuery = viewModel::setGameQuery,
        onSetGame = viewModel::setGame)
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
    gameQuery: String,
    gameSuggestions: List<Game>,
    isSearching: Boolean,
    onSetGameQuery: (String) -> Unit,
    onSetGame: (Game) -> Unit,
    initialStock: List<Pair<Game, Int>> = emptyList()
) {
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    fun onDiscard() {
        onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        EditShopUi.Strings.ScreenTitle,
                        modifier = Modifier.testTag(EditShopScreenTestTags.TITLE))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(EditShopScreenTestTags.NAV_BACK)) {
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
                    title = EditShopUi.Strings.SectionRequired,
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
                            })
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
                    title = EditShopUi.Strings.SectionAvailability,
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
                    title = EditShopUi.Strings.SectionGames,
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
                                EditShopUi.Strings.BtnAddGame,
                                modifier =
                                    Modifier.testTag(EditShopScreenTestTags.GAMES_ADD_LABEL))
                        }
                    },
                    content = {
                        GamesSection(
                            stock = stock,
                            onQuantityChange = { game, newQuantity ->
                                stock = stock.map { (g, qty) -> if (g.uid == game.uid) g to newQuantity else g to qty }
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
 * Sections
 * ================================================================================================ */

/**
 * Composable function representing the required information section of the Edit Shop screen.
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
    onPickLocation: (Location) -> Unit
) {
    Box(Modifier.testTag(EditShopScreenTestTags.FIELD_SHOP)) {
        LabeledField(
            label = EditShopUi.Strings.LabelShop,
            placeholder = EditShopUi.Strings.PlaceholderShop,
            value = shopName,
            onValueChange = onShopName)
    }
    Box(Modifier.testTag(EditShopScreenTestTags.FIELD_EMAIL)) {
        LabeledField(
            label = EditShopUi.Strings.LabelEmail,
            placeholder = EditShopUi.Strings.PlaceholderEmail,
            value = email,
            onValueChange = onEmail,
            keyboardType = KeyboardType.Email)
    }
    Box(Modifier.testTag(EditShopScreenTestTags.FIELD_PHONE)) {
        LabeledField(
            label = EditShopUi.Strings.LabelPhone,
            placeholder = EditShopUi.Strings.PlaceholderPhone,
            value = phone,
            onValueChange = onPhone,
            keyboardType = KeyboardType.Phone)
    }
    Box(Modifier.testTag(EditShopScreenTestTags.FIELD_LINK)) {
        LabeledField(
            label = EditShopUi.Strings.LabelLink,
            placeholder = EditShopUi.Strings.PlaceholderLink,
            value = link,
            onValueChange = onLink,
            keyboardType = KeyboardType.Uri)
    }
    Box(Modifier.testTag(EditShopScreenTestTags.FIELD_ADDRESS)) {
        val locationResults = remember(addressText) { mockLocationSuggestionsFrom(addressText) }
        LocationSearchField(
            query = addressText,
            onQueryChange = onAddressText,
            results = locationResults,
            onPick = onPickLocation,
            isLoading = false,
            placeholder = EditShopUi.Strings.PlaceholderLocation,
            modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Composable function representing the availability section of the Edit Shop screen.
 *
 * @param week List of opening hours for each day of the week.
 * @param onEdit Callback function to handle editing of opening hours for a specific day.
 */
@Composable
private fun AvailabilitySection(week: List<OpeningHours>, onEdit: (Int) -> Unit) {
    Column(Modifier.testTag(EditShopScreenTestTags.AVAILABILITY_LIST)) {
        week.forEach { oh ->
            val day = oh.day
            DayRow(
                dayName = EditShopUi.dayNames[day], value = humanizeOpeningHours(oh.hours), onEdit = { onEdit(day) })
            HorizontalDivider(
                modifier = Modifier.testTag(EditShopScreenTestTags.AVAILABILITY_DIVIDER_PREFIX + day))
        }
    }
    Spacer(Modifier.height(4.dp))
}

/**
 * Composable function representing the games section of the Edit Shop screen.
 *
 * @param stock List of pairs containing games and their quantities in stock.
 * @param onQuantityChange Callback function to handle updating quantity of a game in the stock list.
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
                        onDelete = onDelete
                    )
                }
            }
    } else {
        Text(
            EditShopUi.Strings.EmptyGames,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(EditShopScreenTestTags.GAMES_EMPTY_TEXT))
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
    Box(Modifier.testTag(EditShopScreenTestTags.OPENING_HOURS_DIALOG_WRAPPER)) {
        OpeningHoursDialog(
            initialSelectedDays = setOf(day),
            current = week[day],
            onDismiss = onDismiss,
            onSave = { selectedDays, closed, open24, intervals ->
                val encoded: List<TimeSlot> =
                    when {
                        closed -> emptyList()
                        open24 -> listOf(TimeSlot(ShopUiDefaults.TimeMagicNumbers.OPEN24_START, ShopUiDefaults.TimeMagicNumbers.OPEN24_END))
                        else -> intervals.map { TimeSlot(it.first.toHhmm(), it.second.toHhmm()) }
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
    Box(Modifier.testTag(EditShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER)) {
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
                        m.testTag(testTag + EditShopScreenTestTags.SECTION_HEADER_SUFFIX)
                    else m
                },
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.weight(1f).let { m ->
                        if (testTag != null)
                            m.testTag(testTag + EditShopScreenTestTags.SECTION_TITLE_SUFFIX)
                        else m
                    })
            header?.invoke(this)
            IconButton(
                onClick = { expanded = !expanded },
                modifier =
                    Modifier.let { m ->
                        if (testTag != null)
                            m.testTag(testTag + EditShopScreenTestTags.SECTION_TOGGLE_SUFFIX)
                        else m
                    }) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription =
                        if (expanded) EditShopUi.Strings.Collapse else EditShopUi.Strings.Expand,
                    modifier = Modifier.rotate(arrowRotation))
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier =
                Modifier.padding(bottom = 12.dp).let { m ->
                    if (testTag != null)
                        m.testTag(testTag + EditShopScreenTestTags.SECTION_DIVIDER_SUFFIX)
                    else m
                })

        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.padding(top = 0.dp).let { m ->
                    if (testTag != null)
                        m.testTag(testTag + EditShopScreenTestTags.SECTION_CONTENT_SUFFIX)
                    else m
                },
                content = content)
        }
    }
}
