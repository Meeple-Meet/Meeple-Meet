// This file contains shared components, test tags, and utilities used by both
// CreateShopScreen and ShopDetailsEditScreen
// Github copilot was used for this file

package com.github.meeplemeet.ui.components

import android.annotation.SuppressLint
import android.util.Patterns
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.meeplemeet.R
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
import com.github.meeplemeet.ui.shops.ShopScreenDefaults
import com.github.meeplemeet.ui.shops.ShopTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

interface ShopFormActions {
  fun onNameChange(name: String)
  fun onEmailChange(email: String)
  fun onPhoneChange(phone: String)
  fun onWebsiteChange(website: String)
  fun onLocationChange(location: Location)
}

interface GamePickerActions {
  fun onStockChange(stock: List<Pair<Game, Int>>)
  fun onQtyChange(qty: Int)
  fun onSetGameQuery(query: String)
  fun onSetGame(game: Game)
  fun onDismiss()
}

/* ================================================================================================
 * Shared Test Tags
 * ================================================================================================ */
object ShopFormTestTags {
  const val SECTION_HEADER_SUFFIX = "_header"
  const val SECTION_TITLE_SUFFIX = "_title"
  const val SECTION_TOGGLE_SUFFIX = "_toggle"
  const val SECTION_TOGGLE_ICON_SUFFIX = "_toggle_icon"
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

