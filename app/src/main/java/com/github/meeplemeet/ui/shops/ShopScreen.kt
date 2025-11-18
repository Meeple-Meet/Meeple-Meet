// AI was used to help comment this screen
@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.github.meeplemeet.ui.shops

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.ui.components.AvailabilitySection
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.launch

/** Object containing test tags used in the Shop screen UI for UI testing purposes. */
object ShopTestTags {
  // Contact section tags
  const val SHOP_PHONE_TEXT = "SHOP_PHONE_TEXT"
  const val SHOP_PHONE_BUTTON = "SHOP_PHONE_BUTTON"
  const val SHOP_EMAIL_TEXT = "SHOP_EMAIL_TEXT"
  const val SHOP_EMAIL_BUTTON = "SHOP_EMAIL_BUTTON"
  const val SHOP_ADDRESS_TEXT = "SHOP_ADDRESS_TEXT"
  const val SHOP_ADDRESS_BUTTON = "SHOP_ADDRESS_BUTTON"
  const val SHOP_WEBSITE_TEXT = "SHOP_WEBSITE_TEXT"
  const val SHOP_WEBSITE_BUTTON = "SHOP_WEBSITE_BUTTON"
  const val SHOP_EDIT_BUTTON = "EDIT_SHOP_BUTTON"

  // Game list tags
  const val SHOP_GAME_PREFIX = "SHOP_GAME_"
  const val SHOP_GAME_PAGER = "SHOP_GAME_PAGER"
  const val SHOP_GAME_PAGER_INDICATOR_PREFIX = "SHOP_GAME_PAGER_INDICATOR_"

  const val SHOP_GAME_NAME_PREFIX = "SHOP_GAME_NAME_"
  const val SHOP_GAME_STOCK_PREFIX = "SHOP_GAME_STOCK_"
}

private const val GAME_SECTION_TITLE = "Discover Games"
private const val GAMES_PER_COLUMN = 4
private const val GAMES_PER_ROW = 2
private const val GAMES_PER_PAGE = GAMES_PER_COLUMN * GAMES_PER_ROW
private const val MAX_PAGES = 6
private const val MAX_GAMES = GAMES_PER_PAGE * MAX_PAGES
private const val MINIMAL_PAGE_COUNT = 1

private const val IMAGE_HEIGHT_CORRECTION = 3.1f
private val GAME_NAME_AREA_HEIGHT = 46.dp

private val PAGER_UNSELECTED_BUBBLE_SIZE = 8.dp
private val PAGER_SELECTED_BUBBLE_SIZE = 10.dp

private val STOCK_BUBBLE_SIZE = 32.dp
private val STOCK_BUBBLE_TOP_PADDING = (STOCK_BUBBLE_SIZE / 2)
private const val GAME_NAME_MAX_LINES = 2
private const val NOT_SHOWING_STOCK_MIN_VALUE = 0

private const val GAME_IMG_RELATIVE_WIDTH = 0.85f
private const val GAME_IMG_DEFAULT_ASPECT_RATIO = 0.75f
const val MAX_STOCK_SHOWED = 99

/**
 * Composable that displays the Shop screen, including the top bar and shop details.
 *
 * @param shopId The ID of the shop to display.
 * @param account The current user account.
 * @param viewModel The ViewModel providing shop data.
 * @param onBack Callback invoked when the back button is pressed.
 * @param onEdit Callback invoked when the edit button is pressed.
 */
