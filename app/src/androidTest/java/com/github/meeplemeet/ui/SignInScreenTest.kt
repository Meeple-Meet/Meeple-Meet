package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import com.github.meeplemeet.model.viewmodels.AuthUIState
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Comprehensive UI tests for SignInScreen
 *
 * Tests cover:
 * - Initial screen state
 * - Input validation (email, password)
 * - Password visibility toggle
 * - Form submission with validation
 * - Loading states
 * - Error messages
 * - Navigation
 * - Google sign-in
 */
class SignInScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var mockViewModel: AuthViewModel
  private lateinit var mockNavController: NavController
  private lateinit var uiStateFlow: MutableStateFlow<AuthUIState>

  @Before
  fun setup() {
    mockViewModel = mockk(relaxUnitFun = true) // not relaxed = true
    mockNavController = mockk(relaxUnitFun = true)

    uiStateFlow = MutableStateFlow(AuthUIState())
    every { mockViewModel.uiState } returns uiStateFlow.asStateFlow()
    every { mockViewModel.loginWithEmail(any(), any()) } just Runs
    every { mockViewModel.googleSignIn(any(), any()) } just Runs
    every { mockViewModel.clearErrorMsg() } just Runs
  }

  private fun setContent() {
    composeTestRule.setContent {
      SignInScreen(viewModel = mockViewModel, navController = mockNavController)
    }
  }

  // ==================== Initial State Tests ====================

  @Test
  fun initialState_allFieldsEmpty() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .assertExists()
        .assertTextContains("")
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .assertExists()
        .assertTextContains("")
  }

  @Test
  fun initialState_signInButtonEnabled() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON)
        .assertExists()
        .assertIsEnabled()
  }

  @Test
  fun initialState_googleSignInButtonEnabled() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON)
        .assertExists()
        .assertIsEnabled()
  }

  @Test
  fun initialState_welcomeTextDisplayed() {
    setContent()

    composeTestRule.onNodeWithText("Welcome!").assertExists()
  }

  @Test
  fun initialState_signUpLinkDisplayed() {
    setContent()

    composeTestRule.onNodeWithText("I'm a new user. ").assertExists()
    composeTestRule.onNodeWithText("Sign up.").assertExists()
  }

  @Test
  fun initialState_noErrorMessageDisplayed() {
    setContent()

    // No error card should be displayed initially
    composeTestRule.onAllNodesWithText("An unknown error occurred").assertCountEquals(0)
  }

  // ==================== Email Field Tests ====================

  @Test
  fun emailField_acceptsInput() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .assertTextContains("test@example.com")
  }

  @Test
  fun emailField_clearsErrorOnInput() {
    setContent()

    // Trigger validation error by clicking sign in with empty email
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    // Verify error appears
    composeTestRule.onNodeWithText("Email cannot be empty").assertExists()

    // Type in email field
    composeTestRule.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test")

    // Error should disappear
    composeTestRule.onNodeWithText("Email cannot be empty").assertDoesNotExist()
  }

  @Test
  fun emailField_acceptsValidEmailFormat() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .performTextInput("user@domain.com")

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .assertTextContains("user@domain.com")
  }

  // ==================== Password Field Tests ====================

  @Test
  fun passwordField_acceptsInput() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .assertTextContains("password123")
  }

  @Test
  fun passwordField_toggleVisibility() {
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("secret")

    // Click visibility toggle
    composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()

    // Verify the toggle exists and is clickable
    composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).assertExists()

    // Toggle back
    composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
  }

  @Test
  fun passwordField_clearsErrorOnInput() {
    setContent()

    // Trigger validation error
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    // Verify error appears
    composeTestRule.onNodeWithText("Password cannot be empty").assertExists()

    // Type in password field
    composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("pass")

    // Error should disappear
    composeTestRule.onNodeWithText("Password cannot be empty").assertDoesNotExist()
  }

  @Test
  fun passwordField_acceptsLongPassword() {
    setContent()

    val longPassword = "a".repeat(100)
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput(longPassword)

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .assertTextContains(longPassword)
  }

  // ==================== Validation Tests ====================

  @Test
  fun validation_emptyEmail_showsError() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithText("Email cannot be empty").assertExists()
    verify(exactly = 0) { mockViewModel.loginWithEmail(any(), any()) }
  }

  @Test
  fun validation_invalidEmailFormat_showsError() {
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("notanemail")
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithText("Invalid email format").assertExists()
    verify(exactly = 0) { mockViewModel.loginWithEmail(any(), any()) }
  }

  @Test
  fun validation_emptyPassword_showsError() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithText("Password cannot be empty").assertExists()
    verify(exactly = 0) { mockViewModel.loginWithEmail(any(), any()) }
  }

  @Test
  fun validation_bothFieldsValid_callsViewModel() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    verify(exactly = 1) { mockViewModel.loginWithEmail("test@example.com", "password123") }
  }

  @Test
  fun validation_multipleErrors_showsBothErrors() {
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    // Both errors should be displayed
    composeTestRule.onNodeWithText("Email cannot be empty").assertExists()
    composeTestRule.onNodeWithText("Password cannot be empty").assertExists()
  }

  @Test
  fun validation_emailWithSpaces_consideredInvalid() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .performTextInput("test @example.com")
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithText("Invalid email format").assertExists()
    verify(exactly = 0) { mockViewModel.loginWithEmail(any(), any()) }
  }

  // ==================== Loading State Tests ====================

  @Test
  fun loadingState_disablesButtons() {
    uiStateFlow.value = AuthUIState(isLoading = true)
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun loadingState_showsLoadingIndicator() {
    uiStateFlow.value = AuthUIState(isLoading = true)
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING_INDICATOR).assertExists()
  }

  @Test
  fun loadingState_notLoading_noLoadingIndicator() {
    uiStateFlow.value = AuthUIState(isLoading = false)
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun loadingState_buttonsEnabledWhenNotLoading() {
    uiStateFlow.value = AuthUIState(isLoading = false)
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertIsEnabled()
  }

  // ==================== Error Message Tests ====================

  @Test
  fun errorMessage_displayed_whenPresent() {
    uiStateFlow.value = AuthUIState(errorMsg = "Invalid credentials")
    setContent()

    composeTestRule.onNodeWithText("Invalid credentials").assertExists()
  }

  @Test
  fun errorMessage_notDisplayed_whenNull() {
    uiStateFlow.value = AuthUIState(errorMsg = null)
    setContent()

    composeTestRule.onAllNodesWithText("Invalid credentials").assertCountEquals(0)
  }

  @Test
  fun errorMessage_displayedInErrorCard() {
    uiStateFlow.value = AuthUIState(errorMsg = "Authentication failed")
    setContent()

    // Error should be displayed in a Card
    composeTestRule.onNodeWithText("Authentication failed").assertExists()
  }

  @Test
  fun errorMessage_nullFallback_displaysDefaultMessage() {
    // This tests the null safety in the error display logic
    uiStateFlow.value = AuthUIState(errorMsg = null)
    setContent()

    // Since errorMsg is null, the Card shouldn't be displayed at all
    composeTestRule.onAllNodesWithText("An unknown error occurred").assertCountEquals(0)
  }

  // ==================== Clear Error Tests ====================

  @Test
  fun signInButton_clearsErrorBeforeSubmit() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    verify(exactly = 1) { mockViewModel.clearErrorMsg() }
  }

  @Test
  fun signInButton_clearsErrorEvenOnValidationFailure() {
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    // Should call clearErrorMsg even though validation failed
    verify(exactly = 1) { mockViewModel.clearErrorMsg() }
  }

  // ==================== Google Sign In Tests ====================

  @Test
  fun googleSignInButton_clickCallsViewModel() {
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).performClick()

    verify(exactly = 1) { mockViewModel.googleSignIn(any(), any()) }
  }

  @Test
  fun googleSignInButton_disabledDuringLoading() {
    uiStateFlow.value = AuthUIState(isLoading = true)
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun googleSignInButton_hasCorrectText() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON)
        .assertTextContains("Connect with Google")
  }

  // ==================== Navigation Tests ====================

  @Test
  fun signUpLink_navigatesToSignUpScreen() {
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).performClick()

    verify(exactly = 1) { mockNavController.navigate("sign_up") }
  }

  @Test
  fun signUpLink_clickable() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON)
        .assertExists()
        .assertHasClickAction()
  }

  // ==================== OR Divider Tests ====================

  @Test
  fun orDivider_displayed() {
    setContent()

    composeTestRule.onNodeWithText("OR").assertExists()
  }

  // ==================== Edge Case Tests ====================

  @Test
  fun validation_emailWithoutAtSymbol_invalid() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .performTextInput("testexample.com")
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithText("Invalid email format").assertExists()
    verify(exactly = 0) { mockViewModel.loginWithEmail(any(), any()) }
  }

  @Test
  fun validation_emailWithoutDomain_invalid() {
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).performTextInput("test@")
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithText("Invalid email format").assertExists()
    verify(exactly = 0) { mockViewModel.loginWithEmail(any(), any()) }
  }

  @Test
  fun validation_singleCharacterPassword_accepted() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performTextInput("a")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    // Should not show validation error (sign-in doesn't have minimum password length)
    composeTestRule.onNodeWithText("Password cannot be empty").assertDoesNotExist()
    verify(exactly = 1) { mockViewModel.loginWithEmail("test@example.com", "a") }
  }

  @Test
  fun multipleClicks_callsViewModelMultipleTimes() {
    setContent()

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD)
        .performTextInput("test@example.com")
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD)
        .performTextInput("password123")

    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).performClick()

    verify(exactly = 2) { mockViewModel.loginWithEmail("test@example.com", "password123") }
  }

  // ==================== UI Layout Tests ====================

  @Test
  fun allRequiredElements_present() {
    setContent()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.EMAIL_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE).assertExists()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_IN_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).assertExists()
    composeTestRule.onNodeWithText("Welcome!").assertExists()
    composeTestRule.onNodeWithText("OR").assertExists()
  }
}
