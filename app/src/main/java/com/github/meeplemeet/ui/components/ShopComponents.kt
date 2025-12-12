// This file contains shared components, test tags, and utilities used by both
// CreateShopScreen and ShopDetailsEditScreen
// Github copilot was used for this file

package com.github.meeplemeet.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
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
import com.github.meeplemeet.model.shared.game.GameSearchResult
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.GameItem
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopSearchViewModel
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.shops.ShopScreenDefaults
import com.github.meeplemeet.ui.shops.ShopTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import java.text.DateFormatSymbols
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface ShopFormActions {
  fun onNameChange(name: String)

  fun onEmailChange(email: String)

  fun onPhoneChange(phone: String)

  fun onWebsiteChange(website: String)

  fun onLocationChange(location: Location)
}

interface GamePickerActions {
  fun onStockChange(stock: List<GameItem>)

  fun onQtyChange(qty: Int)

  fun onSetGameQuery(query: String)

  fun onSetGame(game: GameSearchResult)

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

  const val OPENING_HOURS_DIALOG_WRAPPER = "opening_hours_dialog_wrapper"
  const val GAME_STOCK_DIALOG_WRAPPER = "game_stock_dialog_wrapper"

  fun gameTestTag(gameUid: String): String {
    return "${ShopComponentsTestTags.SHOP_GAME_PREFIX}${gameUid}"
  }
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
    const val thumbSize = 30
    const val bubbleSize = 100
    const val imageSize = 200
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

@Stable
class CreateShopFormState(
    initialStock: List<GameItem> = emptyList(),
    initialWeek: List<OpeningHours> = emptyWeek(),
    initialShop: Shop? = null,
    private val onSetGameQueryCallback: (String) -> Unit,
    private val onSetGameCallback: (GameSearchResult) -> Unit
) : ShopFormActions, GamePickerActions {

  var shopName by mutableStateOf(initialShop?.name ?: "")
  var email by mutableStateOf(initialShop?.email ?: "")
  var phone by mutableStateOf(initialShop?.phone ?: "")
  var website by mutableStateOf(initialShop?.website ?: "")
  var addressText by mutableStateOf(initialShop?.address?.name ?: "")

  var week by mutableStateOf(initialShop?.openingHours ?: initialWeek)
  var stock by mutableStateOf(initialShop?.gameCollection ?: initialStock)
  var photoCollectionUrl by mutableStateOf(initialShop?.photoCollectionUrl ?: listOf())

  // Dialog & UI states
  var showHoursDialog by mutableStateOf(false)
  var editingDay by mutableStateOf<Int?>(null)

  var showGameDialog by mutableStateOf(false)
  var overwriteStock by mutableStateOf(true)
  var editingGame by mutableStateOf<GameSearchResult?>(null)
  var qty by mutableIntStateOf(1)

  // Derived state for validation
  private val hasOpeningHours by derivedStateOf { week.any { it.hours.isNotEmpty() } }

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
  override fun onStockChange(stock: List<GameItem>) {
    this.stock = stock
  }

  override fun onQtyChange(qty: Int) {
    this.qty = qty
  }

  override fun onSetGameQuery(query: String) {
    onSetGameQueryCallback(query)
  }

  override fun onSetGame(game: GameSearchResult) {
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
  fun updateStockQuantity(game: GameItem, qty: Int) {
    stock = stock.map { if (it.gameId == game.gameId) it.copy(quantity = qty) else it }
  }

  fun removeFromStock(game: GameItem) {
    stock = stock.filterNot { it.gameId == game.gameId }
  }

  fun addOrUpdateStock(game: GameItem, qty: Int) {
    val existing = stock.find { it.gameId == game.gameId }
    stock =
        if (existing != null) {
          stock.map { if (it.gameId == game.gameId) it.copy(quantity = qty) else it }
        } else {
          stock + GameItem(game.gameId, game.gameName, qty)
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
  Box(Modifier.padding(bottom = Dimensions.Padding.small)) {
    LabeledField(
        label = ShopFormUi.Strings.SHOP_LABEL,
        placeholder = ShopFormUi.Strings.SHOP_PLACEHOLDER,
        value = shop.name,
        onValueChange = { if (it.length <= 32) actions.onNameChange(it) },
        modifier = Modifier.testTag(ShopFormTestTags.FIELD_SHOP))
  }

  Box(
      Modifier.testTag(ShopFormTestTags.FIELD_ADDRESS)
          .padding(bottom = Dimensions.Padding.medium)) {
        ShopLocationSearchBar(
            account = owner,
            shop = shop,
            enabled = online,
            viewModel = viewModel,
            inputFieldTestTag = SessionComponentsTestTags.LOCATION_FIELD,
            dropdownItemTestTag = SessionComponentsTestTags.LOCATION_FIELD_ITEM)
      }

  Box(modifier = Modifier.fillMaxWidth().padding(bottom = Dimensions.Padding.small)) {
    Text(text = "Contact Info", style = MaterialTheme.typography.titleMedium)
  }

  Box(Modifier.padding(bottom = Dimensions.Padding.small)) {
    LabeledField(
        label = ShopFormUi.Strings.EMAIL_LABEL,
        placeholder = ShopFormUi.Strings.EMAIL_PLACEHOLDER,
        leadingIcon = {
          Icon(
              imageVector = Icons.Default.Email,
              tint = AppColors.neutral,
              contentDescription = null)
        },
        value = shop.email,
        onValueChange = { if (it.length <= 60) actions.onEmailChange(it) },
        keyboardType = KeyboardType.Email,
        modifier = Modifier.testTag(ShopFormTestTags.FIELD_EMAIL))
  }

  val showEmailError = shop.email.isNotEmpty() && !isValidEmail(shop.email)
  if (showEmailError) {
    Text(
        text = ShopFormUi.Strings.ERROR_EMAIL_MSG,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall)
  }

  Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
    Box(Modifier.weight(1f).padding(end = Dimensions.Padding.medium)) {
      LabeledField(
          label = ShopFormUi.Strings.PHONE_LABEL,
          placeholder = ShopFormUi.Strings.PHONE_PLACEHOLDER,
          value = shop.phone,
          leadingIcon = {
            Icon(
                imageVector = Icons.Default.Call,
                tint = AppColors.neutral,
                contentDescription = null)
          },
          onValueChange = { if (it.length <= 16) actions.onPhoneChange(it) },
          keyboardType = KeyboardType.Phone,
          modifier = Modifier.testTag(ShopFormTestTags.FIELD_PHONE))
    }

    Box(Modifier.weight(1f)) {
      LabeledField(
          label = ShopFormUi.Strings.LINK_LABEL,
          placeholder = ShopFormUi.Strings.LINK_PLACEHOLDER,
          value = shop.website,
          leadingIcon = {
            Icon(
                imageVector = Icons.Default.Link,
                tint = AppColors.neutral,
                contentDescription = null)
          },
          onValueChange = { if (it.length <= 50) actions.onWebsiteChange(it) },
          modifier = Modifier.testTag(ShopFormTestTags.FIELD_LINK),
          keyboardType = KeyboardType.Uri)
    }
  }
}

/**
 * Composable function representing the game stock picker dialog.
 *
 * @param owner Current user
 * @param shop Shop to fetch data from
 * @param viewModel VM used by this screen
 * @param gameUIState Ui state of the game related components
 * @param state Ui state
 */
@Composable
fun GameStockPicker(
    owner: Account,
    shop: Shop?,
    viewModel: ShopSearchViewModel,
    gameUIState: GameUIState,
    state: CreateShopFormState
) {
  if (!state.showGameDialog) return

  val existing = remember(state.stock) { state.stock.map { it.gameId }.toSet() }
  val isEditMode = state.overwriteStock && state.editingGame != null

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
        ignoreId = state.editingGame?.id,
        isEditMode = isEditMode,
        onDismiss = {
          state.onDismiss()
          state.onQtyChange(1)
          state.onSetGameQuery("")
        },
        onSave = {
          if (isEditMode) {
            state.editingGame?.let { editGame ->
              val gameItem = state.stock.find { it.gameId == editGame.id }
              if (gameItem != null) {
                state.updateStockQuantity(gameItem, state.qty)
              }
            }
          } else {
            gameUIState.selectedGameSearchResult?.let { g ->
              state.addOrUpdateStock(GameItem(g.id, g.name), state.qty)
            }
          }
          state.onQtyChange(1)
          state.onSetGameQuery("")
          state.onDismiss()
        })
  }
}

/**
 * Displays the fetched game's image
 *
 * @param gameUIState game Ui state after the search
 */
@Composable
fun GameStockImage(gameUIState: GameUIState) {
  val game = gameUIState.fetchedGame
  if (game != null) {
    AsyncImage(
        model = game.imageURL,
        contentDescription = "Game image",
        modifier =
            Modifier.sizeIn(
                    maxWidth = ShopFormUi.Dim.imageSize.dp, maxHeight = ShopFormUi.Dim.imageSize.dp)
                .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                .padding(vertical = Dimensions.Padding.medium),
        contentScale = ContentScale.Fit)
  }
}

/**
 * A composable function that displays a quantity input with +/- buttons and a label.
 *
 * @param value The current quantity value.
 * @param onValueChange A callback function that is invoked when the quantity value changes.
 * @param modifier The modifier to be applied to the quantity input.
 * @param max The max value the slider can reach
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
        modifier = Modifier.fillMaxWidth()) {
          var sliderWidth by remember { mutableStateOf(0f) }
          var bubbleWidth by remember { mutableStateOf(0f) }

          val density = LocalDensity.current
          val thumbDiameterPx = with(density) { ShopFormUi.Dim.thumbSize.dp.toPx() }
          val thumbRadiusPx = thumbDiameterPx / 2f

          Box(
              modifier =
                  Modifier.weight(1f)
                      .testTag(ShopComponentsTestTags.QTY_INPUT_FIELD)
                      .height(ShopFormUi.Dim.bubbleSize.dp)) {
                Box(
                    modifier =
                        Modifier.onGloballyPositioned { coords ->
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

                              val x = (thumbCenterX - bubbleWidth / 2f).coerceIn(minX, maxX)

                              IntOffset(x.toInt(), 0)
                            }
                            .align(Alignment.TopStart)) {
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier =
                                Modifier.shadow(Dimensions.Elevation.extraHigh, CircleShape)
                                    .background(AppColors.focus, CircleShape)
                                    .padding(Dimensions.Padding.extraMedium)) {
                              Text(
                                  text = value.toString(),
                                  color = AppColors.primary,
                                  style = MaterialTheme.typography.bodyMedium)
                            }

                        // Pointer triangle
                        val pathColor = AppColors.focus
                        Canvas(
                            modifier =
                                Modifier.size(
                                    width = Dimensions.Padding.extraLarge,
                                    height = Dimensions.Padding.extraMedium)) {
                              val path =
                                  Path().apply {
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
                    modifier =
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter).onGloballyPositioned {
                          sliderWidth = it.size.width.toFloat()
                        },
                    colors =
                        SliderDefaults.colors(
                            thumbColor = AppColors.focus,
                            activeTrackColor = AppColors.focus,
                            inactiveTrackColor = AppColors.textIconsFade),
                    thumb = {
                      Box(
                          modifier =
                              Modifier.size(ShopFormUi.Dim.thumbSize.dp)
                                  .shadow(Dimensions.Elevation.high, CircleShape)
                                  .background(AppColors.focus, CircleShape))
                    })
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
 * @param owner current user
 * @param shop Current shop we're creating
 * @param viewModel VM used by this screen
 * @param gameUIState Current ui state of the game
 * @param onQueryChange A callback function that is invoked when the search query changes.
 * @param quantity The current quantity value.
 * @param onQuantityChange A callback function that is invoked when the quantity value changes.
 * @param existingIds A set of existing game IDs to prevent duplicates.
 * @param ignoreId used to remove duplicate warning messages when editing the game item
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
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
  val selectedGame = gameUIState.selectedGameSearchResult
  val isDuplicate =
      selectedGame?.id?.let { it in existingIds && (ignoreId == null || it != ignoreId) } ?: false

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
                  if (isEditMode) ShopUiDefaults.StringsMagicNumbers.GAME_DIALOG_EDIT_TITLE
                  else ShopUiDefaults.StringsMagicNumbers.GAME_DIALOG_TITLE,
                  style = MaterialTheme.typography.headlineSmall)
            }
      },
      text = {
        Column(
            Modifier.fillMaxWidth().testTag(ShopComponentsTestTags.GAME_DIALOG_BODY),
            horizontalAlignment = Alignment.CenterHorizontally) {
              ShopGameSearchBar(
                  owner,
                  shop,
                  viewModel,
                  gameUIState.selectedGameSearchResult,
                  existingIds,
                  enabled = !isEditMode,
                  inputFieldTestTag = ShopComponentsTestTags.GAME_SEARCH_FIELD,
                  dropdownItemTestTag = ShopComponentsTestTags.GAME_SEARCH_ITEM)

              if (isEditMode) {
                Spacer(Modifier.height(Dimensions.Padding.small))
                Text(
                    ShopUiDefaults.StringsMagicNumbers.quantityUpdateDialog(selectedGame?.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textIconsFade,
                    textAlign = TextAlign.Center)
              }

              if (isDuplicate && !isEditMode) {
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
            enabled =
                (isEditMode || (gameUIState.selectedGameSearchResult != null && !isDuplicate)) &&
                    quantity > 0,
            modifier = Modifier.testTag(ShopComponentsTestTags.GAME_DIALOG_SAVE)) {
              Text(ShopUiDefaults.StringsMagicNumbers.BTN_SAVE)
            }
      })
}

/* =============================================================================
 * Games: grid + item (with optional delete)
 * ============================================================================= */

/* =============================================================================
 * Games: editable item helper
 * ============================================================================= */

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
    game: GameItem,
    count: Int,
    modifier: Modifier = Modifier,
    online: Boolean,
    editable: Boolean = false,
    clickable: Boolean = true,
    onClick: (GameItem) -> Unit = {},
    onEdit: (GameItem) -> Unit = {},
    onDelete: (GameItem) -> Unit = {},
    imageHeight: Dp? = null,
    fetchedGames: Map<String, Game> = emptyMap()
) {
  Log.d("checkpoint testTag", ShopFormTestTags.gameTestTag(game.gameId))

  val fetchedGame = fetchedGames[game.gameId]
  val imageUrl = fetchedGame?.imageURL ?: ""

  Box(modifier = modifier.testTag(ShopFormTestTags.gameTestTag(game.gameId))) {
    Column(
        modifier =
            Modifier.padding(top = ShopScreenDefaults.Stock.STOCK_BUBBLE_TOP_PADDING)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.background)
                .clickable(enabled = clickable) { onClick(game) },
        horizontalAlignment = Alignment.CenterHorizontally) {
          AsyncImage(
              model = imageUrl,
              contentDescription = game.gameName,
              contentScale = ContentScale.Crop,
              modifier =
                  Modifier.fillMaxWidth(ShopScreenDefaults.Game.GAME_IMG_RELATIVE_WIDTH)
                      .shadow(Dimensions.Elevation.high, MaterialTheme.shapes.medium, clip = true)
                      .clip(MaterialTheme.shapes.medium)
                      .background(MaterialTheme.colorScheme.background)
                      .let {
                        if (imageHeight != null) it.height(imageHeight)
                        else it.aspectRatio(ShopScreenDefaults.Game.GAME_IMG_DEFAULT_ASPECT_RATIO)
                      },
              placeholder = painterResource(R.drawable.ic_dice),
              error = painterResource(R.drawable.ic_dice))

          Spacer(Modifier.height(Dimensions.Spacing.small))

          Text(
              text = game.gameName,
              style = MaterialTheme.typography.bodySmall,
              textAlign = TextAlign.Center,
              maxLines = ShopScreenDefaults.Game.GAME_NAME_MAX_LINES,
              overflow = TextOverflow.Ellipsis,
              modifier =
                  Modifier.fillMaxWidth()
                      .testTag("${ShopTestTags.SHOP_GAME_NAME_PREFIX}${game.gameId}"))
        }

    if (count > ShopScreenDefaults.Stock.NOT_SHOWING_STOCK_MIN_VALUE) {
      val label =
          if (count > ShopScreenDefaults.Stock.MAX_STOCK_SHOWED)
              "${ShopScreenDefaults.Stock.MAX_STOCK_SHOWED}+"
          else count.toString()

      Column(
          modifier = Modifier.padding(top = Dimensions.Padding.xLarge).align(Alignment.TopEnd),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.SpaceEvenly) {
            Box(
                modifier =
                    Modifier.size(Dimensions.Padding.xLarge)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("${ShopTestTags.SHOP_GAME_STOCK_PREFIX}${game.gameId}"),
                contentAlignment = Alignment.Center) {
                  Text(
                      text = label,
                      style = MaterialTheme.typography.bodySmall,
                      fontSize = Dimensions.TextSize.small,
                      color = MaterialTheme.colorScheme.onPrimary)
                }

            if (editable && online) {
              IconButton(
                  onClick = { onDelete(game) },
                  modifier =
                      Modifier.offset(x = Dimensions.Padding.large)
                          .testTag("${ShopComponentsTestTags.SHOP_GAME_DELETE}:${game.gameId}")) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = AppColors.textIcons)
                  }

              IconButton(
                  onClick = {
                    onEdit(game)
                    Log.d("checkpoint testTag", ShopFormTestTags.gameTestTag(game.gameId))
                  },
                  modifier =
                      Modifier.offset(
                              x = Dimensions.Padding.large, y = -Dimensions.Padding.extraMedium)
                          .testTag("${ShopComponentsTestTags.SHOP_GAME_EDIT}:${game.gameId}")) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = AppColors.textIcons)
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
 * @param editable Used to distinguish between view and edit mode
 * @param onClick A callback function that is invoked when a game card is clicked
 * @param onEdit A callback function that is invoked when the edit button is clicked
 * @param onDelete A callback function that is invoked when the delete button is clicked
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameImageListSection(
    games: List<GameItem>,
    modifier: Modifier = Modifier,
    clickableGames: Boolean = false,
    title: String,
    editable: Boolean = false,
    online: Boolean,
    onClick: (GameItem) -> Unit = {},
    onEdit: (GameItem) -> Unit = {},
    onDelete: (GameItem) -> Unit = {},
    fetchedGames: Map<String, Game> = emptyMap(),
    onPageChanged: (Int) -> Unit = {},
    periodicFetch: Boolean = false
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

  if (periodicFetch) {
    LaunchedEffect(pagerState.currentPage) {
      // Initial fetch
      onPageChanged(pagerState.currentPage)

      // Periodic fetch
      while (true) {
        delay(ShopScreenDefaults.Pager.PERIODIC_FETCH_INTERVAL_MS)
        // Re-fetch la page courante
        onPageChanged(pagerState.currentPage)
      }
    }
  } else {
    // Boot-up fetch (only one)
    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage) }
  }

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
                      items(pages[pageIndex], key = { it.gameId }) { gameItem ->
                        GameItemImage(
                            game = gameItem,
                            count = gameItem.quantity,
                            clickable = clickableGames,
                            editable = editable,
                            onClick = onClick,
                            onDelete = onDelete,
                            onEdit = onEdit,
                            imageHeight = imageHeight,
                            online = online,
                            fetchedGames = fetchedGames,
                            modifier = Modifier.height(rowHeight))
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
