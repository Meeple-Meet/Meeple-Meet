package com.github.meeplemeet.ui.rental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.rental.RentalViewModel
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.ui.components.SectionCard
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.sessions.toTimestamp
import com.github.meeplemeet.ui.theme.Dimensions
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

object SpaceRentalTestTags {
  const val SCAFFOLD = "space_rental_scaffold"
  const val CONTENT = "space_rental_content"
  const val INFO_SECTION = "space_rental_info_section"
  const val DATETIME_SECTION = "space_rental_datetime_section"
  const val COST_SECTION = "space_rental_cost_section"
  const val NOTES_FIELD = "space_rental_notes_field"
  const val CONFIRM_BUTTON = "space_rental_confirm_button"
  const val CANCEL_BUTTON = "space_rental_cancel_button"
}

/**
 * Composable screen for renting a specific space.
 *
 * This screen allows a user to select a rental period, view space details, calculate the total
 * cost, and confirm or cancel the rental.
 *
 * @param account The account of the user making the rental.
 * @param spaceRenter The business providing rentable spaces.
 * @param space The specific space being rented.
 * @param spaceIndex The index of the space within the SpaceRenter's list of spaces.
 * @param viewModel ViewModel used to manage rental operations.
 * @param onBack Callback invoked when the user cancels or navigates back.
 * @param onSuccess Callback invoked after a successful rental creation, receiving the rental ID as
 *   a parameter.
 */
