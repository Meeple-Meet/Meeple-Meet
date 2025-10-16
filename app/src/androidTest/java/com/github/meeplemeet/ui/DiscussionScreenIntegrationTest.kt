/** Sections of this file were generated using ChatGPT */
package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.*
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DiscussionScreenIntegrationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var repository: FirestoreRepository
  private lateinit var viewModel: FirestoreViewModel
  private lateinit var mockNavigation: NavigationActions
  private lateinit var currentUser: Account
  private lateinit var safeDiscussion: Discussion
  private lateinit var testScope: TestScope

  @Before
  fun setup() {
    val dispatcher = StandardTestDispatcher()
    testScope = TestScope(dispatcher)
    repository = mockk(relaxed = true)
    mockNavigation = mockk(relaxed = true)
    currentUser =
        Account(uid = "user1", handle = "user1", name = "Alice", email = "Alice@example.com")

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

  /** Tests that DiscussionScreen displays discussion title and messages */
  @Test
  fun discussionScreen_displays_messages_and_title() {
    composeTestRule.setContent {
      DiscussionScreen(
          viewModel = viewModel,
          discussionId = "disc1",
          currentUser = currentUser,
          onBack = { mockNavigation.goBack() })
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
          onBack = { mockNavigation.goBack() })
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
          onBack = { mockNavigation.goBack() })
    }

    composeTestRule.onNodeWithContentDescription("Back").performClick()
    verify { mockNavigation.goBack() }
  }
  /** Tests that DiscussionScreen autoscrolls to the latest message when opened */
  @Test
  fun discussionScreen_autoscrolls_to_latest_message() = runTest {
    val messages = (1..50).map { i -> Message("user${i % 2 + 1}", "Message $i", Timestamp(Date())) }
    val discussionWithManyMessages = safeDiscussion.copy(messages = messages)

    val discussionFlowsField = viewModel::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map =
        discussionFlowsField.get(viewModel) as MutableMap<String, MutableStateFlow<Discussion>>
    map["disc1"]?.value = discussionWithManyMessages

    composeTestRule.setContent {
      DiscussionScreen(
          viewModel = viewModel,
          discussionId = "disc1",
          currentUser = currentUser,
          onBack = { mockNavigation.goBack() })
    }

    composeTestRule.onNodeWithText("Message 50").assertIsDisplayed()
  }
}