@Composable
fun ShopScreen(
    shopId: String,
    account: Account,
    viewModel: ShopViewModel = viewModel(),
    onBack: () -> Unit = {},
    onEdit: (Shop?) -> Unit = {},
) {
  // Collect the current shop state from the ViewModel
  val shopState by viewModel.shop.collectAsStateWithLifecycle()
  // Trigger loading of shop data when shopId changes
  LaunchedEffect(shopId) { viewModel.getShop(shopId) }

  Scaffold(
      topBar = {
        TopBarWithDivider(
            text = "Shop",
            onReturn = { onBack() },
            trailingIcons = {
              // Show edit button only if current account is the shop owner
              if (account.uid == (shopState?.owner?.uid)) {
                IconButton(
                    onClick = { onEdit(shopState) },
                    modifier = Modifier.testTag(ShopTestTags.SHOP_EDIT_BUTTON)) {
                      Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
              }
            })
      }) { innerPadding ->
        // Show shop details if loaded, otherwise show a loading indicator
        shopState?.let { shop ->
          ShopDetails(
              shop = shop,
              modifier =
                  Modifier.padding(innerPadding)
                      .padding(Dimensions.Padding.extraLarge)
                      .fillMaxSize())
        }
            ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
      }
}

/**
 * Composable that displays detailed information about a shop, including contact info, availability,
 * and game list.
 *
 * @param shop The shop data to display.
 * @param modifier Modifier to be applied to the layout.
 */
@Composable
fun ShopDetails(shop: Shop, modifier: Modifier = Modifier) {
  LazyColumn(
      modifier = modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xxLarge),
      contentPadding = PaddingValues(bottom = Dimensions.Spacing.xxLarge)) {

        /* TODO: Add here shop images composable (pager) when done */

        item { ContactSection(shop) }

        item { AvailabilitySection(shop.openingHours) }

        item {
          GameImageListSection(
              games = shop.gameCollection,
              modifier = Modifier.fillMaxWidth(),
              clickableGames = true,
              title = GAME_SECTION_TITLE,
          )
        }
      }
}

// -------------------- CONTACT SECTION --------------------

/**
 * Composable that displays the contact information section of the shop.
 *
 * @param shop The shop whose contact information is displayed.
 */
@Composable
fun ContactSection(shop: Shop) {
  Column(
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
      modifier = Modifier.fillMaxWidth()) {
        Text(
            text = shop.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        // Display address contact row
        ContactRow(
            Icons.Default.Place,
            shop.address.name,
            ShopTestTags.SHOP_ADDRESS_TEXT,
            ShopTestTags.SHOP_ADDRESS_BUTTON)
        // Display email contact row
        ContactRow(
            Icons.Default.Email,
            shop.email,
            ShopTestTags.SHOP_EMAIL_TEXT,
            ShopTestTags.SHOP_EMAIL_BUTTON)

        // Display phone contact row
        if (shop.phone.isNotEmpty()) {
          ContactRow(
              Icons.Default.Phone,
              shop.phone,
              ShopTestTags.SHOP_PHONE_TEXT,
              ShopTestTags.SHOP_PHONE_BUTTON)
        }
        // Display website contact row
        if (shop.website.isNotEmpty()) {
          ContactRow(
              Icons.Default.Language,
              shop.website,
              ShopTestTags.SHOP_WEBSITE_TEXT,
              ShopTestTags.SHOP_WEBSITE_BUTTON)
        }
      }
}

/**
 * Composable that displays a single row of contact information with an icon, text, and a button to
 * copy the text to the clipboard.
 *
 * @param icon The icon to display for the contact method.
 * @param text The contact text to display and copy.
 * @param textTag The test tag for the text element.
 * @param buttonTag The test tag for the copy button.
 */
@Composable
fun ContactRow(icon: ImageVector, text: String, textTag: String, buttonTag: String) {
  val clipboardManager: ClipboardManager = LocalClipboardManager.current
  val context = LocalContext.current

  // Split text into "first line" and "rest"
  val lines = text.split('\n')
  val firstLine = lines.firstOrNull().orEmpty()
  val restLines = lines.drop(n = 1)

  Row(
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.small)) {
        IconButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(text))
              Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(Dimensions.IconSize.large).testTag(buttonTag)) {
              Icon(icon, contentDescription = null, tint = AppColors.neutral)
            }

        Column(modifier = Modifier.weight(1f).testTag(textTag)) {
          // First line on the same row as the icon
          Text(text = firstLine, style = LocalTextStyle.current)

          // Remaining lines displayed below
          if (restLines.isNotEmpty()) {
            Text(text = restLines.joinToString("\n"), style = LocalTextStyle.current)
          }
        }
      }
}

