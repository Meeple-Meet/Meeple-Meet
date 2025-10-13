@file:Suppress("TestFunctionName")

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
  fun initialState_signInButtonEnabled() {
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists().assertIsEnabled()
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
  fun initialState_noErrorMessageDisplayed() {
    compose.onAllNodesWithText("An unknown error occurred").assertCountEquals(0)
  }

  // ===== Email field =====

  @Test
  fun emailField_acceptsInput() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertTextContains("test@example.com")
  }

  @Test
  fun emailField_clearsErrorOnInput() {
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onNodeWithText("Email cannot be empty").assertExists()
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("t")
    compose.onNodeWithText("Email cannot be empty").assertDoesNotExist()
  }

  @Test
  fun emailField_acceptsValidEmailFormat() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("user@domain.com")
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertTextContains("user@domain.com")
  }

  // ===== Password field =====

  @Test
  fun passwordField_acceptsInput() {
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("password123")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains("•••••••••••")
  }

  @Test
  fun passwordField_toggleVisibility() {
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("secret")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).assertExists()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
  }

  @Test
  fun passwordField_clearsErrorOnInput() {
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onNodeWithText("Password cannot be empty").assertExists()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("p")
    compose.onNodeWithText("Password cannot be empty").assertDoesNotExist()
  }

  @Test
  fun passwordField_acceptsLongPassword() {
    val longPassword = "a".repeat(100)
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput(longPassword)
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertTextContains(longPassword)
  }

  // ===== Validation =====

  @Test
  fun validation_emptyEmail_showsError() {
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onNodeWithText("Email cannot be empty").assertExists()
  }

  @Test
  fun validation_invalidEmailFormat_showsError() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun validation_emptyPassword_showsError() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onNodeWithText("Password cannot be empty").assertExists()
  }

  @Test
  fun validation_bothFieldsValid_noClientErrors() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onAllNodesWithText("Invalid email format").assertCountEquals(0)
    compose.onAllNodesWithText("Email cannot be empty").assertCountEquals(0)
    compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
  }

  @Test
  fun validation_multipleErrors_showsBothErrors() {
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onNodeWithText("Email cannot be empty").assertExists()
    compose.onNodeWithText("Password cannot be empty").assertExists()
  }

  @Test
  fun validation_emailWithSpaces_consideredInvalid() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test @example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  // ===== Loading state =====

  @Test
  fun loadingState_disablesButtons() {
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

  @Test
  fun loadingState_buttonsEnabledWhenNotLoading() {
    setVmState(AuthUIState(isLoading = false))
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
    compose.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertIsEnabled()
  }

  // ===== Error messages =====

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
  fun errorMessage_nullFallback_noCardShown() {
    setVmState(AuthUIState(errorMsg = null))
    compose.onAllNodesWithText("An unknown error occurred").assertCountEquals(0)
  }

  // ===== Clear error =====

  @Test
  fun signInButton_clearsErrorBeforeSubmit() {
    setVmState(AuthUIState(errorMsg = "E"))
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    // wait one frame then assert card gone
    compose.waitForIdle()
    compose.onAllNodesWithText("E").assertCountEquals(0)
  }

  @Test
  fun signInButton_clearsErrorEvenOnValidationFailure() {
    setVmState(AuthUIState(errorMsg = "E"))
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.waitForIdle()
    compose.onAllNodesWithText("E").assertCountEquals(0)
  }

  // ===== Google sign-in button (no platform flow assertions) =====

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

  // ===== OR divider =====

  @Test
  fun orDivider_displayed() {
    compose.onNodeWithText("OR").assertExists()
  }

  // ===== Edge cases =====

  @Test
  fun validation_emailWithoutAtSymbol_invalid() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("testexample.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun validation_emailWithoutDomain_invalid() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("password123")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun validation_singleCharacterPassword_accepted() {
    compose.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@example.com")
    compose.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("a")
    compose.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    compose.onAllNodesWithText("Password cannot be empty").assertCountEquals(0)
  }

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
}
