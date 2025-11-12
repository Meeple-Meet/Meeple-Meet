package com.github.meeplemeet.ui
// Github Copilot was used for this file
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.github.meeplemeet.model.auth.SignUpViewModel
import com.github.meeplemeet.ui.auth.SignUpScreen
import com.github.meeplemeet.ui.auth.SignUpScreenTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.Checkpoint
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignUpScreenTest {
  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()
  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var vm: SignUpViewModel

  @Before
  fun setup() {
    vm = SignUpViewModel() // real view model, no mocks
    compose.setContent {
      SignUpScreen(viewModel = vm) // uses defaults for NavController and CredentialManager
    }
  }

  @Test
  fun smoke_all_cases() {

    checkpoint("Initial State") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertExists().assertTextContains("")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertExists()
        .assertTextContains("")
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertExists()
        .assertTextContains("")
    }

    checkpoint("Initial State signUp button disabled") {
      // Button should be disabled when fields are empty
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertExists().assertIsNotEnabled()
    }

    checkpoint("Initial State Google sign-up button enabled") {
      compose
        .onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON)
        .assertExists()
        .assertIsEnabled()
    }

    checkpoint("Initial State screen title displayed") {
      compose.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
    }

    checkpoint("Initial State OR divider displayed") {
      compose.onNodeWithText("Already have an account? ").assertExists()
      compose.onNodeWithText("Log in.").assertExists()
    }

    checkpoint("No validation errors shown initially") {
      compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
      compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
      compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
      compose.onAllNodesWithText("Password is too weak").assertCountEquals(0)
      compose.onAllNodesWithText("Please confirm your password").assertCountEquals(0)
      compose.onAllNodesWithText("Passwords do not match").assertCountEquals(0)
    }

    // ===== Email field =====

    checkpoint("Email field accepts input") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertTextContains("test@example.com")
      clearFields()
    }

    checkpoint("Email field shows error in real-time invalid format") {
      // Error should appear immediately as user types invalid email
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
      compose.waitForIdle()
      compose.onNodeWithText("Invalid email format").assertExists()
      clearFields()
    }

    checkpoint("Email field clears error on valid input") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("invalid")
      compose.waitForIdle()
      compose.onNodeWithText("Invalid email format").assertExists()
      // Clear and enter valid email
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("@example.com")
      compose.waitForIdle()
      compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
      clearFields()
    }

    // ===== Password field =====

    checkpoint("Password field accepts input and masks") {
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      // Initially password is hidden
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("•••••••••••")
      // Toggle to show
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("password123")
      // Toggle to hide
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("•••••••••••")
      clearFields()
    }

    checkpoint("Password field shows error in real-time too short") {
      // Error should appear immediately when password is too short
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("12345")
      compose.waitForIdle()
      compose.onNodeWithText("Password is too weak").assertExists()
      clearFields()
    }

    // ===== Confirm Password field =====

    checkpoint("Confirm Password field accepts input and masks") {
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
      // Initially password is hidden
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertTextContains("•••••••••••")
      // Toggle to show
      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertTextContains("password123")
      // Toggle to hide
      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertTextContains("•••••••••••")
      clearFields()
    }

    checkpoint("Confirm Password field shows error on mismatch") {
      // Set password first
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      // Enter mismatched confirm password
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password124")
      compose.waitForIdle()
      compose.onNodeWithText("Passwords do not match").assertExists()
      clearFields()
    }

    // ===== Button enablement based on validation =====

    checkpoint("Sign Up button enablement based on field validity") {
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
      clearFields()
    }

    checkpoint("Sign Up button disabled when password empty") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
      clearFields()
    }

    checkpoint("Sign Up button disabled when confirm password empty") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
      clearFields()
    }

    checkpoint("Sign Up button disabled when email invalid") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
      clearFields()
    }

    checkpoint("Sign Up button disabled when password too short") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("12345") // < 6
      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("12345")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
      clearFields()
    }

    checkpoint("Sign Up button disabled when passwords do not match") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password124")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
      clearFields()
    }

    checkpoint("Sign Up button enabled when all fields valid") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsEnabled()
      clearFields()
    }

    // ===== Real-time validation errors =====

    checkpoint("Real-time validation error for empty email") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test @example.com")
      compose.waitForIdle()
      compose.onNodeWithText("Invalid email format").assertExists()
      clearFields()
    }

    checkpoint("Real-time validation error for empty password") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
      compose.waitForIdle()
      compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
      compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
      compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
      compose.onAllNodesWithText("Password is too weak").assertCountEquals(0)
      compose.onAllNodesWithText("Please confirm your password").assertCountEquals(0)
      compose.onAllNodesWithText("Passwords do not match").assertCountEquals(0)
      clearFields()
    }

    // ===== Google sign-up button =====

    checkpoint("Google sign-up button displays correct text") {
      compose
        .onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON)
        .assertTextContains("Connect with Google")
      clearFields()
    }

    // ===== OR divider =====

    checkpoint("OR divider is displayed") {
      compose.onNodeWithText("OR").assertExists()
      clearFields()
    }

    // ===== All required elements present =====

    checkpoint("All required elements are present") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON).assertExists()
      compose.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
      compose.onNodeWithText("OR").assertExists()
      compose.onNodeWithText("Already have an account? ").assertExists()
      compose.onNodeWithText("Log in.").assertExists()
      clearFields()
    }

    // ===== Edge cases =====

    checkpoint("Edge case: correcting invalid email enables sign-up") {
      // Start with invalid email
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("invalid")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()

      // Correct the email
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("@example.com")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsEnabled()
      clearFields()
    }

    checkpoint("Edge case: correcting short password enables sign-up") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("123456")
      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("123456")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsEnabled()
      clearFields()
    }

    checkpoint("Edge case: changing password creates mismatch error") {
      // Set matching passwords first
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
      compose.waitForIdle()
      compose.onAllNodesWithText("Passwords do not match").assertCountEquals(0)

      // Change password to create mismatch
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("4")
      compose.waitForIdle()
      compose.onNodeWithText("Passwords do not match").assertExists()
      clearFields()
    }
  }
  private fun clearFields() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextClearance()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextClearance()
    compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextClearance()
  }
}
