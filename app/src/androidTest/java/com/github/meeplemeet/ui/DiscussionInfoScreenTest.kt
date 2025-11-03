package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.FirestoreHandlesRepository
import com.github.meeplemeet.model.auth.HandlesViewModel
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.discussions.FirestoreRepository
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class DiscussionSettingScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()

  private lateinit var viewModel: DiscussionViewModel
  private lateinit var repository: FirestoreRepository
  private lateinit var currentAccount: Account
  private lateinit var otherUser: Account
  private lateinit var thirdUser: Account
  private lateinit var testDiscussion: Discussion
  private lateinit var handlesRepository: FirestoreHandlesRepository
  private lateinit var handlesViewModel: HandlesViewModel

  @Before
  fun setup() = runBlocking {
    repository = FirestoreRepository()
    handlesRepository = FirestoreHandlesRepository()
    viewModel = DiscussionViewModel(repository)

    handlesViewModel = HandlesViewModel(handlesRepository)

    // Create test users
    currentAccount =
        repository.createAccount(
            userHandle = "testuser1_${System.currentTimeMillis()}",
            name = "Alice",
            email = "alice@test.com",
            photoUrl = null)

    otherUser =
        repository.createAccount(
            userHandle = "testuser2_${System.currentTimeMillis()}",
            name = "Bob",
            email = "bob@test.com",
            photoUrl = null)

    thirdUser =
        repository.createAccount(
            userHandle = "testuser3_${System.currentTimeMillis()}",
            name = "Charlie",
            email = "charlie@test.com",
            photoUrl = null)

    // Create a test discussion
    testDiscussion =
        repository.createDiscussion(
            name = "Test Discussion",
            description = "A sample group",
            creatorId = currentAccount.uid,
            participants = listOf(otherUser.uid))

    // Add a test message
    repository.sendMessageToDiscussion(testDiscussion, currentAccount, "Hi")

    // Fetch updated discussion and accounts with previews
    testDiscussion = repository.getDiscussion(testDiscussion.uid)
    currentAccount = repository.getAccount(currentAccount.uid)
    otherUser = repository.getAccount(otherUser.uid)
    thirdUser = repository.getAccount(thirdUser.uid)
  }

  @After
  fun cleanup() = runBlocking {
    try {
      repository.deleteDiscussion(testDiscussion)
      repository.deleteAccount(currentAccount.uid)
      repository.deleteAccount(otherUser.uid)
      repository.deleteAccount(thirdUser.uid)
    } catch (e: Exception) {
      // Ignore cleanup errors
    }
  }

  @Test
  fun screen_displaysDiscussionName_andButtons() {
    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel,
          discussion = testDiscussion,
          account = currentAccount,
          handlesViewModel = handlesViewModel)
    }

    compose.waitForIdle()

    compose
        .onNodeWithTag("discussion_name")
        .assertIsDisplayed()
        .assertTextContains("Test Discussion")

    compose
        .onNodeWithTag("discussion_description")
        .assertIsDisplayed()
        .assertTextContains("A sample group")

    compose.onNodeWithTag("delete_button").assertIsDisplayed().assertIsEnabled()

    compose.onNodeWithTag("leave_button").assertIsDisplayed().assertIsEnabled()
  }

  @Test
  fun clickingDeleteButton_showsDeleteDialog() {
    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel,
          discussion = testDiscussion,
          account = currentAccount,
          handlesViewModel = handlesViewModel)
    }

    compose.waitForIdle()
    compose.onNodeWithTag("delete_button").performClick()

    compose.waitForIdle()
    compose.onNodeWithTag("delete_discussion_display").assertIsDisplayed()
    compose.onNodeWithText("Cancel").assertIsDisplayed()
  }

  @Test
  fun clickingLeaveButton_showsLeaveDialog() {
    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel,
          discussion = testDiscussion,
          account = currentAccount,
          handlesViewModel = handlesViewModel)
    }

    compose.waitForIdle()
    compose.onNodeWithTag("leave_button").performClick()

    compose.waitForIdle()
    compose.onNodeWithTag("leave_discussion_display").assertIsDisplayed()
    compose.onNodeWithText("Cancel").assertIsDisplayed()
  }

  @Ignore
  @Test
  fun memberList_displaysMembersWithBadges() {
    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel,
          discussion = testDiscussion,
          account = currentAccount,
          handlesViewModel = handlesViewModel)
    }

    compose.waitForIdle()

    compose.onNodeWithTag("member_row_${currentAccount.uid}").assertIsDisplayed()
    compose.onNodeWithTag("member_row_${otherUser.uid}").assertIsDisplayed()
    compose.onNodeWithText("Owner").assertIsDisplayed()
    compose.onNodeWithText("Member").assertIsDisplayed()
  }

  @Test
  fun backButton_savesChanges() {
    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel,
          discussion = testDiscussion,
          account = currentAccount,
          handlesViewModel = handlesViewModel)
    }

    compose.waitForIdle()

    compose.onNodeWithTag("discussion_name").performTextInput(" Updated")
    compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    compose.waitForIdle()
    // Here we just assert the UI still exists (no crash)
    compose.onNodeWithTag("discussion_name").assertExists()
  }

  @Ignore
  @Test
  fun participantView_cannotEditOrAddMembers() = runBlocking {
    // Create a discussion where current user is participant but not admin or owner
    val participantDiscussion =
        repository.createDiscussion(
            name = "Participant Test",
            description = "Test description",
            creatorId = thirdUser.uid, // Third user is the owner
            participants = listOf(currentAccount.uid, otherUser.uid))

    repository.sendMessageToDiscussion(participantDiscussion, thirdUser, "Test message")
    val updatedDiscussion = repository.getDiscussion(participantDiscussion.uid)

    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel,
          discussion = updatedDiscussion,
          account = currentAccount,
          handlesViewModel = handlesViewModel)
    }

    compose.waitForIdle()

    // Name and description should be visible but read-only
    compose.onNodeWithTag("discussion_name").assertIsDisplayed().assertIsNotEnabled()
    compose.onNodeWithTag("discussion_description").assertIsDisplayed().assertIsNotEnabled()

    // Member search field should not be displayed
    compose.onAllNodesWithText("Add Members").assertCountEquals(0)

    // Member row for current user: just check displayed
    compose.onNodeWithTag("member_row_${currentAccount.uid}").assertIsDisplayed()

    // Member row for other user: check displayed and not clickable
    compose
        .onNodeWithTag("member_row_${otherUser.uid}")
        .assertIsDisplayed()
        .assertHasNoClickAction()

    // Cleanup
    repository.deleteDiscussion(updatedDiscussion)
  }

  @Test
  fun adminView_canEditAndAddMembers() = runBlocking {
    // Create a discussion where current user is admin but not owner
    val adminDiscussion =
        repository.createDiscussion(
            name = "Admin Test",
            description = "Test description",
            creatorId = thirdUser.uid, // Third user is the owner
            participants = listOf(currentAccount.uid, otherUser.uid))

    // Make current user an admin
    repository.addAdminToDiscussion(adminDiscussion, currentAccount.uid)

    repository.sendMessageToDiscussion(adminDiscussion, thirdUser, "Test message")
    val updatedDiscussion = repository.getDiscussion(adminDiscussion.uid)

    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel,
          discussion = updatedDiscussion,
          account = currentAccount,
          handlesViewModel = handlesViewModel)
    }

    compose.waitForIdle()

    // Can edit name and description
    compose.onNodeWithTag("discussion_name").assertIsDisplayed().performTextInput(" updated")
    compose.onNodeWithTag("discussion_description").assertIsDisplayed().performTextInput(" changed")

    // Can see search bar
    compose.onNodeWithText("Add Members").assertIsDisplayed()

    // Cleanup
    repository.deleteDiscussion(updatedDiscussion)
  }

  @Ignore
  @Test
  fun ownerView_canRemoveAdmins() = runBlocking {
    // Create a discussion where current user is the owner
    val ownerDiscussion =
        repository.createDiscussion(
            name = "Owner Test",
            description = "Test description",
            creatorId = currentAccount.uid,
            participants = listOf(otherUser.uid))

    // Make other user an admin
    repository.addAdminToDiscussion(ownerDiscussion, otherUser.uid)

    repository.sendMessageToDiscussion(ownerDiscussion, currentAccount, "Test message")
    val updatedDiscussion = repository.getDiscussion(ownerDiscussion.uid)

    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel,
          discussion = updatedDiscussion,
          account = currentAccount,
          handlesViewModel = handlesViewModel)
    }

    compose.waitForIdle()

    // Can click member to open dialog
    compose.onNodeWithTag("member_row_${otherUser.uid}").performClick()
    compose.waitForIdle()

    // Owner can remove admin
    compose.onNodeWithText("Remove Admin").assertIsDisplayed()

    // Cleanup
    repository.deleteDiscussion(updatedDiscussion)
  }
}
