package com.github.meeplemeet.ui.rental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.ui.components.DatePickerDockedField
import com.github.meeplemeet.ui.components.TimePickerField
import com.github.meeplemeet.ui.theme.Dimensions
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

object RentalDateTimePickerTestTags {
  const val DATE_FIELD = "rental_date_field"
  const val ERROR_MESSAGE = "rental_datetime_error"
}

/**
 * A combined date and time picker for rental operations.
 *
 * This component provides validation specific to rentals:
 * - Prevents selecting dates/times in the past
 * - Validates against space renter opening hours
 * - Shows appropriate error messages
 *
 * @param date The currently selected date.
 * @param time The currently selected time.
 * @param onDateChange Callback when the date changes.
 * @param onTimeChange Callback when the time changes.
 * @param openingHours Optional opening hours to validate against.
 * @param otherDate Optional other date (start/end) for comparison.
 * @param otherTime Optional other time (start/end) for comparison.
 * @param isStartDateTime Whether this is the start (true) or end (false) date/time.
 * @param modifier Modifier for the component.
 */
@Composable
fun RentalDateAndTimePicker(
    date: LocalDate?,
    time: LocalTime?,
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    openingHours: List<OpeningHours>? = null,
    otherDate: LocalDate? = null,
    otherTime: LocalTime? = null,
    isStartDateTime: Boolean = true
) {
  Column(modifier = modifier) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium))) {
          Box(Modifier.fillMaxWidth(0.55f)) {
            DatePickerDockedField(
                value = date,
                onValueChange = onDateChange,
                label = if (isStartDateTime) "Start Date" else "End Date",
                testTagPick = "${RentalDateTimePickerTestTags.DATE_FIELD}_pick",
                testTagDate = RentalDateTimePickerTestTags.DATE_FIELD)
          }

          Box(Modifier.weight(1f)) {
            TimePickerField(
                value = time,
                onValueChange = onTimeChange,
                label = if (isStartDateTime) "Start Time" else "End Time")
          }
        }

    // Validation and error messages
    val errorMessage =
        validateRentalDateTime(
            date = date,
            time = time,
            openingHours = openingHours,
            otherDate = otherDate,
            otherTime = otherTime,
            isStartDateTime = isStartDateTime)

    if (errorMessage != null) {
      Spacer(Modifier.height(Dimensions.Spacing.extraSmall))
      Text(
          text = errorMessage,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          modifier =
              Modifier.padding(start = Dimensions.Padding.medium)
                  .testTag(RentalDateTimePickerTestTags.ERROR_MESSAGE))
    }
  }
}

/**
 * Validates rental date/time selection.
 *
 * @return Error message if validation fails, null otherwise.
 */
fun validateRentalDateTime(
    date: LocalDate?,
    time: LocalTime?,
    openingHours: List<OpeningHours>?,
    otherDate: LocalDate?,
    otherTime: LocalTime?,
    isStartDateTime: Boolean
): String? {
  if (date == null || time == null) return null

  val now = java.time.LocalDateTime.now()
  val selectedDateTime = date.atTime(time)

  // Check if in the past
  if (selectedDateTime.isBefore(now)) {
    return "Cannot select a time in the past"
  }

  // Check against other date/time (start vs end)
  if (otherDate != null && otherTime != null) {
    val otherDateTime = otherDate.atTime(otherTime)

    // Only validate end time relationship if both dates are valid (not in past)
    if (!otherDateTime.isBefore(now)) {
      if (isStartDateTime && selectedDateTime.isAfter(otherDateTime)) {
        return "Start time must be before end time"
      }

      if (!isStartDateTime && selectedDateTime.isBefore(otherDateTime)) {
        return "End time must be after start time"
      }
    }

    // Check if spanning multiple days
    if (!isStartDateTime && date != otherDate) {
      return "Multi-day rentals require direct contact with space renter"
    }
  }

  // Validate against opening hours
  if (openingHours != null) {
    val dayOfWeek = date.dayOfWeek.value % 7 // Convert to 0-6 (Sunday=0)
    val dayHours = openingHours.find { it.day == dayOfWeek }

    if (dayHours == null || dayHours.hours.isEmpty()) {
      return "Space renter is closed on this day"
    }

    // Check if time falls within any of the opening hour slots
    val timeString = String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute)
    val isWithinHours =
        dayHours.hours.any { slot ->
          val slotStart = slot.open ?: "00:00"
          val slotEnd = slot.close ?: "23:59"

          // Simple string comparison works for HH:mm format
          timeString in slotStart..slotEnd
        }

    if (!isWithinHours) {
      val hoursStr = dayHours.hours.joinToString(", ") { slot -> "${slot.open} - ${slot.close}" }
      return "Outside opening hours ($hoursStr)"
    }
  }

  return null
}
