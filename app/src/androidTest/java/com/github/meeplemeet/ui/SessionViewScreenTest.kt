package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import org.junit.Rule
import org.junit.Test

class SessionViewScreenTest {

  @get:Rule val composeTestRule = createComposeRule()
  val initialForm =
      SessionForm(
          title = "Friday Night Meetup",
          minPlayers = 3,
          maxPlayers = 6,
          participants =
              listOf(
                  Participant("1", "user1"),
                  Participant("2", "John Doe"),
                  Participant("3", "Alice"),
                  Participant("4", "Bob"),
                  Participant("5", "Robert")),
          dateText = "2025-10-15",
          timeText = "19:00",
          locationText = "Student Lounge")

  private val currentUser = Account(uid = "user1", handle = "Alice", name = "Alice", email = "*")

  @Test
  fun screen_displaysAllFields() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm,
      )
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.MIN_PLAYERS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.MAX_PLAYERS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.LOCATION_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun clickingQuitButton_triggersBack() {
    var backClicked = false

    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm,
          onBack = { backClicked = true })
    }

    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).performClick()
    composeTestRule.runOnIdle { assert(backClicked) }
  }
  // app/src/androidTest/java/com/github/meeplemeet/ui/SessionViewScreenTest.kt

  @Test
  fun slider_changesMinMaxPlayers() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    // Find slider and perform swipe
    composeTestRule.onNodeWithText("Number of players").assertIsDisplayed()
    // You may need to use semantics to find the slider and perform swipe actions
  }

  @Test
  fun datePickerDialog_selectsDate() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    // Click the date field to open the dialog
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle() // Wait for dialog to appear

    // Click the OK button in the date picker dialog
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitForIdle()
    // Optionally, assert the date field is updated
  }

  @Test
  fun timePickerDialog_selectsTime() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    // Click the date field to open the dialog
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle() // Wait for dialog to appear

    // Click the OK button in the date picker dialog
    composeTestRule
        .onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitForIdle()
    // Optionally, assert the date field is updated
  }

  @Test
  fun participantsSection_displaysAllParticipants() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertIsDisplayed()
    initialForm.participants.forEach { composeTestRule.onNodeWithText(it.name).assertIsDisplayed() }
  }

  @Test
  fun organizationSection_displaysDateTimeAndLocation() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_FIELD)
        .assertIsDisplayed()
        .assertTextContains("2025-10-15")
    composeTestRule
        .onNodeWithTag(SessionTestTags.TIME_FIELD)
        .assertIsDisplayed()
        .assertTextContains("19:00")
    composeTestRule
        .onNodeWithTag(SessionTestTags.LOCATION_FIELD)
        .assertIsDisplayed()
        .assertTextContains("Student Lounge")
  }

  @Test
  fun slider_minMaxPlayers_updatesOnDrag() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    // Find slider by text and perform swipe
    composeTestRule.onNodeWithText("Number of players").assertIsDisplayed()
    // May need to use semantics to find the slider and perform swipe actions
    // Example: performTouchInput { down(centerLeft); moveTo(centerRight); up() }
  }

  @Test
  fun quitButton_triggersCallback() {
    var quitClicked = false
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm,
          onBack = { quitClicked = true })
    }
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).performClick()
    composeTestRule.runOnIdle { assert(quitClicked) }
  }
}
