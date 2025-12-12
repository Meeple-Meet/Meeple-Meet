package com.github.meeplemeet.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.ui.account.*
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest : FirestoreTests() {

  @get:Rule val composeTestRule = createComposeRule()

  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var viewModel: ProfileScreenViewModel
  private lateinit var user: Account

  @Before
  fun setup() = runBlocking {
    viewModel = ProfileScreenViewModel()

    // Create a user with random handle to avoid collisions
    user =
        accountRepository.createAccount(
            userHandle = "@profiletest${Random.nextInt(100000)}",
            name = "Profile Tester",
            email = "tester@example.com",
            photoUrl = null)
  }

  @Test
  fun testRolesSection_dialogFlow() {
    // Setup: User with a shop
    runBlocking {
      shopRepository.createShop(
          owner = user,
          name = "Test Shop",
          address = com.github.meeplemeet.model.shared.location.Location(0.0, 0.0, "Loc"),
          openingHours = emptyList())
      // Update user to be shop owner
      accountRepository.setAccountRole(user.uid, isShopOwner = true, isSpaceRenter = false)
      user = accountRepository.getAccount(user.uid)
    }

    composeTestRule.setContent {
      MainTab(
          viewModel = viewModel,
          account = user,
          onFriendsClick = {},
          onNotificationClick = {},
          onSignOutOrDel = {},
          onDelete = {},
          onShopClick = {},
          onSpaceRenterClick = {})
    }

    // 1. Navigate to Businesses Section
    checkpoint("Navigate to Businesses") {
      // Scroll to settings row to ensure visibility
      composeTestRule
          .onNodeWithTag(MainTabTestTags.CONTENT_SCROLL)
          .performScrollToNode(hasTestTag(ProfileNavigationTestTags.SETTINGS_ROW_BUSINESSES))
      composeTestRule
          .onNodeWithTag(ProfileNavigationTestTags.SETTINGS_ROW_BUSINESSES)
          .assertIsDisplayed()
          .performClick()

      // Verify we are on the businesses page by checking for the page title "Your Businesses"
      composeTestRule.waitUntil(5000) {
        composeTestRule.onAllNodesWithText("Your Businesses").fetchSemanticsNodes().isNotEmpty()
      }
    }

    // 2. Open Roles Section
    checkpoint("Open Roles Section") {
      // COLLAPSABLE is on a Text inside a clickable Row, so we must use unmerged tree
      composeTestRule.waitUntil(5000) {
        composeTestRule
            .onAllNodesWithTag(PrivateInfoTestTags.COLLAPSABLE, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

      // Check if expanded. If shop checkbox is not displayed, it is likely collapsed.
      val isExpanded =
          runCatching {
                composeTestRule
                    .onNodeWithTag(PrivateInfoTestTags.ROLE_SHOP_CHECKBOX)
                    .assertIsDisplayed()
                true
              }
              .getOrDefault(false)

      if (!isExpanded) {
        // Click on the COLLAPSABLE text (useUnmergedTree = true)
        composeTestRule
            .onNodeWithTag(PrivateInfoTestTags.COLLAPSABLE, useUnmergedTree = true)
            .performClick()
      }
    }

    // 3. Uncheck Shop Role -> Should trigger Dialog because we have a shop
    checkpoint("Uncheck Shop Role Triggers Dialog") {
      // Ensure checkbox is displayed before clicking
      composeTestRule
          .onNodeWithTag(PrivateInfoTestTags.ROLE_SHOP_CHECKBOX)
          .assertIsDisplayed()
          .performClick()

      // Wait for dialog
      composeTestRule.waitUntil(3000) {
        composeTestRule
            .onAllNodesWithTag(PrivateInfoTestTags.ROLE_DIALOG)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }
      composeTestRule.onNodeWithTag(PrivateInfoTestTags.ROLE_DIALOG).assertIsDisplayed()
      composeTestRule
          .onNodeWithTag(PrivateInfoTestTags.ROLE_DIALOG_TEXT)
          .assertTextEquals(MainTabUi.PrivateInfo.ROLE_ACTION_SHOP)
    }

    // 4. Confirm Dialog
    checkpoint("Confirm Dialog and Verify Role Removed") {
      composeTestRule.onNodeWithTag(PrivateInfoTestTags.ROLE_DIALOG_CONFIRM).performClick()

      composeTestRule.waitUntil(3000) {
        composeTestRule
            .onAllNodesWithTag(PrivateInfoTestTags.ROLE_DIALOG)
            .fetchSemanticsNodes()
            .isEmpty()
      }

      // Verify checkbox is unchecked
      composeTestRule.onNodeWithTag(PrivateInfoTestTags.ROLE_SHOP_CHECKBOX).assertIsOff()
    }
  }

  @Test
  fun testEmailSection_toast() {
    composeTestRule.setContent {
      // Isolating EmailSection to test the Toast logic specifically
      EmailSection(
          email = "user@example.com",
          isVerified = false,
          online = true,
          onEmailChange = {},
          onFocusChanged = {},
          onSendVerification = {}, // Mock callback
          onChangeEmail = { _, _ -> })
    }

    checkpoint("Send Verification Shows Toast") {
      composeTestRule.onNodeWithTag(PrivateInfoTestTags.EMAIL_SEND_BUTTON).performClick()

      // Wait for Toast
      composeTestRule.waitUntil(2000) {
        composeTestRule
            .onAllNodesWithTag(PrivateInfoTestTags.EMAIL_TOAST)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }
      composeTestRule.onNodeWithText(MainTabUi.PrivateInfo.TOAST_MSG).assertIsDisplayed()

      // Wait for Toast to disappear (duration 1500)
      composeTestRule.waitUntil(3000) {
        composeTestRule
            .onAllNodesWithTag(PrivateInfoTestTags.EMAIL_TOAST)
            .fetchSemanticsNodes()
            .isEmpty()
      }
    }
  }

  @Test
  fun testDeleteAccountDialog() {
    val show = mutableStateOf(false)
    composeTestRule.setContent {
      if (show.value) {
        DeleteAccountDialog(show = true, onCancel = { show.value = false }, onConfirm = {})
      }
    }

    checkpoint("Show Dialog") {
      show.value = true
      composeTestRule.waitUntil(1000) {
        composeTestRule
            .onAllNodesWithTag(DeleteAccSectionTestTags.POPUP)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }
      composeTestRule.onNodeWithTag(DeleteAccSectionTestTags.POPUP).assertIsDisplayed()
    }

    checkpoint("Dismiss Dialog") {
      composeTestRule.onNodeWithTag(DeleteAccSectionTestTags.CANCEL).performClick()
      composeTestRule.waitUntil(1000) {
        composeTestRule
            .onAllNodesWithTag(DeleteAccSectionTestTags.POPUP)
            .fetchSemanticsNodes()
            .isEmpty()
      }
    }
  }

  @Test
  fun testNotificationCount_zero() {
    val userNoNotif = user.copy(notifications = emptyList())
    composeTestRule.setContent {
      MainTab(
          viewModel = viewModel,
          account = userNoNotif,
          onFriendsClick = {},
          onNotificationClick = {},
          onSignOutOrDel = {},
          onDelete = {},
          onSpaceRenterClick = {},
          onShopClick = {})
    }

    composeTestRule.onNodeWithTag(PublicInfoTestTags.NOTIF_COUNT).assertDoesNotExist()
  }

  @Test
  fun testNotificationCount_five() {
    val notifList =
        List(5) {
          com.github.meeplemeet.model.account.Notification(
              senderId = "sender",
              sentAt = Timestamp.now(),
              type = com.github.meeplemeet.model.account.NotificationType.FRIEND_REQUEST,
              read = false)
        }
    val user5Notif = user.copy(notifications = notifList)
    composeTestRule.setContent {
      MainTab(
          viewModel = viewModel,
          account = user5Notif,
          onFriendsClick = {},
          onNotificationClick = {},
          onSignOutOrDel = {},
          onDelete = {},
          onSpaceRenterClick = {},
          onShopClick = {})
    }

    composeTestRule.onNodeWithTag(PublicInfoTestTags.NOTIF_COUNT).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PublicInfoTestTags.NOTIF_COUNT)
        .onChildren()
        .onFirst()
        .assertTextEquals("5")
  }

  @Test
  fun testNotificationCount_overflow() {
    val notifList10 =
        List(10) {
          com.github.meeplemeet.model.account.Notification(
              senderId = "sender",
              sentAt = Timestamp.now(),
              type = com.github.meeplemeet.model.account.NotificationType.FRIEND_REQUEST,
              read = false)
        }
    val user10Notif = user.copy(notifications = notifList10)
    composeTestRule.setContent {
      MainTab(
          viewModel = viewModel,
          account = user10Notif,
          onFriendsClick = {},
          onNotificationClick = {},
          onSignOutOrDel = {},
          onDelete = {},
          onSpaceRenterClick = {},
          onShopClick = {})
    }

    composeTestRule.onNodeWithTag(PublicInfoTestTags.NOTIF_COUNT).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PublicInfoTestTags.NOTIF_COUNT)
        .onChildren()
        .onFirst()
        .assertTextEquals("9+")
  }

  @Test
  fun testEmailPage_navigation_and_content() {
    composeTestRule.setContent {
      MainTab(
          viewModel = viewModel,
          account = user,
          onFriendsClick = {},
          onNotificationClick = {},
          onSignOutOrDel = {},
          onDelete = {},
          onSpaceRenterClick = {},
          onShopClick = {})
    }

    // Scroll to Email settings
    composeTestRule
        .onNodeWithTag(MainTabTestTags.CONTENT_SCROLL)
        .performScrollToNode(hasTestTag(ProfileNavigationTestTags.SETTINGS_ROW_EMAIL))

    // Click Email settings
    composeTestRule.onNodeWithTag(ProfileNavigationTestTags.SETTINGS_ROW_EMAIL).performClick()

    // Verify Email Page Content
    composeTestRule.waitUntil(3000) {
      composeTestRule
          .onAllNodesWithTag(PrivateInfoTestTags.EMAIL_SECTION)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(PrivateInfoTestTags.EMAIL_SECTION).assertIsDisplayed()

    // Verify Email Input displays user email (mocked user has tester@example.com)
    // Note: The Email page logic fetches currentUser?.email ?: account.email
    // Since Firebase Auth mock might return null or something else, we check if it displays
    // *something* or the account email.
    // In this test setup, `user` is passed to MainTab. The `ProfilePage.Email` block uses
    // `viewModel.uiState` and `FirebaseProvider.auth.currentUser`.
    // We should ensure `Auth` is set up or expect `account.email` to be used if currentUser is
    // null.
    // However, MainTab uses `account.email` as fallback.

    composeTestRule.onNodeWithTag(PrivateInfoTestTags.EMAIL_INPUT).assertTextContains(user.email)
  }
}
