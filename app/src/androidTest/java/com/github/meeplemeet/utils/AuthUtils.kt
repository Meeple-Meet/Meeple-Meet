package com.github.meeplemeet.utils

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.meeplemeet.FirebaseProvider.auth
import com.github.meeplemeet.ui.account.CreateAccountTestTags
import com.github.meeplemeet.ui.auth.OnBoardingTestTags
import com.github.meeplemeet.ui.auth.SignInScreenTestTags
import com.github.meeplemeet.ui.auth.SignUpScreenTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

object AuthUtils {
  private suspend fun waitUntilAuthReady() = retryUntil { auth.currentUser != null }

  private suspend fun retryUntil(
      timeoutMs: Long = 30_000,
      intervalMs: Long = 500,
      predicate: suspend () -> Boolean
  ) {
    try {
      withTimeout(timeoutMs) {
        while (!predicate()) {
          continue
        }
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError("Condition not met within ${timeoutMs}ms", e)
    }
  }

  suspend fun ComposeTestRule.signUpUser(
      email: String,
      password: String,
      handle: String,
      username: String
  ) {
    delay(3000)
    waitForIdle()
    waitUntil(5000) { onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).isDisplayed() }
    // --- Navigate to sign-up screen ---
    onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).assertExists().performClick()

    // --- Fill out email and password ---
    onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertExists().performTextInput(email)

    onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertExists().performTextInput(password)

    onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertExists()
        .performTextInput(password)

    // --- Close keyboard (Compose-only!) ---
    closeKeyboardSafely()

    // --- Submit ---
    onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON)
        .assertExists()
        .assertIsEnabled()
        .performClick()

    // --- Wait for Create Account screen
    //
    // onAllNodesWithTag(CreateAccountTestTags.SUBMIT_BUTTON).fetchSemanticsNodes().isNotEmpty()
    waitUntil(timeoutMillis = 15_000) {
      try {
        onAllNodesWithTag(CreateAccountTestTags.SUBMIT_BUTTON, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    // --- Fill Create Account fields ---
    waitUntilWithCatch({
      onNodeWithText("Handle", substring = true).assertExists().performTextInput(handle)
      true
    })

    waitUntilWithCatch({
      onNodeWithText("Username", substring = true).assertExists().performTextInput(username)
      true
    })

    closeKeyboardSafely()

    waitUntilWithCatch({
      onNodeWithText("Let's go!").assertIsEnabled().performClick()
      true
    })

    waitUntilAuthReady()
    // --- Wait for Onboarding ---
    waitUntil(timeoutMillis = 10_000) {
      onAllNodesWithTag(OnBoardingTestTags.SKIP_BUTTON).fetchSemanticsNodes().isNotEmpty()
    }

    // --- Skip onboarding ---
    onNodeWithTag(OnBoardingTestTags.SKIP_BUTTON).performClick()

    waitForIdle()

    // --- Wait for main app ---
    waitUntil(timeoutMillis = 10_000) {
      onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists().assertIsDisplayed()
  }

  fun ComposeTestRule.closeKeyboardSafely() {
    // Tap the root of the composition to clear focus
    try {
      onRoot().performClick()
      waitForIdle()
    } catch (_: Throwable) {}
  }

  fun ComposeTestRule.signInUser(email: String, password: String) {
    onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertExists().performTextInput(email)
    onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertExists().performTextInput(password)

    closeKeyboardSafely()

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
    waitForIdle()
    onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()
    waitForIdle()
    waitUntil(timeoutMillis = 5_000) {
      try {
        onAllNodesWithTag("Logout Button", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }
    onNodeWithTag("Logout Button").assertExists().performClick()
    waitForIdle()
    onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists()
  }

  fun ComposeTestRule.waitUntilWithCatch(predicate: () -> Boolean, timeoutMs: Long = 5000) {
    waitUntil(timeoutMs) {
      try {
        predicate()
      } catch (_: Throwable) {
        false
      }
    }
  }
}
