// AI was used to help comment this screen
package com.github.meeplemeet.ui.space_renter

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterViewModel
import com.github.meeplemeet.ui.components.SpacesList
import com.github.meeplemeet.ui.shops.AvailabilitySection
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions

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

  fun emailContactRow(email: String) = "- Email: $email"

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
    onEdit: (SpaceRenter?) -> Unit = {},
) {
  // Collect the current space renter state from the ViewModel
  val spaceState by viewModel.spaceRenter.collectAsStateWithLifecycle()
  // Trigger loading of space renter data when spaceId changes
  LaunchedEffect(spaceId) { viewModel.getSpaceRenter(spaceId) }

  Scaffold(
      topBar = {
        TopBarAndDivider(
            text = spaceState?.name ?: "Space Renter",
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
      }) { innerPadding ->
        // Show space renter details if loaded, otherwise show a loading indicator
        spaceState?.let { space ->
          SpaceRenterDetails(
              spaceRenter = space,
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
 * Composable that displays detailed information about a space renter, including contact info,
 * availability, and game list.
 *
 * @param spaceRenter The space renter data to display.
 * @param modifier Modifier to be applied to the layout.
 */
@Composable
fun SpaceRenterDetails(spaceRenter: SpaceRenter, modifier: Modifier = Modifier) {
  Column(
      modifier =
          modifier
              .verticalScroll(rememberScrollState())
              .padding(bottom = Dimensions.Padding.xxxLarge),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xxLarge)) {
        ContactSection(spaceRenter)
        HorizontalDivider(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = SpaceRenterUi.HORIZONTAL_PADDING))
        AvailabilitySection(spaceRenter.openingHours, SpaceRenterTestTags.SPACE_RENTER_DAY_PREFIX)
        HorizontalDivider(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = SpaceRenterUi.HORIZONTAL_PADDING))
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.xxxLarge)) {
              Text(
                  "Provided spaces",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.SemiBold)

              SpacesList(
                  spaces = spaceRenter.spaces,
                  modifier = Modifier.fillMaxWidth(),
                  onChange = { _, _ -> },
                  onDelete = {},
                  isEditing = false,
              )
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
            "Contact",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)

        // Display phone contact row
        if (spaceRenter.phone.isNotBlank()) {
          ContactRow(
              Icons.Default.Phone,
              SpaceRenterUi.phoneContactRow(spaceRenter.phone),
              SpaceRenterTestTags.SPACE_RENTER_PHONE_TEXT,
              SpaceRenterTestTags.SPACE_RENTER_PHONE_BUTTON)
        }

        // Display email contact row
        ContactRow(
            Icons.Default.Email,
            SpaceRenterUi.emailContactRow(spaceRenter.email),
            SpaceRenterTestTags.SPACE_RENTER_EMAIL_TEXT,
            SpaceRenterTestTags.SPACE_RENTER_EMAIL_BUTTON)

        // Display address contact row
        ContactRow(
            Icons.Default.Place,
            SpaceRenterUi.addressContactRow(spaceRenter.address.name),
            SpaceRenterTestTags.SPACE_RENTER_ADDRESS_TEXT,
            SpaceRenterTestTags.SPACE_RENTER_ADDRESS_BUTTON)

        // Display website contact row
        if (spaceRenter.website.isNotBlank()) {
          ContactRow(
              Icons.Default.Language,
              SpaceRenterUi.websiteContactRow(spaceRenter.website),
              SpaceRenterTestTags.SPACE_RENTER_WEBSITE_TEXT,
              SpaceRenterTestTags.SPACE_RENTER_WEBSITE_BUTTON)
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
fun ContactRow(icon: ImageVector, text: String, textTag: String, buttonTag: String) {
  val clipboardManager: ClipboardManager = LocalClipboardManager.current
  val context = LocalContext.current

  val copyToClipboard =
      remember(text) {
        {
          clipboardManager.setText(AnnotatedString(text))
          Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
      }

  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.small)) {
        Text(
            text,
            style = LocalTextStyle.current.copy(textIndent = TextIndent(restLine = 8.sp)),
            modifier = Modifier.weight(1f).testTag(textTag))

        IconButton(
            onClick = copyToClipboard,
            modifier = Modifier.size(Dimensions.IconSize.large).testTag(buttonTag)) {
              Icon(imageVector = icon, contentDescription = null, tint = AppColors.neutral)
            }
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
              Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
          }

      Text(
          text = text,
          style = MaterialTheme.typography.titleLarge,
          maxLines =
              Int.MAX_VALUE, // Allows vertical expansion when text is too to fit horizontally
          modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp).fillMaxWidth(),
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
