package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import com.github.meeplemeet.model.viewmodels.AuthUIState
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Comprehensive UI tests for SignUpScreen
 *
 * Tests cover:
 * - Initial screen state
 * - Input validation (email, password, confirm password)
 * - Password visibility toggles
 * - Form submission with validation
 * - Loading states
 * - Error messages
 * - Navigation
 * - Google sign-up
 */
class SignUpScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var mockViewModel: AuthViewModel
  private lateinit var mockNavController: NavController
  private lateinit var uiStateFlow: MutableStateFlow<AuthUIState>

  @Before
  fun setup() {
    mockViewModel = mockk(relaxed = true)
    mockNavController = mockk(relaxed = true)

    uiStateFlow = MutableStateFlow(AuthUIState())
    every { mockViewModel.uiState } returns uiStateFlow
    every { mockViewModel.registerWithEmail(any(), any()) } just runs
    every { mockViewModel.googleSignIn(any(), any()) } just runs
  }

  private fun setContent() {
    composeTestRule.setContent {
      SignUpScreen(navController = mockNavController, viewModel = mockViewModel)
      // Don't pass context or credentialManager - let them use defaults
      // Mocking CredentialManager causes IncompatibleClassChangeError in instrumentation tests
    }
  }

  // ==================== Initial State Tests ====================

  @Test
  fun initialState_allFieldsEmpty() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .assertExists()
        .assertTextContains("")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .assertExists()
        .assertTextContains("")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertExists()
        .assertTextContains("")
  }

  @Test
  fun initialState_signUpButtonEnabled() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON)
        .assertExists()
        .assertIsEnabled()
  }

  @Test
  fun initialState_googleSignUpButtonEnabled() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON)
        .assertExists()
        .assertIsEnabled()
  }

  @Test
  fun initialState_welcomeTextDisplayed() {
    setContent()

    composeTestRule.onNodeWithText("Welcome!").assertExists()
  }

  @Test
  fun initialState_logInLinkDisplayed() {
    setContent()

    composeTestRule.onNodeWithText("Already have an account? ").assertExists()
    composeTestRule.onNodeWithText("Log in.").assertExists()
  }

  // ==================== Email Field Tests ====================

  @Test
  fun emailField_acceptsInput() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .assertTextContains("test@example.com")
  }

  @Test
  fun emailField_clearsErrorOnInput() {
    setContent()

    // Trigger validation error by clicking sign up with empty email
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    // Verify error appears
    composeTestRule.onNodeWithText("Email cannot be empty").assertExists()

    // Type in email field
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("test")

    // Error should disappear
    composeTestRule.onNodeWithText("Email cannot be empty").assertDoesNotExist()
  }

  // ==================== Password Field Tests ====================

  @Test
  fun passwordField_acceptsInput() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .assertTextContains("password123")
  }

  @Test
  fun passwordField_toggleVisibility() {
    setContent()

    composeTestRule.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("secret")

    // Click visibility toggle
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()

    // Password should be visible (no way to directly test VisualTransformation,
    // but we can verify the toggle exists and is clickable)
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).assertExists()

    // Toggle back
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
  }

  @Test
  fun passwordField_clearsErrorOnInput() {
    setContent()

    // Trigger validation error
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    // Verify error appears
    composeTestRule.onNodeWithText("Password cannot be empty").assertExists()

    // Type in password field
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("pass")

    // Error should disappear
    composeTestRule.onNodeWithText("Password cannot be empty").assertDoesNotExist()
  }

  // ==================== Confirm Password Field Tests ====================

  @Test
  fun confirmPasswordField_acceptsInput() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .assertTextContains("password123")
  }

  @Test
  fun confirmPasswordField_toggleVisibility() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("secret")

    // Click visibility toggle
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE)
        .performClick()

    // Verify toggle exists and is clickable
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE)
        .assertExists()

    // Toggle back
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE)
        .performClick()
  }

  @Test
  fun confirmPasswordField_clearsErrorOnInput() {
    setContent()

    // Trigger validation error
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    // Verify error appears
    composeTestRule.onNodeWithText("Please confirm your password").assertExists()

    // Type in confirm password field
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("pass")

    // Error should disappear
    composeTestRule.onNodeWithText("Please confirm your password").assertDoesNotExist()
  }

  // ==================== Validation Tests ====================

  @Test
  fun validation_emptyEmail_showsError() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    composeTestRule.onNodeWithText("Email cannot be empty").assertExists()
    verify(exactly = 0) { mockViewModel.registerWithEmail(any(), any()) }
  }

  @Test
  fun validation_invalidEmailFormat_showsError() {
    setContent()

    composeTestRule.onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    composeTestRule.onNodeWithText("Invalid email format").assertExists()
    verify(exactly = 0) { mockViewModel.registerWithEmail(any(), any()) }
  }

  @Test
  fun validation_emptyPassword_showsError() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    composeTestRule.onNodeWithText("Password cannot be empty").assertExists()
    verify(exactly = 0) { mockViewModel.registerWithEmail(any(), any()) }
  }

  @Test
  fun validation_weakPassword_showsError() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("123")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("123")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    composeTestRule.onNodeWithText("Password is too weak").assertExists()
    verify(exactly = 0) { mockViewModel.registerWithEmail(any(), any()) }
  }

  @Test
  fun validation_emptyConfirmPassword_showsError() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    composeTestRule.onNodeWithText("Please confirm your password").assertExists()
    verify(exactly = 0) { mockViewModel.registerWithEmail(any(), any()) }
  }

  @Test
  fun validation_passwordMismatch_showsError() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("different")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    composeTestRule.onNodeWithText("Passwords do not match").assertExists()
    verify(exactly = 0) { mockViewModel.registerWithEmail(any(), any()) }
  }

  @Test
  fun validation_allFieldsValid_callsViewModel() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    verify(exactly = 1) { mockViewModel.registerWithEmail("test@example.com", "password123") }
  }

  // ==================== Loading State Tests ====================

  @Test
  fun loadingState_disablesButtons() {
    uiStateFlow.value = AuthUIState(isLoading = true)
    setContent()

    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun loadingState_showsLoadingIndicator() {
    uiStateFlow.value = AuthUIState(isLoading = true)
    setContent()

    composeTestRule.onNodeWithTag(SignUpScreenTestTags.LOADING_INDICATOR).assertExists()
  }

  @Test
  fun loadingState_notLoading_noLoadingIndicator() {
    uiStateFlow.value = AuthUIState(isLoading = false)
    setContent()

    composeTestRule.onNodeWithTag(SignUpScreenTestTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  // ==================== Error Message Tests ====================

  @Test
  fun errorMessage_emailAlreadyInUse_showsFriendlyMessage() {
    uiStateFlow.value = AuthUIState(errorMsg = "email-already-in-use")
    setContent()

    composeTestRule.onNodeWithText("Email already in use").assertExists()
  }

  @Test
  fun errorMessage_weakPassword_showsFriendlyMessage() {
    uiStateFlow.value = AuthUIState(errorMsg = "weak-password")
    setContent()

    composeTestRule.onNodeWithText("Password is too weak").assertExists()
  }

  @Test
  fun errorMessage_invalidEmail_showsFriendlyMessage() {
    uiStateFlow.value = AuthUIState(errorMsg = "invalid-email")
    setContent()

    composeTestRule.onNodeWithText("Invalid email format").assertExists()
  }

  @Test
  fun errorMessage_unknownError_showsOriginalMessage() {
    uiStateFlow.value = AuthUIState(errorMsg = "Some unknown error")
    setContent()

    composeTestRule.onNodeWithText("Some unknown error").assertExists()
  }

  @Test
  fun errorMessage_null_noErrorDisplayed() {
    uiStateFlow.value = AuthUIState(errorMsg = null)
    setContent()

    // No error message should be displayed
    composeTestRule.onAllNodesWithText("Email already in use").assertCountEquals(0)
  }

  // ==================== Google Sign Up Tests ====================

  @Test
  fun googleSignUpButton_clickCallsViewModel() {
    setContent()

    composeTestRule.onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON).performClick()

    verify(exactly = 1) { mockViewModel.googleSignIn(any(), any()) }
  }

  @Test
  fun googleSignUpButton_disabledDuringLoading() {
    uiStateFlow.value = AuthUIState(isLoading = true)
    setContent()

    composeTestRule.onNodeWithTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON).assertIsNotEnabled()
  }

  // ==================== Navigation Tests ====================

  @Test
  fun logInLink_navigatesToSignInScreen() {
    setContent()

    composeTestRule.onNodeWithText("Log in.").performClick()

    verify(exactly = 1) { mockNavController.navigate("SignInScreen") }
  }

  // ==================== Multiple Validation Errors Tests ====================

  @Test
  fun validation_multipleErrors_showsAllErrors() {
    setContent()

    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    // All three errors should be displayed
    composeTestRule.onNodeWithText("Email cannot be empty").assertExists()
    composeTestRule.onNodeWithText("Password cannot be empty").assertExists()
    composeTestRule.onNodeWithText("Please confirm your password").assertExists()
  }

  // ==================== Edge Case Tests ====================

  @Test
  fun validation_exactlySixCharacterPassword_passes() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("123456")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("123456")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    // Should not show "Password is too weak"
    composeTestRule.onNodeWithText("Password is too weak").assertDoesNotExist()
    verify(exactly = 1) { mockViewModel.registerWithEmail("test@example.com", "123456") }
  }

  @Test
  fun validation_fiveCharacterPassword_fails() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD).performTextInput("12345")
    composeTestRule
        .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("12345")
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON).performClick()

    composeTestRule.onNodeWithText("Password is too weak").assertExists()
    verify(exactly = 0) { mockViewModel.registerWithEmail(any(), any()) }
  }
}
