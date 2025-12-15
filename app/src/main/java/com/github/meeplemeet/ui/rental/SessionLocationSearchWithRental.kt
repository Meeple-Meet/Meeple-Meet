package com.github.meeplemeet.ui.rental

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.rental.RentalViewModel
import com.github.meeplemeet.model.sessions.CreateSessionViewModel
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.components.SessionLocationSearchButton
import com.github.meeplemeet.ui.theme.Dimensions

/**
 * A search bar for selecting a session location, with the option to choose from the user's active
 * rentals.
 *
 * This composable combines the standard session location search with a rental selector dialog. It
 * allows the user to either:
 * - Pick a location manually via the search bar.
 * - Select one of their already rented spaces via the rental selector.
 *
 * @param account The user's account.
 * @param discussion The discussion associated with the session.
 * @param sessionViewModel ViewModel for managing session state.
 * @param rentalViewModel ViewModel for managing rentals (defaults to [RentalViewModel]).
 * @param onRentalSelected Callback invoked when a rental is selected, providing the rental ID and a
 *   constructed [Location].
 */
@Composable
fun SessionLocationSearchWithRental(
    account: Account,
    discussion: Discussion,
    sessionViewModel: CreateSessionViewModel,
    rentalViewModel: RentalViewModel = viewModel(),
    onRentalSelected: (String, Location) -> Unit = { _, _ -> }
) {
  var showRentalSelector by remember { mutableStateOf(false) }
  val activeRentals by rentalViewModel.activeSpaceRentals.collectAsStateWithLifecycle()

  LaunchedEffect(account.uid) { rentalViewModel.loadActiveSpaceRentals(account.uid) }

  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
        // Standard location search bar
        Box(modifier = Modifier.weight(1f)) {
          SessionLocationSearchButton(
              account = account, discussion = discussion, viewModel = sessionViewModel)
        }

        // Button to open the rental selector dialog
        IconButton(
            onClick = { showRentalSelector = true },
            modifier = Modifier.size(Dimensions.ContainerSize.timeFieldHeight)) {
              Badge(
                  containerColor =
                      if (activeRentals.isNotEmpty()) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant) {
                    if (activeRentals.isNotEmpty()) {
                      Text(activeRentals.size.toString())
                    }
                  }
              Icon(
                  imageVector = Icons.Default.EventAvailable,
                  contentDescription = "Select from rented spaces",
                  tint =
                      if (activeRentals.isNotEmpty()) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant)
            }
      }

  // Rental selector dialog
  if (showRentalSelector) {
    RentalSelectorDialog(
        rentals = activeRentals,
        onSelectRental = { rentalInfo ->
          // Create a Location object based on the rental's address information
          val location =
              rentalInfo.resourceAddress.copy(
                  name = "${rentalInfo.resourceName} - ${rentalInfo.detailInfo}")

          onRentalSelected(rentalInfo.rental.uid, location)

          showRentalSelector = false
        },
        onDismiss = { showRentalSelector = false })
  }
}
