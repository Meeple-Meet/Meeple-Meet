// AI was used to help comment this screen
package com.github.meeplemeet.ui.space_renter

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
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
import com.github.meeplemeet.ui.components.AvailabilitySection
import com.github.meeplemeet.ui.components.SpaceRenterComponentsTestTags
import com.github.meeplemeet.ui.shops.AvailabilitySection
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import java.util.Calendar
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
  const val RESERVE_NO_SELECTION = "RESERVATION_NO_SELECTION"
  const val RESERVE_WITH_SELECTION = "RESERVATION_WITH_SELECTION"
  const val AVAILABILITY_HEADER = "AVAILABILITY_HEADER"
}

object SpaceRenterUi {
  val HORIZONTAL_PADDING: Dp = 100.dp
  val ROW_WIDTH: Dp = 48.dp

  object IconDescriptions {
    const val BACK_BUTTON = "Back Button"
    const val EDIT_BUTTON = "Edit Space Renter"
  }

  object BottomBar {
    const val INFO = "Click on a space to setup a reservation!"
    const val APPROVE = "Approve"
    const val SHAPE_PER = 60

    fun selectSpace(selectedIndex: Int?) = "Space N°${(selectedIndex ?: 0) + 1}"

    fun spaceDetails(seats: Int, costPerHour: Double) = "${seats} seats - ${costPerHour}$"
  }

  object Misc {
    const val TOAST_SUCCESS = "Copied to clipboard"
    const val NO_TIME = "Closed"
    const val TITLE = "Details"
  }

  object SpaceSection {
    const val TITLE = "Available Spaces"
    val CARD_HEIGHT: Dp = 72.dp

    fun setSpaceNumber(index: Int) = "Space N°${index + 1}"

    fun setSpaceCapacity(seats: Int) = "Capacity: $seats seats"

    fun setSpacePrice(costPerHour: Double) = "$costPerHour\$ per hour"
  }

  object AvailabilitySection {
    const val TITLE = "Availability"

    fun todayDate(todayText: String) = "Today: $todayText"

    fun timeRange(start: String?, end: String?) = "$start - $end"
  }
}

