package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import org.junit.Rule
import org.junit.Test

class SessionViewScreenTest {

    @get:Rule val composeTestRule = createComposeRule()
    val initialForm = SessionForm(
        title = "Friday Night Meetup",
        minPlayers = 3,
        maxPlayers = 6,
        participants = listOf(
            Participant("1", "user1"),
            Participant("2", "John Doe"),
            Participant("3", "Alice"),
            Participant("4", "Bob"),
            Participant("5", "Robert")
        ),
        dateText = "2025-10-15",
        timeText = "19:00",
        locationText = "Student Lounge"
    )

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
                onBack = { backClicked = true }
            )
        }

        composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).performClick()
        composeTestRule.runOnIdle { assert(backClicked) }
    }
}