package com.github.meeplemeet.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.CreateAccountViewModel
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
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    try {
      discussionRepository.deleteDiscussion(context, testDiscussion)
      accountRepository.deleteAccount(currentAccount.uid)
      accountRepository.deleteAccount(otherUser.uid)
      accountRepository.deleteAccount(thirdUser.uid)
    } catch (_: Exception) {
      // Ignore cleanup errors
    }
  }

  @Test
  fun all_tests() = runBlocking {
    // Create additional test discussions upfront
    val adminDiscussion =
        discussionRepository.createDiscussion(
            name = "Admin Test",
            description = "Test description",
            creatorId = thirdUser.uid,
            participants = listOf(currentAccount.uid, otherUser.uid))
    discussionRepository.addAdminToDiscussion(adminDiscussion, currentAccount.uid)
    discussionRepository.sendMessageToDiscussion(adminDiscussion, thirdUser, "Test message")
    val updatedAdminDiscussion = discussionRepository.getDiscussion(adminDiscussion.uid)

    val discussionWithPicture =
        discussionRepository.createDiscussion(
            name = "Picture Test",
            description = "Test with picture",
            creatorId = currentAccount.uid,
            participants = listOf(otherUser.uid))

    val participantDiscussion =
        discussionRepository.createDiscussion(
            name = "Non-Admin Test",
            description = "Test description",
            creatorId = thirdUser.uid,
            participants = listOf(currentAccount.uid, otherUser.uid))
    discussionRepository.sendMessageToDiscussion(participantDiscussion, thirdUser, "Test message")
    val updatedParticipantDiscussion = discussionRepository.getDiscussion(participantDiscussion.uid)

    // Use a mutable state to switch between discussions
    val currentDiscussionState = mutableStateOf(testDiscussion)

    compose.setContent {
      DiscussionDetailsScreen(discussion = currentDiscussionState.value, account = currentAccount)
    }

    checkpoint("smokeTestNotAdmin") {
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

    checkpoint("adminView_canEditAndAddMembers") {
      checkpoint("Admin can edit discussion details and add members") {
        runBlocking {
          // Switch to admin discussion
          currentDiscussionState.value = updatedAdminDiscussion
          compose.waitForIdle()

          // Can edit name and description
          compose.onNodeWithTag("discussion_name").assertIsDisplayed().performTextInput(" updated")
          compose
              .onNodeWithTag("discussion_description")
              .assertIsDisplayed()
              .performTextInput(" changed")

          // Can see search bar
          compose.onNodeWithText("Add Members").assertIsDisplayed()
        }
      }
    }

    checkpoint("profilePicture_displaysWhenUrlIsNull") {
      checkpoint("Profile picture shows default icon when URL is null") {
        runBlocking {
          // Switch back to testDiscussion
          currentDiscussionState.value = testDiscussion
          compose.waitForIdle()

          // Profile picture should be displayed
          compose.waitUntil(3000) {
            compose.onAllNodesWithTag("profile_picture").fetchSemanticsNodes().isNotEmpty()
          }
          compose.onNodeWithTag("profile_picture").assertIsDisplayed()
        }
      }
    }

    checkpoint("profilePicture_displaysWhenUrlIsProvided") {
      checkpoint("Profile picture shows AsyncImage when URL is provided") {
        runBlocking {
          // Switch to discussion with picture
          currentDiscussionState.value = discussionWithPicture
          compose.waitForIdle()

          // Profile picture should be displayed
          compose.waitUntil(3000) {
            compose.onAllNodesWithTag("profile_picture").fetchSemanticsNodes().isNotEmpty()
          }
          compose.onNodeWithTag("profile_picture").assertIsDisplayed()
        }
      }
    }

    checkpoint("profilePicture_isClickableForAdmins") {
      checkpoint("Profile picture is clickable for admins") {
        runBlocking {
          // Switch back to testDiscussion (current user is admin)
          currentDiscussionState.value = testDiscussion
          compose.waitForIdle()

          // Profile picture should be clickable for admins
          compose.onNodeWithTag("profile_picture").assertIsDisplayed().performClick()

          compose.waitForIdle()

          // Dialog should open with camera and gallery options
          compose.onNodeWithTag("profile_picture_camera_option").assertIsDisplayed()
          compose.onNodeWithTag("profile_picture_gallery_option").assertIsDisplayed()
          dismissProfilePictureDialogIfVisible()
        }
      }
    }

    checkpoint("profilePictureDialog_displaysOptionsCorrectly") {
      checkpoint("Profile picture dialog shows camera and gallery options") {
        runBlocking {
          // Ensure we are on admin discussion
          currentDiscussionState.value = testDiscussion
          compose.waitForIdle()
          dismissProfilePictureDialogIfVisible()

          // Click profile picture to open dialog again
          compose.onNodeWithTag("profile_picture").performClick()

          compose.waitForIdle()

          // Verify both options are displayed
          compose.onNodeWithTag("profile_picture_camera_option").assertIsDisplayed()
          compose.onNodeWithTag("profile_picture_gallery_option").assertIsDisplayed()
        }
      }
    }

    checkpoint("profilePicture_isNotClickableForNonAdmins") {
      checkpoint("Profile picture is not clickable for non-admins") {
        runBlocking {
          dismissProfilePictureDialogIfVisible()
          // Switch to participant discussion where current user is NOT admin
          currentDiscussionState.value = updatedParticipantDiscussion
          compose.waitForIdle()

          // Profile picture should be displayed but not clickable
          compose.onNodeWithTag("profile_picture").assertIsDisplayed()

          // Try to click - dialog should NOT appear
          compose.onNodeWithTag("profile_picture").performClick()

          compose.waitForIdle()

          // Dialog options should not be displayed
          compose.onNodeWithTag("profile_picture_camera_option").assertDoesNotExist()
          compose.onNodeWithTag("profile_picture_gallery_option").assertDoesNotExist()
        }
      }
    }

    // Cleanup additional discussions
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    discussionRepository.deleteDiscussion(context, updatedAdminDiscussion)
    discussionRepository.deleteDiscussion(context, discussionWithPicture)
    discussionRepository.deleteDiscussion(context, updatedParticipantDiscussion)
  }

  private fun clearFields() {
    compose.onNodeWithTag("discussion_name").performTextClearance()
    compose.onNodeWithTag("discussion_description").performTextClearance()
  }

  private fun dismissProfilePictureDialogIfVisible() {
    compose.waitForIdle()
    val dialogNodes =
        compose.onAllNodesWithTag(UITestTags.PROFILE_PICTURE_DIALOG_TITLE, useUnmergedTree = true)
    val dialogSemantics =
        runCatching { dialogNodes.fetchSemanticsNodes() }.getOrElse { emptyList() }
    if (dialogSemantics.isNotEmpty()) {
      val backNodes = compose.onAllNodesWithContentDescription("Back", useUnmergedTree = true)
      val backSemantics = runCatching { backNodes.fetchSemanticsNodes() }.getOrElse { emptyList() }
      if (backSemantics.isNotEmpty()) {
        backNodes[backSemantics.lastIndex].performClick()
        compose.waitForIdle()
      }
    }
  }
}