// -------------------- GAME ITEM --------------------

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
    clickable: Boolean = true,
    onClick: (Game) -> Unit = {},
    imageHeight: Dp? = null,
) {

  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .testTag("${ShopComponentsTestTags.SHOP_GAME_PREFIX}${game.uid}")) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(top = STOCK_BUBBLE_TOP_PADDING)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(enabled = clickable) { onClick(game) }
                    .padding(Dimensions.Padding.small),
            horizontalAlignment = Alignment.CenterHorizontally) {
              Box(
                  modifier =
                      Modifier.fillMaxWidth(GAME_IMG_RELATIVE_WIDTH)
                          .shadow(
                              Dimensions.Elevation.high, MaterialTheme.shapes.medium, clip = true)
                          .clip(MaterialTheme.shapes.medium)
                          .background(MaterialTheme.colorScheme.background)
                          .let { base ->
                            if (imageHeight != null) base.height(imageHeight)
                            else base.aspectRatio(GAME_IMG_DEFAULT_ASPECT_RATIO)
                          }) {
                    AsyncImage(
                        model = game.imageURL,
                        contentDescription = game.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                  }

              Spacer(Modifier.height(Dimensions.Spacing.small))

              Text(
                  text = game.name,
                  style = MaterialTheme.typography.bodySmall,
                  textAlign = TextAlign.Center,
                  maxLines = GAME_NAME_MAX_LINES,
                  overflow = TextOverflow.Ellipsis,
                  modifier =
                      Modifier.fillMaxWidth()
                          .testTag("${ShopTestTags.SHOP_GAME_NAME_PREFIX}${game.uid}"))
            }

        if (count > NOT_SHOWING_STOCK_MIN_VALUE) {
          val label = if (count > MAX_STOCK_SHOWED) "$MAX_STOCK_SHOWED+" else count.toString()

          Box(
              modifier =
                  Modifier.size(STOCK_BUBBLE_SIZE)
                      .align(Alignment.TopStart)
                      .offset(x = Dimensions.Padding.small, y = Dimensions.Padding.small)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary)
                      .testTag("${ShopTestTags.SHOP_GAME_STOCK_PREFIX}${game.uid}"),
              contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary)
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
    onClick: (Game) -> Unit = {},
) {
  val clampedGames = remember(games) { games.shuffled().take(MAX_GAMES) }
  if (clampedGames.isEmpty()) return

  val pages = remember(clampedGames) { clampedGames.chunked(GAMES_PER_PAGE).take(MAX_PAGES) }
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
          val imageHeight = gridWidth / IMAGE_HEIGHT_CORRECTION
          val textAreaHeight = GAME_NAME_AREA_HEIGHT
          val rowHeight = imageHeight + textAreaHeight
          val gridHeight = rowHeight * GAMES_PER_COLUMN

          HorizontalPager(
              state = pagerState,
              modifier =
                  Modifier.fillMaxWidth()
                      .height(gridHeight)
                      .testTag(ShopTestTags.SHOP_GAME_PAGER)) { pageIndex ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(GAMES_PER_ROW),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize()) {
                      items(pages[pageIndex], key = { it.first.uid }) { (game, count) ->
                        GameItemImage(
                            game = game,
                            count = count,
                            clickable = clickableGames,
                            onClick = onClick,
                            imageHeight = imageHeight,
                            modifier = Modifier.height(rowHeight),
                        )
                      }
                    }
              }
        }

        if (pageCount > MINIMAL_PAGE_COUNT) {
          Row(
              modifier = Modifier.fillMaxWidth().padding(top = Dimensions.Spacing.medium),
              horizontalArrangement = Arrangement.Center) {
                repeat(pageCount) { index ->
                  val selected = (index == pagerState.currentPage)
                  Box(
                      modifier =
                          Modifier.padding(horizontal = Dimensions.Padding.small)
                              .size(
                                  if (selected) PAGER_SELECTED_BUBBLE_SIZE
                                  else PAGER_UNSELECTED_BUBBLE_SIZE)
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
