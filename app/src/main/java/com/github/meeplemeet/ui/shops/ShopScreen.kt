// AI was used to help comment this screen
@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.github.meeplemeet.ui.shops

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
import com.github.meeplemeet.ui.components.ContactSection
import com.github.meeplemeet.ui.components.GameDetailsCard
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.launch

/** Object containing test tags used in the Shop screen UI for UI testing purposes. */
object ShopTestTags {

  const val SHOP_EDIT_BUTTON = "EDIT_SHOP_BUTTON"

  // Game list tags
  const val SHOP_GAME_PREFIX = "SHOP_GAME_"
  const val SHOP_GAME_PAGER = "SHOP_GAME_PAGER"
  const val SHOP_GAME_PAGER_INDICATOR_PREFIX = "SHOP_GAME_PAGER_INDICATOR_"

  const val SHOP_GAME_NAME_PREFIX = "SHOP_GAME_NAME_"
  const val SHOP_GAME_STOCK_PREFIX = "SHOP_GAME_STOCK_"
}

object ShopScreenDefaults {

  object Game {
    const val GAME_SECTION_TITLE = "Discover Games"
    val GAME_NAME_AREA_HEIGHT = 46.dp
    const val GAME_NAME_MAX_LINES = 2
    const val GAME_IMG_RELATIVE_WIDTH = 0.85f
    const val GAME_IMG_DEFAULT_ASPECT_RATIO = 0.75f
  }

  object Pager {
    const val MINIMAL_PAGE_COUNT = 1
    const val GAMES_PER_COLUMN = 4
    const val GAMES_PER_ROW = 2
    const val GAMES_PER_PAGE = GAMES_PER_COLUMN * GAMES_PER_ROW
    const val MAX_PAGES = 6
    const val MAX_GAMES = GAMES_PER_PAGE * MAX_PAGES
    const val IMAGE_HEIGHT_CORRECTION = 3.1f
    val PAGER_UNSELECTED_BUBBLE_SIZE = 8.dp
    val PAGER_SELECTED_BUBBLE_SIZE = 10.dp
  }

  object Stock {
    val STOCK_BUBBLE_SIZE = 32.dp
    val STOCK_BUBBLE_TOP_PADDING = (STOCK_BUBBLE_SIZE / 2)

    const val NOT_SHOWING_STOCK_MIN_VALUE = 0
    const val MAX_STOCK_SHOWED = 99
  }
}

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
  val shopState by viewModel.shop.collectAsStateWithLifecycle()
  LaunchedEffect(shopId) { viewModel.getShop(shopId) }

  var popupGame by remember { mutableStateOf<Game?>(null) }

  Box(modifier = Modifier.fillMaxSize()) {

    // ────────────────────────────────────────────
    // SCAFFOLD WITH TOPBAR & MAIN CONTENT
    // ────────────────────────────────────────────
    Scaffold(
        topBar = {
          TopBarWithDivider(
              text = "Shop",
              onReturn = { if (popupGame == null) onBack() },
              trailingIcons = {
                if (account.uid == shopState?.owner?.uid) {
                  IconButton(
                      onClick = { if (popupGame == null) onEdit(shopState) },
                      modifier = Modifier.testTag(ShopTestTags.SHOP_EDIT_BUTTON)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                      }
                }
              })
        }) { innerPadding ->
          shopState?.let { shop ->
            ShopDetails(
                shop = shop,
                modifier =
                    Modifier.padding(innerPadding)
                        .padding(Dimensions.Padding.extraLarge)
                        .fillMaxSize(),
                onGameClick = { game -> popupGame = game },
            )
          }
              ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }
        }

    // ────────────────────────────────────────────
    // SCRIM BLOCKING ALL TOUCHES WHEN POPUP OPEN
    // ────────────────────────────────────────────
    if (popupGame != null) {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                  .clickable(
                      enabled = true,
                      indication = null,
                      interactionSource =
                          remember { MutableInteractionSource() }) {} // block everything behind
          )
    }

    // ────────────────────────────────────────────
    // CENTERED POPUP WITH BORDER
    // ────────────────────────────────────────────
    popupGame?.let { game ->
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GameDetailsCard(
            game = game,
            onClose = { popupGame = null },
            modifier = Modifier.wrapContentSize().padding(Dimensions.Padding.extraLarge))
      }
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
fun ShopDetails(shop: Shop, modifier: Modifier = Modifier, onGameClick: (Game) -> Unit) {
  LazyColumn(
      modifier = modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xxLarge),
      contentPadding = PaddingValues(bottom = Dimensions.Spacing.xxLarge)) {

        // Contact info
        item {
          ContactSection(
              name = shop.name,
              address = shop.address.name,
              email = shop.email,
              phone = shop.email,
              website = shop.website)
        }

        // Opening hours
        item { AvailabilitySection(shop.openingHours) }

        // Game list
        item {
          GameImageListSection(
              games = shop.gameCollection,
              modifier = Modifier.fillMaxWidth(),
              clickableGames = true,
              title = ShopScreenDefaults.Game.GAME_SECTION_TITLE,
              onClick = onGameClick)
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
                    .padding(top = ShopScreenDefaults.Stock.STOCK_BUBBLE_TOP_PADDING)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(enabled = clickable) { onClick(game) }
                    .padding(Dimensions.Padding.small),
            horizontalAlignment = Alignment.CenterHorizontally) {
              Box(
                  modifier =
                      Modifier.fillMaxWidth(ShopScreenDefaults.Game.GAME_IMG_RELATIVE_WIDTH)
                          .shadow(
                              Dimensions.Elevation.high, MaterialTheme.shapes.medium, clip = true)
                          .clip(MaterialTheme.shapes.medium)
                          .background(MaterialTheme.colorScheme.background)
                          .let { base ->
                            if (imageHeight != null) base.height(imageHeight)
                            else
                                base.aspectRatio(
                                    ShopScreenDefaults.Game.GAME_IMG_DEFAULT_ASPECT_RATIO)
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
                  maxLines = ShopScreenDefaults.Game.GAME_NAME_MAX_LINES,
                  overflow = TextOverflow.Ellipsis,
                  modifier =
                      Modifier.fillMaxWidth()
                          .testTag("${ShopTestTags.SHOP_GAME_NAME_PREFIX}${game.uid}"))
            }

        if (count > ShopScreenDefaults.Stock.NOT_SHOWING_STOCK_MIN_VALUE) {
          val label =
              if (count > ShopScreenDefaults.Stock.MAX_STOCK_SHOWED)
                  "$ShopScreenDefaults.Pager.MAX_STOCK_SHOWED+"
              else count.toString()

          Box(
              modifier =
                  Modifier.size(ShopScreenDefaults.Stock.STOCK_BUBBLE_SIZE)
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
