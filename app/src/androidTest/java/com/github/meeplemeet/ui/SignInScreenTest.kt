@file:Suppress("TestFunctionName")
// Github Copilot was used for this file
package com.github.meeplemeet.ui

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
import com.github.meeplemeet.model.auth.SignInViewModel
import com.github.meeplemeet.ui.auth.SignInScreen
import com.github.meeplemeet.ui.auth.SignInScreenTestTags
import com.github.meeplemeet.utils.Checkpoint
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignInScreenTest {
  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var vm: SignInViewModel

  @Before
  fun setup() {
    vm = SignInViewModel() // real view model
  }

  @Test
  fun smoke_all_cases() {
    compose.setContent { SignInScreen(viewModel = vm) }

    checkpoint("Initial State") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertExists().assertTextContains("")
      compose
          .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
          .assertExists()
          .assertTextContains("")
    }

    checkpoint("signInButton initially disabled") {
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists().assertIsNotEnabled()
    }

    checkpoint("googleSignInButton exists and enabled") {
      compose
          .onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON)
          .assertExists()
          .assertIsEnabled()
    }

    checkpoint("Welcome text displayed") { compose.onNodeWithText("Welcome!").assertExists() }

    checkpoint("Sign up prompt displayed") {
      compose.onNodeWithText("I'm a new user. ").assertExists()
      compose.onNodeWithText("Sign up.").assertExists()
    }

    checkpoint("No errors initially") {
      compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
      compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
      compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
    }

    // ===== Email field =====

    checkpoint("Email field accepts input") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertTextContains("test@example.com")
      clearFields()
    }

    checkpoint("Email field shows error on invalid input") {
      // Error should appear immediately as user types invalid email
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
      compose.waitForIdle()
      compose.onNodeWithText("Invalid email format").assertExists()
      clearFields()
    }

    checkpoint("Email field no error when empty") {
      // No error should show when field is empty (not yet touched with invalid data)
      compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
    }

    checkpoint("Email field accepts valid email") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("user@domain.com")
      compose.waitForIdle()
      compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
      clearFields()
    }

    checkpoint("Email field corrects error after invalid input") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("invalid")
      compose.waitForIdle()
      compose.onNodeWithText("Invalid email format").assertExists()
      // Clear and enter valid email
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("@example.com")
      compose.waitForIdle()
      compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
      clearFields()
    }

    // ===== Password field =====

    checkpoint("Password field accepts input") {
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("password123")
      clearFields()
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    }

    checkpoint("Password visibility toggle works") {
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("secret")
      // Initially password is hidden
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("••••••")
      // Toggle to show
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("secret")
      // Toggle to hide
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("••••••")
      clearFields()
    }

    checkpoint("Password field no error when empty") {
      // No error should show when field is empty (not yet touched)
      compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
    }

    checkpoint("Password field accepts long passwords") {
      val longPassword = "a".repeat(100)
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput(longPassword)
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains(longPassword)
      clearFields()
    }

    // ===== Button enablement based on validation =====

    checkpoint("Sign-in button enablement based on field validity") {
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
      clearFields()
    }

    checkpoint("Sign-in button enabled when both fields valid") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.waitForIdle()
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
      clearFields()
    }

    checkpoint("Sign-in button disabled when email invalid") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
      clearFields()
    }

    checkpoint("Sign-in button enabled when both fields valid") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
      clearFields()
    }

    checkpoint("Sign-in button disabled when password empty") {
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
    }

    // ===== Real-time validation errors =====

    checkpoint("Real-time validation errors for email field") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test @example.com")
      compose.waitForIdle()
      compose.onNodeWithText("Invalid email format").assertExists()
      clearFields()
    }

    checkpoint("Real-time validation errors for password field") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("testexample.com")
      compose.waitForIdle()
      compose.onNodeWithText("Invalid email format").assertExists()
      clearFields()
    }

    checkpoint("Real-time validation corrects email error") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@")
      compose.waitForIdle()
      compose.onNodeWithText("Invalid email format").assertExists()
      clearFields()
    }

    checkpoint("No validation errors when fields valid") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose.waitForIdle()
      compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
      compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
      compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
      clearFields()
    }

    // ===== Google sign-in button =====

    checkpoint("Google sign-in button text displayed") {
      compose
          .onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON)
          .assertTextContains("Connect with Google")
      clearFields()
    }

    // ===== OR divider =====

    checkpoint("OR divider displayed") {
      compose.onNodeWithText("OR").assertExists()
      clearFields()
    }

    // ===== Complete screen elements =====

    checkpoint("Complete screen elements exist") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertExists()
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertExists()
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).assertExists()
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists()
      compose.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertExists()
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).assertExists()
      compose.onNodeWithText("Welcome!").assertExists()
      compose.onNodeWithText("OR").assertExists()
      clearFields()
    }

    // ===== Edge cases =====

    checkpoint("Edge case: short password accepted") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("a")
      compose.waitForIdle()
      compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
      clearFields()
    }

    checkpoint("Edge case: correcting email from invalid to valid enables sign-in") {
      // Start with invalid email
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("invalid")
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password")
      compose.waitForIdle()
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()

      // Correct the email
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("@example.com")
      compose.waitForIdle()
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
      clearFields()
    }

    checkpoint("Edge case: special characters in password") {
      compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("p@ssw0rd!#$%")
      compose.waitForIdle()
      compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
      clearFields()
    }
  }

  private fun clearFields() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextClearance()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextClearance()
  }
}
