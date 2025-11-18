// AI was used to help comment this screen
package com.github.meeplemeet.ui.space_renter

import android.icu.util.Calendar
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterViewModel
import com.github.meeplemeet.ui.shops.AvailabilitySection
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import kotlin.math.ceil

/** Object containing test tags used in the Space Renter screen UI for UI testing purposes. */
object SpaceRenterTestTags {
  // Contact section tags
  const val SPACE_RENTER_PHONE_TEXT = "SPACE_RENTER_PHONE_TEXT"
  const val SPACE_RENTER_PHONE_BUTTON = "SPACE_RENTER_PHONE_BUTTON"
  const val SPACE_RENTER_EMAIL_TEXT = "SPACE_RENTER_EMAIL_TEXT"
  const val SPACE_RENTER_EMAIL_BUTTON = "SPACE_RENTER_EMAIL_BUTTON"
  const val SPACE_RENTER_ADDRESS_TEXT = "SPACE_RENTER_ADDRESS_TEXT"
  const val SPACE_RENTER_ADDRESS_BUTTON = "SPACE_RENTER_ADDRESS_BUTTON"
  const val SPACE_RENTER_WEBSITE_TEXT = "SPACE_RENTER_WEBSITE_TEXT"
  const val SPACE_RENTER_WEBSITE_BUTTON = "SPACE_RENTER_WEBSITE_BUTTON"
  const val SPACE_RENTER_EDIT_BUTTON = "EDIT_SPACE_BUTTON"

  // Availability section tags
  const val SPACE_RENTER_DAY_PREFIX = "SPACE_RENTER_DAY_"
}

object SpaceRenterUi {
  fun phoneContactRow(phoneNumber: String) = "- Phone: $phoneNumber"

  fun emailContactRow(email: String) = "$email"

  fun addressContactRow(address: String) = "- Address: $address"

  fun websiteContactRow(website: String) = "- Website: $website"

  val HORIZONTAL_PADDING: Dp = 100.dp
  val ROW_WIDTH: Dp = 48.dp
}

/**
 * Composable that displays the Space Renter screen, including the top bar and Space Renter details.
 *
 * @param spaceId The ID of the Space Renter to display.
 * @param account The current user account.
 * @param viewModel The ViewModel providing space renter data.
 * @param onBack Callback invoked when the back button is pressed.
 * @param onEdit Callback invoked when the edit button is pressed.
 */
@Composable
fun SpaceRenterScreen(
    spaceId: String,
    account: Account,
    viewModel: SpaceRenterViewModel = viewModel(),
    onBack: () -> Unit = {},
    onReserve: () -> Unit = {},
    onEdit: (SpaceRenter?) -> Unit = {},
) {
  // Collect the current space renter state from the ViewModel
  val spaceState by viewModel.spaceRenter.collectAsStateWithLifecycle()
  // Trigger loading of space renter data when spaceId changes
  LaunchedEffect(spaceId) { viewModel.getSpaceRenter(spaceId) }
  var selectedIndex by remember { mutableStateOf<Int?>(null) }

  Scaffold(
      topBar = {
        TopBarAndDivider(
            text = "Details",
            onReturn = { onBack() },
            trailingIcons = {
              // Edit button should only show if current account is the space renter owner
              if (account.uid == (spaceState?.owner?.uid)) {
                IconButton(
                    onClick = { onEdit(spaceState) },
                    modifier = Modifier.testTag(SpaceRenterTestTags.SPACE_RENTER_EDIT_BUTTON)) {
                      Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
              }
            })
      },
      bottomBar = {
        ReservationBar(
            selectedSpace = selectedIndex?.let { spaceState?.spaces?.getOrNull(it) },
            selectedIndex = selectedIndex,
            onApprove = onReserve)
      },
  ) { innerPadding ->
    // Show space renter details if loaded, otherwise show a loading indicator
    spaceState?.let { space ->
      SpaceRenterDetails(
          spaceRenter = space,
          selectedIndex = selectedIndex,
          onSelect = { selectedIndex = it },
          modifier = Modifier.padding(innerPadding).fillMaxSize())
    }
        ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
  }
}

/**
 * Composable that displays detailed information about a space renter, including contact info,
 * availability, and game list.
 *
 * @param spaceRenter The space renter data to display.
 * @param modifier Modifier to be applied to the layout.
 */
@Composable
fun SpaceRenterDetails(
    spaceRenter: SpaceRenter,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
  Column(
      modifier =
          modifier.verticalScroll(rememberScrollState()).padding(bottom = Dimensions.Padding.large),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xxLarge)) {
        TemporaryPhotoCarousel()
        ContactSection(spaceRenter)
        AvailabilityRowPopup(openingHours = spaceRenter.openingHours)
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.xxLarge)) {
              SpacesSection(
                  spaces = spaceRenter.spaces,
                  selectedIndex = selectedIndex,
                  onSelect = onSelect,
                  modifier = Modifier.fillMaxWidth())
            }
      }
}