@Composable
fun SpaceRentalScreen(
    account: Account,
    spaceRenter: SpaceRenter,
    space: Space,
    spaceIndex: Int,
    viewModel: RentalViewModel = viewModel(),
    onBack: () -> Unit = {},
    onSuccess: (String) -> Unit = {}
) {
  var startDate by remember { mutableStateOf<LocalDate?>(null) }
  var startTime by remember { mutableStateOf<LocalTime?>(null) }
  var endDate by remember { mutableStateOf<LocalDate?>(null) }
  var endTime by remember { mutableStateOf<LocalTime?>(null) }
  var notes by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  /** Computed total cost of the rental. */
  val totalCost =
      remember(startDate, startTime, endDate, endTime) {
        if (startDate != null && startTime != null && endDate != null && endTime != null) {
          val start = startDate!!.atTime(startTime!!)
          val end = endDate!!.atTime(endTime!!)
          val hours = Duration.between(start, end).toHours()
          if (hours > 0) hours * space.costPerHour else 0.0
        } else {
          0.0
        }
      }

  /** Check if all validations pass */
  val hasValidationErrors =
      remember(startDate, startTime, endDate, endTime) {
        if (startDate == null || startTime == null || endDate == null || endTime == null) {
          true
        } else {
          // Check for any validation errors
          val startError =
              validateRentalDateTime(
                  date = startDate,
                  time = startTime,
                  openingHours = spaceRenter.openingHours,
                  otherDate = endDate,
                  otherTime = endTime,
                  isStartDateTime = true)

          val endError =
              validateRentalDateTime(
                  date = endDate,
                  time = endTime,
                  openingHours = spaceRenter.openingHours,
                  otherDate = startDate,
                  otherTime = startTime,
                  isStartDateTime = false)

          startError != null || endError != null
        }
      }

  val isValid = !hasValidationErrors && totalCost > 0

  LaunchedEffect(errorMessage) {
    errorMessage?.let {
      snackbarHostState.showSnackbar(it)
      errorMessage = null
    }
  }

  Scaffold(
      modifier = Modifier.testTag(SpaceRentalTestTags.SCAFFOLD),
      topBar = { TopBarWithDivider(text = "Rent Space", onReturn = onBack) },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.Padding.xxxLarge,
                        vertical = Dimensions.Padding.xxLarge),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraLarge)) {
              OutlinedButton(
                  onClick = onBack,
                  modifier = Modifier.weight(1f).testTag(SpaceRentalTestTags.CANCEL_BUTTON),
                  shape = CircleShape) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(Dimensions.Spacing.medium))
                    Text("Cancel")
                  }

              Button(
                  onClick = {
                    if (!isValid) return@Button
                    scope.launch {
                      isLoading = true
                      try {
                        val rentalId =
                            viewModel.createSpaceRental(
                                renterId = account.uid,
                                spaceRenterId = spaceRenter.id,
                                spaceIndex = spaceIndex.toString(),
                                startDate = toTimestamp(startDate, startTime),
                                endDate = toTimestamp(endDate, endTime),
                                totalCost = totalCost,
                                notes = notes)
                        onSuccess(rentalId)
                      } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to create rental"
                      } finally {
                        isLoading = false
                      }
                    }
                  },
                  enabled = isValid && !isLoading,
                  modifier = Modifier.weight(1f).testTag(SpaceRentalTestTags.CONFIRM_BUTTON),
                  shape = CircleShape) {
                    if (isLoading) {
                      CircularProgressIndicator(
                          modifier = Modifier.size(Dimensions.IconSize.small),
                          color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                      Icon(Icons.Default.Check, contentDescription = null)
                      Spacer(Modifier.width(Dimensions.Spacing.medium))
                      Text("Confirm")
                    }
                  }
            }
      }) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimensions.Padding.extraLarge)
                    .testTag(SpaceRentalTestTags.CONTENT),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraLarge)) {
              // Space infos
              SectionCard(
                  modifier = Modifier.fillMaxWidth().testTag(SpaceRentalTestTags.INFO_SECTION)) {
                    Text(
                        text = "Space Information",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)

                    Spacer(Modifier.height(Dimensions.Spacing.medium))

                    InfoRow(Icons.Default.Business, spaceRenter.name)
                    InfoRow(Icons.Default.LocationOn, spaceRenter.address.name)
                    InfoRow(Icons.Default.MeetingRoom, "Space N°${spaceIndex + 1}")
                    InfoRow(Icons.Default.People, "${space.seats} seats")
                    InfoRow(Icons.Default.AttachMoney, "${space.costPerHour}$/hour")
                  }

              // Date/hours selection
              SectionCard(
                  modifier =
                      Modifier.fillMaxWidth().testTag(SpaceRentalTestTags.DATETIME_SECTION)) {
                    Text(
                        text = "Rental Period",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)

                    Spacer(Modifier.height(Dimensions.Spacing.small))

                    Text("Start", style = MaterialTheme.typography.titleMedium)
                    RentalDateAndTimePicker(
                        date = startDate,
                        time = startTime,
                        onDateChange = { startDate = it },
                        onTimeChange = { startTime = it },
                        openingHours = spaceRenter.openingHours,
                        otherDate = endDate,
                        otherTime = endTime,
                        isStartDateTime = true)

                    Spacer(Modifier.height(Dimensions.Spacing.large))

                    Text("End", style = MaterialTheme.typography.titleMedium)
                    RentalDateAndTimePicker(
                        date = endDate,
                        time = endTime,
                        onDateChange = { endDate = it },
                        onTimeChange = { endTime = it },
                        openingHours = spaceRenter.openingHours,
                        otherDate = startDate,
                        otherTime = startTime,
                        isStartDateTime = false)
                  }

              // Cost
              if (totalCost > 0) {
                SectionCard(
                    modifier = Modifier.fillMaxWidth().testTag(SpaceRentalTestTags.COST_SECTION)) {
                      Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Total Cost",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text(
                                text = "$%.2f".format(totalCost),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                          }
                    }
              }

              // Notes
              OutlinedTextField(
                  value = notes,
                  onValueChange = { notes = it },
                  label = { Text("Notes (optional)") },
                  modifier = Modifier.fillMaxWidth().testTag(SpaceRentalTestTags.NOTES_FIELD),
                  minLines = 3,
                  maxLines = 5)
            }
      }
}

/**
 * Displays a single row of information with an icon and text.
 *
 * @param icon The icon representing the type of information (e.g., Business, Location).
 * @param text The text value to display next to the icon.
 */
@Composable
private fun InfoRow(icon: ImageVector, text: String) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(vertical = Dimensions.Padding.small)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimensions.IconSize.large))
        Spacer(Modifier.width(Dimensions.Spacing.medium))
        Text(text, style = MaterialTheme.typography.bodyLarge)
      }
}