    const val SECTION_REQUIRED = "Required Info"
    const val SECTION_AVAILABILITY = "Availability"
    const val SECTION_GAMES = "Games in stock"
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

@Stable
class CreateShopFormState(
    initialStock: List<Pair<Game, Int>> = emptyList(),
    initialWeek: List<OpeningHours> = emptyWeek(),
    private val onSetGameQueryCallback: (String) -> Unit,
    private val onSetGameCallback: (Game) -> Unit
) : ShopFormActions, GamePickerActions {

    var shopName by mutableStateOf("")
    var email by mutableStateOf("")
    var phone by mutableStateOf("")
    var website by mutableStateOf("")
    var addressText by mutableStateOf("")

    var week by mutableStateOf(initialWeek)
    var stock by mutableStateOf(initialStock)
    var photoCollectionUrl by mutableStateOf(listOf<String>())

    // Dialog & UI states
    var showHoursDialog by mutableStateOf(false)
    var editingDay by mutableStateOf<Int?>(null)

    var showGameDialog by mutableStateOf(false)
    var editingGame by mutableStateOf<Game?>(null)
    var qty by mutableIntStateOf(1)

    // Derived state for validation
    private val hasOpeningHours by derivedStateOf { week.any { it.hours.isNotEmpty() } }

    fun isValid(selectedLocation: Location?): Boolean {
        return shopName.isNotBlank() &&
                isValidEmail(email) &&
                selectedLocation != null &&
                hasOpeningHours
    }

    // ---- ShopFormActions implementation ----
    override fun onNameChange(name: String) {
        shopName = name
    }

    override fun onEmailChange(email: String) {
        this.email = email
    }

    override fun onPhoneChange(phone: String) {
        this.phone = phone
    }

    override fun onWebsiteChange(website: String) {
        this.website = website
    }

    override fun onLocationChange(location: Location) {
        addressText = location.name
    }

    // ---- GamePickerActions implementation ----
    override fun onStockChange(stock: List<Pair<Game, Int>>) {
        this.stock = stock
    }

    override fun onQtyChange(qty: Int) {
        this.qty = qty
    }

    override fun onSetGameQuery(query: String) {
        onSetGameQueryCallback(query)
    }

    override fun onSetGame(game: Game) {
        onSetGameCallback(game)
    }

    override fun onDismiss() {
        showGameDialog = false
        editingGame = null
    }

    // ---- Helpers for photos ----
    fun addOrReplacePhoto(path: String, index: Int) {
        photoCollectionUrl =
            if (index < photoCollectionUrl.size && photoCollectionUrl[index].isNotEmpty()) {
                photoCollectionUrl.mapIndexed { i, old -> if (i == index) path else old }
            } else {
                photoCollectionUrl + path
            }
    }

    fun removePhoto(url: String) {
        photoCollectionUrl = photoCollectionUrl.filter { it != url }
    }

    // ---- Helpers for stock ----
    fun updateStockQuantity(game: Game, qty: Int) {
        stock = stock.map { if (it.first.uid == game.uid) it.first to qty else it }
    }

    fun removeFromStock(game: Game) {
        stock = stock.filterNot { it.first.uid == game.uid }
    }

    fun addOrUpdateStock(game: Game, qty: Int) {
        val existing = stock.find { it.first.uid == game.uid }
        stock = if (existing != null) {
            stock.map { if (it.first.uid == game.uid) it.first to qty else it }
        } else {
            stock + (game to qty)
        }
    }

    // ---- Helpers ----
    fun onDiscard(onBack: () -> Unit) {
        shopName = ""
        email = ""
        addressText = ""
        phone = ""
        website = ""
        week = emptyWeek()
        editingDay = null
        showHoursDialog = false
        showGameDialog = false
        qty = 1
        stock = emptyList()
        photoCollectionUrl = emptyList()
        onSetGameQueryCallback("")
        editingGame = null
        onBack()
    }
}

/* ================================================================================================
 * Shared Composable Components
 * ================================================================================================ */

/**
 * Composable function representing the required information section.
 *
 * @param shop The current shop state.
 * @param actions The actions to handle form updates.
 * @param viewModel The view model for location search.
 * @param owner The owner account.
 */
@Composable
fun RequiredInfoSection(
    shop: Shop,
    actions: ShopFormActions,
    online: Boolean,
    viewModel: ShopSearchViewModel,
    owner: Account
) {
  Box(Modifier.testTag(ShopFormTestTags.FIELD_SHOP).padding(bottom = Dimensions.Padding.small)) {
    LabeledField(
        label = ShopFormUi.Strings.SHOP_LABEL,
        placeholder = ShopFormUi.Strings.SHOP_PLACEHOLDER,
        value = shop.name,
        onValueChange = actions::onNameChange)
  }

    Box(Modifier.testTag(ShopFormTestTags.FIELD_ADDRESS).padding(bottom = Dimensions.Padding.small)) {
        ShopLocationSearchBar(
            account = owner,
            shop = shop,
            enabled = online,
            viewModel = viewModel,
            inputFieldTestTag = SessionTestTags.LOCATION_FIELD,
            dropdownItemTestTag = SessionTestTags.LOCATION_FIELD_ITEM)
    }

    Box(modifier = Modifier.fillMaxWidth().padding(bottom = Dimensions.Padding.small)) {
        Text(
            text = "Contact Info",
            style = MaterialTheme.typography.titleMedium)
    }

  Box(Modifier.testTag(ShopFormTestTags.FIELD_EMAIL).padding(bottom = Dimensions.Padding.small)) {
    LabeledField(
        label = ShopFormUi.Strings.EMAIL_LABEL,
        placeholder = ShopFormUi.Strings.EMAIL_PLACEHOLDER,
        leadingIcon =  { Icon(imageVector = Icons.Default.Email, tint = AppColors.neutral, contentDescription = null)},
        value = shop.email,
        onValueChange = actions::onEmailChange,
        keyboardType = KeyboardType.Email)
  }

  val showEmailError = shop.email.isNotEmpty() && !isValidEmail(shop.email)
  if (showEmailError) {
    Text(
        text = ShopFormUi.Strings.ERROR_EMAIL_MSG,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall)
  }

    Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Box(Modifier.testTag(ShopFormTestTags.FIELD_PHONE).weight(1f).padding(end = Dimensions.Padding.medium)) {
            LabeledField(
                label = ShopFormUi.Strings.PHONE_LABEL,
                placeholder = ShopFormUi.Strings.PHONE_PLACEHOLDER,
                value = shop.phone,
                leadingIcon = {Icon(imageVector = Icons.Default.Call, tint=AppColors.neutral, contentDescription = null)},
                onValueChange = actions::onPhoneChange,
                keyboardType = KeyboardType.Phone
            )
        }

        Box(Modifier.testTag(ShopFormTestTags.FIELD_LINK).weight(1f)) {
            LabeledField(
                label = ShopFormUi.Strings.LINK_LABEL,
                placeholder = ShopFormUi.Strings.LINK_PLACEHOLDER,
                value = shop.website,
                leadingIcon = {Icon(imageVector = Icons.Default.Link, tint = AppColors.neutral, contentDescription = null)},
                onValueChange = actions::onWebsiteChange,
                keyboardType = KeyboardType.Uri
            )
        }
    }
}

