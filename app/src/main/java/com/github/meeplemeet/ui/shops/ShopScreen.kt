// AI was used to help comment this screen
@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.github.meeplemeet.ui.shops

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.ui.components.AvailabilitySectionWithChevron
import com.github.meeplemeet.ui.components.ContactSection
import com.github.meeplemeet.ui.components.GameDetailsCard
import com.github.meeplemeet.ui.components.GameImageListSection
import com.github.meeplemeet.ui.components.ImageCarousel
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.theme.Dimensions

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
    const val GAME_IMG_RELATIVE_WIDTH = 0.75f
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
  // Collect the current shop state from the ViewModel
  val shopState by viewModel.shop.collectAsStateWithLifecycle()
  // Trigger loading of shop data when shopId changes
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
fun ShopDetails(
    shop: Shop,
    modifier: Modifier = Modifier,
    onGameClick: (Game) -> Unit,
    photoCollectionUrl: List<String> = shop.photoCollectionUrl
) {
  LazyColumn(
      modifier = modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xxLarge),
      contentPadding = PaddingValues(bottom = Dimensions.Spacing.xxLarge)) {
        item {
          if (photoCollectionUrl.isNotEmpty()) {
            ImageCarousel(
                photoCollectionUrl = photoCollectionUrl,
                maxNumberOfImages = shop.photoCollectionUrl.size,
                onAdd = { _, _ -> },
                onRemove = { _ -> },
                editable = false)
          }
        }

        item {
          ContactSection(
              name = shop.name,
              address = shop.address.name,
              email = shop.email,
              phone = shop.phone,
              website = shop.website)
        }

        item {
          AvailabilitySectionWithChevron(
              shop.openingHours, dayTagPrefix = ShopComponentsTestTags.SHOP_DAY_PREFIX)
        }

        // Game list
        item {
          GameImageListSection(
              games = shop.gameCollection,
              modifier = Modifier.fillMaxWidth(),
              clickableGames = true,
              editable = false,
              online =
                  false, // Does not matter what the value is set as since the user cannot interact
              // with the games either way
              title = ShopScreenDefaults.Game.GAME_SECTION_TITLE,
              onClick = onGameClick)
        }
      }
}