// -------------------- CONTACT SECTION --------------------

/**
 * Composable that displays the contact information section of the space renter.
 *
 * @param spaceRenter The space renter whose contact information is displayed.
 */
@Composable
fun ContactSection(spaceRenter: SpaceRenter) {
  Column(
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.xxLarge)) {
        Text(
            spaceRenter.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)

        // Display address contact row
        ContactRow(
            Icons.Default.Place,
            humanReadableAddress(spaceRenter.address.name),
            SpaceRenterTestTags.SPACE_RENTER_ADDRESS_TEXT)

        if (spaceRenter.phone.isNotBlank()) {
          ContactRow(
              Icons.Default.Phone, spaceRenter.phone, SpaceRenterTestTags.SPACE_RENTER_PHONE_TEXT)
        }

        ContactRow(
            Icons.Default.Email, spaceRenter.email, SpaceRenterTestTags.SPACE_RENTER_EMAIL_TEXT)

        if (spaceRenter.website.isNotBlank()) {
          ContactRow(
              Icons.Default.Language,
              spaceRenter.website,
              SpaceRenterTestTags.SPACE_RENTER_WEBSITE_TEXT)
        }
      }
}

/**
 * Composable displaying a row of contact info. This includes an clickable icon, and information
 * about the spaceRenter The clickable icons currently only copy the text to the clipboard.
 *
 * @param icon The icon to display for the contact method.
 * @param text The contact text to display and copy.
 * @param textTag The test tag for the text element.
 * @param buttonTag The test tag for the copy button.
 */
@Composable
fun ContactRow(
    icon: ImageVector,
    text: String,
    textTag: String,
) {
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable {
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
              }
              .padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = AppColors.neutral,
            modifier = Modifier.size(20.dp))

        Text(
            text,
            modifier = Modifier.weight(1f).testTag(textTag),
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
      }
}

/**
 * Composable that replaces material3's TopBarWithDivider The main difference is the text is now
 * centered horizontally and can expand vertically if needed
 *
 * @param text The title's text to display in the top bar.
 * @param onReturn Callback function for the back button is pressed.
 * @param trailingIcons Optional composable for an additional trailing icon (to the right)
 */
@Composable
fun TopBarAndDivider(
    text: String,
    onReturn: () -> Unit,
    trailingIcons: @Composable (() -> Unit)? = null
) {
  Column {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.Padding.large)) {
      // Button to the left
      Row(
          modifier = Modifier.align(Alignment.CenterStart).width(SpaceRenterUi.ROW_WIDTH),
          verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onReturn) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }

      Text(
          text = text,
          style = MaterialTheme.typography.titleLarge,
          maxLines =
              Int.MAX_VALUE, // Allows vertical expansion when text is too to fit horizontally
          modifier =
              Modifier.align(Alignment.Center)
                  .padding(horizontal = Dimensions.AvatarSize.large)
                  .fillMaxWidth(),
          textAlign = TextAlign.Center)

      // (Optional) Button to the right
      Row(
          modifier = Modifier.align(Alignment.CenterEnd).width(SpaceRenterUi.ROW_WIDTH),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically) {
            trailingIcons?.invoke()
          }
    }

    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SpaceRenterUi.HORIZONTAL_PADDING))
  }
}

// todo: Implement a better version of this
private fun humanReadableAddress(address: String): String {
  val limit = 30
  if (address.length <= limit) return address else return address.take(limit - 3) + "..."
}

@Composable
fun SpacesSection(
    spaces: List<Space>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
  if (spaces.isEmpty()) return

  val spacesPerPage = 3
  val pageCount = ceil(spaces.size / spacesPerPage.toFloat()).toInt()
  val pagerState = rememberPagerState(pageCount = { pageCount })

  val minPrice = spaces.minOf { it.costPerHour }
  val maxPrice = spaces.maxOf { it.costPerHour }

  Column(
      modifier = modifier.fillMaxWidth(), // Matches Figma spacing
      verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Text(
                  "Available spaces",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.SemiBold)

              PriceRangeChip(minPrice, maxPrice)
            }

        // Pager
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
          Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val start = page * spacesPerPage
                val end = minOf(start + spacesPerPage, spaces.size)

                for (i in start until end) {
                  SpaceCard(
                      space = spaces[i],
                      index = i,
                      isSelected = selectedIndex == i,
                      onClick = { onSelect(if (selectedIndex == i) null else i) })
                }
              }
        }

        // Pager dots
        if (pageCount > 1) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pageCount) { index ->
              val active = pagerState.currentPage == index
              Box(
                  modifier =
                      Modifier.padding(4.dp)
                          .size(if (active) 9.dp else 7.dp)
                          .background(
                              color = if (active) AppColors.textIcons else AppColors.textIconsFade,
                              shape = CircleShape))
            }
          }
        }
      }
}

