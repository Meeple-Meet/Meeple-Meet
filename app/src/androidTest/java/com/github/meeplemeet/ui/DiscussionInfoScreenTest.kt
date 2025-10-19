package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Message
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DiscussionSettingScreenTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var viewModel: FirestoreViewModel
  private lateinit var repository: FirestoreRepository

  private val currentAccount = Account(uid = "user1", handle = "user1", name = "Alice", email = "*")

  private val safeDiscussion =
      Discussion(
          uid = "disc1",
          name = "Test Discussion",
          description = "A sample group",
          messages = listOf(Message("user1", "Hi", com.google.firebase.Timestamp.now())),
          participants = listOf("user1", "user2"),
          creatorId = "user1",
          admins = listOf("user1"))

  @Before
  fun setup() {
    repository = mockk(relaxed = true)
    coEvery { repository.getDiscussion("disc1") } returns safeDiscussion
    coEvery { repository.getAccount(any()) } answers
        {
          val uid = firstArg<String>()
          Account(uid = uid, handle = uid, name = "NameFor$uid", email = "***")
        }

    // Initialize real ViewModel with repository
    viewModel = FirestoreViewModel(repository)

    // Inject MutableStateFlow<Discussion> into discussionFlows
    val discussionFlowsField = viewModel::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map =
        discussionFlowsField.get(viewModel) as MutableMap<String, MutableStateFlow<Discussion>>
    map["disc1"] = MutableStateFlow(safeDiscussion)

    // Also inject account flow
    val accountFlowField = viewModel::class.java.getDeclaredField("_account")
    accountFlowField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (accountFlowField.get(viewModel) as MutableStateFlow<Account?>).value = currentAccount
  }

  @Test
  fun screen_displaysDiscussionName_andButtons() {
    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel, discussion = safeDiscussion, account = currentAccount)
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
          viewModel = viewModel, discussion = safeDiscussion, account = currentAccount)
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
          viewModel = viewModel, discussion = safeDiscussion, account = currentAccount)
    }

    compose.waitForIdle()
    compose.onNodeWithTag("leave_button").performClick()

    compose.waitForIdle()
    compose.onNodeWithTag("leave_discussion_display").assertIsDisplayed()
    compose.onNodeWithText("Cancel").assertIsDisplayed()
  }

  @Test
  fun memberList_displaysMembersWithBadges() {
    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel, discussion = safeDiscussion, account = currentAccount)
    }

    compose.waitForIdle()

    compose.onNodeWithTag("member_row_user1").assertIsDisplayed()
    compose.onNodeWithTag("member_row_user2").assertIsDisplayed()
    compose.onNodeWithText("Owner").assertIsDisplayed()
    compose.onNodeWithText("Member").assertIsDisplayed()
  }

  @Test
  fun backButton_savesChanges() {
    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel, discussion = safeDiscussion, account = currentAccount)
    }

    compose.waitForIdle()

    compose.onNodeWithTag("discussion_name").performTextInput(" Updated")
    compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    compose.waitForIdle()
    // Here we just assert the UI still exists (no crash)
    compose.onNodeWithTag("discussion_name").assertExists()
  }

  @Test
  fun participantView_cannotEditOrAddMembers() {
    // Participant is not admin or owner
    val participantDiscussion = safeDiscussion.copy(admins = emptyList(), creatorId = "user3")
    val discussionFlowsField = viewModel::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map =
        discussionFlowsField.get(viewModel) as MutableMap<String, MutableStateFlow<Discussion>>
    map["disc1"] = MutableStateFlow(participantDiscussion)

    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel, discussion = safeDiscussion, account = currentAccount)
    }

    compose.waitForIdle()

    // Name and description should be visible but read-only
    compose.onNodeWithTag("discussion_name").assertIsDisplayed().assertIsNotEnabled()
    compose.onNodeWithTag("discussion_description").assertIsDisplayed().assertIsNotEnabled()

    // Member search field should not be displayed
    compose.onAllNodesWithText("Add Members").assertCountEquals(0)

    // Member row for current user: just check displayed
    compose.onNodeWithTag("member_row_user1").assertIsDisplayed()

    // Member row for other user: check displayed and not clickable
    compose.onNodeWithTag("member_row_user2").assertIsDisplayed().assertHasNoClickAction()
  }

  @Test
  fun adminView_canEditAndAddMembers() {
    // Admin but not owner
    val adminDiscussion = safeDiscussion.copy(admins = listOf("user1"), creatorId = "user3")
    val discussionFlowsField = viewModel::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map =
        discussionFlowsField.get(viewModel) as MutableMap<String, MutableStateFlow<Discussion>>
    map["disc1"] = MutableStateFlow(adminDiscussion)

    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel, discussion = safeDiscussion, account = currentAccount)
    }

    compose.waitForIdle()

    // Can edit name and description
    compose.onNodeWithTag("discussion_name").assertIsDisplayed().performTextInput(" updated")
    compose.onNodeWithTag("discussion_description").assertIsDisplayed().performTextInput(" changed")

    // Can see search bar
    compose.onNodeWithText("Add Members").assertIsDisplayed()
  }

  @Test
  fun ownerView_canRemoveAdmins() {
    // Owner is also admin and creator
    val ownerDiscussion =
        safeDiscussion.copy(admins = listOf("user1", "user2"), creatorId = "user1")
    val discussionFlowsField = viewModel::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map =
        discussionFlowsField.get(viewModel) as MutableMap<String, MutableStateFlow<Discussion>>
    map["disc1"] = MutableStateFlow(ownerDiscussion)

    compose.setContent {
      DiscussionDetailsScreen(
          viewModel = viewModel, discussion = safeDiscussion, account = currentAccount)
    }

    compose.waitForIdle()

    // Can click member to open dialog
    compose.onNodeWithTag("member_row_user2").performClick()
    compose.waitForIdle()

    // Owner can remove admin
    compose.onNodeWithText("Remove Admin").assertIsDisplayed()
  }
}
