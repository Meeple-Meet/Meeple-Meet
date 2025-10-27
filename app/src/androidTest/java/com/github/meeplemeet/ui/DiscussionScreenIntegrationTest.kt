package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.*
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DiscussionScreenIntegrationTest : FirestoreTests() {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var repository: FirestoreRepository
  private lateinit var viewModel: FirestoreViewModel
  private lateinit var currentUser: Account
  private lateinit var otherUser: Account
  private lateinit var testDiscussion: Discussion
  private var backPressed = false

  @Before
  fun setup() = runBlocking {
    repository = FirestoreRepository()
    viewModel = FirestoreViewModel(repository)
    backPressed = false

    // Create test users
    currentUser =
        repository.createAccount(
            userHandle = "testuser_${System.currentTimeMillis()}",
            name = "Alice",
            email = "alice@test.com",
            photoUrl = null)

    otherUser =
        repository.createAccount(
            userHandle = "otheruser_${System.currentTimeMillis()}",
            name = "Bob",
            email = "bob@test.com",
            photoUrl = null)

    // Create a test discussion with messages
    testDiscussion =
        repository.createDiscussion(
            name = "Test Discussion",
            description = "A test discussion",
            creatorId = currentUser.uid,
            participants = listOf(otherUser.uid))

    // Add some test messages
    repository.sendMessageToDiscussion(testDiscussion, currentUser, "Hi there!")
    repository.sendMessageToDiscussion(testDiscussion, otherUser, "Hey Alice!")

    // Fetch updated discussion with messages
    testDiscussion = repository.getDiscussion(testDiscussion.uid)

    currentUser = repository.getAccount(currentUser.uid)
    otherUser = repository.getAccount(otherUser.uid)
  }

  /** Tests that DiscussionScreen displays discussion title and messages */
  @Test
  fun discussionScreen_displays_messages_and_title() {
    composeTestRule.setContent {
      DiscussionScreen(
          account = currentUser,
          discussion = testDiscussion,
          viewModel = viewModel,
          onBack = { backPressed = true })
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Test Discussion").assertExists()
    composeTestRule.onNodeWithText("Hi there!").assertExists()
    composeTestRule.onNodeWithText("Hey Alice!").assertExists()
  }

  /** Tests that the send button clears the text field after sending a message */
  @Test
  fun sendButton_clears_text_field_after_sending() = runTest {
    composeTestRule.setContent {
      DiscussionScreen(
          account = currentUser,
          discussion = testDiscussion,
          viewModel = viewModel,
          onBack = { backPressed = true })
    }

    composeTestRule.waitForIdle()

    val textField = composeTestRule.onNodeWithText("Type something...")
    val sendButton = composeTestRule.onNodeWithContentDescription("Send")

    textField.performTextInput("Hello!")
    sendButton.performClick()

    // Wait for the message to be sent via ViewModel -> Repository
    composeTestRule.waitForIdle()

    // Verify the text field is cleared
    composeTestRule.onNodeWithText("Type something...").assertExists()
  }

  /** Tests that pressing the back button calls the onBack callback */
  @Test
  fun backButton_calls_onBack_callback() {
    composeTestRule.setContent {
      DiscussionScreen(
          account = currentUser,
          discussion = testDiscussion,
          viewModel = viewModel,
          onBack = { backPressed = true })
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithContentDescription("Back").performClick()

    assert(backPressed) { "Back button should trigger onBack callback" }
  }
}