/**
 * Composable that displays the Space Renter screen, including the top bar and Space Renter details.
 *
 * @param spaceId The ID of the Space Renter to display.
 * @param account The current user account.
 * @param viewModel The ViewModel providing space renter data.
 * @param onBack Callback invoked when the back button is pressed.
 * @param onReserve Callback invoked when the reserve button from the bottom bar is pressed.
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
            text = SpaceRenterUi.Misc.TITLE,
            onReturn = { onBack() },
            trailingIcons = {
              // Edit button should only show if current account is the space renter owner
              if (account.uid == (spaceState?.owner?.uid)) {
                IconButton(
                    onClick = { onEdit(spaceState) },
                    modifier = Modifier.testTag(SpaceRenterTestTags.SPACE_RENTER_EDIT_BUTTON)) {
                      Icon(
                          Icons.Default.Edit,
                          contentDescription = SpaceRenterUi.IconDescriptions.EDIT_BUTTON)
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
    // Only show the actual screen if space renter data is available
    spaceState?.let { space ->
      SpaceRenterDetails(
          spaceRenter = space,
          selectedIndex = selectedIndex,
          onSelect = { selectedIndex = it },
          modifier = Modifier.padding(innerPadding).fillMaxSize())
    }
        // Show a loading indicator, awaiting data to be fetched
        ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
  }
}

/**
 * Composable that displays detailed information about a space renter, including contact info,
 * availability, and game list.
 *
 * @param spaceRenter The space renter's data to display.
 * @param onSelect Callback invoked upon interaction (press) with a space (selection/unselection)
 * @param selectedIndex Index of the selected card, null if none is selected.
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
        AvailabilitySection(
            openingHours = spaceRenter.openingHours,
            dayTagPrefix = SpaceRenterTestTags.SPACE_RENTER_DAY_PREFIX,
            addPadding = true)
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
            text = spaceRenter.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)

        // Display address contact row
        ContactRow(
            icon = Icons.Default.Place,
            text = spaceRenter.address.name,
            testTag = SpaceRenterTestTags.SPACE_RENTER_ADDRESS_TEXT,
            buttonTestTag = SpaceRenterTestTags.SPACE_RENTER_ADDRESS_BUTTON)

        // Display phone contact row if provided
        if (spaceRenter.phone.isNotBlank()) {
          ContactRow(
              icon = Icons.Default.Phone,
              text = spaceRenter.phone,
              testTag = SpaceRenterTestTags.SPACE_RENTER_PHONE_TEXT,
              buttonTestTag = SpaceRenterTestTags.SPACE_RENTER_PHONE_BUTTON)
        }

        // Display email contact row
        ContactRow(
            icon = Icons.Default.Email,
            text = spaceRenter.email,
            testTag = SpaceRenterTestTags.SPACE_RENTER_EMAIL_TEXT,
            buttonTestTag = SpaceRenterTestTags.SPACE_RENTER_EMAIL_BUTTON)

        // Display website contact row if provided
        if (spaceRenter.website.isNotBlank()) {
          ContactRow(
              icon = Icons.Default.Language,
              text = spaceRenter.website,
              testTag = SpaceRenterTestTags.SPACE_RENTER_WEBSITE_TEXT,
              buttonTestTag = SpaceRenterTestTags.SPACE_RENTER_WEBSITE_BUTTON)
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
    testTag: String,
    buttonTestTag: String,
) {
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable {
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, SpaceRenterUi.Misc.TOAST_SUCCESS, Toast.LENGTH_SHORT).show()
              }
              .padding(vertical = Dimensions.Padding.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large)) {
        Icon(
            icon,
            contentDescription = null,
            tint = AppColors.neutral,
            modifier = Modifier.size(Dimensions.IconSize.standard).testTag(buttonTestTag))

        Text(
            text,
            modifier = Modifier.weight(Dimensions.Weight.full).testTag(testTag),
            maxLines = 1,
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
              Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = SpaceRenterUi.IconDescriptions.BACK_BUTTON)
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

/**
 * Composable that displays a section of available spaces in a horizontal pager format.
 *
 * @param spaces Carousel displaying a list of spaces to display.
 * @param selectedIndex The index of the currently selected space, null if none is selected.
 * @param onSelect Callback invoked upon selection/unselection of a space (click actions).
 * @param modifier Modifier(s) to be applied to the layout.
 */
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
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large)) {

        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Text(
                  text = SpaceRenterUi.AvailabilitySection.TITLE,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.SemiBold)

              PriceRangeChip(minPrice, maxPrice)
            }

        // Pager
        HorizontalPager(
            verticalAlignment = Alignment.Top,
            state = pagerState,
            modifier = Modifier.fillMaxWidth()) { page ->
              val start = page * spacesPerPage
              val end = minOf(start + spacesPerPage, spaces.size)
              val missing = spacesPerPage - (end - start)
              Column(
                  modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                  verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large)) {
                    for (i in start until end) {
                      SpaceCard(
                          space = spaces[i],
                          index = i,
                          isSelected = selectedIndex == i,
                          onClick = { onSelect(if (selectedIndex == i) null else i) })
                    }
                    // Code needed to fix scrolling issues, otherwise UI looks weird when going from
                    // tab to tab with less than 3 items
                    repeat(missing) {
                      Spacer(Modifier.height(SpaceRenterUi.SpaceSection.CARD_HEIGHT).fillMaxWidth())
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
                      Modifier.padding(Dimensions.Padding.small)
                          .size(
                              if (active) Dimensions.Padding.extraMedium
                              else Dimensions.Padding.medium)
                          .background(
                              color = if (active) AppColors.textIcons else AppColors.textIconsFade,
                              shape = CircleShape))
            }
          }
        }
      }
}

/**
 * Composable that displays a card representing a space with its details.
 *
 * @param space The space with data (seats and price) to display.
 * @param index The index of the space in the list.
 * @param isSelected Whether the card is currently selected.
 * @param onClick Callback invoked when the card is clicked.
 */
@Composable
private fun SpaceCard(space: Space, index: Int, isSelected: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(Dimensions.Spacing.large)

  Surface(
      modifier =
          Modifier.fillMaxWidth()
              .clickable { onClick() }
              .height(SpaceRenterUi.SpaceSection.CARD_HEIGHT),
      shape = shape,
      color = AppColors.secondary,
      border =
          if (isSelected) BorderStroke(Dimensions.DividerThickness.medium, AppColors.neutral)
          else null) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(Dimensions.Padding.large)
                    .testTag(SpaceRenterComponentsTestTags.SPACE_ROW_PREFIX + index),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = SpaceRenterUi.SpaceSection.setSpaceNumber(index),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.textIcons,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    text = SpaceRenterUi.SpaceSection.setSpaceCapacity(space.seats),
                    color = AppColors.textIcons,
                    style = MaterialTheme.typography.labelSmall)
              }

              Text(
                  text = SpaceRenterUi.SpaceSection.setSpacePrice(space.costPerHour),
                  color = AppColors.textIcons,
                  style = MaterialTheme.typography.bodySmall,
                  textAlign = TextAlign.End)
            }
      }
}

/**
 * Composable that displays a chip showing the price range of spaces, going from minimum to maximum
 * (with an icon to pretty-fy)
 *
 * @param minPrice The minimum price of the spaces.
 * @param maxPrice The maximum price of the spaces.
 */
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

