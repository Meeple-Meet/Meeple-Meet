package com.github.meeplemeet

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.ui.SignInScreenTestTags
import com.github.meeplemeet.ui.SignUpScreenTestTags
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test for Meeple Meet application. Tests the complete user journey from sign-up to
 * navigating the app.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest : FirestoreTests() {

  companion object {
    @BeforeClass
    @JvmStatic
    fun ensureEmulatorIsSet() {
      // Ensure emulator is configured for this test class as well (guard against timing issues)
      FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
      FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
    }
  }

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  private val testEmail = "e2etest${UUID.randomUUID().toString().take(8)}@example.com"
  private val testPassword = "Password123!"
  private val testUsername = "Test User"

  @Test
  fun completeUserJourney_signUpCreateAccountAndNavigate() {
    // Step 1: Navigate from Sign In to Sign Up screen
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    // Step 2: Fill in registration form on Sign Up screen
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .assertExists()
        .performTextInput(testEmail)

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .assertExists()
        .performTextInput(testPassword)

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertExists()
        .performTextInput(testPassword)

    // Step 3: Submit registration
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON)
        .assertExists()
        .assertIsEnabled()
        .performClick()

    // Wait for Create Account screen to appear
    composeTestRule.waitUntil(timeoutMillis = 12_000) {
      try {
        composeTestRule
            .onAllNodesWithText("Let's go!", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    // Fill Create Account screen: provide a unique handle and username, then press the create
    // button
    val uniqueHandle = "e2eHandle${UUID.randomUUID().toString().take(6)}"

    composeTestRule
        .onNodeWithText("Handle", substring = true)
        .assertExists()
        .performTextInput(uniqueHandle)

    composeTestRule
        .onNodeWithText("Username", substring = true)
        .assertExists()
        .performTextInput(testUsername)

    composeTestRule.onNodeWithText("Let's go!").assertExists().assertIsEnabled().performClick()

    composeTestRule.onNodeWithText("Let's go!").assertExists().assertIsEnabled().performClick()

    // After submitting sign-up + create account: wait for the main app bottom navigation to appear.
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      try {
        composeTestRule
            .onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    // Verify user is in the main app with bottom navigation
    composeTestRule
        .onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
        .assertExists()
        .assertIsDisplayed()

    // Step 6: Navigate through the main tabs to verify full access
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).assertExists()

    // Navigate to Sessions tab
    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).assertExists().performClick()

    composeTestRule.waitForIdle()

    // Navigate to Discussions tab
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).assertExists().performClick()

    composeTestRule.waitForIdle()

    // Navigate to Profile tab
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()

    composeTestRule.waitForIdle()

    // Verify profile screen shows
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertIsDisplayed()
        .assertTextContains(MeepleMeetScreen.ProfileScreen.title)

    // Sign out from profile
    composeTestRule.onNodeWithTag("Logout Button").assertExists().assertIsEnabled().performClick()

    composeTestRule.waitForIdle()

    // Verify returned to sign-in screen
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON)
        .assertExists()
        .assertIsDisplayed()
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
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .assertExists()
        .performTextInput(user1Email)

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .assertExists()
        .performTextInput(password)

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertExists()
        .performTextInput(password)

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON)
        .assertExists()
        .assertIsEnabled()
        .performClick()

    // Wait for Create Account screen
    composeTestRule.waitUntil(timeoutMillis = 12_000) {
      try {
        composeTestRule
            .onAllNodesWithText("Let's go!", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    // Fill Create Account for User 1 (Alice)
    composeTestRule
        .onNodeWithText("Handle", substring = true)
        .assertExists()
        .performTextInput(user1Handle)

    composeTestRule
        .onNodeWithText("Username", substring = true)
        .assertExists()
        .performTextInput(user1Name)

    composeTestRule.onNodeWithText("Let's go!").assertExists().assertIsEnabled().performClick()

    composeTestRule.onNodeWithText("Let's go!").assertExists().assertIsEnabled().performClick()

    // Wait for main app to load
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      try {
        composeTestRule
            .onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()

    // ===== PART 2: Alice signs out =====
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("Logout Button").assertExists().performClick()

    composeTestRule.waitForIdle()

    // Verify back at sign-in screen
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists()

    // ===== PART 3: Create second account (Bob) =====
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).assertExists().performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .assertExists()
        .performTextInput(user2Email)

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .assertExists()
        .performTextInput(password)

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertExists()
        .performTextInput(password)

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON)
        .assertExists()
        .assertIsEnabled()
        .performClick()

    // Wait for Create Account screen
    composeTestRule.waitUntil(timeoutMillis = 12_000) {
      try {
        composeTestRule
            .onAllNodesWithText("Let's go!", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    // Fill Create Account for User 2 (Bob)
    composeTestRule
        .onNodeWithText("Handle", substring = true)
        .assertExists()
        .performTextInput(user2Handle)

    composeTestRule
        .onNodeWithText("Username", substring = true)
        .assertExists()
        .performTextInput(user2Name)

    composeTestRule.onNodeWithText("Let's go!").assertExists().assertIsEnabled().performClick()

    composeTestRule.onNodeWithText("Let's go!").assertExists().assertIsEnabled().performClick()

    // Wait for main app to load
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      try {
        composeTestRule
            .onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()

    // ===== PART 4: Alice signs back in, creates a discussion with Bob, and sends a message =====
    // Sign out Bob first if still logged in (ensure back at sign-in)
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Logout Button").assertExists().performClick()
    composeTestRule.waitForIdle()

    // Alice signs in
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .assertExists()
        .performTextInput(user1Email)
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .assertExists()
        .performTextInput(password)
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists().performClick()

    // Wait for main app
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      try {
        composeTestRule
            .onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    // Alice navigates to Discussions
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).assertExists().performClick()
    composeTestRule.waitForIdle()

    // Click the Add Discussion FAB
    composeTestRule.onNodeWithTag("Add Discussion").assertExists().performClick()
    composeTestRule.waitForIdle()

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
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
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
    Thread.sleep(2000) // Wait for discussion to be created

    // Verify discussion appears in Alice's list
    composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).assertExists()

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

    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .assertExists()
        .performClick()

    composeTestRule.waitForIdle()

    // ===== PART 5: Alice signs out =====
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Logout Button").assertExists().performClick()
    composeTestRule.waitForIdle()

    // ===== PART 6: Bob signs in and verifies he can read Alice's message =====
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .assertExists()
        .performTextInput(user2Email)
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .assertExists()
        .performTextInput(password)
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists().performClick()

    // Wait for main app
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      try {
        composeTestRule
            .onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    // Navigate to discussions
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).assertExists().performClick()
    composeTestRule.waitForIdle()

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