/**
 * Composable function representing the game stock picker dialog.
 *
 * @param show Boolean indicating whether to show the dialog.
 * @param stock List of pairs containing games and their quantities in stock.
 * @param state The actions to handle game picking.
 * @param gameQuery The current query string for searching games.
 * @param gameSuggestions List of game suggestions based on the current query.
 * @param isSearching Boolean indicating if a search operation is in progress.
 * @param qty The quantity of the picked game.
 */
@Composable
fun GameStockPicker(
    owner: Account,
    shop: Shop?,
    viewModel: ShopSearchViewModel,
    gameUIState: GameUIState,
    state: CreateShopFormState,
) {
  if (!state.showGameDialog) return

  val existing = remember(state.stock) { state.stock.map { it.first.uid }.toSet() }
  Box(Modifier.testTag(ShopFormTestTags.GAME_STOCK_DIALOG_WRAPPER)) {
    GameStockDialog(
        owner,
        shop,
        viewModel = viewModel,
        gameUIState = gameUIState,
        onQueryChange = state::onSetGameQuery,
        quantity = state.qty,
        onQuantityChange = state::onQtyChange,
        existingIds = existing,
        ignoreId = state.editingGame?.uid,
        onDismiss = {
          state.onDismiss()
          state.onQtyChange(1)
          state.onSetGameQuery("")
        },
        onSave = {
          gameUIState.fetchedGame?.let { g ->
            state.addOrUpdateStock(g, state.qty)
          }
          state.onQtyChange(1)
          state.onSetGameQuery("")
          state.onDismiss()
        })
  }
}

/**
 * Displays the fetched game's image
 * @param gameUIState game Ui state after the search
 */
