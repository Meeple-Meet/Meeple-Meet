package com.github.meeplemeet.utils

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.github.meeplemeet.ui.auth.SignInScreenTestTags
import com.github.meeplemeet.ui.auth.SignUpScreenTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags

object AuthUtils {
  fun ComposeTestRule.signUpUser(
      email: String,
      password: String,
      handle: String,
      username: String
  ) {
    onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).assertExists().performClick()
    onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertExists().performTextInput(email)
    onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertExists().performTextInput(password)

    onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertExists()
        .performTextInput(password)

    Espresso.closeSoftKeyboard()

    onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON)
        .assertExists()
        .assertIsEnabled()
        .performClick()

    // Wait for Create Account screen
    waitUntil(timeoutMillis = 5_000) {
      try {
        onAllNodesWithText("Let's go!", substring = true).fetchSemanticsNodes().isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    // Fill Create Account for User 1 (Alice)
    onNodeWithText("Handle", substring = true).assertExists().performTextInput(handle)
    onNodeWithText("Username", substring = true).assertExists().performTextInput(username)
    onNodeWithText("Let's go!").assertExists().assertIsEnabled().performClick()

    // Wait for main app to load
    waitUntil(timeoutMillis = 5_000) {
      try {
        onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists().assertIsDisplayed()
  }

  fun ComposeTestRule.signInUser(email: String, password: String) {
    onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertExists().performTextInput(email)
    onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertExists().performTextInput(password)

    Espresso.closeSoftKeyboard()

    onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON)
        .assertExists()
        .assertIsEnabled()
        .performClick()

    waitUntil(timeoutMillis = 15_000) {
      try {
        onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }
  }

  fun ComposeTestRule.signOutWithBottomBar() {
    onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()
    waitForIdle()
    onNodeWithTag("Logout Button").assertExists().performClick()
    waitForIdle()
    onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists()
  }
}
