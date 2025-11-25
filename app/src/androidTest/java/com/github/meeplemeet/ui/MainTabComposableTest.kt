package com.github.meeplemeet.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountNoUid
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.ui.account.DeleteAccSectionTestTags
import com.github.meeplemeet.ui.account.MainTab
import com.github.meeplemeet.ui.account.NotificationsSectionTestTags
import com.github.meeplemeet.ui.account.PrivateInfoTestTags
import com.github.meeplemeet.ui.account.PublicInfoTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabComposableTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var profileVM: ProfileScreenViewModel
  private lateinit var user: Account
  private lateinit var otherUser: Account

  @Before
  fun setup() {
    runBlocking {
      profileVM = ProfileScreenViewModel()

      val uid1 = "test_user_1"
      val uid2 = "test_user_2"

      val payload1 =
          AccountNoUid(
              handle = "@tester",
              name = "testUser",
              email = "tester@example.com",
              photoUrl = null,
              description = null,
              shopOwner = false,
              spaceRenter = false)

      val payload2 =
          AccountNoUid(
              handle = "@number_1",
              name = "Existing User",
              email = "number1@example.com",
              photoUrl = null,
              description = null,
              shopOwner = false,
              spaceRenter = false)

      db.collection("accounts").document(uid1).set(payload1).await()

      db.collection("accounts").document(uid2).set(payload2).await()

      user = accountRepository.getAccount(uid1)
      otherUser = accountRepository.getAccount(uid2)
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /** Shorthand for getting a single node by tag. */
  private fun ComposeTestRule.onTag(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

  /** Shorthand for getting multiple nodes by tag. */
  private fun ComposeTestRule.onTags(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true)

  /** Scroll to a tag inside scrollable containers. */
  private fun scrollToTag(tag: String) {
    try {
      compose.onTag(PublicInfoTestTags.PUBLIC_INFO).performScrollToNode(hasTestTag(tag))
      compose.waitForIdle()
    } catch (_: AssertionError) {
      // node might already be visible; ignore
    }
  }

  /** Clears and types text into an input field. */
  private fun inputText(tag: String, value: String) {
    compose.onTag(tag).performTextClearance()
    compose.onTag(tag).performTextInput(value)
    compose.waitForIdle()
  }

  /** Returns the given account state as a mutable Compose state holder. */
  private fun mutableAccount(account: Account) = mutableStateOf(account)

  // -------------------------------------------------------------------------
  // Element Helpers (one per testTag)
  // -------------------------------------------------------------------------

  // =======================
  // PUBLIC INFO ROOT
  // =======================
  private fun ComposeTestRule.publicInfoRoot() = onTag(PublicInfoTestTags.PUBLIC_INFO)

  // =======================
  // AVATAR SECTION
  // =======================
  private fun ComposeTestRule.avatarContainer() = onTag(PublicInfoTestTags.AVATAR_CONTAINER)

  private fun ComposeTestRule.avatarPlaceholder() = onTag(PublicInfoTestTags.AVATAR_PLACEHOLDER)

  private fun ComposeTestRule.avatarEditIcon() = onTag(PublicInfoTestTags.AVATAR_EDIT_ICON)

  private fun ComposeTestRule.avatarChooserDialog() =
      onTag(PublicInfoTestTags.AVATAR_CHOOSER_DIALOG)

  private fun ComposeTestRule.avatarChooserCamera() =
      onTag(PublicInfoTestTags.AVATAR_CHOOSER_CAMERA)

  private fun ComposeTestRule.avatarChooserGallery() =
      onTag(PublicInfoTestTags.AVATAR_CHOOSER_GALLERY)

  private fun ComposeTestRule.avatarChooserRemove() =
      onTag(PublicInfoTestTags.AVATAR_CHOOSER_REMOVE)

  private fun ComposeTestRule.avatarChooserCancel() =
      onTag(PublicInfoTestTags.AVATAR_CHOOSER_CANCEL)

  // =======================
  // PUBLIC INFO ACTIONS
  // =======================
  private fun ComposeTestRule.actionFriends() = onTag(PublicInfoTestTags.ACTION_FRIENDS)

  private fun ComposeTestRule.actionNotifications() = onTag(PublicInfoTestTags.ACTION_NOTIFICATIONS)

  private fun ComposeTestRule.actionLogout() = onTag(PublicInfoTestTags.ACTION_LOGOUT)

  // =======================
  // PUBLIC INFO INPUTS
  // =======================
  private fun ComposeTestRule.usernameField() = onTag(PublicInfoTestTags.INPUT_USERNAME)

  private fun ComposeTestRule.usernameError() = onTag(PublicInfoTestTags.ERROR_USERNAME)

  private fun ComposeTestRule.handleField() = onTag(PublicInfoTestTags.INPUT_HANDLE)

  private fun ComposeTestRule.handleError() = onTag(PublicInfoTestTags.ERROR_HANDLE)

  private fun ComposeTestRule.descriptionField() = onTag(PublicInfoTestTags.INPUT_DESCRIPTION)

  private fun ComposeTestRule.publicInfoToast() = onTag(PublicInfoTestTags.GLOBAL_TOAST)

  // =======================
  // PRIVATE INFO SECTION
  // =======================
  private fun ComposeTestRule.privateInfoRoot() = onTag(PrivateInfoTestTags.PRIVATE_INFO)

  private fun ComposeTestRule.privateInfoTitle() = onTag(PrivateInfoTestTags.PRIVATE_INFO_TITLE)

  // =======================
  // EMAIL SECTION
  // =======================
  private fun ComposeTestRule.emailNotVerifiedLabel() =
      onTag(PrivateInfoTestTags.EMAIL_NOT_VERIFIED_LABEL)

  private fun ComposeTestRule.emailSendButton() = onTag(PrivateInfoTestTags.EMAIL_SEND_BUTTON)

  private fun ComposeTestRule.emailToast() = onTag(PrivateInfoTestTags.EMAIL_TOAST)

  // =======================
  // ROLES SECTION
  // =======================
  private fun ComposeTestRule.rolesTitle() = onTag(PrivateInfoTestTags.ROLES_SECTION_TITLE)

  private fun ComposeTestRule.roleShopCheckbox() = onTag(PrivateInfoTestTags.ROLE_SHOP_CHECKBOX)

  private fun ComposeTestRule.roleSpaceCheckbox() = onTag(PrivateInfoTestTags.ROLE_SPACE_CHECKBOX)

  // =======================
  // ROLE REMOVAL DIALOG
  // =======================
  private fun ComposeTestRule.roleDialog() = onTag(PrivateInfoTestTags.ROLE_DIALOG)

  private fun ComposeTestRule.roleDialogConfirm() = onTag(PrivateInfoTestTags.ROLE_DIALOG_CONFIRM)

  private fun ComposeTestRule.roleDialogCancel() = onTag(PrivateInfoTestTags.ROLE_DIALOG_CANCEL)

  private fun ComposeTestRule.rolesDialogText() = onTag(PrivateInfoTestTags.ROLE_DIALOG_TEXT)

  // NOTIFICATION SECTION
  private fun ComposeTestRule.notifRadioNone() = onTag(NotificationsSectionTestTags.RADIO_NONE)

  private fun ComposeTestRule.notifTitle() =
      onTag(NotificationsSectionTestTags.NOTIFICATION_SECTION_TITLE)

  private fun ComposeTestRule.notifRadioEvery() = onTag(NotificationsSectionTestTags.RADIO_EVERYONE)

  private fun ComposeTestRule.notifRadioFriends() =
      onTag(NotificationsSectionTestTags.RADIO_FRIENDS)

  // DELETE ACCOUNT

  private fun ComposeTestRule.delAccountBtn() = onTag(DeleteAccSectionTestTags.BUTTON)

  private fun ComposeTestRule.delAccountPopup() = onTag(DeleteAccSectionTestTags.POPUP)

  private fun ComposeTestRule.delAccountPopupConfirm() = onTag(DeleteAccSectionTestTags.CONFIRM)

  private fun ComposeTestRule.delAccountPopupCancel() = onTag(DeleteAccSectionTestTags.CANCEL)

  /// Other helpers

  private fun clearFocusWithBack() {
    Espresso.pressBack()
    compose.waitForIdle()
  }

  @Test
  fun main_tab_full_user_flow() {

    // Local state wrapper so changes propagate to UI as in your other screens
    val accountState = mutableAccount(user)
    var friendsClicked = false
    var notifClicked = false
    var logoutClicked = false

    compose.setContent {
      AppTheme {
        MainTab(
            viewModel = profileVM,
            account = accountState.value,
            onFriendsClick = { friendsClicked = true },
            onNotificationClick = { notifClicked = true },
            onSignOut = { logoutClicked = true })
      }
    }

    // ---------------------------------------------------------------------
    checkpoint("screen_renders_and_prefills") {
      compose.publicInfoRoot().assertExists()

      // Avatar present (placeholder initially)
      compose.avatarPlaceholder().assertExists()

      // Inputs prefilled
      compose.usernameField().assertTextEquals("testUser")
      compose.handleField().assertTextEquals("@tester")
      compose.descriptionField().assertTextEquals("")
      compose.waitForIdle()
    }

    // ---------------------------------------------------------------------
    checkpoint("avatar_shows_chooser_and_handles_cancel") {
      scrollToTag(PublicInfoTestTags.AVATAR_CONTAINER)
      compose.avatarContainer().performClick()
      compose.avatarEditIcon().assertExists()

      compose.avatarChooserDialog().assertExists()

      compose.avatarChooserCamera().assertExists()
      compose.avatarChooserGallery().assertExists()
      compose.avatarChooserRemove().assertExists().performClick()
      compose.avatarChooserCancel().assertExists().performClick()
      compose.avatarChooserDialog().assertDoesNotExist()
      compose.waitForIdle()
    }

    checkpoint("button_callbacks_are_handled") {
      compose.actionFriends().assertExists().performClick()
      assert(friendsClicked)
      compose.actionNotifications().assertExists().performClick()
      assert(notifClicked)
      compose.actionLogout().assertExists().performClick()
      assert(logoutClicked)
      compose.waitForIdle()
    }

    // ---------------------------------------------------------------------
    checkpoint("username_blank_shows_error_and_can_be_fixed") {
      scrollToTag(PublicInfoTestTags.INPUT_USERNAME)

      // Clear username
      inputText(PublicInfoTestTags.INPUT_USERNAME, "")
      compose.usernameError().assertExists()

      // Fix the username
      inputText(PublicInfoTestTags.INPUT_USERNAME, "NewName")
      compose.usernameError().assertDoesNotExist()
      compose.waitForIdle()
    }

    // ---------------------------------------------------------------------
    checkpoint("handle_conflict_shows_error_until_corrected") {
      scrollToTag(PublicInfoTestTags.INPUT_HANDLE)

      // The VM expects to detect conflicts. Make a value that will trigger one.
      inputText(PublicInfoTestTags.INPUT_HANDLE, "@number_1") // Assume taken
      compose.waitForIdle()

      // Expect handle error visible
      compose.handleError().assertExists("Handle is in use.")

      inputText(PublicInfoTestTags.INPUT_HANDLE, "invalidHandle&*&&(*&*") // Still taken
      compose.handleError().assertExists()

      // Fix it
      inputText(PublicInfoTestTags.INPUT_HANDLE, "validHandle")
      compose.handleError().assertDoesNotExist()

      inputText(PublicInfoTestTags.INPUT_HANDLE, "tester") // Original handle, should be OK
      compose.handleError().assertDoesNotExist()
      compose.waitForIdle()
    }

    // ---------------------------------------------------------------------
    checkpoint("description_updates_without_errors") {
      scrollToTag(PublicInfoTestTags.INPUT_DESCRIPTION)

      inputText(PublicInfoTestTags.INPUT_DESCRIPTION, "New description here")
      compose.descriptionField().assertTextEquals("New description here")
    }

    checkpoint("Private info section renders") {
      compose.privateInfoRoot().assertExists()
      compose.privateInfoTitle().assertExists()
      compose.waitForIdle()
    }

    // ---------------------------------------------------------------------
    checkpoint("email_change_and_verification_toast") {
      scrollToTag(PrivateInfoTestTags.EMAIL_SECTION)

      compose.emailNotVerifiedLabel().assertExists()

      inputText(PrivateInfoTestTags.EMAIL_INPUT, "newmail@example.com")

      // Trigger verification
      compose.emailSendButton().performClick()

      // Toast visible
      compose.emailToast().assertExists()
      compose.publicInfoToast().assertExists()
      compose.waitForIdle()
    }

    // ---------------------------------------------------------------------
    checkpoint("roles_shop_toggle_shows_dialog_cancel_and_confirm") {
      clearFocusWithBack()
      scrollToTag(PrivateInfoTestTags.ROLE_SHOP_CHECKBOX)

      compose.waitForIdle()
      compose.onRoot().performTouchInput { swipeUp() }

      compose.rolesTitle().assertExists()
      // Toggle on for the first time == no dialog
      compose.roleShopCheckbox().assertExists().performClick()
      compose.roleDialog().assertDoesNotExist()

      // Toggle Off == dialog
      compose.roleShopCheckbox().assertExists().performClick()
      compose.roleDialog().assertExists()
      compose.rolesDialogText().assertExists().assertTextContains("shops", substring = true)

      // Cancel
      compose.roleDialogCancel().performClick()
      compose.roleDialog().assertDoesNotExist()

      // Toggle OFF again → confirm
      compose.roleShopCheckbox().performClick()
      compose.roleDialog().assertExists()

      compose.roleDialogConfirm().performClick()
      compose.roleDialog().assertDoesNotExist()
      compose.waitForIdle()
    }

    // ---------------------------------------------------------------------
    checkpoint("roles_space_toggle_shows_dialog_cancel_and_confirm") {
      scrollToTag(PrivateInfoTestTags.ROLE_SPACE_CHECKBOX)

      compose.waitForIdle()

      // Toggle on for the first time == no dialog
      compose.roleSpaceCheckbox().assertExists().performClick()
      compose.roleDialog().assertDoesNotExist()

      // Toggle Off == dialog
      compose.roleSpaceCheckbox().assertExists().performClick()
      compose.roleDialog().assertExists()
      compose.rolesDialogText().assertExists().assertTextContains("spaces", substring = true)

      // Cancel
      compose.roleDialogCancel().performClick()
      compose.roleDialog().assertDoesNotExist()

      // Toggle OFF again → confirm
      compose.roleSpaceCheckbox().performClick()
      compose.roleDialog().assertExists()

      compose.roleDialogConfirm().performClick()
      compose.roleDialog().assertDoesNotExist()
      compose.waitForIdle()
    }

    checkpoint("Notifications section exists and works well") {
      compose.onRoot().performTouchInput { swipeUp() }

      compose.notifTitle().assertExists()
      compose.notifRadioEvery().assertExists().performClick()
      compose
          .notifRadioEvery()
          .onChildren()
          .filterToOne(hasClickAction() or hasSetTextAction())
          .assertIsSelected()

      compose.notifRadioFriends().assertExists().performClick()
      compose
          .notifRadioFriends()
          .onChildren()
          .filterToOne(hasClickAction() or hasSetTextAction())
          .assertIsSelected()

      compose.notifRadioNone().assertExists().performClick()
      compose
          .notifRadioNone()
          .onChildren()
          .filterToOne(hasClickAction() or hasSetTextAction())
          .assertIsSelected()
      compose.waitForIdle()
    }

    checkpoint("Delete button user flow") {
      compose.onRoot().performTouchInput { swipeUp() }

      compose.delAccountBtn().assertExists().performClick()
      compose.delAccountPopup().assertExists()
      compose.delAccountPopupCancel().assertExists().performClick()
      compose.delAccountPopup().assertDoesNotExist()
      compose.delAccountBtn().performClick()
      compose.delAccountPopupConfirm().assertExists().performClick()
      compose.waitForIdle()
    }
  }
}
