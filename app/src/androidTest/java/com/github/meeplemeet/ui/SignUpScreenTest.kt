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
import com.github.meeplemeet.model.repositories.AuthRepository
import com.github.meeplemeet.model.viewmodels.AuthUIState
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignUpScreenTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var vm: AuthViewModel

  @Before
  fun setup() {
    vm = AuthViewModel(AuthRepository()) // real view model, no mocks
    // :contentReference[oaicite:1]{index=1}
    compose.setContent {
      SignUpScreen(viewModel = vm) // uses defaults for NavController and CredentialManager
      // :contentReference[oaicite:2]{index=2}
    }
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
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertExists().assertTextContains("")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertExists().assertTextContains("")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertExists()
        .assertTextContains("")
  }

  @Test
  fun initialState_signUpButtonEnabled() {
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertExists().assertIsEnabled()
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
    compose.onNodeWithText("Welcome!").assertExists()
  }

  @Test
  fun initialState_logInLinkDisplayed() {
    compose.onNodeWithText("Already have an account? ").assertExists()
    compose.onNodeWithText("Log in.").assertExists()
  }

  @Test
  fun initialState_noErrorMessageDisplayed() {
    compose.onAllNodesWithText("An unknown error occurred").assertCountEquals(0)
  }

  // ===== Email field =====

  @Test
  fun emailField_acceptsInput() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertTextContains("test@example.com")
  }

  @Test
  fun emailField_clearsErrorOnInput() {
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Email cannot be empty").assertExists()
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("t")
    compose.onNodeWithText("Email cannot be empty").assertDoesNotExist()
  }

  @Test
  fun emailField_acceptsValidEmailFormat() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("user@domain.com")
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).assertTextContains("user@domain.com")
  }

  // ===== Password field =====

  @Test
  fun passwordField_acceptsInput_andMasks() {
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("password123")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains("•••••••••••")
  }

  @Test
  fun passwordField_toggleVisibility() {
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("secret")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).assertExists()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
  }

  @Test
  fun passwordField_clearsErrorOnInput() {
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Password cannot be empty").assertExists()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("p")
    compose.onNodeWithText("Password cannot be empty").assertDoesNotExist()
  }

  @Test
  fun passwordField_acceptsLongPassword() {
    val longPassword = "a".repeat(100)
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput(longPassword)
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).assertTextContains(longPassword)
  }

  // ===== Confirm Password field =====

  @Test
  fun confirmPasswordField_acceptsInput_andMasks() {
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertTextContains("password123")
    compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertTextContains("•••••••••••")
  }

  @Test
  fun confirmPasswordField_clearsErrorOnInput() {
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Please confirm your password").assertExists()
    compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("p")
    compose.onNodeWithText("Please confirm your password").assertDoesNotExist()
  }

  // ===== Validation =====

  @Test
  fun validation_emptyEmail_showsError() {
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Email cannot be empty").assertExists()
  }

  @Test
  fun validation_invalidEmailFormat_showsError() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun validation_emptyPassword_showsError() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("x")
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Password cannot be empty").assertExists()
  }

  @Test
  fun validation_weakPassword_showsError() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("12345") // < 6
    compose.onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("12345")
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Password is too weak").assertExists()
  }

  @Test
  fun validation_emptyConfirmPassword_showsError() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Please confirm your password").assertExists()
  }

  @Test
  fun validation_mismatchedPasswords_showsError() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password124")
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Passwords do not match").assertExists()
  }

  @Test
  fun validation_allValid_noClientErrors() {
    compose.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
    compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
    compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
    compose.onAllNodesWithText("Password is too weak").assertCountEquals(0)
    compose.onAllNodesWithText("Please confirm your password").assertCountEquals(0)
    compose.onAllNodesWithText("Passwords do not match").assertCountEquals(0)
  }

  @Test
  fun validation_multipleErrors_showsAllErrors() {
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()
    compose.onNodeWithText("Email cannot be empty").assertExists()
    compose.onNodeWithText("Password cannot be empty").assertExists()
    compose.onNodeWithText("Please confirm your password").assertExists()
  }

  // ===== Loading state =====

  @Test
  fun loadingState_disablesButtons() {
    setVmState(AuthUIState(isLoading = true))
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
    compose.onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun loadingState_showsLoadingIndicator() {
    setVmState(AuthUIState(isLoading = true))
    compose.onNodeWithTag(SignUpScreenTestTags.LOADING_INDICATOR).assertExists()
  }

  @Test
  fun loadingState_notLoading_noLoadingIndicator() {
    setVmState(AuthUIState(isLoading = false))
    compose.onNodeWithTag(SignUpScreenTestTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun loadingState_buttonsEnabledWhenNotLoading() {
    setVmState(AuthUIState(isLoading = false))
    compose.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsEnabled()
    compose.onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON).assertIsEnabled()
  }

  // ===== Error messages (server-side mapping) =====

  @Test
  fun errorMessage_emailAlreadyInUse_mappedAndDisplayed() {
    setVmState(AuthUIState(errorMsg = "auth/email-already-in-use"))
    compose
        .onNodeWithText("Email already in use")
        .assertExists() // mapped in UI :contentReference[oaicite:3]{index=3}
  }

  @Test
  fun errorMessage_weakPassword_mappedAndDisplayed() {
    setVmState(AuthUIState(errorMsg = "auth/weak-password"))
    compose
        .onNodeWithText("Password is too weak")
        .assertExists() // mapped in UI :contentReference[oaicite:4]{index=4}
  }

  @Test
  fun errorMessage_invalidEmail_mappedAndDisplayed() {
    setVmState(AuthUIState(errorMsg = "auth/invalid-email"))
    compose
        .onNodeWithText("Invalid email format")
        .assertExists() // mapped in UI :contentReference[oaicite:5]{index=5}
  }

  @Test
  fun errorMessage_other_unmapped_passthroughDisplayed() {
    setVmState(AuthUIState(errorMsg = "Some other error"))
    compose
        .onNodeWithText("Some other error")
        .assertExists() // passthrough :contentReference[oaicite:6]{index=6}
  }

  // ===== Google sign-up button =====

  @Test
  fun googleSignUpButton_hasCorrectText() {
    compose
        .onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON)
        .assertTextContains("Connect with Google")
  }

  @Test
  fun googleSignUpButton_disabledDuringLoading() {
    setVmState(AuthUIState(isLoading = true))
    compose.onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON).assertIsNotEnabled()
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
    compose.onNodeWithText("Welcome!").assertExists()
    compose.onNodeWithText("OR").assertExists()
    compose.onNodeWithText("Already have an account? ").assertExists()
    compose.onNodeWithText("Log in.").assertExists()
  }
}
