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
    waitUntilWithCatch(
        { onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).isDisplayed() }, timeoutMs = 5000)
    // --- Navigate to sign-up screen ---
    waitUntilWithCatch({
      onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).assertExists().performClick()
      true
    })

    // --- Fill out email and password ---
    waitUntilWithCatch({
      onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertExists().performTextInput(email)
      true
    })

    waitUntilWithCatch({
      onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertExists().performTextInput(password)
      true
    })

    waitUntilWithCatch({
      onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .assertExists()
          .performTextInput(password)
      true
    })

    // --- Close keyboard (Compose-only!) ---
    closeKeyboardSafely()

    // --- Submit ---
    waitUntilWithCatch({
      onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON)
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })

    // --- Wait for Create Account screen
    //
    // onAllNodesWithTag(CreateAccountTestTags.SUBMIT_BUTTON).fetchSemanticsNodes().isNotEmpty()
    waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          onAllNodesWithTag(CreateAccountTestTags.SUBMIT_BUTTON, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
        })

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
    waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          onAllNodesWithTag(OnBoardingTestTags.SKIP_BUTTON).fetchSemanticsNodes().isNotEmpty()
        })

    // --- Skip onboarding ---
    waitUntilWithCatch({
      onNodeWithTag(OnBoardingTestTags.SKIP_BUTTON).performClick()
      true
    })

    waitForIdle()

    // --- Wait for main app ---
    waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
              .fetchSemanticsNodes()
              .isNotEmpty()
        })

    waitUntilWithCatch({
      onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists().assertIsDisplayed()
      true
    })
  }

  fun ComposeTestRule.closeKeyboardSafely() {
    // Tap the root of the composition to clear focus
    try {
      onRoot().performClick()
      waitForIdle()
    } catch (_: Throwable) {}
  }

  fun ComposeTestRule.signInUser(email: String, password: String) {
    waitUntilWithCatch({
      onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertExists().performTextInput(email)
      true
    })
    waitUntilWithCatch({
      onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertExists().performTextInput(password)
      true
    })

    closeKeyboardSafely()

    waitUntilWithCatch({
      onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON)
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })

    waitUntilWithCatch(
        timeoutMs = 15_000,
        predicate = {
          onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
              .fetchSemanticsNodes()
              .isNotEmpty()
        })
  }

  fun ComposeTestRule.signOutWithBottomBar() {
    waitForIdle()
    waitUntilWithCatch({
      onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()
      true
    })
    waitForIdle()
    waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          onAllNodesWithTag("Logout Button", useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
        })
    waitUntilWithCatch({
      onNodeWithTag("Logout Button").assertExists().performClick()
      true
    })
    waitForIdle()
    waitUntilWithCatch({
      onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists()
      true
    })
  }

  fun ComposeTestRule.waitUntilWithCatch(predicate: () -> Boolean, timeoutMs: Long = 50_000) {
    waitUntil(timeoutMs) {
      try {
        predicate()
      } catch (_: Throwable) {
        false
      }
    }
  }
}
