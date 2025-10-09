package com.github.meeplemeet.ui

import android.text.format.DateFormat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.structures.*
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionScreenTest {

  /** Compose test rule to run UI tests */
  @get:Rule val composeTestRule = createComposeRule()

  /** Mocked Firestore repository */
  private lateinit var repository: FirestoreRepository

  /** Real ViewModel instance under test */
  private lateinit var viewModel: FirestoreViewModel

  /** Mocked navigation actions for UI testing */
  private lateinit var mockNavigation: NavigationActions

  /** Current user for discussion tests */
  private lateinit var currentUser: Account

  /** Coroutine test scope */
  private lateinit var testScope: TestScope

  /** Fully initialized safe discussion used for all tests */
  private lateinit var safeDiscussion: Discussion

  /**
   * Setup method executed before each test Initializes the repository, ViewModel, navigation, and
   * safe discussion Injects safe discussion into the ViewModel's internal flow to prevent NPEs
   */
  @Before
  fun setup() {
    val dispatcher = StandardTestDispatcher()
    testScope = TestScope(dispatcher)

    repository = mockk(relaxed = true)
    mockNavigation = mockk(relaxed = true)
    currentUser = Account(uid = "user1", name = "Alice")

    val messages =
        listOf(
            Message("user1", "Hi there!", Timestamp(Date())),
            Message("user2", "Hey Alice!", Timestamp(Date())))
    val participants = listOf(currentUser.uid, "user2")

    safeDiscussion =
        Discussion(
            uid = "disc1",
            name = "Test Discussion",
            messages = messages,
            participants = participants,
            creatorId = currentUser.uid)

    coEvery { repository.getDiscussion("disc1") } returns safeDiscussion

    viewModel = FirestoreViewModel(repository)

    val discussionFlowsField = viewModel::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map =
        discussionFlowsField.get(viewModel) as MutableMap<String, MutableStateFlow<Discussion>>
    map["disc1"] = MutableStateFlow(safeDiscussion)
  }

  // ---------- UNIT TESTS ----------

  /** Tests that isSameDay returns true for two dates on the same day */
  @Test
  fun isSameDay_returns_true_for_same_day() {
    val cal1 = Calendar.getInstance().apply { set(2025, 0, 1) }
    val cal2 = Calendar.getInstance().apply { set(2025, 0, 1) }
    assertTrue(isSameDay(cal1, cal2))
  }

  /** Tests that isSameDay returns false for dates on different days */
  @Test
  fun isSameDay_returns_false_for_different_days() {
    val cal1 = Calendar.getInstance().apply { set(2025, 0, 1) }
    val cal2 = Calendar.getInstance().apply { set(2025, 0, 2) }
    assertFalse(isSameDay(cal1, cal2))
  }

  /** Tests that shouldShowDateHeader returns true if previous date is null */
  @Test
  fun shouldShowDateHeader_returns_true_when_previous_is_null() {
    val now = Date()
    assertTrue(shouldShowDateHeader(now, null))
  }

  /** Tests that shouldShowDateHeader returns false for messages on the same day */
  @Test
  fun shouldShowDateHeader_returns_false_for_same_day() {
    val now = Date()
    val laterSameDay = Date(now.time + 1000 * 60)
    assertFalse(shouldShowDateHeader(now, laterSameDay))
  }

  /** Tests that shouldShowDateHeader returns true for messages on different days */
  @Test
  fun shouldShowDateHeader_returns_true_for_different_days() {
    val now = Calendar.getInstance().apply { set(2025, 0, 1) }.time
    val nextDay = Calendar.getInstance().apply { set(2025, 0, 2) }.time
    assertTrue(shouldShowDateHeader(nextDay, now))
  }

  /** Tests that formatDateBubble returns "Today" for today's date */
  @Test
  fun formatDateBubble_returns_Today_for_todays_date() {
    val today = Date()
    assertEquals("Today", formatDateBubble(today))
  }

  /** Tests that formatDateBubble returns "Yesterday" for yesterday's date */
  @Test
  fun formatDateBubble_returns_Yesterday_for_yesterdays_date() {
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
    assertEquals("Yesterday", formatDateBubble(yesterday))
  }

  /** Tests that formatDateBubble formats older dates correctly */
  @Test
  fun formatDateBubble_returns_formatted_date_for_older_date() {
    val date = Calendar.getInstance().apply { set(2020, 0, 1) }.time
    val expected = DateFormat.format("MMM dd, yyyy", date).toString()
    assertEquals(expected, formatDateBubble(date))
  }

  // ---------- COMPOSE UI TESTS ----------

  /** Tests that DiscussionScreen displays discussion title and messages */
  @Test
  fun discussionScreen_displays_messages_and_title() {
    composeTestRule.setContent {
      DiscussionScreen(
          viewModel = viewModel,
          discussionId = "disc1",
          currentUser = currentUser,
          navigation = mockNavigation)
    }

    composeTestRule.onNodeWithText("Test Discussion").assertExists()
    composeTestRule.onNodeWithText("Hi there!").assertExists()
    composeTestRule.onNodeWithText("Hey Alice!").assertExists()
  }

  /** Tests that the send button clears the text field after sending a message */
  @Test
  fun sendButton_clears_text_field_after_sending() = runTest {
    composeTestRule.setContent {
      DiscussionScreen(
          viewModel = viewModel,
          discussionId = "disc1",
          currentUser = currentUser,
          navigation = mockNavigation)
    }

    val textField = composeTestRule.onNodeWithText("Type something...")
    val sendButton = composeTestRule.onNodeWithContentDescription("Send")

    textField.performTextInput("Hello!")
    sendButton.performClick()

    composeTestRule.onNodeWithText("Type something...").assertExists()
  }

  /** Tests that pressing the back button calls navigation.goBack */
  @Test
  fun backButton_calls_navigation_goBack() {
    composeTestRule.setContent {
      DiscussionScreen(
          viewModel = viewModel,
          discussionId = "disc1",
          currentUser = currentUser,
          navigation = mockNavigation)
    }

    composeTestRule.onNodeWithContentDescription("Back").performClick()
    verify { mockNavigation.goBack() }
  }

  /** Tests that ChatBubble displays the message and sender name correctly */
  @Test
  fun chatBubble_displays_message_and_sender() {
    val msg = Message("user2", "Hey!", Timestamp.now())
    composeTestRule.setContent { ChatBubble(message = msg, isMine = false, senderName = "Bob") }

    composeTestRule.onNodeWithText("Hey!").assertExists()
    composeTestRule.onNodeWithText("Bob").assertExists()
  }

  /** Tests that DateSeparator displays the formatted date correctly */
  @Test
  fun dateSeparator_displays_formatted_date() {
    val date = Calendar.getInstance().apply { set(2025, 0, 1) }.time
    composeTestRule.setContent { DateSeparator(date = date) }

    composeTestRule.onNodeWithText(formatDateBubble(date)).assertExists()
  }
}
