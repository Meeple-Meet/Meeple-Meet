package com.github.meeplemeet.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountNoUid
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.account.DeleteAccSectionTestTags
import com.github.meeplemeet.ui.account.EmailVerificationTestTags
import com.github.meeplemeet.ui.account.MainTab
import com.github.meeplemeet.ui.account.MainTabTestTags
import com.github.meeplemeet.ui.account.NotificationsSectionTestTags
import com.github.meeplemeet.ui.account.PreferencesSectionTestTags
import com.github.meeplemeet.ui.account.PrivateInfoTestTags
import com.github.meeplemeet.ui.account.ProfileNavigationTestTags
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
      // node might already be visible or not in PUBLIC_INFO; try scrolling the main content
      try {
        compose.onTag(MainTabTestTags.CONTENT_SCROLL).performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
      } catch (_: AssertionError) {
        // node might be visible; ignore
      }
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

  // Preferences section
  private fun ComposeTestRule.preferencesTitle() =
      onTag(PreferencesSectionTestTags.PREFERENCES_SECTION_TITLE)

  private fun ComposeTestRule.radioLight() = onTag(PreferencesSectionTestTags.RADIO_LIGHT)

  private fun ComposeTestRule.radioDark() = onTag(PreferencesSectionTestTags.RADIO_DARK)

  private fun ComposeTestRule.radioSystem() = onTag(PreferencesSectionTestTags.RADIO_SYSTEM)

  // =======================
  // EMAIL SECTION
  // =======================

  private fun ComposeTestRule.emailErrorLabel() = onTag(PrivateInfoTestTags.EMAIL_ERROR_LABEL)

  private fun ComposeTestRule.emailSendButton() = onTag(PrivateInfoTestTags.EMAIL_SEND_BUTTON)

  private fun ComposeTestRule.emailToast() = onTag(PrivateInfoTestTags.EMAIL_TOAST)

  // =======================
  // ROLES SECTION
  // =======================
  private fun ComposeTestRule.rolesTitle() = onTag(PrivateInfoTestTags.COLLAPSABLE)

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

  private fun ComposeTestRule.delAccountPasswordInput() =
      onTag(DeleteAccSectionTestTags.PASSWORD_INPUT)

  private fun ComposeTestRule.delAccountErrorText() = onTag(DeleteAccSectionTestTags.ERROR_TEXT)

  // =======================
  // NAVIGATION HELPERS
  // =======================
  private fun ComposeTestRule.settingsRowPreferences() =
      onTag(ProfileNavigationTestTags.SETTINGS_ROW_PREFERENCES)

  private fun ComposeTestRule.settingsRowNotifications() =
      onTag(ProfileNavigationTestTags.SETTINGS_ROW_NOTIFICATIONS)

  private fun ComposeTestRule.settingsRowBusinesses() =
      onTag(ProfileNavigationTestTags.SETTINGS_ROW_BUSINESSES)

  private fun ComposeTestRule.settingsRowEmail() =
      onTag(ProfileNavigationTestTags.SETTINGS_ROW_EMAIL)

  private fun ComposeTestRule.subPageBackButton() =
      onTag(ProfileNavigationTestTags.SUB_PAGE_BACK_BUTTON)

  /** Waits until a child of the node with [parentTag] is selected. */
  private fun ComposeTestRule.waitUntilChildIsSelected(
      parentTag: String,
      timeoutMillis: Long = 5000
  ) {
    waitUntil(timeoutMillis) {
      try {
        onNodeWithTag(parentTag).onChildren().filter(isSelectable()).fetchSemanticsNodes().any {
          it.config.getOrElse(SemanticsProperties.Selected) { false }
        }
      } catch (e: Exception) {
        false
      }
    }
  }

  @Test
  fun main_tab_full_user_flow() {
    var friendsClicked = false
    var notifClicked = false
    var logoutClicked = false
    var delClicked = false
    var shopClickedId: String? = null
    var spaceClickedId: String? = null

    // Pre-populate businesses for checking later
    runBlocking {
      shopRepository.createShop(
          owner = user,
          name = "Test Shop",
          address = Location(0.0, 0.0, "Test Address"),
          openingHours = listOf(OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "17:00")))))

      spaceRenterRepository.createSpaceRenter(
          owner = user,
          name = "Test Space",
          address = Location(0.0, 0.0, "Test Address"),
          openingHours = listOf(OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "17:00")))))
    }

    compose.setContent {
      val accountState by accountRepository.listenAccount(user.uid).collectAsState(initial = user)
      AppTheme {
        MainTab(
            viewModel = profileVM,
            account = accountState,
            onFriendsClick = { friendsClicked = true },
            onNotificationClick = { notifClicked = true },
            onDelete = { delClicked = true },
            onSignOutOrDel = { logoutClicked = true },
            onShopClick = { shopClickedId = it },
            onSpaceRenterClick = { spaceClickedId = it })
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

    // ---------------------------------------------------------------------
    checkpoint("navigate_to_preferences_and_back") {
      scrollToTag(ProfileNavigationTestTags.SETTINGS_ROW_PREFERENCES)
      compose.settingsRowPreferences().assertExists().performClick()
      compose.waitForIdle()

      compose.preferencesTitle().assertExists()
      compose.radioLight().assertExists().performClick()
      compose.waitUntilChildIsSelected(PreferencesSectionTestTags.RADIO_LIGHT)
      compose
          .radioLight()
          .onChildren()
          .filterToOne(hasClickAction() or hasSetTextAction())
          .assertIsSelected()

      compose.waitForIdle()

      compose.radioDark().assertExists().performClick()
      compose.waitUntilChildIsSelected(PreferencesSectionTestTags.RADIO_DARK)

      compose
          .radioDark()
          .onChildren()
          .filterToOne(hasClickAction() or hasSetTextAction())
          .assertIsSelected()

      compose.radioSystem().assertExists().performClick()
      compose.waitUntilChildIsSelected(PreferencesSectionTestTags.RADIO_SYSTEM)
      compose
          .radioSystem()
          .onChildren()
          .filterToOne(hasClickAction() or hasSetTextAction())
          .assertIsSelected()
      compose.waitForIdle()

      // navigate back to main page
      compose.subPageBackButton().performClick()
      compose.waitForIdle()
      compose.publicInfoRoot().assertExists()
      compose.publicInfoRoot().assertExists()
    }

    // ---------------------------------------------------------------------

    checkpoint("navigate_to_notifications_and_verify") {
      scrollToTag(ProfileNavigationTestTags.SETTINGS_ROW_NOTIFICATIONS)
      compose.settingsRowNotifications().assertExists().performClick()
      compose.waitForIdle()

      compose.notifTitle().assertExists()
      compose.notifRadioEvery().assertExists().performClick()
      compose.waitUntilChildIsSelected(NotificationsSectionTestTags.RADIO_EVERYONE)
      compose
          .notifRadioEvery()
          .onChildren()
          .filterToOne(hasClickAction() or hasSetTextAction())
          .assertIsSelected()

      compose.waitForIdle()

      compose.notifRadioFriends().assertExists().performClick()
      compose.waitUntilChildIsSelected(NotificationsSectionTestTags.RADIO_FRIENDS)

      compose
          .notifRadioFriends()
          .onChildren()
          .filterToOne(hasClickAction() or hasSetTextAction())
          .assertIsSelected()

      compose.notifRadioNone().assertExists().performClick()
      compose.waitUntilChildIsSelected(NotificationsSectionTestTags.RADIO_NONE)
      compose
          .notifRadioNone()
          .onChildren()
          .filterToOne(hasClickAction() or hasSetTextAction())
          .assertIsSelected()
      compose.waitForIdle()

      compose.subPageBackButton().performClick()
      compose.waitForIdle()
      compose.publicInfoRoot().assertExists()
      compose.waitForIdle()
    }

    // ---------------------------------------------------------------------
    checkpoint("navigate_to_businesses_and_verify") {
      scrollToTag(ProfileNavigationTestTags.SETTINGS_ROW_BUSINESSES)
      compose.settingsRowBusinesses().assertExists()
      compose.settingsRowBusinesses().performClick()
      compose.waitForIdle()

      compose.rolesTitle().assertExists()

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

      // Now add the shop owner role and leave the subscaffold. Check that UI is updated right
      // with
      // regards to these changes
      compose.roleShopCheckbox().performClick()
      compose.roleDialog().assertDoesNotExist()

      compose.subPageBackButton().performClick()
      compose.waitForIdle()
      compose.publicInfoRoot().assertExists()

      compose.settingsRowBusinesses().performClick()
      compose.waitForIdle()
      compose.rolesTitle().assertExists().performClick()
      compose.roleShopCheckbox().assertExists() // UI updated correctly

      compose.subPageBackButton().performClick()
      compose.waitForIdle()
    }

    // ---------------------------------------------------------------------
    checkpoint("email_verification") {
      compose.onNodeWithTag(EmailVerificationTestTags.VERIFICATION_SECTION).assertIsDisplayed()
      compose
          .onNodeWithTag(EmailVerificationTestTags.RESEND_MAIL_VERIFICATION_BTN)
          .assertIsDisplayed()
          .performClick()
      compose.waitForIdle()
      compose.onNodeWithTag(PrivateInfoTestTags.EMAIL_TOAST).assertIsDisplayed()
      compose
          .onNodeWithTag(EmailVerificationTestTags.USER_EMAIL)
          .assertIsDisplayed()
          .assertTextContains(user.email, ignoreCase = true)
    }

    checkpoint("Delete button user flow") {
      scrollToTag(DeleteAccSectionTestTags.BUTTON)

      compose.delAccountBtn().assertExists().performClick()
      compose.delAccountPopup().assertExists()
      compose.delAccountPopupCancel().assertExists().performClick()
      compose.delAccountPopup().assertDoesNotExist()
      compose.delAccountBtn().performClick()
      compose.delAccountPopupConfirm().assertExists().performClick()
      assert(delClicked)
      compose.waitForIdle()
    }
  }
}
