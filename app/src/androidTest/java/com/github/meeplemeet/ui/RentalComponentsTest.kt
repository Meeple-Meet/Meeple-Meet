package com.github.meeplemeet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.rental.RentalViewModel
import com.github.meeplemeet.model.sessions.CreateSessionViewModel
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.ui.rental.RentalDateAndTimePicker
import com.github.meeplemeet.ui.rental.RentalDateTimePickerTestTags
import com.github.meeplemeet.ui.rental.RentalSelectorDialog
import com.github.meeplemeet.ui.rental.RentalSelectorTestTags
import com.github.meeplemeet.ui.rental.SessionLocationSearchWithRental
import com.github.meeplemeet.ui.rental.checkRentalCompatibility
import com.github.meeplemeet.ui.rental.validateRentalDateTime
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RentalComponentsTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var account: Account
  private lateinit var discussion: Discussion
  private lateinit var createSessionVM: CreateSessionViewModel
  private lateinit var rentalVM: RentalViewModel

  private val openingHours =
      listOf(
          OpeningHours(day = 1, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))),
          OpeningHours(day = 2, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))),
          OpeningHours(day = 3, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))),
          OpeningHours(day = 4, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))),
          OpeningHours(day = 5, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))))

  @Before
  fun setup() = runBlocking {
    account = accountRepository.createAccount("test_user", "Test User", "test@test.com", null)

    discussion =
        discussionRepository.createDiscussion(
            name = "Test Discussion",
            description = "Test",
            creatorId = account.uid,
            participants = listOf(account.uid))

    createSessionVM = CreateSessionViewModel(accountRepository, sessionRepository, gameRepository)
    rentalVM = RentalViewModel()

    cleanupAllRentals()
  }

  @After fun tearDown() = runBlocking { cleanupAllRentals() }

  private suspend fun cleanupAllRentals() {
    val snapshot = rentalsRepository.collection.get().await()
    val batch = rentalsRepository.db.batch()
    snapshot.documents.forEach { batch.delete(it.reference) }
    batch.commit().await()
  }

  @Test
  fun all_rental_components_tests() {
    var datePickerDate by mutableStateOf<LocalDate?>(null)
    var datePickerTime by mutableStateOf<LocalTime?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)
    var endTime by mutableStateOf<LocalTime?>(null)
    var showSelector by mutableStateOf(false)
    var selectedRentalId by mutableStateOf<String?>(null)
    var sessionDate by mutableStateOf<LocalDate?>(null)
    var sessionTime by mutableStateOf<LocalTime?>(null)

    compose.setContent {
      AppTheme {
        Column {
          // RentalDateAndTimePicker - start
          RentalDateAndTimePicker(
              date = datePickerDate,
              time = datePickerTime,
              onDateChange = { datePickerDate = it },
              onTimeChange = { datePickerTime = it },
              openingHours = openingHours,
              otherDate = endDate,
              otherTime = endTime,
              isStartDateTime = true)

          // RentalDateAndTimePicker - end
          RentalDateAndTimePicker(
              date = endDate,
              time = endTime,
              onDateChange = { endDate = it },
              onTimeChange = { endTime = it },
              openingHours = openingHours,
              otherDate = datePickerDate,
              otherTime = datePickerTime,
              isStartDateTime = false)

          // SessionLocationSearchWithRental
          SessionLocationSearchWithRental(
              account = account,
              discussion = discussion,
              sessionViewModel = createSessionVM,
              rentalViewModel = rentalVM,
              sessionDate = sessionDate,
              sessionTime = sessionTime,
              onDateTimeUpdate = { d, t ->
                sessionDate = d
                sessionTime = t
              },
              onRentalSelected = { id, _ -> selectedRentalId = id },
              currentRentalId = selectedRentalId)
        }

        // RentalSelectorDialog (conditionally shown)
        if (showSelector) {
          val rentals by rentalVM.activeSpaceRentals.collectAsState()
          RentalSelectorDialog(
              rentals = rentals,
              sessionDate = sessionDate,
              sessionTime = sessionTime,
              onSelectRental = {
                selectedRentalId = it.rental.uid
                showSelector = false
              },
              onDismiss = { showSelector = false },
              currentRentalId = selectedRentalId)
        }
      }
    }

    checkpoint("date_time_picker_displays_fields") {
      compose.onAllNodesWithTag(RentalDateTimePickerTestTags.DATE_FIELD).assertCountEquals(2)
      // The fields exist, but they show "Date" as the label, not "Start Date" / "End Date"
      // when no date is selected. Just verify the basic structure exists.
    }

    checkpoint("date_time_picker_no_error_initially") {
      compose.onAllNodesWithTag(RentalDateTimePickerTestTags.ERROR_MESSAGE).assertCountEquals(0)
    }

    checkpoint("date_time_picker_shows_error_for_past_date") {
      val yesterday = LocalDate.now().minusDays(1)
      compose.runOnUiThread {
        datePickerDate = yesterday
        datePickerTime = LocalTime.of(10, 0)
      }
      compose.waitForIdle()

      compose.onNodeWithTag(RentalDateTimePickerTestTags.ERROR_MESSAGE).assertExists()
      compose.onNodeWithText("Cannot select a time in the past").assertExists()

      // Reset
      compose.runOnUiThread {
        datePickerDate = null
        datePickerTime = null
      }
      compose.waitForIdle()
    }

    checkpoint("date_time_picker_shows_error_end_before_start") {
      val tomorrow = LocalDate.now().plusDays(1)
      compose.runOnUiThread {
        datePickerDate = tomorrow
        datePickerTime = LocalTime.of(14, 0)
        endDate = tomorrow
        endTime = LocalTime.of(10, 0) // Before start
      }
      compose.waitForIdle()

      // Only the end time picker should show an error
      compose.onNodeWithText("End time must be after start time").assertExists()

      // Reset
      compose.runOnUiThread {
        datePickerDate = null
        datePickerTime = null
        endDate = null
        endTime = null
      }
      compose.waitForIdle()
    }

    checkpoint("date_time_picker_shows_error_outside_opening_hours") {
      // Find next Monday
      val today = LocalDate.now()
      val daysUntilMonday = (DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7
      val nextMonday =
          if (daysUntilMonday == 0) today.plusWeeks(1) else today.plusDays(daysUntilMonday.toLong())

      compose.runOnUiThread {
        datePickerDate = nextMonday
        datePickerTime = LocalTime.of(20, 0) // After 18:00 closing
      }
      compose.waitForIdle()

      compose.onNodeWithTag(RentalDateTimePickerTestTags.ERROR_MESSAGE).assertExists()
      compose.onNodeWithText("Outside opening hours", substring = true).assertExists()

      // Reset
      compose.runOnUiThread {
        datePickerDate = null
        datePickerTime = null
      }
      compose.waitForIdle()
    }

    checkpoint("date_time_picker_shows_error_closed_day") {
      // Find next Sunday
      val today = LocalDate.now()
      val daysUntilSunday = (7 - today.dayOfWeek.value) % 7
      val nextSunday =
          if (daysUntilSunday == 0) today.plusWeeks(1) else today.plusDays(daysUntilSunday.toLong())

      compose.runOnUiThread {
        datePickerDate = nextSunday
        datePickerTime = LocalTime.of(10, 0)
      }
      compose.waitForIdle()

      compose.onNodeWithTag(RentalDateTimePickerTestTags.ERROR_MESSAGE).assertExists()
      compose.onNodeWithText("Space renter is closed on this day").assertExists()

      // Reset
      compose.runOnUiThread {
        datePickerDate = null
        datePickerTime = null
      }
      compose.waitForIdle()
    }

    checkpoint("location_search_with_rental_displays") {
      // The rental button (IconButton with EventAvailable icon) should exist
      compose.onNodeWithContentDescription("Select from rented spaces").assertExists()
    }

    checkpoint("location_search_rental_button_no_badge_initially") {
      // No rentals loaded yet, so no badge
      compose.onAllNodesWithText("0").assertCountEquals(0)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun rental_selector_with_actual_rental() = runBlocking {
    // Create a space renter and rental
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = account,
            name = "Test Space",
            phone = "1234567890",
            email = "space@test.com",
            website = "test.com",
            address = Location(0.0, 0.0, "Test Address"),
            openingHours = openingHours,
            spaces = listOf(Space(seats = 4, costPerHour = 10.0)))

    val tomorrow = LocalDate.now().plusDays(1)
    val startDateTime = tomorrow.atTime(10, 0)
    val endDateTime = tomorrow.atTime(14, 0)
    val now =
        Timestamp(
            java.util.Date.from(startDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant()))
    val future =
        Timestamp(
            java.util.Date.from(endDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant()))

    val rental =
        rentalsRepository.createRental(
            renterId = account.uid,
            type = com.github.meeplemeet.model.rental.RentalType.SPACE,
            resourceId = spaceRenter.id,
            resourceDetailId = "0",
            startDate = now,
            endDate = future,
            totalCost = 40.0)

    var showSelector by mutableStateOf(false)
    var selectedRentalId by mutableStateOf<String?>(null)
    var testSessionDate by mutableStateOf<LocalDate?>(null)
    var testSessionTime by mutableStateOf<LocalTime?>(null)

    compose.setContent {
      AppTheme {
        if (showSelector) {
          val rentals by rentalVM.activeSpaceRentals.collectAsState()
          RentalSelectorDialog(
              rentals = rentals,
              sessionDate = testSessionDate,
              sessionTime = testSessionTime,
              onSelectRental = {
                selectedRentalId = it.rental.uid
                showSelector = false
              },
              onDismiss = { showSelector = false },
              currentRentalId = selectedRentalId)
        }
      }
    }

    // Load rentals
    rentalVM.loadActiveSpaceRentals(account.uid)
    compose.waitForIdle()

    compose.waitUntil(5000) { rentalVM.activeSpaceRentals.value.isNotEmpty() }

    checkpoint("rental_selector_empty_state_initially") {
      compose.runOnUiThread { showSelector = true }
      compose.waitForIdle()

      // Wait for dialog to appear
      compose.waitUntilAtLeastOneExists(hasTestTag(RentalSelectorTestTags.DIALOG), 5000)
      compose.onNodeWithTag(RentalSelectorTestTags.DIALOG).assertExists()

      // Close button should exist
      compose.onNodeWithTag(RentalSelectorTestTags.CLOSE_BUTTON).assertExists()

      // List should exist (not empty state)
      compose.onNodeWithTag(RentalSelectorTestTags.LIST).assertExists()
    }

    checkpoint("rental_selector_displays_rental_details") {
      // Resource name
      compose.onNodeWithText("Test Space").assertExists()

      // Space details
      compose.onNodeWithText("Space N°1 - 4 seats").assertExists()

      // Address
      compose.onNodeWithText("Test Address").assertExists()

      // Cost
      compose.onNodeWithText("$40.00", substring = true).assertExists()
    }

    checkpoint("rental_selector_item_clickable") {
      compose
          .onNodeWithTag("${RentalSelectorTestTags.ITEM_PREFIX}${rental.uid}")
          .assertExists()
          .performClick()
      compose.waitForIdle()

      assert(selectedRentalId == rental.uid) { "Rental should be selected" }
      assert(!showSelector) { "Dialog should close after selection" }
    }

    checkpoint("rental_selector_close_button_works") {
      compose.runOnUiThread { showSelector = true }
      compose.waitForIdle()

      compose.onNodeWithTag(RentalSelectorTestTags.CLOSE_BUTTON).performClick()
      compose.waitForIdle()

      assert(!showSelector) { "Dialog should be closed" }
    }

    checkpoint("rental_selector_compatibility_check") {
      val incompatibleDate = LocalDate.now().plusDays(10)
      val incompatibleTime = LocalTime.of(10, 0)

      // Update state variables to trigger recomposition with incompatible date/time
      compose.runOnUiThread {
        testSessionDate = incompatibleDate
        testSessionTime = incompatibleTime
        showSelector = true
      }
      compose.waitForIdle()

      // Conflict warning should appear
      compose.onNodeWithTag(RentalSelectorTestTags.CONFLICT_WARNING).assertExists()
      compose
          .onNodeWithText("Only rentals matching your session date/time are selectable")
          .assertExists()

      // Incompatibility warning on item
      compose.onNodeWithText("Session time outside rental period").assertExists()
    }
  }

  @Test
  fun validation_functions_work_correctly() {
    checkpoint("validateRentalDateTime_null_inputs") {
      val error =
          validateRentalDateTime(
              date = null,
              time = null,
              openingHours = null,
              otherDate = null,
              otherTime = null,
              isStartDateTime = true)
      assert(error == null) { "Should return null for null inputs" }
    }

    checkpoint("validateRentalDateTime_valid_future_date") {
      val tomorrow = LocalDate.now().plusDays(1)
      val validTime = LocalTime.of(10, 0)

      val error =
          validateRentalDateTime(
              date = tomorrow,
              time = validTime,
              openingHours = openingHours,
              otherDate = null,
              otherTime = null,
              isStartDateTime = true)

      assert(error == null) { "Should return null for valid future date within hours" }
    }

    checkpoint("validateRentalDateTime_past_date") {
      val yesterday = LocalDate.now().minusDays(1)
      val time = LocalTime.of(10, 0)

      val error =
          validateRentalDateTime(
              date = yesterday,
              time = time,
              openingHours = null,
              otherDate = null,
              otherTime = null,
              isStartDateTime = true)

      assert(error == "Cannot select a time in the past")
    }

    checkpoint("validateRentalDateTime_start_after_end") {
      val tomorrow = LocalDate.now().plusDays(1)
      val startTime = LocalTime.of(14, 0)
      val endTime = LocalTime.of(10, 0)

      val error =
          validateRentalDateTime(
              date = tomorrow,
              time = startTime,
              openingHours = null,
              otherDate = tomorrow,
              otherTime = endTime,
              isStartDateTime = true)

      assert(error == "Start time must be before end time")
    }

    checkpoint("validateRentalDateTime_multiday_rental") {
      val day1 = LocalDate.now().plusDays(1)
      val day2 = LocalDate.now().plusDays(2)
      val time = LocalTime.of(10, 0)

      val error =
          validateRentalDateTime(
              date = day2,
              time = time,
              openingHours = null,
              otherDate = day1,
              otherTime = time,
              isStartDateTime = false)

      assert(error == "Multi-day rentals require direct contact with space renter")
    }

    checkpoint("validateRentalDateTime_closed_day") {
      // Find next Sunday
      val today = LocalDate.now()
      val daysUntilSunday = (7 - today.dayOfWeek.value) % 7
      val sunday =
          if (daysUntilSunday == 0) today.plusWeeks(1) else today.plusDays(daysUntilSunday.toLong())
      val time = LocalTime.of(10, 0)

      val error =
          validateRentalDateTime(
              date = sunday,
              time = time,
              openingHours = openingHours, // Only Mon-Fri
              otherDate = null,
              otherTime = null,
              isStartDateTime = true)

      assert(error == "Space renter is closed on this day")
    }

    checkpoint("validateRentalDateTime_outside_hours") {
      // Find next Monday
      val today = LocalDate.now()
      val daysUntilMonday = (DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7
      val monday =
          if (daysUntilMonday == 0) today.plusWeeks(1) else today.plusDays(daysUntilMonday.toLong())
      val earlyTime = LocalTime.of(7, 0) // Before 09:00

      val error =
          validateRentalDateTime(
              date = monday,
              time = earlyTime,
              openingHours = openingHours,
              otherDate = null,
              otherTime = null,
              isStartDateTime = true)

      assert(error != null && error.contains("Outside opening hours")) {
        "Expected error containing 'Outside opening hours' but got: $error"
      }
    }
  }

  @Test
  fun checkRentalCompatibility_function_works() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = account,
            name = "Compat Test Space",
            phone = "1234567890",
            email = "compat@test.com",
            website = "test.com",
            address = Location(0.0, 0.0, "Compat Address"),
            openingHours = openingHours,
            spaces = listOf(Space(seats = 4, costPerHour = 10.0)))

    val tomorrow = LocalDate.now().plusDays(1)
    val startDateTime = tomorrow.atTime(10, 0)
    val endDateTime = tomorrow.atTime(14, 0)
    val now =
        Timestamp(
            java.util.Date.from(startDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant()))
    val future =
        Timestamp(
            java.util.Date.from(endDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant()))

    val rental =
        rentalsRepository.createRental(
            renterId = account.uid,
            type = com.github.meeplemeet.model.rental.RentalType.SPACE,
            resourceId = spaceRenter.id,
            resourceDetailId = "0",
            startDate = now,
            endDate = future,
            totalCost = 40.0)

    val rentalInfo =
        com.github.meeplemeet.model.rental.RentalResourceInfo(
            rental = rental,
            resourceName = "Compat Test Space",
            resourceAddress = Location(0.0, 0.0, "Compat Address"),
            detailInfo = "Space N°1 - 4 seats")

    checkpoint("compatibility_null_datetime") {
      val isCompatible = checkRentalCompatibility(rentalInfo, null, null)
      assert(isCompatible) { "Should be compatible when no session date/time" }
    }

    checkpoint("compatibility_within_rental_period") {
      val matchingTime = LocalTime.of(12, 0) // Between 10:00 and 14:00
      val isCompatible = checkRentalCompatibility(rentalInfo, tomorrow, matchingTime)
      assert(isCompatible) { "Should be compatible when session is within rental period" }
    }

    checkpoint("compatibility_before_rental_start") {
      val beforeTime = LocalTime.of(8, 0) // Before 10:00
      val isCompatible = checkRentalCompatibility(rentalInfo, tomorrow, beforeTime)
      assert(!isCompatible) { "Should be incompatible when session is before rental start" }
    }

    checkpoint("compatibility_after_rental_end") {
      val afterTime = LocalTime.of(16, 0) // After 14:00
      val isCompatible = checkRentalCompatibility(rentalInfo, tomorrow, afterTime)
      assert(!isCompatible) { "Should be incompatible when session is after rental end" }
    }
  }
}
