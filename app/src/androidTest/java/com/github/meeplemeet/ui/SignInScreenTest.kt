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
import androidx.compose.ui.test.performTextInput
import com.github.meeplemeet.model.auth.AuthRepository
import com.github.meeplemeet.model.auth.AuthUIState
import com.github.meeplemeet.model.auth.AuthViewModel
import com.github.meeplemeet.ui.auth.SignInScreen
import com.github.meeplemeet.ui.auth.SignInScreenTestTags
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignInScreenTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var vm: AuthViewModel

  @Before
  fun setup() {
    vm = AuthViewModel(AuthRepository()) // real view model
    compose.setContent { SignInScreen(viewModel = vm) }
  }

  // ===== helpers =====

  private fun setVmState(state: AuthUIState) {
    val f =
        vm::class.java.declaredFields.firstOrNull { field ->
          field.isAccessible = true
          val v = field.get(vm)
          v is MutableStateFlow<*> && v.value is AuthUIState
        } ?: error("MutableStateFlow<AuthUIState> not found on AuthViewModel")

    @Suppress("UNCHECKED_CAST") val flow = f.get(vm) as MutableStateFlow<AuthUIState>
    flow.value = state
    compose.waitForIdle()
  }

  // ===== Initial state =====

  @Test
  fun initialState_allFieldsEmpty() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertExists().assertTextContains("")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertExists().assertTextContains("")
  }

  @Test
  fun initialState_signInButtonDisabled() {
    // Button should be disabled when fields are empty
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists().assertIsNotEnabled()
  }

  @Test
  fun initialState_googleSignInButtonEnabled() {
    compose
        .onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON)
        .assertExists()
        .assertIsEnabled()
  }

  @Test
  fun initialState_welcomeTextDisplayed() {
    compose.onNodeWithText("Welcome!").assertExists()
  }

  @Test
  fun initialState_signUpLinkDisplayed() {
    compose.onNodeWithText("I'm a new user. ").assertExists()
    compose.onNodeWithText("Sign up.").assertExists()
  }

  @Test
  fun initialState_noValidationErrorsDisplayed() {
    compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
    compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
    compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
  }

  // ===== Email field =====

  @Test
  fun emailField_acceptsInput() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertTextContains("test@example.com")
  }

  @Test
  fun emailField_showsErrorInRealTime_invalidFormat() {
    // Error should appear immediately as user types invalid email
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
    compose.waitForIdle()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun emailField_noErrorWhenEmpty_untouched() {
    // No error should show when field is empty (not yet touched with invalid data)
    compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
  }

  @Test
  fun emailField_validEmail_noError() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("user@domain.com")
    compose.waitForIdle()
    compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
  }

  @Test
  fun emailField_errorClearsWhenCorrected() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("invalid")
    compose.waitForIdle()
    compose.onNodeWithText("Invalid email format").assertExists()
    // Clear and enter valid email
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("@example.com")
    compose.waitForIdle()
    compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
  }

  // ===== Password field =====

  @Test
  fun passwordField_acceptsInput() {
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("password123")
  }

  @Test
  fun passwordField_toggleVisibility() {
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("secret")
    // Initially password is hidden
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("••••••")
    // Toggle to show
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("secret")
    // Toggle to hide
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("••••••")
  }

  @Test
  fun passwordField_noErrorWhenEmpty_untouched() {
    // No error should show when field is empty (not yet touched)
    compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
  }

  @Test
  fun passwordField_acceptsLongPassword() {
    val longPassword = "a".repeat(100)
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput(longPassword)
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains(longPassword)
  }

  // ===== Button enablement based on validation =====

  @Test
  fun signInButton_disabledWhenEmailEmpty() {
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.waitForIdle()
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signInButton_disabledWhenPasswordEmpty() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.waitForIdle()
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signInButton_disabledWhenEmailInvalid() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.waitForIdle()
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signInButton_enabledWhenBothFieldsValid() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.waitForIdle()
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
  }

  @Test
  fun signInButton_disabledWhenBothFieldsEmpty() {
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  // ===== Real-time validation errors =====

  @Test
  fun validation_emailWithSpaces_showsErrorImmediately() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test @example.com")
    compose.waitForIdle()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun validation_emailWithoutAtSymbol_showsErrorImmediately() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("testexample.com")
    compose.waitForIdle()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun validation_emailWithoutDomain_showsErrorImmediately() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@")
    compose.waitForIdle()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun validation_bothFieldsValid_noClientErrors() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.waitForIdle()
    compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
    compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
    compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
  }

  // ===== Loading state =====

  @Test
  fun loadingState_disablesAllButtons() {
    // Even with valid input, buttons should be disabled during loading
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    setVmState(AuthUIState(isLoading = true))
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
    compose.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun loadingState_showsLoadingIndicator() {
    setVmState(AuthUIState(isLoading = true))
    compose.onNodeWithTag(SignInScreenTestTags.LOADING_INDICATOR).assertExists()
  }

  @Test
  fun loadingState_notLoading_noLoadingIndicator() {
    setVmState(AuthUIState(isLoading = false))
    compose.onNodeWithTag(SignInScreenTestTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  // ===== Server error messages =====

  @Test
  fun errorMessage_displayed_whenPresent() {
    setVmState(AuthUIState(errorMsg = "Invalid credentials"))
    compose.onNodeWithText("Invalid credentials").assertExists()
  }

  @Test
  fun errorMessage_notDisplayed_whenNull() {
    setVmState(AuthUIState(errorMsg = null))
    compose.onAllNodesWithText("Invalid credentials").assertCountEquals(0)
  }

  @Test
  fun errorMessage_clearedOnButtonClick() {
    setVmState(AuthUIState(errorMsg = "Previous error"))
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.waitForIdle()
    compose.onAllNodesWithText("Previous error").assertCountEquals(0)
  }

  // ===== Google sign-in button =====

  @Test
  fun googleSignInButton_hasCorrectText() {
    compose
        .onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON)
        .assertTextContains("Connect with Google")
  }

  @Test
  fun googleSignInButton_disabledDuringLoading() {
    setVmState(AuthUIState(isLoading = true))
    compose.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun googleSignInButton_enabledWhenNotLoading() {
    setVmState(AuthUIState(isLoading = false))
    compose.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertIsEnabled()
  }

  // ===== OR divider =====

  @Test
  fun orDivider_displayed() {
    compose.onNodeWithText("OR").assertExists()
  }

  // ===== Complete screen elements =====

  @Test
  fun allRequiredElements_present() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertExists()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertExists()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).assertExists()
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists()
    compose.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertExists()
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).assertExists()
    compose.onNodeWithText("Welcome!").assertExists()
    compose.onNodeWithText("OR").assertExists()
  }

  // ===== Edge cases =====

  @Test
  fun validation_singleCharacterPassword_accepted() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("a")
    compose.waitForIdle()
    compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
  }

  @Test
  fun validation_buttonEnabledAfterCorrectingEmail() {
    // Start with invalid email
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("invalid")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password")
    compose.waitForIdle()
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()

    // Correct the email
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("@example.com")
    compose.waitForIdle()
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
  }

  @Test
  fun validation_specialCharactersInPassword_accepted() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("p@ssw0rd!#$%")
    compose.waitForIdle()
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
  }
}