@Composable
private fun SpaceCard(space: Space, index: Int, isSelected: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(12.dp)

  Surface(
      modifier = Modifier.fillMaxWidth().clickable { onClick() }.height(72.dp),
      shape = shape,
      color = AppColors.secondary,
      border = if (isSelected) BorderStroke(2.dp, AppColors.neutral) else null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Space N°${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.textIcons,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Capacity: ${space.seats} seats",
                    color = AppColors.textIcons,
                    style = MaterialTheme.typography.labelSmall)
              }

              Text(
                  "${space.costPerHour}\$ per hour",
                  style = MaterialTheme.typography.bodySmall,
                  textAlign = TextAlign.End)
            }
      }
}

@Composable
private fun PriceRangeChip(minPrice: Double, maxPrice: Double) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(24.dp)) {
          Icon(
              Icons.Default.Info,
              contentDescription = null,
              tint = AppColors.textIcons,
              modifier = Modifier.padding(4.dp))
        }

        Text(
            if (minPrice == maxPrice) "${minPrice}\$" else "${minPrice}-${maxPrice}\$",
            style = MaterialTheme.typography.bodyMedium)
      }
}

// TODO: remove me once the real photo carousel is implemented
@Composable
private fun TemporaryPhotoCarousel() {
  val pageCount = 4
  val pagerState = rememberPagerState(pageCount = { pageCount })

  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(180.dp)
              .padding(Dimensions.Padding.large)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFF757575)) // grey placeholder
      ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()) { /* empty for now – just grey */}

        // Dots inside the image
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center) {
              repeat(pageCount) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier =
                        Modifier.padding(4.dp)
                            .size(if (active) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) AppColors.textIcons else AppColors.textIconsFade))
              }
            }
      }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityRowPopup(openingHours: List<OpeningHours>) {
  var showSheet by remember { mutableStateOf(false) }

  val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
  val todayHours = openingHours.firstOrNull { it.day == today }

  // Build "Today: 7:30AM - 8:00PM"
  val todayText =
      when {
        todayHours == null || todayHours.hours.isEmpty() -> "Closed"
        todayHours.hours.size == 1 -> {
          val (start, end) = todayHours.hours.first()
          "$start - $end"
        }
        else -> todayHours.hours.joinToString(" ") { (start, end) -> "$start - $end" }
      }

  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.xxLarge)) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { showSheet = true }
                .padding(vertical = Dimensions.Spacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
          Column {
            Text(
                "Availability",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Text("Today: $todayText", style = MaterialTheme.typography.bodyMedium)
          }

          Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.textIcons)
        }
  }

  if (showSheet) {
    ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = AppColors.primary) {
      AvailabilitySection(
          openingHours = openingHours, dayTagPrefix = SpaceRenterTestTags.SPACE_RENTER_DAY_PREFIX)

      Spacer(Modifier.height(20.dp))
    }
  }
}

@Composable
fun ReservationBar(selectedSpace: Space?, selectedIndex: Int?, onApprove: () -> Unit) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .background(Color.Transparent)
              .padding(horizontal = 16.dp, vertical = 12.dp),
      contentAlignment = Alignment.BottomCenter) {
        if (selectedSpace == null) {

          Surface(
              shape = RoundedCornerShape(60), color = AppColors.secondary, shadowElevation = 8.dp) {
                Surface(shape = RoundedCornerShape(60), color = AppColors.affirmative) {
                  Text(
                      "Click on a space to setup a reservation!",
                      modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                }
              }
        } else {

          Surface(
              shape = RoundedCornerShape(60), color = AppColors.secondary, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                      Box(
                          modifier =
                              Modifier.background(Color.Transparent, RoundedCornerShape(50))
                                  .padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Column {
                              Text(
                                  "Space N°${(selectedIndex ?: 0) + 1}",
                                  fontWeight = FontWeight.SemiBold)
                              Text("${selectedSpace.seats} seats - ${selectedSpace.costPerHour}$")
                            }
                          }

                      Spacer(Modifier.width(12.dp))

                      Button(
                          onClick = onApprove,
                          shape = RoundedCornerShape(50),
                          colors =
                              ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
                          modifier = Modifier.height(56.dp)) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = "Approve")
                          }
                    }
              }
        }
      }
}
