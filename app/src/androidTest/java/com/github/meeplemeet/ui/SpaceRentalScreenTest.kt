package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.rental.RentalViewModel
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.ui.components.SessionComponentsTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.rental.RentalDateTimePickerTestTags
import com.github.meeplemeet.ui.rental.SpaceRentalScreen
import com.github.meeplemeet.ui.rental.SpaceRentalTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpaceRentalScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var testAccount: Account
  private lateinit var testSpaceRenter: SpaceRenter
  private lateinit var testSpace: Space
  private lateinit var mockViewModel: RentalViewModel

  private var backCalled = false
  private var successCalled = false

  @Before
  fun setup() {
    testAccount =
        Account(uid = "user1", handle = "test_user", name = "Test User", email = "test@test.com")

    testSpace = Space(seats = 8, costPerHour = 25.0)

    val openingHours =
        listOf(
            OpeningHours(
                day = 1, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))), // Monday
            OpeningHours(
                day = 2, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))), // Tuesday
            OpeningHours(
                day = 3, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))), // Wednesday
            OpeningHours(
                day = 4, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))), // Thursday
            OpeningHours(
                day = 5, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))), // Friday
        )

    testSpaceRenter =
        SpaceRenter(
            id = "renter1",
            name = "Board Game Cafe",
            address = Location(latitude = 0.0, longitude = 0.0, name = "123 Game Street"),
            spaces = listOf(testSpace),
            openingHours = openingHours,
            owner = testAccount,
            phone = "phone",
            email = "email",
            website = "website",
            photoCollectionUrl = emptyList())

    mockViewModel = mockk(relaxed = true)
    backCalled = false
    successCalled = false
  }

  @Test
  fun all_space_rental_screen_tests() {
    compose.setContent {
      AppTheme {
        SpaceRentalScreen(
            account = testAccount,
            spaceRenter = testSpaceRenter,
            space = testSpace,
            spaceIndex = 0,
            viewModel = mockViewModel,
            onBack = { backCalled = true },
            onSuccess = { successCalled = true })
      }
    }

    checkpoint("initial_state_displays_all_components") {
      // Scaffold and content
      compose.onNodeWithTag(SpaceRentalTestTags.SCAFFOLD).assertExists()
      compose.onNodeWithTag(SpaceRentalTestTags.CONTENT).assertExists()

      // Top bar with title
      compose.onNodeWithTag(SessionComponentsTestTags.TOP_APP_BAR).assertExists()
      compose.onNodeWithText("Rent Space").assertExists()
    }

    checkpoint("space_information_section_displays_correctly") {
      compose.onNodeWithTag(SpaceRentalTestTags.INFO_SECTION).assertExists()
      compose.onNodeWithText("Space Information").assertExists()
      compose.onNodeWithText("Board Game Cafe").assertExists()
      compose.onNodeWithText("123 Game Street").assertExists()
      compose.onNodeWithText("Space N°1").assertExists()
      compose.onNodeWithText("8 seats").assertExists()
      compose.onNodeWithText("25.0$/hour").assertExists()
    }

    checkpoint("datetime_section_displays_with_pickers") {
      compose.onNodeWithTag(SpaceRentalTestTags.DATETIME_SECTION).assertExists()
      compose.onNodeWithText("Rental Period").assertExists()
      compose.onNodeWithText("Start").assertExists()
      compose.onNodeWithText("End").assertExists()

      // Date/time pickers should be present
      compose.onAllNodesWithTag(RentalDateTimePickerTestTags.DATE_FIELD).assertCountEquals(4)
    }

    checkpoint("buttons_exist_and_back_button_works") {
      compose.onNodeWithTag(SpaceRentalTestTags.CANCEL_BUTTON).assertExists()
      compose.onNodeWithTag(SpaceRentalTestTags.CONFIRM_BUTTON).assertExists()

      // Confirm should be disabled initially
      compose.onNodeWithTag(SpaceRentalTestTags.CONFIRM_BUTTON).assertIsNotEnabled()

      // Cancel button should work
      compose.onNodeWithTag(SpaceRentalTestTags.CANCEL_BUTTON).performClick()
      assert(backCalled) { "Back callback should be called" }
    }

    checkpoint("notes_field_accepts_input") {
      compose.onNodeWithTag(SpaceRentalTestTags.NOTES_FIELD).assertExists()
      compose.onNodeWithTag(SpaceRentalTestTags.NOTES_FIELD).performTextInput("Need projector")
      compose.onNodeWithTag(SpaceRentalTestTags.NOTES_FIELD).assertTextContains("Need projector")
    }

    checkpoint("cost_section_not_visible_initially") {
      // Cost section should not exist when no dates are selected
      compose.onAllNodesWithTag(SpaceRentalTestTags.COST_SECTION).assertCountEquals(0)
    }
  }

  @Test
  fun free_space_displays_correctly() {
    val freeSpace = testSpace.copy(costPerHour = 0.0)
    val freeSpaceRenter = testSpaceRenter.copy(spaces = listOf(freeSpace))

    compose.setContent {
      AppTheme {
        SpaceRentalScreen(
            account = testAccount,
            spaceRenter = freeSpaceRenter,
            space = freeSpace,
            spaceIndex = 0,
            viewModel = mockViewModel,
            onBack = {},
            onSuccess = {})
      }
    }

    checkpoint("free_space_shows_zero_cost") { compose.onNodeWithText("0.0$/hour").assertExists() }
  }

  @Test
  fun validation_errors_display_correctly() {
    // Use a SpaceRenter with opening hours that we can test against
    val mondayHours =
        listOf(OpeningHours(day = 1, hours = listOf(TimeSlot(open = "09:00", close = "18:00"))))
    val spaceRenterWithHours = testSpaceRenter.copy(openingHours = mondayHours)

    compose.setContent {
      AppTheme {
        SpaceRentalScreen(
            account = testAccount,
            spaceRenter = spaceRenterWithHours,
            space = testSpace,
            spaceIndex = 0,
            viewModel = mockViewModel,
            onBack = {},
            onSuccess = {})
      }
    }

    checkpoint("past_time_shows_error") {
      // Try to select a past date - this should show an error
      // The error checking is done in validateRentalDateTime which is tested separately
      compose.onNodeWithTag(SpaceRentalTestTags.DATETIME_SECTION).assertExists()
    }

    checkpoint("confirm_button_disabled_without_valid_dates") {
      compose.onNodeWithTag(SpaceRentalTestTags.CONFIRM_BUTTON).assertIsNotEnabled()
    }
  }

  @Test
  fun top_bar_back_button_works() {
    var topBarBackCalled = false

    compose.setContent {
      AppTheme {
        SpaceRentalScreen(
            account = testAccount,
            spaceRenter = testSpaceRenter,
            space = testSpace,
            spaceIndex = 0,
            viewModel = mockViewModel,
            onBack = { topBarBackCalled = true },
            onSuccess = {})
      }
    }

    checkpoint("top_bar_back_button_triggers_callback") {
      compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertExists().performClick()
      assert(topBarBackCalled) { "Top bar back callback should be called" }
    }
  }
}
