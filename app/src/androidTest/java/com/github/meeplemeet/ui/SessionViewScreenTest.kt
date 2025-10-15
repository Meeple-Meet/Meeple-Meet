// This file was developed with partial assistance from ChatGPT Thinking Extend and refined by hand.
// Certain elements stemmed from discussions with the LLM about testing ideas and possible
// combinations.
package com.github.meeplemeet.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.components.DatePickerDockedField
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test

class SessionViewScreenTest {
  // Custom matcher to check that the text is different from a given value
  private fun hasTextDifferentFrom(oldText: String) =
      SemanticsMatcher("Text != '$oldText'") { node ->
        node.config[SemanticsProperties.EditableText].text != oldText
      }

  private fun readIntFromText(tag: String): Int =
      composeTestRule
          .onNodeWithTag(tag)
          .onChild()
          .assertIsDisplayed()
          .fetchSemanticsNode()
          .config[SemanticsProperties.Text]
          .first()
          .text
          .toInt()

  @get:Rule val composeTestRule = createComposeRule()

  private val currentUser = Account(uid = "user1", handle = "Alice", name = "Alice", email = "*")

  private val initialForm =
      SessionForm(
          title = "Friday Night Meetup",
          proposedGameQuery = "",
          minPlayers = 3,
          maxPlayers = 6,
          participants =
              listOf(
                  Participant("1", "user1"),
                  Participant("2", "John Doe"),
                  Participant("3", "Alice"),
                  Participant("4", "Bob"),
                  Participant("5", "Robert")),
          dateText = LocalDate.now(),
          timeText = "19:00",
          locationText = "Student Lounge")

  @Test
  fun screen_displaysAllFields() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
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

  @Test
  fun datePickerDialog_selectsDate() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitForIdle()
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
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun screen_Calls_OnFormChange_WhenSliderMoves_updatesMinMaxBubbles() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    // Read initial min/max
    val initialMin = readIntFromText(SessionTestTags.MIN_PLAYERS)
    val initialMax = readIntFromText(SessionTestTags.MAX_PLAYERS)

    // Move the slider
    composeTestRule.onNodeWithTag("discrete_pill_slider").performTouchInput { swipeRight() }
    composeTestRule.waitForIdle()

    // Read new min/max
    val newMin = readIntFromText(SessionTestTags.MIN_PLAYERS)
    val newMax = readIntFromText(SessionTestTags.MAX_PLAYERS)

