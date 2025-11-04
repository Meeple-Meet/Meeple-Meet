// AI was used to help comment this screen
package com.github.meeplemeet.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.theme.AppColors
import java.text.DateFormatSymbols
import java.util.Calendar

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

  // Availability section tags
  const val SHOP_DAY_PREFIX = "SHOP_DAY_"

  // Game list tags
  const val SHOP_GAME_PREFIX = "SHOP_GAME_"
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
fun ShopDetailsScreen(
    shopId: String,
    account: Account,
    viewModel: ShopViewModel,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
  // Collect the current shop state from the ViewModel
  val shopState by viewModel.shop.collectAsStateWithLifecycle()
  // Trigger loading of shop data when shopId changes
  LaunchedEffect(shopId) { viewModel.getShop(shopId) }

  Scaffold(
      topBar = {
        TopBarWithDivider(
            text = shopState?.name ?: "Shop",
            onReturn = { onBack },
            trailingIcons = {
              // Show edit button only if current account is the shop owner
              if (account == (shopState?.owner ?: false)) {
                IconButton(
                    onClick = onEdit, modifier = Modifier.testTag(ShopTestTags.SHOP_EDIT_BUTTON)) {
                      Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
              }
            })
      }) { innerPadding ->
        // Show shop details if loaded, otherwise show a loading indicator
        shopState?.let { shop ->
          ShopDetails(
              shop = shop, modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize())
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
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
    ContactSection(shop)
    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 100.dp))
    AvailabilitySection(shop.openingHours)
    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 100.dp))
    GameListSection(shop.gameCollection)
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
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 25.dp)) {
        Text(
            "Contact:",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline)

        // Display phone contact row
        ContactRow(
            Icons.Default.Phone,
            "- Phone: ${shop.phone}",
            ShopTestTags.SHOP_PHONE_TEXT,
            ShopTestTags.SHOP_PHONE_BUTTON)
        // Display email contact row
        ContactRow(
            Icons.Default.Email,
            "- Email: ${shop.email}",
            ShopTestTags.SHOP_EMAIL_TEXT,
            ShopTestTags.SHOP_EMAIL_BUTTON)
        // Display address contact row
        ContactRow(
            Icons.Default.Place,
            "- Address: ${shop.address.name}",
            ShopTestTags.SHOP_ADDRESS_TEXT,
            ShopTestTags.SHOP_ADDRESS_BUTTON)
        // Display website contact row
        ContactRow(
            Icons.Default.Language,
            "- Website: ${shop.website}",
            ShopTestTags.SHOP_WEBSITE_TEXT,
            ShopTestTags.SHOP_WEBSITE_BUTTON)
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
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            text,
            style = LocalTextStyle.current.copy(textIndent = TextIndent(restLine = 8.sp)),
            modifier = Modifier.weight(1f).testTag(textTag))
        IconButton(
            onClick = {
              // Copy the contact text to the clipboard and show a toast confirmation
              clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
              Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            content = { Icon(icon, contentDescription = null, tint = AppColors.neutral) },
            modifier = Modifier.size(24.dp).testTag(buttonTag))
      }
}

// -------------------- AVAILABILITY SECTION --------------------

/**
 * Composable that displays the shop's opening hours for each day of the week.
 *
 * @param openingHours List of OpeningHours representing the shop's weekly schedule.
 */
@Composable
fun AvailabilitySection(openingHours: List<OpeningHours>) {
  val daysOfWeek = remember { DateFormatSymbols().weekdays }
  val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
        Text(
            "Availability:",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline)

        // Loop through each day's opening hours
        openingHours.forEach { entry ->
          // Get the day name from the weekdays array (offset by 1 because weekdays start at 1)
          val dayName = daysOfWeek.getOrNull(entry.day + 1) ?: "Unknown"
          // Check if this day is the current day to highlight it
          val isToday = (entry.day + 1) == currentDay

          if (entry.hours.isEmpty()) {
            // No opening hours means the shop is closed on this day
            Row(
                modifier =
                    Modifier.fillMaxWidth().testTag("${ShopTestTags.SHOP_DAY_PREFIX}${entry.day}"),
                horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(
                      dayName,
                      fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                      modifier = Modifier.weight(1f))
                  Text(
                      "Closed",
                      fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                      modifier =
                          Modifier.testTag("${ShopTestTags.SHOP_DAY_PREFIX}${entry.day}_HOURS"))
                }
          } else {
            // Display each time interval for the day
            entry.hours.forEachIndexed { idx, (start, end) ->
              Row(
                  modifier =
                      Modifier.fillMaxWidth()
                          .testTag("${ShopTestTags.SHOP_DAY_PREFIX}${entry.day}_HOURS_${idx}"),
                  horizontalArrangement = Arrangement.SpaceBetween) {
                    if (idx == 0) {
                      // Show the day name only on the first interval row
                      Text(
                          dayName,
                          fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                          modifier =
                              Modifier.weight(1f)
                                  .testTag("${ShopTestTags.SHOP_DAY_PREFIX}${entry.day}"))
                    } else {
                      // Empty space for subsequent interval rows to align with day name column
                      Text("", modifier = Modifier.weight(1f))
                    }
                    // Format the time interval or show "Closed" if times are null
                    val timeText = if (start != null && end != null) "$start - $end" else "Closed"
                    Text(timeText, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                  }
            }
          }
        }
      }
}

// -------------------- GAMES SECTION --------------------

/**
 * Composable that displays a list of games available in the shop.
 *
 * @param games List of pairs of Game and available count.
 */
@Composable
fun GameListSection(
    games: List<Pair<Game, Int>>,
    horizontalPadding: Dp = 30.dp,
    clickableGames: Boolean = false,
    onClick: (Game) -> Unit = {}
) {
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        Text(
            "Games:",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 600.dp)) {
              // For each game-count pair, display a GameItem
              items(games) { (game, count) -> GameItem(game, count, clickableGames, onClick) }
            }
      }
}

/**
 * Composable that displays a single game item with its count badge.
 *
 * @param game The game to display.
 * @param count The number of copies available.
 */
@Composable
fun GameItem(game: Game, count: Int, clickable: Boolean = false, onClick: (Game) -> Unit = {}) {
  Card(
      modifier =
          Modifier.fillMaxWidth()
              .testTag("${ShopTestTags.SHOP_GAME_PREFIX}${game.uid}")
              .clickable(enabled = clickable, onClick = { onClick(game) }),
      colors = CardDefaults.cardColors(containerColor = AppColors.secondary)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
          // Placeholder box for game image or icon
          Box(
              modifier = Modifier.size(48.dp),
              contentAlignment = Alignment.Center,
          ) {
            BadgedBox(
                modifier = Modifier,
                badge = {
                  // Show badge only if count is greater than zero
                  if (count > 0) {
                    Badge(
                        modifier = Modifier.offset(x = 8.dp, y = (-6).dp).size(20.dp),
                        containerColor = AppColors.focus) {
                          Text(count.toString())
                        }
                  }
                }) {
                  Icon(Icons.Default.VideogameAsset, contentDescription = null)
                }
          }

          Spacer(modifier = Modifier.width(8.dp))
          Column(
              modifier = Modifier.weight(1f),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center) {
                Text(
                    game.name,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center)
              }
        }
      }
}
