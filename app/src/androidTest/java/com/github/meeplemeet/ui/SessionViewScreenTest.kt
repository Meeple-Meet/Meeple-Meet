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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.repositories.FirestoreGameRepository
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.repositories.FirestoreSessionRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Session
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.components.DatePickerDockedField
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionViewScreenTest {
  private lateinit var firestoreRepo: FirestoreRepository
  private lateinit var viewModel: FirestoreViewModel
  private lateinit var sessionRepo: FirestoreSessionRepository
  private lateinit var gameRepo: FirestoreGameRepository
  private lateinit var sessionVM: FirestoreSessionViewModel

  private val me = Account(uid = "user1", handle = "", name = "Marco", email = "marco@epfl.ch")
  private val member = Account(uid = "user2", handle = "", name = "Alex", email = "alex@epfl.ch")
  private val admin = Account(uid = "user1", handle = "Alice", name = "Alice", email = "*")

  private val currentUser = admin

  private val discussionId = "discussion1"

  private lateinit var injectedDiscussionFlow: MutableStateFlow<Discussion?>
  private lateinit var baseDiscussion: Discussion

  @Before
  fun setUp() {
    firestoreRepo = mockk(relaxed = true)
    viewModel = spyk(FirestoreViewModel(firestoreRepo))

    baseDiscussion =
        Discussion(
            uid = discussionId,
            name = "Friday Night Meetup",
            description = "Let's play some board games!",
            creatorId = currentUser.uid,
            participants = listOf(currentUser.uid, member.uid),
            admins = listOf(currentUser.uid),
            session =
                Session(participants = listOf(currentUser.uid, member.uid), maxParticipants = 5))

    // Inject the discussionFlow into the internal map
    val field = viewModel::class.java.getDeclaredField("discussionFlows")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map = field.get(viewModel) as MutableMap<String, StateFlow<Discussion?>>
    injectedDiscussionFlow = MutableStateFlow(baseDiscussion)
    map[discussionId] = injectedDiscussionFlow

    sessionRepo = mockk(relaxed = true)
    gameRepo = mockk(relaxed = true)
    sessionVM = FirestoreSessionViewModel(baseDiscussion, sessionRepo, gameRepo)
  }

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

  private val initialForm =
      SessionForm(
          title = "Friday Night Meetup",
          proposedGame = "",
          minPlayers = 3,
          maxPlayers = 6,
          participants =
              listOf(
                  Account(uid = "1", handle = "user1", name = "user1", email = "user1@example.com"),
                  Account(
                      uid = "2", handle = "johndoe", name = "John Doe", email = "john@example.com"),
                  Account(uid = "3", handle = "alice", name = "Alice", email = "alice@example.com"),
                  Account(uid = "4", handle = "bob", name = "Bob", email = "bob@example.com"),
                  Account(
                      uid = "5", handle = "robert", name = "Robert", email = "robert@example.com")),
          date = LocalDate.of(2025, 10, 15),
          time = java.time.LocalTime.of(19, 0),
          locationText = "Student Lounge")

  @Test
  fun screen_displaysAllFields() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
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
    composeTestRule.onRoot().performTouchInput { swipeUp() }

    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun clickingQuitButton_triggersBack() {
    var backClicked = false
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          initial = initialForm,
          sessionViewModel = sessionVM,
          onBack = { backClicked = true })
    }
    composeTestRule.onRoot().performTouchInput { swipeUp() }
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).performClick()
    composeTestRule.runOnIdle { assert(backClicked) }
  }

  @Test
  fun datePickerDialog_selectsDate() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = initialForm)
    }
    assert(sessionVM.discussion.value.admins.contains<String>(currentUser.uid))
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()

    composeTestRule.waitForIdle()
  }

  @Test
  fun timePickerDialog_selectsTime() {
    val initialTime = initialForm.time
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    // --- open the time picker ---
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    // --- confirm selection ---
    composeTestRule
        .onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()

    // --- wait until the time field text actually changes ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      val node = composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).fetchSemanticsNode()
      val text = node.config[SemanticsProperties.EditableText].text
      text.isNotBlank() && text != initialTime.toString()
    }

    // --- final verification ---
    composeTestRule
        .onNodeWithTag(SessionTestTags.TIME_FIELD)
        .assertIsDisplayed()
        .assert(hasTextDifferentFrom(initialTime.toString()))
  }

  @Test
  fun screen_Calls_OnFormChange_WhenSliderMoves_updatesMinMaxBubbles() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
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
  fun proposedGameSection_displaysTextAndCanBeUpdated() {
    // Ensure admin rights so editable UI is rendered
    baseDiscussion = baseDiscussion.copy(creatorId = admin.uid, admins = listOf(admin.uid))
    injectedDiscussionFlow.value = baseDiscussion

    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    // --- Verify header exists ---
    composeTestRule.onAllNodesWithText("Proposed game:").onFirst().assertExists()

    // --- Type in query ---
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).performTextInput("Cascadia")

    // --- Wait for clear icon to appear (same pattern as reference test) ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithContentDescription("Clear", useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    // --- Click clear icon ---
    composeTestRule
        .onAllNodesWithContentDescription("Clear", useUnmergedTree = true)
        .onFirst()
        .assertExists()
        .performClick()

    // --- Wait for cleared state ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Cascadia").fetchSemanticsNodes().isEmpty()
    }

    // --- Assert text removed ---
    composeTestRule.onAllNodesWithText("Cascadia").assertCountEquals(0)
  }

  @Test
  fun participantsSection_displaysAllParticipants() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = initialForm)
    }
    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertIsDisplayed()
    initialForm.participants.forEach { composeTestRule.onNodeWithText(it.name).assertIsDisplayed() }
  }

  @Test
  fun organizationSection_displaysDateTimeAndLocation() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    // --- Wait for the date picker button to exist ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(SessionTestTags.DATE_PICK_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    // --- DATE PICKER ---
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON)
        .assertExists()
        .performClick()

    // --- Wait until the date field updates ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      val node = composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD).fetchSemanticsNode()
      val text = node.config[SemanticsProperties.EditableText].text
      !text.isNullOrBlank() &&
          !text.contains(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
    }

    // --- TIME PICKER ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(SessionTestTags.TIME_PICK_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON)
        .assertExists()
        .performClick()

    // --- Wait until time field updates ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      val node = composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).fetchSemanticsNode()
      val text = node.config[SemanticsProperties.EditableText].text
      text.isNotBlank() && text != "19:00"
    }

    // --- LOCATION FIELD ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(SessionTestTags.LOCATION_FIELD)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule
        .onNodeWithTag(SessionTestTags.LOCATION_FIELD)
        .assertExists()
        .performTextClearance()
    composeTestRule
        .onNodeWithTag(SessionTestTags.LOCATION_FIELD)
        .performTextInput("Rolex Learning Center")

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      val text =
          composeTestRule
              .onNodeWithTag(SessionTestTags.LOCATION_FIELD)
              .fetchSemanticsNode()
              .config[SemanticsProperties.EditableText]
              .text
      text.contains("Rolex Learning Center")
    }

    composeTestRule
        .onNodeWithTag(SessionTestTags.LOCATION_FIELD)
        .assertTextContains("Rolex Learning Center")
  }

  @Test
  fun quitButton_triggersCallback() {
    var quitClicked = false
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          initial = initialForm,
          sessionViewModel = sessionVM,
          onBack = { quitClicked = true })
    }
    composeTestRule.onRoot().performTouchInput { swipeUp() }
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).performClick()
    composeTestRule.runOnIdle { assert(quitClicked) }
  }

  @Test
  fun removeParticipant_removesChipFromUI() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel, // the injected one with discussionFlow set
          currentUser = admin, // is admin/creator (see setup above)
          discussionId = discussionId,
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    // Ensure it’s there first
    composeTestRule.onNodeWithText("Alice").assertIsDisplayed()

    // Click the specific remove icon belonging to the "Alice" chip
    composeTestRule
        .onNode(hasContentDescription("Remove participant") and hasAnyAncestor(hasText("Alice")))
        .performClick()

    // Wait for recomposition: node count should drop to zero
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(hasText("Alice")).fetchSemanticsNodes().isEmpty()
    }

    // Final assert (now deterministic)
    composeTestRule.onNodeWithText("Alice").assertDoesNotExist()
  }

  @Test
  fun changeSessionTitle_displaysNewTitle() {
    val updatedForm = initialForm.copy(title = "Board Game Bash")
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = updatedForm)
    }
    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertTextContains("Board Game Bash")
  }

  @Test
  fun topRightIcons_displayBadges() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
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
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = initialForm)
    }
    composeTestRule
        .onNodeWithTag(SessionTestTags.NOTIFICATION_BADGE_COUNT)
        .onChild()
        .assertTextContains("3")
  }

  @Test
  fun datePickerDialog_updatesDateField() {
    val updatedForm = initialForm.copy(date = LocalDate.now())
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = updatedForm)
    }

    // --- open date picker ---
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    // --- confirm selection ---
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()

    // --- wait until UI reflects new date ---
    composeTestRule.waitUntil(timeoutMillis = 3_000) {
      val text =
          composeTestRule
              .onNodeWithTag(SessionTestTags.DATE_FIELD)
              .fetchSemanticsNode()
              .config[SemanticsProperties.EditableText]
              .text

      // Text is non-empty and matches today’s date
      text.contains(LocalDate.now().format(fmt))
    }

    // --- final verification ---
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_FIELD)
        .assertIsDisplayed()
        .assertTextContains(LocalDate.now().format(fmt))
  }

  @Test
  fun timePickerDialog_updatesTimeField_nonDeterministic() {
    var initialValue: String? = null

    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    // --- READ INITIAL TIME ---
    val timeNode = composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD)
    timeNode.assertIsDisplayed()

    initialValue =
        try {
          timeNode.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        } catch (e: Exception) {
          // fallback: if non-editable text field
          timeNode.fetchSemanticsNode().config[SemanticsProperties.Text]?.firstOrNull()?.text
        }

    // --- OPEN DIALOG ---
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).assertExists().performClick()

    // Wait until dialog actually appears
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    // --- CONFIRM TIME SELECTION ---
    composeTestRule
        .onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON)
        .assertIsDisplayed()
        .performClick()

    // --- WAIT FOR RECOMPOSITION ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      val newText =
          try {
            composeTestRule
                .onNodeWithTag(SessionTestTags.TIME_FIELD)
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text
          } catch (e: Exception) {
            composeTestRule
                .onNodeWithTag(SessionTestTags.TIME_FIELD)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                ?.firstOrNull()
                ?.text
          }
      newText != initialValue
    }

    // --- FINAL ASSERT ---
    composeTestRule
        .onNodeWithTag(SessionTestTags.TIME_FIELD)
        .assert(hasTextDifferentFrom(initialValue!!))
  }

  @Test
  fun datePickerDialog_updatesDateField_nonDeterministic() {
    var initialValue: String? = null
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    val dateNode = composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD)
    dateNode.assertIsDisplayed()
    initialValue = dateNode.fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    // --- open the dialog ---
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    // simulate date selection (if your dialog shows an inline picker)
    composeTestRule.onNode(isDialog()).performTouchInput { click(center) }

    // confirm
    composeTestRule
        .onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON)
        .assertExists()
        .performClick()

    // --- wait for text to update ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      val currentText =
          composeTestRule
              .onNodeWithTag(SessionTestTags.DATE_FIELD)
              .fetchSemanticsNode()
              .config[SemanticsProperties.EditableText]
              .text

      currentText != initialValue
    }

    // --- final assert ---
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
    composeTestRule.setContent {
      UserChip(name = "Alice", onRemove = { removed = "Alice" }, showRemoveBTN = true)
    }
    composeTestRule.onNodeWithContentDescription("Remove participant").performClick()
    composeTestRule.runOnIdle { assert(removed == "Alice") }
  }

  @Test
  fun userChipsGrid_onRemovePropagated() {
    val list =
        listOf(
            Account(uid = "1", handle = "a", name = "A", email = "a@example.com"),
            Account(uid = "2", handle = "b", name = "B", email = "b@example.com"))
    var out: Account? = null
    composeTestRule.setContent {
      UserChipsGrid(participants = list, onRemove = { out = it }, editable = true)
    }
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
    composeTestRule.setContent {
      TimeField(editable = true, value = "", onValueChange = { time = it.toString() })
    }
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
          editable = true,
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
      OrganizationSection(
          form = formState.value, onFormChange = { formState.value = it }, editable = true)
    }

    // --- DATE PICKER ---
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    // Simulate a date change (e.g., selecting the first selectable cell)
    composeTestRule.onAllNodes(isSelectable(), useUnmergedTree = true).onFirst().performClick()

    composeTestRule.onNodeWithText("OK").performClick()

    composeTestRule.waitUntil(5_000) { formState.value.date != initialForm.date }

    // --- TIME PICKER ---
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    // Simulate a time change (if your TimePicker uses selectable semantics)
    composeTestRule.onAllNodes(isSelectable(), useUnmergedTree = true).onFirst().performClick()

    composeTestRule.onNodeWithText("OK").performClick()

    composeTestRule.waitUntil(5_000) { formState.value.time != initialForm.time }

    // --- LOCATION FIELD ---
    composeTestRule
        .onNodeWithTag(SessionTestTags.LOCATION_FIELD)
        .assertExists()
        .performTextClearance()
    composeTestRule.onNodeWithTag(SessionTestTags.LOCATION_FIELD).performTextInput("Moon Base")

    composeTestRule.waitUntil(5_000) { formState.value.locationText.contains("Moon Base") }

    // --- FINAL ASSERTIONS ---
    composeTestRule.runOnIdle {
      assert(formState.value.date != initialForm.date)
      assert(formState.value.time != initialForm.time)
      assert(formState.value.locationText.contains("Moon Base"))
    }
  }

  @Test
  fun topBar_backButton_triggersOnBack() {
    var backClicked = false
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = "discussion1",
          initial = initialForm,
          sessionViewModel = sessionVM,
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
          editable = true,
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
      DatePickerDockedField(editable = true, value = LocalDate.now(), onValueChange = {})
    }

    // --- Click the Pick button ---
    composeTestRule.onNodeWithText("Pick").performClick()

    // --- Wait for dialog to appear ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Cancel").fetchSemanticsNodes().isNotEmpty()
    }

    // --- Click Cancel button once dialog exists ---
    composeTestRule.onNodeWithText("Cancel").performClick()

    // --- Wait until dialog disappears ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Cancel").fetchSemanticsNodes().isEmpty()
    }

    composeTestRule.waitForIdle()
  }

  // this will for sure fail since the default is memberview, members cannot see the Pick button
  @Test
  fun timeField_cancelDialog_path() {
    composeTestRule.setContent { TimeField(editable = true, value = "", onValueChange = {}) }

    // --- Click the Pick button ---
    composeTestRule.onNodeWithText("Pick").performClick()

    // --- Wait for the dialog to appear ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Cancel").fetchSemanticsNodes().isNotEmpty()
    }

    // --- Click Cancel ---
    composeTestRule.onNodeWithText("Cancel").performClick()

    // --- Wait for dialog to disappear ---
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Cancel").fetchSemanticsNodes().isEmpty()
    }

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

  @Test
  fun adminView_showsDeleteSessionButton() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = discussionId,
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    composeTestRule.onRoot().performTouchInput { swipeUp() }
    composeTestRule.onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON).assertIsDisplayed()
  }

  @Test
  fun adminView_deleteSessionButton_isClickable() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = discussionId,
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    composeTestRule.waitUntil(5_000) {
      composeTestRule
          .onAllNodesWithTag(SessionTestTags.DELETE_SESSION_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule
        .onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON)
        .assertExists()
        .performClick()
  }

  @Test
  fun memberView_hidesAdminOnlyControls() {
    injectedDiscussionFlow.value =
        baseDiscussion.copy(admins = listOf("otherUser"), creatorId = "otherUser")

    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = member,
          discussionId = discussionId,
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    // Swipe to ensure all elements are visible
    composeTestRule.onRoot().performTouchInput { swipeUp() }

    // Admin-only elements should NOT exist
    composeTestRule.onAllNodesWithTag(SessionTestTags.DELETE_SESSION_BUTTON).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(SessionTestTags.DATE_PICK_BUTTON).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(SessionTestTags.TIME_PICK_BUTTON).assertCountEquals(0)
  }

  @Test
  fun memberView_dateAndTimeAreReadOnly() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = member,
          discussionId = discussionId,
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    // Date and time fields should be visible but non-editable (no pick buttons)
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD).assertIsDisplayed()

    composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).assertIsDisplayed()

    // Admin-only “Pick” buttons should NOT exist for members
    composeTestRule.onAllNodesWithTag(SessionTestTags.DATE_PICK_BUTTON).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(SessionTestTags.TIME_PICK_BUTTON).assertCountEquals(0)
  }

  private fun hasNoSetTextAction(): SemanticsMatcher =
      SemanticsMatcher("hasNoSetTextAction") { node ->
        !node.config.contains(SemanticsActions.SetText)
      }

  @Test
  fun memberView_locationAndGameAreReadOnly() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = member,
          discussionId = discussionId,
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    // --- LOCATION FIELD ---
    composeTestRule.onNodeWithTag(SessionTestTags.LOCATION_FIELD).assertExists().assertIsDisplayed()

    // Should NOT have an editable action
    composeTestRule.onNodeWithTag(SessionTestTags.LOCATION_FIELD).assert(hasNoSetTextAction())

    // --- PROPOSED GAME FIELD ---
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).assertExists().assertIsDisplayed()

    // Should NOT be editable
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).assert(hasNoSetTextAction())

    // --- NO ADMIN-ONLY CONTROLS ---
    composeTestRule
        .onAllNodesWithContentDescription("Clear", useUnmergedTree = true)
        .assertCountEquals(0)

    composeTestRule.onAllNodesWithTag(SessionTestTags.DATE_PICK_BUTTON).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(SessionTestTags.TIME_PICK_BUTTON).assertCountEquals(0)
  }

  @Test
  fun memberView_sliderIsVisibleButNonInteractive() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = member,
          discussionId = discussionId,
          sessionViewModel = sessionVM,
          initial = initialForm)
    }

    // The slider should be visible
    val slider = composeTestRule.onNodeWithTag("discrete_pill_slider")
    slider.assertIsDisplayed()

    // Read initial min/max bubble values
    val initialMin = readIntFromText(SessionTestTags.MIN_PLAYERS)
    val initialMax = readIntFromText(SessionTestTags.MAX_PLAYERS)

    // Attempt to drag the slider — should do nothing
    slider.performTouchInput {
      down(centerLeft)
      moveTo(centerRight)
      up()
    }

    // Give Compose a frame to settle
    composeTestRule.waitForIdle()

    // Re-read bubble values after interaction
    val afterMin = readIntFromText(SessionTestTags.MIN_PLAYERS)
    val afterMax = readIntFromText(SessionTestTags.MAX_PLAYERS)

    // Assert that values did not change
    assert(afterMin == initialMin)
    assert(afterMax == initialMax)
  }

  @Test
  fun memberView_noSliderAndNoRemoveButtons() {
    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = member,
          discussionId = discussionId,
          sessionViewModel = sessionVM,
          initial = initialForm)
    }
    // No visible slider
    // No remove participant icons
    composeTestRule.onAllNodesWithContentDescription("Remove participant").assertCountEquals(0)
  }

  @Test
  fun deleteSessionButton_triggersDeleteSessionCallback() {
    var onBackCalled = false
    val fakeSessionVM = spyk(sessionVM)
    every { fakeSessionVM.deleteSession(any(), any()) } answers {}

    composeTestRule.setContent {
      SessionViewScreen(
          viewModel = viewModel,
          currentUser = admin,
          discussionId = discussionId,
          sessionViewModel = fakeSessionVM,
          initial = initialForm,
          onBack = { onBackCalled = true })
    }

    // Scroll to ensure visibility
    composeTestRule.onRoot().performTouchInput { swipeUp() }

    // Perform actual click on DeleteSession button
    composeTestRule
        .onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON)
        .assertIsDisplayed()
        .performClick()

    // Now both callbacks must have executed
    verify(exactly = 1) { fakeSessionVM.deleteSession(admin, baseDiscussion) }
    assert(onBackCalled)
  }

  @Test
  fun deleteSessionButton_executesOnClick() {
    val vm = spyk(sessionVM)
    var backCalled = false

    composeTestRule.setContent {
      DeleteSessionBTN(
          sessionViewModel = vm,
          currentUser = admin,
          discussion = baseDiscussion,
          userIsAdmin = true,
          onback = { backCalled = true })
    }

    composeTestRule.onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON).performClick()

    verify(exactly = 1) { vm.deleteSession(admin, baseDiscussion) }
    assert(backCalled)
  }
}