@Composable
fun GameStockImage(gameUIState: GameUIState) {
    val game = gameUIState.fetchedGame
    if (game != null) {
        AsyncImage(
            model = game.imageURL,
            contentDescription = "Game image",
            modifier = Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 200.dp)
                .clip(RoundedCornerShape(8.dp)).padding(vertical = 6.dp),
            contentScale = ContentScale.Fit
        )
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameAddUI(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    max: Int = 100
) {
    Column(modifier.testTag(ShopComponentsTestTags.QTY_CONTAINER)) {

        Spacer(Modifier.height(Dimensions.Spacing.large))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
            modifier = Modifier.fillMaxWidth()
        ) {
            var sliderWidth by remember { mutableStateOf(0f) }
            var bubbleWidth by remember { mutableStateOf(0f) }

            val density = LocalDensity.current
            val thumbSize = 30.dp
            val thumbDiameterPx = with(density) { thumbSize.toPx() }
            val thumbRadiusPx = thumbDiameterPx / 2f

            Box(
                modifier = Modifier
                    .weight(1f)
                    .testTag(ShopComponentsTestTags.QTY_INPUT_FIELD)
                    .height(100.dp)
            ) {

                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            bubbleWidth = coords.size.width.toFloat()
                        }
                        .offset {

                            if (sliderWidth <= 0f || bubbleWidth <= 0f)
                                return@offset IntOffset.Zero

                            val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                            val trackWidth = sliderWidth - thumbDiameterPx
                            val thumbCenterX = thumbRadiusPx + (trackWidth * fraction)

                            val minX = thumbRadiusPx - bubbleWidth / 2f
                            val maxX = sliderWidth - thumbRadiusPx - bubbleWidth / 2f

                            val x = (thumbCenterX - bubbleWidth / 2f)
                                .coerceIn(minX, maxX)

                            IntOffset(x.toInt(), 0)
                        }

                        .align(Alignment.TopStart)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        Box(
                            modifier = Modifier
                                .shadow(6.dp, CircleShape)
                                .background(AppColors.focus, CircleShape)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = value.toString(),
                                color = AppColors.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Pointer triangle
                        val pathColor = AppColors.focus
                        Canvas(modifier = Modifier.size(width = 16.dp, height = 10.dp)) {
                            val path = Path().apply {
                                moveTo(size.width / 2f, size.height)
                                lineTo(0f, 0f)
                                lineTo(size.width, 0f)
                                close()
                            }
                            drawPath(path, pathColor)
                        }
                    }
                }

                Slider(
                    value = value.toFloat(),
                    onValueChange = { onValueChange(it.toInt().coerceIn(0, max)) },
                    valueRange = 0f..max.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned {
                            sliderWidth = it.size.width.toFloat()
                        },
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.focus,
                        activeTrackColor = AppColors.focus,
                        inactiveTrackColor = AppColors.textIconsFade
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(thumbSize)
                                .shadow(4.dp, CircleShape)
                                .background(AppColors.focus, CircleShape)
                        )
                    }
                )
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
    ignoreId: String? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
  val selectedGame = gameUIState.fetchedGame
  val isDuplicate =
      selectedGame?.uid?.let { it in existingIds && (ignoreId == null || it != ignoreId) }
          ?: false

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
        Column(Modifier.fillMaxWidth().testTag(ShopComponentsTestTags.GAME_DIALOG_BODY), horizontalAlignment = Alignment.CenterHorizontally) {
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
            GameStockImage(gameUIState = gameUIState)

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
    showButtons: Boolean = false,
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
                    showButtons = showButtons,
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
    onEdit: (Game) -> Unit = {},
    onDelete: (Game) -> Unit = {},
    showButtons: Boolean = false,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${game.uid}")
            .let {
                if (clickable) it.clickable { onClick(game) } else it
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.Padding.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // LEFT: Game image/icon
            Icon(
                Icons.Filled.VideogameAsset,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.IconSize.huge)
            )

            Spacer(Modifier.width(Dimensions.Spacing.medium))

            // CENTER: Game name
            Text(
                text = game.name,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            // RIGHT: Badge + Edit + Delete
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)
            ) {

                // Badge
                if (count > 0) {
                    val max = ShopUiDefaults.RangesMagicNumbers.qtyGameDialog.last
                    val label = if (count > max) "$max+" else count.toString()

                    Badge(
                        containerColor = MaterialTheme.colorScheme.inversePrimary
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = Dimensions.Spacing.small)
                        )
                    }
                }

                // Edit button
                if (showButtons) {
                    IconButton(
                        onClick = { onEdit(game) },
                        modifier = Modifier.testTag(
                            "${ShopComponentsTestTags.SHOP_GAME_EDIT}:${game.uid}"
                        )
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit ${game.name}"
                        )
                    }
                }

                // Delete button
                if (showButtons) {
                    IconButton(
                        onClick = { onDelete(game) },
                        modifier = Modifier.testTag(
                            "${ShopComponentsTestTags.SHOP_GAME_DELETE}:${game.uid}"
                        ),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove ${game.name} from list"
                        )
                    }
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

/**
 * A composable function that displays a game item as an image card with an optional stock badge
 *
 * @param game The [Game] object whose image and name are displayed
 * @param count The stock quantity for the game. When greater than zero, a stock badge is shown
 * @param modifier The [Modifier] to be applied to the root container of the game item
 * @param clickable A boolean indicating whether the game card is clickable
 * @param onClick A callback function that is invoked when the game card is clicked
 * @param imageHeight An optional fixed height for the image area
 */
@Composable
fun GameItemImage(
    game: Game,
    count: Int,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    clickable: Boolean = true,
    onClick: (Game) -> Unit = {},
    onEdit: (Game) -> Unit = {},
    onDelete: (Game) -> Unit = {},
    imageHeight: Dp? = null,
) {
    Box(
        modifier = modifier
            .testTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${game.uid}")
    ) {
        Column(
            modifier = Modifier
                .padding(top = ShopScreenDefaults.Stock.STOCK_BUBBLE_TOP_PADDING)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.background)
                .clickable(enabled = clickable) { onClick(game) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = game.imageURL,
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(ShopScreenDefaults.Game.GAME_IMG_RELATIVE_WIDTH)
                    .shadow(Dimensions.Elevation.high, MaterialTheme.shapes.medium, clip = true)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.background)
                    .let { if (imageHeight != null) it.height(imageHeight)
                    else it.aspectRatio(ShopScreenDefaults.Game.GAME_IMG_DEFAULT_ASPECT_RATIO) },
                placeholder = painterResource(R.drawable.ic_dice),
                error = painterResource(R.drawable.ic_dice)
            )

            Spacer(Modifier.height(Dimensions.Spacing.small))

            Text(
                text = game.name,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = ShopScreenDefaults.Game.GAME_NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${ShopTestTags.SHOP_GAME_NAME_PREFIX}${game.uid}")
            )
        }

        if (count > ShopScreenDefaults.Stock.NOT_SHOWING_STOCK_MIN_VALUE) {
            val label = if (count > ShopScreenDefaults.Stock.MAX_STOCK_SHOWED)
                "${ShopScreenDefaults.Stock.MAX_STOCK_SHOWED}+"
            else count.toString()

            Column(
                modifier = Modifier
                    .padding(top = 20.dp).align(Alignment.TopEnd),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("${ShopTestTags.SHOP_GAME_STOCK_PREFIX}${game.uid}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                if (editable) {
                    IconButton(
                        onClick = { onDelete(game) },
                        modifier = Modifier.offset(x = 12.dp).padding(0.dp) // Shift icons to the right
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = AppColors.textIcons
                        )
                    }

                    IconButton(
                        onClick = { onEdit(game) },
                        modifier = Modifier.offset(x = 12.dp, y = (-10).dp).padding(0.dp) // Shift icons to the right
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = AppColors.textIcons
                        )
                    }
                }
            }
        }
    }
}


