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
import com.github.meeplemeet.ui.auth.ALREADY_HAVE_ACCOUNT_TEXT
import com.github.meeplemeet.ui.auth.CANNOT_BE_EMPTY_EMAIL_TEXT
import com.github.meeplemeet.ui.auth.EMPTY_PWD_TEXT
import com.github.meeplemeet.ui.auth.INVALID_EMAIL_TEXT
import com.github.meeplemeet.ui.auth.LOG_IN_TEXT
import com.github.meeplemeet.ui.auth.OPTION_TEXT
import com.github.meeplemeet.ui.auth.PWD_CONFIRMATION_TEXT
import com.github.meeplemeet.ui.auth.PWD_MISSMATCH_TEXT
import com.github.meeplemeet.ui.auth.SignUpScreen
import com.github.meeplemeet.ui.auth.SignUpScreenTestTags
import com.github.meeplemeet.ui.auth.WEAK_PWD_TEXT
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignUpScreenTest : FirestoreTests() {
  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var vm: SignUpViewModel

  @Before
  fun setup() {
    vm = SignUpViewModel()
    compose.setContent { SignUpScreen(viewModel = vm) }
  }

  @Test
  fun smoke_all_cases() {

    checkpoint("Initial State – all fields empty") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertExists().assertTextContains("")
      compose
          .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
          .assertExists()
          .assertTextContains("")
      compose
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .assertExists()
          .assertTextContains("")
    }

    checkpoint("Initial State – sign-up button disabled") {
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertExists().assertIsNotEnabled()
    }

    checkpoint("Initial State – Google sign-up button enabled") {
      compose
          .onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON)
          .assertExists()
          .assertIsEnabled()
    }

    checkpoint("Initial State – screen title & OR divider") {
      compose.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
      compose.onNodeWithText(OPTION_TEXT).assertExists()
      compose.onNodeWithText(ALREADY_HAVE_ACCOUNT_TEXT).assertExists()
      compose.onNodeWithText(LOG_IN_TEXT).assertExists()
    }

    checkpoint("Initial State – no validation errors") {
      compose.onAllNodesWithText(CANNOT_BE_EMPTY_EMAIL_TEXT).assertCountEquals(0)
      compose.onAllNodesWithText(EMPTY_PWD_TEXT).assertCountEquals(0)
      compose.onAllNodesWithText(INVALID_EMAIL_TEXT).assertCountEquals(0)
      compose.onAllNodesWithText(WEAK_PWD_TEXT).assertCountEquals(0)
      compose.onAllNodesWithText(PWD_CONFIRMATION_TEXT).assertCountEquals(0)
      compose.onAllNodesWithText(PWD_MISSMATCH_TEXT).assertCountEquals(0)
    }

    // ===== Email field =====

    checkpoint("Email field accepts input") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertTextContains("test@example.com")
      clearFields()
    }

    checkpoint("Email field – real-time invalid format error") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
      compose.waitForIdle()
      compose.onNodeWithText(INVALID_EMAIL_TEXT).assertExists()
      clearFields()
    }

    checkpoint("Email field – error cleared on valid input") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("invalid")
      compose.waitForIdle()
      compose.onNodeWithText(INVALID_EMAIL_TEXT).assertExists()

      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("@example.com")
      compose.waitForIdle()
      compose.onAllNodesWithText(INVALID_EMAIL_TEXT).assertCountEquals(0)
      clearFields()
    }

    // ===== Password field =====

    checkpoint("Password field – input accepted & visibility toggle") {
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("•••••••••••")

      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("password123")

      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("•••••••••••")
      clearFields()
    }

    checkpoint("Password field – real-time weak password error") {
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("12345")
      compose.waitForIdle()
      compose.onNodeWithText(WEAK_PWD_TEXT).assertExists()
      clearFields()
    }

    // ===== Confirm Password field =====

    checkpoint("Confirm-password field – input accepted & visibility toggle") {
      compose
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .performTextInput("password123")
      compose
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .assertTextContains("•••••••••••")

      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .assertTextContains("password123")

      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE).performClick()
      compose
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .assertTextContains("•••••••••••")
      clearFields()
    }

    checkpoint("Confirm-password field – mismatch error") {
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .performTextInput("password124")
      compose.waitForIdle()
      compose.onNodeWithText(PWD_MISSMATCH_TEXT).assertExists()
      clearFields()
    }

    // ===== Button enablement =====

    checkpoint("Sign-up button – enabled only when all fields valid") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsEnabled()
      clearFields()
    }

    // ===== Google & static elements =====

    checkpoint("Google button text") {
      compose
          .onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON)
          .assertTextContains("Connect with Google")
      clearFields()
    }

    checkpoint("All required composables present") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertExists()
      compose.onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON).assertExists()
      compose.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
      compose.onNodeWithText(OPTION_TEXT).assertExists()
      compose.onNodeWithText(ALREADY_HAVE_ACCOUNT_TEXT).assertExists()
      compose.onNodeWithText(LOG_IN_TEXT).assertExists()
      clearFields()
    }

    // ===== Edge cases =====

    checkpoint("Edge case – correcting invalid email enables sign-up") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("invalid")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()

      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("@example.com")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsEnabled()
      clearFields()
    }

    checkpoint("Edge case – correcting short password enables sign-up") {
      compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("123456")
      compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("123456")
      compose.waitForIdle()
      compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsEnabled()
      clearFields()
    }

    checkpoint("Edge case – changing password creates mismatch") {
      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
      compose
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .performTextInput("password123")
      compose.waitForIdle()
      compose.onAllNodesWithText(PWD_MISSMATCH_TEXT).assertCountEquals(0)

      compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("4")
      compose.waitForIdle()
      compose.onNodeWithText(PWD_MISSMATCH_TEXT).assertExists()
      clearFields()
    }
  }

  private fun clearFields() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextClearance()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextClearance()
    compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextClearance()
  }
}
