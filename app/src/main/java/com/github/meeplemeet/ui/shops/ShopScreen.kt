// AI was used to help comment this screen
package com.github.meeplemeet.ui.shops

import android.widget.Toast
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.ui.components.GameListSection
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.google.firebase.Timestamp
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

private const val CLOSED_MSG = "Closed"
private const val PHONE_LINE_TEXT = "- Phone:"
private const val EMAIL_LINE_TEXT = "- Email:"
private const val ADDRESS_LINE_TEXT = "- Address:"
private const val WEBSITE_LINE_TEXT = "- Website:"

private val horizontalPadding = 100.dp

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
            text = shopState?.name ?: "Shop",
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
  Column(
      modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xxLarge)) {
        ContactSection(shop)
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding))
        AvailabilitySection(shop.openingHours)
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding))
        GameListSection(
            games = shop.gameCollection,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
            hasDeleteButton = false,
            title = "Games:")
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
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.xxLarge)) {
        Text(
            text = "Contact:",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline)

        // Display phone contact row
        ContactRow(
            Icons.Default.Phone,
            "$PHONE_LINE_TEXT ${shop.phone}",
            ShopTestTags.SHOP_PHONE_TEXT,
            ShopTestTags.SHOP_PHONE_BUTTON)
        // Display email contact row
        ContactRow(
            Icons.Default.Email,
            "$EMAIL_LINE_TEXT ${shop.email}",
            ShopTestTags.SHOP_EMAIL_TEXT,
            ShopTestTags.SHOP_EMAIL_BUTTON)
        // Display address contact row
        ContactRow(
            Icons.Default.Place,
            "$ADDRESS_LINE_TEXT ${shop.address.name}",
            ShopTestTags.SHOP_ADDRESS_TEXT,
            ShopTestTags.SHOP_ADDRESS_BUTTON)
        // Display website contact row
        ContactRow(
            Icons.Default.Language,
            "$WEBSITE_LINE_TEXT ${shop.website}",
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
      horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.small)) {
        Text(
            text,
            style = LocalTextStyle.current.copy(textIndent = TextIndent(restLine = 8.sp)),
            modifier = Modifier.weight(1f).testTag(textTag))
        IconButton(
            onClick = {
              // Copy the contact text to the clipboard and show a toast confirmation
              clipboardManager.setText(AnnotatedString(text))
              Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            content = { Icon(icon, contentDescription = null, tint = AppColors.neutral) },
            modifier = Modifier.size(Dimensions.IconSize.large).testTag(buttonTag))
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
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
        Text(
            text = "Availability:",
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
                      CLOSED_MSG,
                      fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                      modifier =
                          Modifier.testTag("${ShopTestTags.SHOP_DAY_PREFIX}${entry.day}_HOURS"))
                }
          } else {
            // Display each time interval for the day
            entry.hours.forEachIndexed { idx, (start, end) ->
              Column {
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
                      Text(
                          timeText,
                          fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    }
                if (isToday && idx == 0) {
                  val closed =
                      Timestamp.now() < stringToTimestamp(start!!)!! ||
                          Timestamp.now() > stringToTimestamp(end!!)!!
                  Text(
                      "Currently ${if (closed) "Closed" else "Open"}",
                      fontWeight = FontWeight.Bold,
                      color = if (closed) AppColors.negative else AppColors.affirmative,
                      modifier = Modifier.padding(top = 4.dp).align(Alignment.End))
                }
              }
            }
          }
        }
      }
}

/**
 * Converts a time string in "HH:mm" format to a Firebase Timestamp.
 *
 * @param timeString The time string to convert (e.g., "09:30").
 * @return A Firebase Timestamp representing the time on the current date, or null if parsing fails.
 */
fun stringToTimestamp(timeString: String): Timestamp? {
  return try {
    val parts = timeString.split(":")
    if (parts.size != 2) return null

    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null

    if (hour !in 0..23 || minute !in 0..59) return null

    val calendar =
        Calendar.getInstance().apply {
          set(Calendar.HOUR_OF_DAY, hour)
          set(Calendar.MINUTE, minute)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }

    Timestamp(calendar.time)
  } catch (e: Exception) {
    null
  }
}
