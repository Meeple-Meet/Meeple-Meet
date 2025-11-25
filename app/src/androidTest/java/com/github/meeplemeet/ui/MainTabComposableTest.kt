package com.github.meeplemeet.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountNoUid
import com.github.meeplemeet.model.account.CreateAccountViewModel
import com.github.meeplemeet.model.auth.AuthenticationViewModel
import com.github.meeplemeet.ui.account.MainTab
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

  private lateinit var authVm: AuthenticationViewModel
  private lateinit var createVm: CreateAccountViewModel
  private lateinit var user: Account
  private lateinit var otherUser: Account

  @Before
  fun setup() {
    runBlocking {
      authVm = AuthenticationViewModel()
      createVm = CreateAccountViewModel()

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

  /** Ensures a toast is visible and returns the node. */
  private fun expectToast(tag: String) = compose.onTag(tag).assertExists()

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

  private fun ComposeTestRule.avatarImage() = onTag(PublicInfoTestTags.AVATAR_IMAGE)

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

  private fun ComposeTestRule.cameraPermissionDialog() =
      onTag(PublicInfoTestTags.CAMERA_PERMISSION_DIALOG)

  private fun ComposeTestRule.cameraPermissionOk() = onTag(PublicInfoTestTags.CAMERA_PERMISSION_OK)

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
  private fun ComposeTestRule.emailSection() = onTag(PrivateInfoTestTags.EMAIL_SECTION)

  private fun ComposeTestRule.emailInput() = onTag(PrivateInfoTestTags.EMAIL_INPUT)

  private fun ComposeTestRule.emailVerifiedLabel() = onTag(PrivateInfoTestTags.EMAIL_VERIFIED_LABEL)

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

  /// Other helpers

  private fun clearFocusWithBack() {
    Espresso.pressBack()
    compose.waitForIdle()
  }

  @Test
  fun main_tab_full_user_flow() {

    // Local state wrapper so changes propagate to UI as in your other screens
    val accountState = mutableAccount(user)

    compose.setContent {
      AppTheme {
        MainTab(
            viewModel = authVm,
            viewModel = createVm,
            account = accountState.value,
            onFriendsClick = {},
            onNotificationClick = {},
            onSignOut = {})
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
    }

    // ---------------------------------------------------------------------
    checkpoint("avatar_shows_chooser_and_handles_cancel") {
      scrollToTag(PublicInfoTestTags.AVATAR_CONTAINER)
      compose.avatarContainer().performClick()

      compose.avatarChooserDialog().assertExists()

      compose.avatarChooserCamera().assertExists()
      compose.avatarChooserGallery().assertExists()
      compose.avatarChooserRemove().assertExists().performClick()
      compose.avatarChooserCancel().assertExists().performClick()
      compose.avatarChooserDialog().assertDoesNotExist()
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
    }

    // ---------------------------------------------------------------------
    checkpoint("roles_shop_toggle_shows_dialog_cancel_and_confirm") {
      clearFocusWithBack()
      scrollToTag(PrivateInfoTestTags.ROLE_SHOP_CHECKBOX)

      compose.waitForIdle()
      compose.onRoot().performTouchInput { swipeUp() }

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
    }
  }
}
