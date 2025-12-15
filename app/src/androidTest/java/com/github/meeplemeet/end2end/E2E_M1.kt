package com.github.meeplemeet.end2end

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.MainActivity
import com.github.meeplemeet.ui.auth.SignInScreenTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.AuthUtils.signInUser
import com.github.meeplemeet.utils.AuthUtils.signOutWithBottomBar
import com.github.meeplemeet.utils.AuthUtils.signUpUser
import com.github.meeplemeet.utils.FirestoreTests
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test for Meeple Meet application. Tests the complete user journey from sign-up to
 * navigating the app.
 */
@RunWith(AndroidJUnit4::class)
class E2E_M1 : FirestoreTests() {
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  private val testEmail = "e2etest${UUID.randomUUID().toString().take(8)}@example.com"
  private val testPassword = "Password123!"
  private val testHandle = "e2eHandle${UUID.randomUUID().toString().take(6)}"
  private val testUsername = "Test User"

  @Test
  fun completeUserJourney_signUpCreateAccountAndNavigate() {
    composeTestRule.signUpUser(testEmail, testPassword, testHandle, testUsername)

    // Step 6: Navigate through the main tabs to verify full access
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).assertExists().performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).assertExists().performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).assertExists().performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()

    composeTestRule.signOutWithBottomBar()
  }

  @Test
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
    composeTestRule.signUpUser(user1Email, password, user1Handle, user1Name)
    composeTestRule.signOutWithBottomBar()

    // ===== PART 2: Create second account (Bob) =====
    composeTestRule.signUpUser(user2Email, password, user2Handle, user2Name)
    composeTestRule.signOutWithBottomBar()

    // ===== PART 3: Alice signs back in, creates a discussion with Bob, and sends a message =====
    composeTestRule.signInUser(user1Email, password)
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).assertExists().performClick()
    composeTestRule.onNodeWithTag("Add Discussion").assertExists().performClick()

    // Fill in discussion details
    composeTestRule.onNodeWithTag("Add Title").assertExists().performTextInput(discussionTitle)
    composeTestRule
        .onNodeWithTag("Add Description")
        .assertExists()
        .performTextInput("E2E test discussion")
    composeTestRule.waitForIdle()

    // Search for Bob and add him as a member during discussion creation
    composeTestRule
        .onNodeWithTag("Add Members", useUnmergedTree = true)
        .assertExists()
        .performTextInput(user2Handle)

    // Wait for search results to appear
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      try {
        composeTestRule.onNodeWithTag("Add Member Element", useUnmergedTree = true).assertExists()
        true
      } catch (_: Throwable) {
        false
      }
    }

    // Click on Bob from search results to add him
    composeTestRule
        .onNodeWithTag("Add Member Element", useUnmergedTree = true)
        .assertExists()
        .performClick()
    composeTestRule.waitForIdle()

    // Create the discussion with Bob as a member
    composeTestRule
        .onNodeWithTag("Create Discussion")
        .assertExists()
        .assertIsEnabled()
        .performClick()

    composeTestRule.waitForIdle()

    // Verify discussion appears in Alice's list
    composeTestRule.waitUntil(5000) {
      composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).isDisplayed()
    }

    // Open the discussion and send an initial message from Alice to Bob
    composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag("Input Field", useUnmergedTree = true)
        .assertExists()
        .performTextInput(initialMessageFromAlice)

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("Send Button").assertExists().performClick()

    composeTestRule.waitForIdle()

    // Wait until the sent message is visible in the thread to ensure persistence before sign-out
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      try {
        composeTestRule
            .onNodeWithText(initialMessageFromAlice, useUnmergedTree = true)
            .assertExists()
        true
      } catch (_: Throwable) {
        false
      }
    }

    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .assertExists()
        .performClick()

    composeTestRule.waitForIdle()
    composeTestRule.signOutWithBottomBar()

    // ===== PART 6: Bob signs in and verifies he can read Alice's message =====
    composeTestRule.signInUser(user2Email, password)
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).assertExists().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onRoot().performClick()

    // Bob should see the discussion that Alice created and added him to
    composeTestRule.waitUntil(15_000) {
      try {
        composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).assertExists()
        true
      } catch (_: Throwable) {
        false
      }
    }

    composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).performClick()
    composeTestRule.waitForIdle()

    // Verify Bob can read Alice's initial message
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      try {
        composeTestRule
            .onNodeWithText(initialMessageFromAlice, useUnmergedTree = true)
            .assertExists()
        true
      } catch (_: Throwable) {
        false
      }
    }

    composeTestRule.onNodeWithText(initialMessageFromAlice, useUnmergedTree = true).assertExists()

    // ===== PART 7: Bob logs out =====
    // Navigate back from the message screen to the discussions list
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .assertExists()
        .performClick()
    composeTestRule.waitForIdle()

    // Navigate to Profile tab
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()
    composeTestRule.waitForIdle()

    // Logout
    composeTestRule.onNodeWithTag("Logout Button").assertExists().performClick()
    composeTestRule.waitForIdle()

    // Verify returned to sign-in screen
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON)
        .assertExists()
        .assertIsDisplayed()
  }
}
