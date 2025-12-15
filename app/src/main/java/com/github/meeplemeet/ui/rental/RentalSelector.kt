package com.github.meeplemeet.ui.rental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.meeplemeet.model.rental.RentalResourceInfo
import com.github.meeplemeet.ui.theme.Dimensions
import java.text.SimpleDateFormat
import java.util.*

object RentalSelectorTestTags {
  const val DIALOG = "rental_selector_dialog"
  const val LIST = "rental_selector_list"
  const val ITEM_PREFIX = "rental_selector_item_"
  const val EMPTY_STATE = "rental_selector_empty"
  const val CLOSE_BUTTON = "rental_selector_close"
}

/**
 * Dialog for selecting one of the user's active rentals.
 *
 * This dialog displays either:
 * - An empty state if no active rentals exist.
 * - A scrollable list of active rentals, each represented by a card.
 *
 * @param rentals List of available rentals to choose from.
 * @param onSelectRental Callback invoked when a rental is selected.
 * @param onDismiss Callback invoked when the dialog is closed.
 */
@Composable
fun RentalSelectorDialog(
    rentals: List<RentalResourceInfo>,
    onSelectRental: (RentalResourceInfo) -> Unit,
    onDismiss: () -> Unit
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
        modifier =
            Modifier.fillMaxWidth().fillMaxHeight(0.8f).testTag(RentalSelectorTestTags.DIALOG),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        tonalElevation = Dimensions.Elevation.high) {
          Column(modifier = Modifier.fillMaxSize().padding(Dimensions.Padding.large)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                  Text(
                      text = "Select Rented Space",
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.Bold)
                  IconButton(
                      onClick = onDismiss,
                      modifier = Modifier.testTag(RentalSelectorTestTags.CLOSE_BUTTON)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                      }
                }

            Spacer(Modifier.height(Dimensions.Spacing.medium))

            if (rentals.isEmpty()) {
              // Empty state when no rentals are available
              Box(
                  modifier = Modifier.fillMaxSize().testTag(RentalSelectorTestTags.EMPTY_STATE),
                  contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
                          Icon(
                              imageVector = Icons.Default.EventBusy,
                              contentDescription = null,
                              modifier = Modifier.size(64.dp),
                              tint = MaterialTheme.colorScheme.onSurfaceVariant)
                          Text(
                              text = "No active space rentals",
                              style = MaterialTheme.typography.bodyLarge,
                              color = MaterialTheme.colorScheme.onSurfaceVariant)
                          Text(
                              text = "Rent a space first to use it for your session",
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                  }
            } else {
              // List of active rentals
              LazyColumn(
                  modifier = Modifier.fillMaxSize().testTag(RentalSelectorTestTags.LIST),
                  verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
                    items(rentals, key = { it.rental.uid }) { rentalInfo ->
                      RentalSelectorItem(
                          rentalInfo = rentalInfo,
                          onClick = { onSelectRental(rentalInfo) },
                          modifier =
                              Modifier.testTag(
                                  "${RentalSelectorTestTags.ITEM_PREFIX}${rentalInfo.rental.uid}"))
                    }
                  }
            }
          }
        }
  }
}

/**
 * Individual item in the rental selection list.
 *
 * Displays:
 * - Resource name (e.g., space renter business).
 * - Space details (e.g., "Space #2 - 8 seats").
 * - Address of the resource.
 * - Rental period (start and end date/time).
 * - Total cost of the rental.
 *
 * @param rentalInfo Information about the rental and its resource.
 * @param onClick Callback invoked when the item is clicked.
 * @param modifier Optional modifier for styling and test tags.
 */
@Composable
private fun RentalSelectorItem(
    rentalInfo: RentalResourceInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
  /** Date formatter */
  val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
  val startDateStr = dateFormat.format(rentalInfo.rental.startDate.toDate())
  val endDateStr = dateFormat.format(rentalInfo.rental.endDate.toDate())

  Card(
      modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
      elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.medium)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimensions.Padding.large),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)) {
              // Resource name
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.IconSize.medium),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Dimensions.Spacing.small))
                Text(
                    text = rentalInfo.resourceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
              }

              // Space details
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MeetingRoom,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.IconSize.small),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Dimensions.Spacing.small))
                Text(
                    text = rentalInfo.detailInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
              }

              // Address
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.IconSize.small),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Dimensions.Spacing.small))
                Text(
                    text = rentalInfo.resourceAddress.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
              }

              Divider(modifier = Modifier.padding(vertical = Dimensions.Spacing.small))

              // Rental period
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                      Text(
                          text = "Start",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                      Text(text = startDateStr, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                      Text(
                          text = "End",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                      Text(text = endDateStr, style = MaterialTheme.typography.bodySmall)
                    }
                  }

              // Cost
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "$%.2f".format(rentalInfo.rental.totalCost),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)
              }
            }
      }
}
