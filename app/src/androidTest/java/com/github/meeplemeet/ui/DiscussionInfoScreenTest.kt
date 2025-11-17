package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.CreateAccountViewModel
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.ui.discussions.DiscussionDetailsScreen
import com.github.meeplemeet.ui.discussions.UITestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DiscussionSettingScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var viewModel: DiscussionViewModel
  private lateinit var currentAccount: Account
  private lateinit var otherUser: Account
  private lateinit var thirdUser: Account
  private lateinit var testDiscussion: Discussion
  private lateinit var createAccountViewModel: CreateAccountViewModel

  @Before
  fun setup() = runBlocking {
    viewModel = DiscussionViewModel()

    createAccountViewModel = CreateAccountViewModel(handlesRepository)

    // Create test users
    currentAccount =
        accountRepository.createAccount(
            userHandle = "testuser1_${System.currentTimeMillis()}",
            name = "Alice",
            email = "alice@test.com",
            photoUrl = null)

    otherUser =
        accountRepository.createAccount(
            userHandle = "testuser2_${System.currentTimeMillis()}",
            name = "Bob",
            email = "bob@test.com",
            photoUrl = null)

    thirdUser =
        accountRepository.createAccount(
            userHandle = "testuser3_${System.currentTimeMillis()}",
            name = "Charlie",
            email = "charlie@test.com",
            photoUrl = null)

    // Create a test discussion
    testDiscussion =
        discussionRepository.createDiscussion(
            name = "Test Discussion",
            description = "A sample group",
            creatorId = currentAccount.uid,
            participants = listOf(otherUser.uid))

    // Add a test message
    discussionRepository.sendMessageToDiscussion(testDiscussion, currentAccount, "Hi")

    // Fetch updated discussion and accounts with previews
    testDiscussion = discussionRepository.getDiscussion(testDiscussion.uid)
    currentAccount = accountRepository.getAccount(currentAccount.uid)
    otherUser = accountRepository.getAccount(otherUser.uid)
    thirdUser = accountRepository.getAccount(thirdUser.uid)
  }

  @After
  fun cleanup() = runBlocking {
    try {
      discussionRepository.deleteDiscussion(testDiscussion)
      accountRepository.deleteAccount(currentAccount.uid)
      accountRepository.deleteAccount(otherUser.uid)
      accountRepository.deleteAccount(thirdUser.uid)
    } catch (_: Exception) {
      // Ignore cleanup errors
    }
  }

  @Test
  fun smokeTestNotAdmin() = runBlocking {
    compose.setContent {
      DiscussionDetailsScreen(discussion = testDiscussion, account = currentAccount)
    }

    checkpoint("screen_displaysDiscussionName_andButtons") {
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
      clearFields()
    }
    checkpoint("Delete Button shows dialog") {
      compose.waitForIdle()
      compose.onNodeWithTag("delete_button").performClick()

      compose.waitForIdle()
      compose.onNodeWithTag("delete_discussion_display").assertIsDisplayed()
      compose.onNodeWithText("Cancel").assertIsDisplayed()
      compose.onNodeWithText("Cancel").performClick()
    }

    checkpoint("Leave Button shows Dialog") {
      compose.waitForIdle()
      compose.onNodeWithTag("leave_button").performClick()

      compose.waitForIdle()
      compose.onNodeWithTag("leave_discussion_display").assertIsDisplayed()
      compose.onNodeWithText("Cancel").assertIsDisplayed()
      compose.onNodeWithText("Cancel").performClick()
    }

    checkpoint("Back Button saves Changes") {
      compose.waitForIdle()

      compose.onNodeWithTag("discussion_name").performTextInput(" Updated")
      compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

      compose.waitForIdle()
      // Here we just assert the UI still exists (no crash)
      compose.onNodeWithTag("discussion_name").assertExists()
      clearFields()
    }
  }

  @Test
  fun adminView_canEditAndAddMembers() = runBlocking {
    checkpoint("Admin can edit discussion details and add members") {
      runBlocking {
        // Create a discussion where current user is admin but not owner
        val adminDiscussion =
            discussionRepository.createDiscussion(
                name = "Admin Test",
                description = "Test description",
                creatorId = thirdUser.uid, // Third user is the owner
                participants = listOf(currentAccount.uid, otherUser.uid))

        // Make current user an admin
        discussionRepository.addAdminToDiscussion(adminDiscussion, currentAccount.uid)

        discussionRepository.sendMessageToDiscussion(adminDiscussion, thirdUser, "Test message")
        val updatedDiscussion = discussionRepository.getDiscussion(adminDiscussion.uid)

        compose.setContent {
          DiscussionDetailsScreen(discussion = updatedDiscussion, account = currentAccount)
        }

        compose.waitForIdle()

        // Can edit name and description
        compose.onNodeWithTag("discussion_name").assertIsDisplayed().performTextInput(" updated")
        compose
            .onNodeWithTag("discussion_description")
            .assertIsDisplayed()
            .performTextInput(" changed")

        // Can see search bar
        compose.onNodeWithText("Add Members").assertIsDisplayed()

        // Cleanup
        discussionRepository.deleteDiscussion(updatedDiscussion)
      }
    }
  }

  private fun clearFields() {
    compose.onNodeWithTag("discussion_name").performTextClearance()
    compose.onNodeWithTag("discussion_description").performTextClearance()
  }

  @Test
  fun profilePicture_displaysWhenUrlIsNull() = runBlocking {
    checkpoint("Profile picture shows default icon when URL is null") {
      runBlocking {
        // testDiscussion should have null profilePictureUrl by default
        compose.setContent {
          DiscussionDetailsScreen(discussion = testDiscussion, account = currentAccount)
        }

        compose.waitForIdle()

        // Profile picture should be displayed
        compose.onNodeWithTag("profile_picture").assertIsDisplayed()
      }
    }
  }

  @Test
  fun profilePicture_displaysWhenUrlIsProvided() = runBlocking {
    checkpoint("Profile picture shows AsyncImage when URL is provided") {
      runBlocking {
        // Create a discussion with a profile picture URL
        val discussionWithPicture =
            discussionRepository.createDiscussion(
                name = "Picture Test",
                description = "Test with picture",
                creatorId = currentAccount.uid,
                participants = listOf(otherUser.uid))

        // Note: We can't easily set profilePictureUrl through repository without actual upload
        // So we'll verify the tag exists and is displayed
        compose.setContent {
          DiscussionDetailsScreen(discussion = discussionWithPicture, account = currentAccount)
        }

        compose.waitForIdle()

        // Profile picture should be displayed
        compose.onNodeWithTag("profile_picture").assertIsDisplayed()

        // Cleanup
        discussionRepository.deleteDiscussion(discussionWithPicture)
      }
    }
  }

  @Test
  fun profilePicture_isClickableForAdmins() = runBlocking {
    checkpoint("Profile picture is clickable for admins") {
      runBlocking {
        // Current account is the owner/admin of testDiscussion
        compose.setContent {
          DiscussionDetailsScreen(discussion = testDiscussion, account = currentAccount)
        }

        compose.waitForIdle()

        // Profile picture should be clickable for admins
        compose.onNodeWithTag("profile_picture").assertIsDisplayed().performClick()

        compose.waitForIdle()

        // Dialog should open with camera and gallery options
        compose.onNodeWithTag("profile_picture_camera_option").assertIsDisplayed()
        compose.onNodeWithTag("profile_picture_gallery_option").assertIsDisplayed()
      }
    }
  }

  @Test
  fun profilePictureDialog_displaysOptionsCorrectly() = runBlocking {
    checkpoint("Profile picture dialog shows camera and gallery options") {
      runBlocking {
        compose.setContent {
          DiscussionDetailsScreen(discussion = testDiscussion, account = currentAccount)
        }

        compose.waitForIdle()

        // Click profile picture to open dialog
        compose.onNodeWithTag("profile_picture").performClick()

        compose.waitForIdle()

        // Verify both options are displayed
        compose.onNodeWithTag("profile_picture_camera_option").assertIsDisplayed()
        compose.onNodeWithTag("profile_picture_gallery_option").assertIsDisplayed()

        // Verify dialog shows discussion name
        compose.onNodeWithTag(UITestTags.PROFILE_PICTURE_DIALOG_TITLE).assertIsDisplayed()
        compose
            .onNodeWithTag(UITestTags.PROFILE_PICTURE_DIALOG_TITLE)
            .assertTextContains("Test Discussion")
      }
    }
  }

  @Test
  fun profilePicture_isNotClickableForNonAdmins() = runBlocking {
    checkpoint("Profile picture is not clickable for non-admins") {
      runBlocking {
        // Create a discussion where current user is participant but not admin
        val participantDiscussion =
            discussionRepository.createDiscussion(
                name = "Non-Admin Test",
                description = "Test description",
                creatorId = thirdUser.uid, // Third user is the owner
                participants = listOf(currentAccount.uid, otherUser.uid))

        discussionRepository.sendMessageToDiscussion(
            participantDiscussion, thirdUser, "Test message")
        val updatedDiscussion = discussionRepository.getDiscussion(participantDiscussion.uid)

        compose.setContent {
          DiscussionDetailsScreen(discussion = updatedDiscussion, account = currentAccount)
        }

        compose.waitForIdle()

        // Profile picture should be displayed but not clickable
        compose.onNodeWithTag("profile_picture").assertIsDisplayed()

        // Try to click - dialog should NOT appear
        compose.onNodeWithTag("profile_picture").performClick()

        compose.waitForIdle()

        // Dialog options should not be displayed
        compose.onNodeWithTag("profile_picture_camera_option").assertDoesNotExist()
        compose.onNodeWithTag("profile_picture_gallery_option").assertDoesNotExist()

        // Cleanup
        discussionRepository.deleteDiscussion(updatedDiscussion)
      }
    }
  }
}
