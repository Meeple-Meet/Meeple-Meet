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
import androidx.compose.ui.test.performTextInput
import com.github.meeplemeet.model.auth.SignUpViewModel
import com.github.meeplemeet.ui.auth.SignUpScreen
import com.github.meeplemeet.ui.auth.SignUpScreenTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignUpScreenTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var vm: SignUpViewModel

  @Before
  fun setup() {
    vm = SignUpViewModel() // real view model, no mocks
    compose.setContent {
      SignUpScreen(viewModel = vm) // uses defaults for NavController and CredentialManager
    }
  }

  // ===== Initial state =====

  @Test
  fun initialState_allFieldsEmpty() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertExists().assertTextContains("")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertExists().assertTextContains("")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertExists()
        .assertTextContains("")
  }

  @Test
  fun initialState_signUpButtonDisabled() {
    // Button should be disabled when fields are empty
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertExists().assertIsNotEnabled()
  }

  @Test
  fun initialState_googleSignUpButtonEnabled() {
    compose
        .onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON)
        .assertExists()
        .assertIsEnabled()
  }

  @Test
  fun initialState_welcomeTextDisplayed() {
    compose.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
  }

  @Test
  fun initialState_logInLinkDisplayed() {
    compose.onNodeWithText("Already have an account? ").assertExists()
    compose.onNodeWithText("Log in.").assertExists()
  }

  @Test
  fun initialState_noValidationErrorsDisplayed() {
    compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
    compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
    compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
    compose.onAllNodesWithText("Password is too weak").assertCountEquals(0)
    compose.onAllNodesWithText("Please confirm your password").assertCountEquals(0)
    compose.onAllNodesWithText("Passwords do not match").assertCountEquals(0)
  }

  // ===== Email field =====

  @Test
  fun emailField_acceptsInput() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertTextContains("test@example.com")
  }

  @Test
  fun emailField_showsErrorInRealTime_invalidFormat() {
    // Error should appear immediately as user types invalid email
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
    compose.waitForIdle()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun emailField_errorClearsWhenCorrected() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("invalid")
    compose.waitForIdle()
    compose.onNodeWithText("Invalid email format").assertExists()
    // Clear and enter valid email
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("@example.com")
    compose.waitForIdle()
    compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
  }

  // ===== Password field =====

  @Test
  fun passwordField_acceptsInput_andMasks() {
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    // Initially password is hidden
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("•••••••••••")
    // Toggle to show
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("password123")
    // Toggle to hide
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("•••••••••••")
  }

  @Test
  fun passwordField_showsErrorInRealTime_tooWeak() {
    // Error should appear immediately when password is too short
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("12345")
    compose.waitForIdle()
    compose.onNodeWithText("Password is too weak").assertExists()
  }

  // ===== Confirm Password field =====

  @Test
  fun confirmPasswordField_acceptsInput_andMasks() {
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
  }

  @Test
  fun confirmPasswordField_showsErrorInRealTime_mismatch() {
    // Set password first
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    // Enter mismatched confirm password
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password124")
    compose.waitForIdle()
    compose.onNodeWithText("Passwords do not match").assertExists()
  }

  // ===== Button enablement based on validation =====

  @Test
  fun signUpButton_disabledWhenEmailEmpty() {
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    compose.waitForIdle()
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signUpButton_disabledWhenPasswordEmpty() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    compose.waitForIdle()
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signUpButton_disabledWhenConfirmPasswordEmpty() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.waitForIdle()
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signUpButton_disabledWhenEmailInvalid() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    compose.waitForIdle()
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signUpButton_disabledWhenPasswordTooWeak() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("12345") // < 6
    compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("12345")
    compose.waitForIdle()
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signUpButton_disabledWhenPasswordsMismatch() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password124")
    compose.waitForIdle()
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signUpButton_enabledWhenAllFieldsValid() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    compose.waitForIdle()
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsEnabled()
  }

  // ===== Real-time validation errors =====

  @Test
  fun validation_emailWithSpaces_showsErrorImmediately() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test @example.com")
    compose.waitForIdle()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun validation_allFieldsValid_noClientErrors() {
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
  }

  // ===== Google sign-up button =====

  @Test
  fun googleSignUpButton_hasCorrectText() {
    compose
        .onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON)
        .assertTextContains("Connect with Google")
  }

  // ===== OR divider =====

  @Test
  fun orDivider_displayed() {
    compose.onNodeWithText("OR").assertExists()
  }

  // ===== All required elements present =====

  @Test
  fun allRequiredElements_present() {
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
  }

  // ===== Edge cases =====

  @Test
  fun validation_buttonEnabledAfterCorrectingEmail() {
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
  }

  @Test
  fun validation_exactlySixCharacterPassword_accepted() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("123456")
    compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("123456")
    compose.waitForIdle()
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsEnabled()
  }

  @Test
  fun validation_passwordChangeRevalidatesConfirmPassword() {
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
  }
}
