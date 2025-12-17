package com.github.meeplemeet.end2end

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import com.github.meeplemeet.MainActivity
import com.github.meeplemeet.ui.auth.SignInScreenTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.AuthUtils.closeKeyboardSafely
import com.github.meeplemeet.utils.AuthUtils.signInUser
import com.github.meeplemeet.utils.AuthUtils.signOutWithBottomBar
import com.github.meeplemeet.utils.AuthUtils.signUpUser
import com.github.meeplemeet.utils.AuthUtils.waitUntilWithCatch
import com.github.meeplemeet.utils.FirestoreTests
import java.util.UUID
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test for Meeple Meet application. Tests the complete user journey from sign-up to
 * navigating the app.
 */
@RunWith(AndroidJUnit4::class)
class E2E_M1 : FirestoreTests() {
  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  private val testEmail = "e2etest${UUID.randomUUID().toString().take(8)}@example.com"
  private val testPassword = "Password123!"
  private val testHandle = "e2eHandle${UUID.randomUUID().toString().take(6)}"
  private val testUsername = "Test User"

  // Generic retry helper used for waiting on backend state convergence
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

  private suspend fun waitUntilAuthReady() = retryUntil { auth.currentUser != null }

  @Test
  fun completeUserJourney_signUpCreateAccountAndNavigate() {
    runBlocking { composeTestRule.signUpUser(testEmail, testPassword, testHandle, testUsername) }

    // Step 6: Navigate through the main tabs to verify full access
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).assertExists().performClick()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).assertExists().performClick()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule.signOutWithBottomBar()
      true
    })
  }

  @Test
  @FlakyTest
  fun twoUsers_createAndJoinDiscussion() {
    val user1Email = "user1_${UUID.randomUUID().toString().take(8)}@example.com"
    val user2Email = "user2_${UUID.randomUUID().toString().take(8)}@example.com"
    val password = "Password123!"
    val user1Handle = "user1_${UUID.randomUUID().toString().take(6)}"
    val user2Handle = "user2_${UUID.randomUUID().toString().take(6)}"
    val user1Name = "Alice"
    val user2Name = "Bob"
    val discussionTitle = "Test Discussion ${UUID.randomUUID().toString().take(6)}"
    val initialMessageFromAlice = "Hi Bob, welcome to our discussion!"

    // ===== PART 1: Create first account (Alice) =====
    composeTestRule.waitForIdle()
    runBlocking { composeTestRule.signUpUser(user1Email, password, user1Handle, user1Name) }
    runBlocking { waitUntilAuthReady() }
    composeTestRule.waitForIdle()
    composeTestRule.signOutWithBottomBar()

    // ===== PART 2: Create second account (Bob) =====
    runBlocking { composeTestRule.signUpUser(user2Email, password, user2Handle, user2Name) }
    runBlocking { waitUntilAuthReady() }
    composeTestRule.signOutWithBottomBar()

    // ===== PART 3: Alice signs back in, creates a discussion with Bob, and sends a message =====
    composeTestRule.signInUser(user1Email, password)
    runBlocking { waitUntilAuthReady() }
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag("Add Discussion").assertExists().performClick()
      true
    })

    // Fill in discussion details
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag("Add Title").assertExists().performTextInput(discussionTitle)
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag("Add Description")
          .assertExists()
          .performTextInput("E2E test discussion")
      true
    })
    composeTestRule.waitForIdle()

    // Search for Bob and add him as a member during discussion creation
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag("Add Members", useUnmergedTree = true)
          .assertExists()
          .performTextInput(user2Handle)
      true
    })

    // Wait for search results to appear
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 15_000,
        predicate = {
          composeTestRule.onNodeWithTag("Add Member Element", useUnmergedTree = true).assertExists()
          true
        })

    // Click on Bob from search results to add him
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag("Add Member Element", useUnmergedTree = true)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Create the discussion with Bob as a member
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag("Create Discussion")
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })

    composeTestRule.waitForIdle()

    // Verify discussion appears in Alice's list
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 15_000,
        predicate = {
          composeTestRule
              .onAllNodesWithText(discussionTitle, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
        })

    // Open the discussion and send an initial message from Alice to Bob
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).performClick()
      true
    })

    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag("Input Field", useUnmergedTree = true)
          .assertExists()
          .performTextInput(initialMessageFromAlice)
      true
    })

    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag("Send Button").assertExists().performClick()
      true
    })

    composeTestRule.waitForIdle()

    // Wait until the sent message is visible in the thread to ensure persistence before sign-out
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 15_000,
        predicate = {
          composeTestRule
              .onNodeWithText(initialMessageFromAlice, useUnmergedTree = true)
              .assertExists()
          true
        })

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
          .assertExists()
          .performClick()
      true
    })

    composeTestRule.waitForIdle()
    composeTestRule.signOutWithBottomBar()

    // ===== PART 6: Bob signs in and verifies he can read Alice's message =====
    composeTestRule.signInUser(user2Email, password)
    runBlocking { waitUntilAuthReady() }
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.closeKeyboardSafely()

    // Bob should see the discussion that Alice created and added him to
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 15_000,
        predicate = {
          composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).assertExists()
          true
        })

    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Verify Bob can read Alice's initial message
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 15_000,
        predicate = {
          composeTestRule
              .onNodeWithText(initialMessageFromAlice, useUnmergedTree = true)
              .assertExists()
          true
        })

    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(initialMessageFromAlice, useUnmergedTree = true).assertExists()
      true
    })

    // ===== PART 7: Bob logs out =====
    // Navigate back from the message screen to the discussions list
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Navigate to Profile tab
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Logout
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag("Logout Button").assertExists().performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Verify returned to sign-in screen
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onAllNodesWithTag(SignInScreenTestTags.SIGN_IN_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON)
          .assertExists()
          .assertIsDisplayed()
      true
    })
  }
}