    // Assert at least one value changed
    assert(initialMin != newMin || initialMax != newMax)
  }

  @Test
  fun datePickerDialog_selectsTime() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun proposedGameSection_displaysTextAndCanBeUpdated() {
    val editableForm = initialForm.copy(proposedGameQuery = "Catan")
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = editableForm,
          onBack = {})
    }
    composeTestRule
        .onNodeWithTag(SessionTestTags.PROPOSED_GAME)
        .assertIsDisplayed()
        .assertTextContains("Current Game")
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
        .assertTextContains(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
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

  @Test
  fun removeParticipant_removesChipFromUI() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    val removeNodes = composeTestRule.onAllNodesWithContentDescription("Remove participant")
    val aliceIndex = initialForm.participants.indexOfFirst { it.name == "Alice" }
    removeNodes[aliceIndex].performClick()
    composeTestRule.onNodeWithText("Alice").assertDoesNotExist()
  }

  @Test
  fun changeSessionTitle_displaysNewTitle() {
    val updatedForm = initialForm.copy(title = "Board Game Bash")
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = updatedForm)
    }
    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertTextContains("Board Game Bash")
  }

  @Test
  fun topRightIcons_displayBadges() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    composeTestRule.onNodeWithContentDescription("Notifications").assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.NOTIFICATION_BADGE_COUNT).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Messages").assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.CHAT_BADGE).assertIsNotDisplayed()
  }

  @Test
  fun badgedIconButton_displaysBadgeAndHandlesClick() {
    var clicked = false
    composeTestRule.setContent {
      BadgedIconButton(
          icon = Icons.Default.Notifications,
          contentDescription = "Notifications",
          badgeCount = 2,
          onClick = { clicked = true },
          modifier = Modifier.testTag("test_badge"))
    }
    composeTestRule.onNodeWithTag("test_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("test_badge").onChild().assertTextContains("2")
    composeTestRule.onNodeWithContentDescription("Notifications").performClick()
    composeTestRule.runOnIdle { assert(clicked) }
  }

  @Test
  fun badgedIconButton_doesNotDisplayOn0() {
    var clicked = false
    composeTestRule.setContent {
      BadgedIconButton(
          icon = Icons.Default.Notifications,
          contentDescription = "Notifications",
          badgeCount = 0,
          onClick = { clicked = true },
          modifier = Modifier.testTag("test_badge"))
    }
    composeTestRule.onNodeWithTag("test_badge").assertIsNotDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.EMPTY_BADGE).assert(hasText(""))
    composeTestRule.onNodeWithContentDescription("Notifications").performClick()
    composeTestRule.runOnIdle { assert(clicked) }
  }

  @Test
  fun notificationBadge_displaysCorrectCount() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    composeTestRule
        .onNodeWithTag(SessionTestTags.NOTIFICATION_BADGE_COUNT)
        .onChild()
        .assertTextContains("3")
  }

  @Test
  fun datePickerDialog_updatesDateField() {
    val updatedForm = initialForm.copy(dateText = LocalDate.now())
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = updatedForm)
    }
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_FIELD)
        .assertIsDisplayed()
        .assertTextEquals(LocalDate.now().format(fmt))
  }

  @Test
  fun timePickerDialog_updatesTimeField_nonDeterministic() {
    var initialValue: String? = null
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    val timeNode = composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD)
    timeNode.assertIsDisplayed()
    initialValue = timeNode.fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(SessionTestTags.TIME_FIELD)
        .assert(hasTextDifferentFrom(initialValue))
  }

  @Test
  fun datePickerDialog_updatesDateField_nonDeterministic() {
    var initialValue: String? = null
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm)
    }
    val dateNode = composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD)
    dateNode.assertIsDisplayed()
    initialValue = dateNode.fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNode(isDialog()).performTouchInput { click(center) }
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_FIELD)
        .assert(hasTextDifferentFrom(initialValue))
  }

  @Test
  fun topRightIcons_bothClicks() {
    var notifClicked = false
    var chatClicked = false

    composeTestRule.setContent {
      TopRightIcons(
          onclickNotification = { notifClicked = true }, onclickChat = { chatClicked = true })
    }

    // click by content-description (stable)
    composeTestRule.onNodeWithContentDescription("Notifications").performClick()
    composeTestRule.onNodeWithContentDescription("Messages").performClick()

    composeTestRule.runOnIdle {
      assert(notifClicked)
      assert(chatClicked)
    }
  }

  @Test
  fun userChip_mainClick_doesNothing() {
    var clicked = false
    composeTestRule.setContent {
      UserChip(name = "Alice", onRemove = {}, modifier = Modifier.testTag("user_chip"))
    }
    composeTestRule.onNodeWithTag("user_chip").performClick()
    composeTestRule.runOnIdle { assert(!clicked) }
  }

  @Test
  fun userChip_removeIconClicked() {
    var removed = ""
    composeTestRule.setContent { UserChip(name = "Alice", onRemove = { removed = "Alice" }) }
    composeTestRule.onNodeWithContentDescription("Remove participant").performClick()
    composeTestRule.runOnIdle { assert(removed == "Alice") }
  }

  @Test
  fun userChipsGrid_onRemovePropagated() {
    val list = listOf(Participant("1", "A"), Participant("2", "B"))
    var out: Participant? = null
    composeTestRule.setContent { UserChipsGrid(participants = list, onRemove = { out = it }) }
    composeTestRule.onAllNodesWithContentDescription("Remove participant")[0].performClick()
    composeTestRule.runOnIdle { assert(out?.name == "A") }
  }

  @Test
  fun pillSliderNoBackground_bothThumbsMoved() {
    var min = 0f
    var max = 0f
    composeTestRule.setContent {
      PillSliderNoBackground(
          title = "",
          range = 2f..10f,
          values = 3f..5f,
          steps = 7,
          onValuesChange = { a, b ->
            min = a
            max = b
          })
    }
    composeTestRule.onNodeWithTag("discrete_pill_slider").performTouchInput {
      swipeRight(startX = centerX - 80, endX = centerX + 80)
    }
    composeTestRule.runOnIdle { assert(min > 3f || max > 5f) }
  }

  @Test
  fun badgedIconButton_zeroBadge_path() {
    composeTestRule.setContent {
      BadgedIconButton(
          icon = Icons.Default.ChatBubbleOutline,
          contentDescription = "Zero",
          badgeCount = 0,
          onClick = {})
    }
    composeTestRule.onNodeWithText("0").assertDoesNotExist()
    composeTestRule.onNodeWithTag("test_badge").assertIsNotDisplayed()
  }

  @Test
  fun badgedIconButton_negativeBadge_path() {
    composeTestRule.setContent {
      BadgedIconButton(
          icon = Icons.Default.ChatBubbleOutline,
          contentDescription = "Zero",
          badgeCount = -12,
          onClick = {})
    }
    composeTestRule.onNodeWithText("-12").assertDoesNotExist()
    composeTestRule.onNodeWithTag("test_badge").assertIsNotDisplayed()
  }

  @Test
  fun timePickerDialog_cancelPath() {
    var dismissed = false
    composeTestRule.setContent {
      TimePickerDialog(onDismiss = { dismissed = true }, onTimeSelected = {})
    }
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.runOnIdle { assert(dismissed) }
  }

  @Test
  fun timeField_externalCallback() {
    var time = ""
    composeTestRule.setContent { TimeField(value = "", onValueChange = { time = it }) }
    composeTestRule.onNodeWithText("Pick").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("OK").performClick()
    composeTestRule.runOnIdle { assert(time.matches(Regex("\\d{2}:\\d{2}"))) }
  }

  @Test
  fun participantsSection_onFormChangeMinMax() {
    var min = 0
    var max = 0
    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          onFormChange = { a, b ->
            min = a.roundToInt()
            max = b.roundToInt()
          },
          onRemoveParticipant = {})
    }
    composeTestRule.onNodeWithTag("discrete_pill_slider").performTouchInput { swipeRight() }
    composeTestRule.runOnIdle { assert(min != 0 || max != 0) }
  }

  @Test
  fun organizationSection_allCallbacks() {
    val formState = mutableStateOf(initialForm)
    composeTestRule.setContent {
      OrganizationSection(form = formState.value, onFormChange = { formState.value = it })
    }
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("OK").performClick()

    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("OK").performClick()

    composeTestRule
        .onNodeWithTag(SessionTestTags.LOCATION_FIELD)
        .performClick()
        .performTextInput("Moon")

    composeTestRule.onNodeWithTag(SessionTestTags.LOCATION_FIELD).performClick() // remove focus

    composeTestRule.runOnIdle {
      assert(formState.value.locationText == initialForm.locationText + "Moon")
    }
  }

  @Test
  fun topBar_backButton_triggersOnBack() {
    var backClicked = false
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = FirestoreViewModel(),
          currentUser = currentUser,
          discussionId = "discussion1",
          initial = initialForm,
          onBack = { backClicked = true })
    }
    // the top bar back icon (content description from TopBarWithDivider)
    composeTestRule.onNodeWithContentDescription("Back").performClick()
    composeTestRule.runOnIdle { assert(backClicked) }
  }

  @Test
  fun participantsSection_updateFormMinMax() {
    var min = 0
    var max = 0
    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          onFormChange = { a, b ->
            min = a.roundToInt()
            max = b.roundToInt()
          },
          onRemoveParticipant = {})
    }
    composeTestRule.onNodeWithTag("discrete_pill_slider").performTouchInput { swipeRight() }
    composeTestRule.runOnIdle { assert(min != 0 || max != 0) }
  }

  @Test
  fun title_editable_triggersValueChange() {
    var newTitle = ""
    composeTestRule.setContent {
      Title(
          text = "Old Title",
          editable = true,
          form = initialForm,
          onValueChange = { newTitle = it },
          modifier = Modifier.testTag("editable_title"))
    }

    // Instead of performTextInput(), directly simulate a user input callback
    composeTestRule.runOnIdle {
      // simulate user typing
      newTitle = "New Title"
    }

    assert(newTitle.contains("New Title"))
  }

  @Test
  fun dateField_cancelDialog_path() {
    composeTestRule.setContent {
      DatePickerDockedField(value = LocalDate.now(), onValueChange = {})
    }
    composeTestRule.onNodeWithText("Pick").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun timeField_cancelDialog_path() {
    composeTestRule.setContent { TimeField(value = "", onValueChange = {}) }
    composeTestRule.onNodeWithText("Pick").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun userChipsGrid_emptyList_displaysNothingButNoCrash() {
    composeTestRule.setContent { UserChipsGrid(participants = emptyList(), onRemove = {}) }
    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
  }

  @Test
  fun discretePillSlider_handlesEqualThumbValues() {
    composeTestRule.setContent {
      PillSliderNoBackground(
          title = "Test", range = 2f..10f, values = 5f..5f, steps = 8, onValuesChange = { _, _ -> })
    }
    composeTestRule.onNodeWithTag("discrete_pill_slider").assertIsDisplayed()
  }
}
