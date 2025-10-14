package com.github.meeplemeet.utils

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationTestTags

object NavigationTestHelpers {

  // ===== Check helpers =====

  fun ComposeTestRule.checkScreenIsDisplayed(screenTitle: String) {
    onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertIsDisplayed()
        .assertTextContains(screenTitle, substring = true, ignoreCase = true)
  }

  fun ComposeTestRule.checkSignInScreenIsDisplayed() {
    checkScreenIsDisplayed("Welcome!")
  }

  fun ComposeTestRule.checkSignUpScreenIsDisplayed() {
    checkScreenIsDisplayed("Welcome!")
  }

  fun ComposeTestRule.checkDiscussionsOverviewIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.DiscussionsOverview.name)
  }

  fun ComposeTestRule.checkDiscoverScreenIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.DiscoverSessions.name)
  }

  fun ComposeTestRule.checkSessionsScreenIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.SessionsOverview.name)
  }

  fun ComposeTestRule.checkProfileScreenIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.ProfileScreen.name)
  }

  fun ComposeTestRule.checkDiscussionScreenIsDisplayed(discussionName: String) {
    checkScreenIsDisplayed(discussionName)
  }

  fun ComposeTestRule.checkDiscussionInfoScreenIsDisplayed(discussionName: String) {
    checkScreenIsDisplayed(discussionName)
  }

  fun ComposeTestRule.checkDiscussionAddScreenIsDisplayed() {
    checkScreenIsDisplayed(MeepleMeetScreen.DiscussionAddScreen.name)
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

  fun ComposeTestRule.navigateBack() {
    onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed().performClick()
  }

  fun ComposeTestRule.clickOnTab(tag: String) {
    onNodeWithTag(tag).assertIsDisplayed().performClick()
  }

  fun ComposeTestRule.navigateToDiscussionScreen(discussionName: String) {
    onNodeWithTag("Discussion/$discussionName").assertIsDisplayed().performClick()
  }

  fun ComposeTestRule.navigateToDiscussionInfoScreen(discussionName: String) {
    onNodeWithTag("DiscussionInfo/$discussionName").assertIsDisplayed().performClick()
  }
}
