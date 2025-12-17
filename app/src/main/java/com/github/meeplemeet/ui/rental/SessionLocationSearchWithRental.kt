package com.github.meeplemeet.ui.rental

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

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
 * @param rentalViewModel ViewModel for managing rentals.
 * @param onRentalSelected Callback invoked when a rental is selected, providing the rental ID and a
 *   constructed [Location].
 * @param sessionDate Current session date (optional, used for validation).
 * @param sessionTime Current session time (optional, used for validation).
 * @param onDateTimeUpdate Callback to update date/time when rental is selected.
 */
@Composable
fun SessionLocationSearchWithRental(
    account: Account,
    discussion: Discussion,
    sessionViewModel: CreateSessionViewModel,
    rentalViewModel: RentalViewModel = viewModel(),
    onRentalSelected: (String?, Location) -> Unit = { _, _ -> },
    sessionDate: LocalDate? = null,
    sessionTime: LocalTime? = null,
    onDateTimeUpdate: ((LocalDate, LocalTime) -> Unit)? = null,
    currentRentalId: String? = null
) {
  var showRentalSelector by remember { mutableStateOf(false) }
  val activeRentals by rentalViewModel.activeSpaceRentals.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  // Check if current rental is still valid when date/time changes
  LaunchedEffect(sessionDate, sessionTime, currentRentalId) {
    if (currentRentalId != null && sessionDate != null && sessionTime != null) {
      val currentRental = activeRentals.find { it.rental.uid == currentRentalId }
      if (currentRental != null) {
        val isStillCompatible = checkRentalCompatibility(currentRental, sessionDate, sessionTime)
        if (!isStillCompatible) {
          // Clear the rental association
          onRentalSelected(null, Location())
          // Clear the rental location
          sessionViewModel.clearLocationSearch()
          scope.launch {
            snackbarHostState.showSnackbar(
                "Session time moved outside rental period. Rental unlinked.")
          }
        }
      }
    }
  }

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
        Box(modifier = Modifier.size(Dimensions.ContainerSize.timeFieldHeight)) {
          // Badge positioned correctly
          if (activeRentals.isNotEmpty()) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = Dimensions.Spacing.small)) {
                  Text(activeRentals.size.toString(), style = MaterialTheme.typography.labelSmall)
                }
          }

          IconButton(onClick = { showRentalSelector = true }, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Default.EventAvailable,
                contentDescription = "Select from rented spaces",
                tint =
                    if (activeRentals.isNotEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

  // Rental selector dialog
  if (showRentalSelector) {
    RentalSelectorDialog(
        rentals = activeRentals,
        sessionDate = sessionDate,
        sessionTime = sessionTime,
        onSelectRental = { rentalInfo ->
          // Create a Location object with the real address but enhanced name
          val enhancedName = "${rentalInfo.detailInfo} - ${rentalInfo.resourceAddress.name}"
          val location = rentalInfo.resourceAddress.copy(name = enhancedName)

          // If no date/time set, use rental dates
          if ((sessionDate == null || sessionTime == null) && onDateTimeUpdate != null) {
            val rental = rentalInfo.rental
            val startInstant = rental.startDate.toDate().toInstant()
            val localDateTime =
                java.time.LocalDateTime.ofInstant(startInstant, java.time.ZoneId.systemDefault())
            onDateTimeUpdate(localDateTime.toLocalDate(), localDateTime.toLocalTime())
          }

          onRentalSelected(rentalInfo.rental.uid, location)
          showRentalSelector = false
        },
        onDismiss = { showRentalSelector = false })
  }

  // Snackbar for error messages
  SnackbarHost(snackbarHostState)
}
