package com.github.meeplemeet.utils

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.meeplemeet.ui.UITestTags
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationTestTags

object NavigationTestHelpers {

  // ===== Check helpers =====

  /**
   * Checks if a screen with the given title is displayed. Tries merged tree first (normal case),
   * then unmerged tree if necessary. This handles cases where testTags are merged with parent
   * nodes.
   */
  fun ComposeTestRule.checkScreenIsDisplayed(screenTitle: String) {
    // Wait a bit for navigation to complete
    waitForIdle()

    // Try to find the node in merged tree first (faster, normal case)
    val foundInMergedTree =
        try {
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE, useUnmergedTree = false)
              .assertIsDisplayed()
              .assertTextContains(screenTitle, substring = true, ignoreCase = true)
          true
        } catch (_: AssertionError) {
          false
        }

    // If not found in merged tree, try unmerged tree
    if (!foundInMergedTree) {
      onNodeWithTag(NavigationTestTags.SCREEN_TITLE, useUnmergedTree = true)
          .assertIsDisplayed()
          .assertTextContains(screenTitle, substring = true, ignoreCase = true)
    }
  }

  fun ComposeTestRule.checkSignInScreenIsDisplayed() {
    checkScreenIsDisplayed("Welcome!")
  }

  fun ComposeTestRule.checkSignUpScreenIsDisplayed() {
    checkScreenIsDisplayed("Welcome!")
  }

  fun ComposeTestRule.checkDiscussionsOverviewIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.DiscussionsOverview.title)
  }

  fun ComposeTestRule.checkDiscoverScreenIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.PostsOverview.title)
  }

  fun ComposeTestRule.checkSessionsScreenIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.SessionsOverview.title)
  }

  fun ComposeTestRule.checkProfileScreenIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.Profile.title)
  }

  fun ComposeTestRule.checkDiscussionScreenIsDisplayed(discussionName: String) {
    checkScreenIsDisplayed(discussionName)
  }

  fun ComposeTestRule.checkDiscussionInfoScreenIsDisplayed(discussionName: String) {
    checkScreenIsDisplayed(discussionName)
  }

  fun ComposeTestRule.checkDiscussionAddScreenIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.AddDiscussion.title)
  }

  fun ComposeTestRule.checkBottomBarIsNotDisplayed() {
    onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsNotDisplayed()
    onNodeWithTag(NavigationTestTags.DISCOVER_TAB).assertIsNotDisplayed()
    onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).assertIsNotDisplayed()
    onNodeWithTag(NavigationTestTags.SESSIONS_TAB).assertIsNotDisplayed()
    onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertIsNotDisplayed()
  }

  fun ComposeTestRule.checkBottomBarIsDisplayed() {
    onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    onNodeWithTag(NavigationTestTags.DISCOVER_TAB).assertIsDisplayed()
    onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).assertIsDisplayed()
    onNodeWithTag(NavigationTestTags.SESSIONS_TAB).assertIsDisplayed()
    onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertIsDisplayed()
  }

  // ===== Navigation helpers =====

  fun ComposeTestRule.navigateBack() {
    waitForIdle()

    // Try merged tree first, then unmerged if necessary
    val foundInMergedTree =
        try {
          onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = false)
              .assertIsDisplayed()
              .performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundInMergedTree) {
      onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
          .assertIsDisplayed()
          .performClick()
    }

    waitForIdle()
  }

  fun ComposeTestRule.clickOnTab(tag: String) {
    waitForIdle()

    // Try merged tree first, then unmerged if necessary
    val foundInMergedTree =
        try {
          onNodeWithTag(tag, useUnmergedTree = false).assertIsDisplayed().performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundInMergedTree) {
      onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().performClick()
    }

    waitForIdle()
  }

  fun ComposeTestRule.navigateToDiscussionScreen(discussionName: String) {
    waitForIdle()

    val tag = "Discussion/$discussionName" // TODO better test tag
    val foundInMergedTree =
        try {
          onNodeWithTag(tag, useUnmergedTree = false).assertIsDisplayed().performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundInMergedTree) {
      onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().performClick()
    }

    waitForIdle()
  }

  fun ComposeTestRule.navigateToAddDiscussionScreen() {
    waitForIdle()

    val tag = "Add Discussion" // TODO better tag
    val foundInMergedTree =
        try {
          onNodeWithTag(tag, useUnmergedTree = false).assertIsDisplayed().performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundInMergedTree) {
      onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().performClick()
    }

    waitForIdle()
  }

  fun ComposeTestRule.navigateToDiscussionInfoScreen(discussionName: String) {
    waitForIdle()

    val tag = "DiscussionInfo/$discussionName"
    val foundInMergedTree =
        try {
          onNodeWithTag(tag, useUnmergedTree = false).assertIsDisplayed().performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundInMergedTree) {
      onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().performClick()
    }

    waitForIdle()
  }

  fun ComposeTestRule.leaveDiscussion() {
    waitForIdle()

    // Leave button
    val foundInMergedTree =
        try {
          onNodeWithTag(UITestTags.LEAVE_BUTTON, useUnmergedTree = false)
              .assertIsDisplayed()
              .performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundInMergedTree) {
      onNodeWithTag(UITestTags.LEAVE_BUTTON, useUnmergedTree = true)
          .assertIsDisplayed()
          .performClick()
    }

    waitForIdle()

    // Confirm leave button
    val foundConfirmInMergedTree =
        try {
          onNodeWithTag(UITestTags.LEAVE_DISCUSSION_CONFIRM_BUTTON, useUnmergedTree = false)
              .assertIsDisplayed()
              .performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundConfirmInMergedTree) {
      onNodeWithTag(UITestTags.LEAVE_DISCUSSION_CONFIRM_BUTTON, useUnmergedTree = true)
          .assertIsDisplayed()
          .performClick()
    }

    waitForIdle()
  }

  fun ComposeTestRule.deleteDiscussion() {
    waitForIdle()

    // Delete button
    val foundInMergedTree =
        try {
          onNodeWithTag(UITestTags.DELETE_BUTTON, useUnmergedTree = false)
              .assertIsDisplayed()
              .performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundInMergedTree) {
      onNodeWithTag(UITestTags.DELETE_BUTTON, useUnmergedTree = true)
          .assertIsDisplayed()
          .performClick()
    }

    waitForIdle()

    // Confirm delete button
    val foundConfirmInMergedTree =
        try {
          onNodeWithTag(UITestTags.DELETE_DISCUSSION_CONFIRM_BUTTON, useUnmergedTree = false)
              .assertIsDisplayed()
              .performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundConfirmInMergedTree) {
      onNodeWithTag(UITestTags.DELETE_DISCUSSION_CONFIRM_BUTTON, useUnmergedTree = true)
          .assertIsDisplayed()
          .performClick()
    }

    waitForIdle()
  }

  fun ComposeTestRule.fillAddDiscussionForm(title: String, description: String) {
    waitForIdle()

    onNodeWithTag("Add Title").performTextInput(title)
    onNodeWithTag("Add Description").performTextInput(description)

    waitForIdle()
  }

  fun ComposeTestRule.addDiscussion(title: String, description: String) {
    waitForIdle()

    fillAddDiscussionForm(title, description)

    val tag = "Create Discussion" // TODO better tag
    val foundInMergedTree =
        try {
          onNodeWithTag(tag, useUnmergedTree = false).assertIsDisplayed().performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundInMergedTree) {
      onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().performClick()
    }

    waitForIdle()
  }

  fun ComposeTestRule.clickOnLogout() {
    waitForIdle()

    val tag = "Logout Button" // TODO better tag
    val foundInMergedTree =
        try {
          onNodeWithTag(tag, useUnmergedTree = false).assertIsDisplayed().performClick()
          true
        } catch (_: AssertionError) {
          false
        }

    if (!foundInMergedTree) {
      onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().performClick()
    }

    waitForIdle()
  }
}