/**
 * Composable that displays a row showing today's availability and opens a bottom popup with full
 * availability when clicked.
 *
 * @param openingHours List of opening hours for each day of the week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityRowPopup(openingHours: List<OpeningHours>) {
  var showSheet by remember { mutableStateOf(false) }

  val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
  val todayHours = openingHours.firstOrNull { it.day == today }

  val todayText =
      when {
        todayHours == null || todayHours.hours.isEmpty() -> SpaceRenterUi.Misc.NO_TIME
        todayHours.hours.size == 1 -> {
          val (start, end) = todayHours.hours.first()
          SpaceRenterUi.AvailabilitySection.timeRange(start, end)
        }
        else ->
            todayHours.hours.joinToString(" ") { (start, end) ->
              SpaceRenterUi.AvailabilitySection.timeRange(start, end)
            }
      }

  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.xxLarge)) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { showSheet = true }
                .testTag(SpaceRenterTestTags.AVAILABILITY_HEADER)
                .padding(vertical = Dimensions.Padding.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
          Column {
            Text(
                text = SpaceRenterUi.AvailabilitySection.TITLE,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Text(
                SpaceRenterUi.AvailabilitySection.todayDate(todayText),
                style = MaterialTheme.typography.bodyMedium)
          }

          Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.textIcons)
        }
  }

  if (showSheet) {
    ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = AppColors.primary) {
      AvailabilitySection(openingHours, SpaceRenterTestTags.SPACE_RENTER_DAY_PREFIX)
      Spacer(Modifier.height(Dimensions.Spacing.xLarge))
    }
  }
}

/**
 * Composable that displays a reservation bar as the bottom of the screen. If no space is selected,
 * prompts the user to select one.
 *
 * @param selectedSpace The currently selected space, or null if none is selected.
 * @param selectedIndex The index of the selected space, or null if none is selected.
 * @param onApprove Callback invoked when the approve button is pressed.
 */
@Composable
fun ReservationBar(selectedSpace: Space?, selectedIndex: Int?, onApprove: () -> Unit) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .background(Color.Transparent)
              .padding(
                  horizontal = Dimensions.Padding.extraLarge, vertical = Dimensions.Padding.large),
      contentAlignment = Alignment.BottomCenter) {
        if (selectedSpace == null) {

          Surface(
              modifier = Modifier.testTag(SpaceRenterTestTags.RESERVE_NO_SELECTION),
              shape = RoundedCornerShape(SpaceRenterUi.BottomBar.SHAPE_PER),
              color = AppColors.secondary,
              shadowElevation = Dimensions.Elevation.extraHigh) {
                Surface(
                    shape = RoundedCornerShape(SpaceRenterUi.BottomBar.SHAPE_PER),
                    color = AppColors.affirmative) {
                      Text(
                          text = SpaceRenterUi.BottomBar.INFO,
                          modifier =
                              Modifier.padding(
                                  horizontal = Dimensions.Padding.xLarge,
                                  vertical = Dimensions.Padding.large))
                    }
              }
        } else {

          Surface(
              Modifier.testTag(SpaceRenterTestTags.RESERVE_WITH_SELECTION),
              shape = RoundedCornerShape(SpaceRenterUi.BottomBar.SHAPE_PER),
              color = AppColors.secondary,
              shadowElevation = Dimensions.Elevation.high) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.extraLarge),
                    verticalAlignment = Alignment.CenterVertically) {
                      Box(
                          modifier =
                              Modifier.background(
                                      Color.Transparent,
                                      RoundedCornerShape(SpaceRenterUi.BottomBar.SHAPE_PER))
                                  .padding(
                                      horizontal = Dimensions.Padding.extraLarge,
                                      vertical = Dimensions.Padding.extraMedium)) {
                            Column {
                              Text(
                                  text = SpaceRenterUi.BottomBar.selectSpace(selectedIndex),
                                  fontWeight = FontWeight.SemiBold)
                              Text(
                                  text =
                                      SpaceRenterUi.BottomBar.spaceDetails(
                                          selectedSpace.seats, selectedSpace.costPerHour))
                            }
                          }

                      Spacer(Modifier.width(Dimensions.Spacing.large))

                      Button(
                          onClick = onApprove,
                          shape = RoundedCornerShape(SpaceRenterUi.BottomBar.SHAPE_PER),
                          colors =
                              ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
                          modifier = Modifier.height(Dimensions.ButtonSize.medium)) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSize.small))
                            Spacer(Modifier.width(Dimensions.Spacing.medium))
                            Text(text = SpaceRenterUi.BottomBar.APPROVE)
                          }
                    }
              }
        }
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