/**
 * A composable function that displays a paged grid section of game image items with an optional
 * title and page indicators
 *
 * @param games The list of pairs of [Game] and stock count to display in the grid
 * @param modifier The [Modifier] to be applied to the section container
 * @param clickableGames A boolean indicating whether individual game cards are clickable
 * @param title The title text displayed above the grid (for example, "Discover Games")
 * @param onClick A callback function that is invoked when a game card is clicked
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameImageListSection(
    games: List<Pair<Game, Int>>,
    modifier: Modifier = Modifier,
    clickableGames: Boolean = false,
    title: String,
    editable: Boolean = false,
    onClick: (Game) -> Unit = {},
    onEdit: (Game) -> Unit = {},
    onDelete: (Game) -> Unit = {}
) {
    val clampedGames = remember(games) { games.shuffled().take(ShopScreenDefaults.Pager.MAX_GAMES) }
    if (clampedGames.isEmpty()) return

    val pages =
        remember(clampedGames) {
            clampedGames
                .chunked(ShopScreenDefaults.Pager.GAMES_PER_PAGE)
                .take(ShopScreenDefaults.Pager.MAX_PAGES)
        }
    val pageCount = pages.size

    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraSmall),
        modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gridWidth = maxWidth
            val imageHeight = gridWidth / ShopScreenDefaults.Pager.IMAGE_HEIGHT_CORRECTION
            val textAreaHeight = ShopScreenDefaults.Game.GAME_NAME_AREA_HEIGHT
            val rowHeight = imageHeight + textAreaHeight
            val gridHeight = rowHeight * ShopScreenDefaults.Pager.GAMES_PER_COLUMN

            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(gridHeight)
                        .testTag(ShopTestTags.SHOP_GAME_PAGER)) { pageIndex ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(ShopScreenDefaults.Pager.GAMES_PER_ROW),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize()) {
                    items(pages[pageIndex], key = { it.first.uid }) { (game, count) ->
                        GameItemImage(
                            game = game,
                            count = count,
                            clickable = clickableGames,
                            editable = editable,
                            onClick = onClick,
                            onDelete = onDelete,
                            onEdit = onEdit,
                            imageHeight = imageHeight,
                            modifier = Modifier.height(rowHeight)
                        )
                    }
                }
            }
        }

        if (pageCount > ShopScreenDefaults.Pager.MINIMAL_PAGE_COUNT) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimensions.Spacing.medium),
                horizontalArrangement = Arrangement.Center) {
                repeat(pageCount) { index ->
                    val selected = (index == pagerState.currentPage)
                    Box(
                        modifier =
                            Modifier.padding(horizontal = Dimensions.Padding.small)
                                .size(
                                    if (selected) ShopScreenDefaults.Pager.PAGER_SELECTED_BUBBLE_SIZE
                                    else ShopScreenDefaults.Pager.PAGER_UNSELECTED_BUBBLE_SIZE)
                                .clip(CircleShape)
                                .testTag("${ShopTestTags.SHOP_GAME_PAGER_INDICATOR_PREFIX}$index")
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline)
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } })
                }
            }
        }
    }
}
