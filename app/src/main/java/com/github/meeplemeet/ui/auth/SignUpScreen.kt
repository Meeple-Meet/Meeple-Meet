package com.github.meeplemeet.ui.auth
// Github Copilot was used for this file
import android.content.Context
import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.R
import com.github.meeplemeet.model.auth.SignUpViewModel
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions

object SignUpScreenTestTags {
  const val EMAIL_FIELD = "email_field"
  const val PASSWORD_VISIBILITY_TOGGLE = "password_visibility_toggle"
  const val PASSWORD_FIELD = "password_field"
  const val SIGN_UP_BUTTON = "sign_up_button"
  const val GOOGLE_SIGN_UP_BUTTON = "google_sign_up_button"
  const val CONFIRM_PASSWORD_FIELD = "confirm_password_field"
  const val CONFIRM_PASSWORD_VISIBILITY_TOGGLE = "confirm_password_visibility_toggle"
  const val SIGN_IN_BUTTON = "sign_in_button"
}

const val SIGN_UP_TEXT = "Sign Up"
const val HIDE_PWD_TEXT = "Hide password"
const val SHOW_PWD_TEXT = "Show password"
const val INVALID_EMAIL_TEXT = "Invalid email format"
const val WEAK_PWD_TEXT = "Password is too weak"
const val MINIMAL_PWD_LENGTH = 6
const val PWD_MISSMATCH_TEXT = "Passwords do not match"
const val PWD_CONFIRMATION_TEXT = "Please confirm your password"
const val EMPTY_PWD_TEXT = "Password cannot be empty"
const val CANNOT_BE_EMPTY_EMAIL_TEXT = "Email cannot be empty"
const val ALREADY_HAVE_ACCOUNT_TEXT = "Already have an account? "
const val LOG_IN_TEXT = "Log in."
const val OPTION_TEXT = "OR"

/**
 * SignUpScreen - User registration interface for new users
 *
 * This composable provides a complete registration experience with:
 * - Email/password registration with comprehensive validation
 * - Password confirmation to ensure accuracy
 * - Google sign-up integration via Credential Manager
 * - Real-time client-side validation with error feedback
 * - Server-side error handling and display
 * - Loading states during registration operations
 * - Navigation to sign-in screen for existing users
 *
 * The screen features enhanced validation compared to sign-in, including:
 * - Password strength requirements (minimum 6 characters)
 * - Password confirmation matching
 * - Immediate feedback on validation errors
 *
 * @param viewModel Authentication view model that manages auth state and operations
 * @param context Android context, used for Credential Manager and other platform services
 * @param credentialManager Credential manager instance for Google sign-up
 */
@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel = viewModel(),
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
    onLogInClick: () -> Unit = {},
    onRegister: () -> Unit = {},
) {
  // Local state management for form inputs and validation
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) } // Controls password visibility toggle
  var confirmPasswordVisible by remember {
    mutableStateOf(false)
  } // Controls confirm password visibility toggle
  var emailError by remember {
    mutableStateOf<String?>(null)
  } // Client-side email validation errors
  var passwordError by remember {
    mutableStateOf<String?>(null)
  } // Client-side password validation errors
  var confirmPasswordError by remember {
    mutableStateOf<String?>(null)
  } // Password confirmation validation errors

  val focusManager = LocalFocusManager.current

  // Observe authentication state from the ViewModel
  val uiState by viewModel.uiState.collectAsState()

  // Check if all credentials are valid in real-time
  val isFormValid =
      remember(email, password, confirmPassword) {
        email.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
            password.isNotBlank() &&
            password.length >= MINIMAL_PWD_LENGTH &&
            confirmPassword.isNotBlank() &&
            password == confirmPassword
      }

  // Snackbar state
  val snackbarHostState = remember { SnackbarHostState() }

  // Show snackbar when there's an error
  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { error ->
      val errorMessage =
          when {
            error.contains("email-already-in-use") -> "Email already in use"
            error.contains("weak-password") -> WEAK_PWD_TEXT
            error.contains("invalid-email") -> INVALID_EMAIL_TEXT
            else -> error
          }
      snackbarHostState.showSnackbar(errorMessage)
    }
  }

  /**
   * Validates email format and emptiness
   *
   * @param email The email string to validate
   * @return Error message if invalid, null if valid
   */
  fun validateEmail(email: String): String? {
    return when {
      email.isBlank() -> CANNOT_BE_EMPTY_EMAIL_TEXT
      !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> INVALID_EMAIL_TEXT
      else -> null
    }
  }

  /**
   * Validates password strength and emptiness Enforces minimum length requirement for security
   *
   * @param password The password string to validate
   * @return Error message if invalid, null if valid
   */
  fun validatePassword(password: String): String? {
    return when {
      password.isBlank() -> EMPTY_PWD_TEXT
      password.length < MINIMAL_PWD_LENGTH -> WEAK_PWD_TEXT
      else -> null
    }
  }

  /**
   * Validates password confirmation matches the original password
   *
   * @param password The original password
   * @param confirmPassword The confirmation password to validate
   * @return Error message if invalid, null if valid
   */
  fun validateConfirmPassword(password: String, confirmPassword: String): String? {
    return when {
      confirmPassword.isBlank() -> PWD_CONFIRMATION_TEXT
      password != confirmPassword -> PWD_MISSMATCH_TEXT
      else -> null
    }
  }

  // Main UI layout using Scaffold for proper Snackbar positioning
  Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(Dimensions.Padding.xxLarge)
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) {
                      detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween) {
              // Top spacing
              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))

              // App logo - changes based on theme
              val isDarkTheme = isSystemInDarkTheme()
              Box(
                  modifier =
                      Modifier.size(
                          Dimensions.IconSize.massive
                              .times(3)
                              .plus(Dimensions.Padding.extraLarge))) {
                    Image(
                        painter =
                            painterResource(
                                id =
                                    if (isDarkTheme) R.drawable.logo_dark
                                    else R.drawable.logo_clear),
                        contentDescription = "Meeple Meet Logo",
                        modifier = Modifier.fillMaxSize())
                  }

              Spacer(modifier = Modifier.height(Dimensions.Spacing.xxLarge))

              // Welcome message
              Text(
                  SIGN_UP_TEXT,
                  style = TextStyle(fontSize = Dimensions.TextSize.displayMedium),
                  color = AppColors.neutral,
                  modifier =
                      Modifier.padding(bottom = Dimensions.Padding.extraLarge)
                          .testTag(NavigationTestTags.SCREEN_TITLE))

              // Email input field with validation
              FocusableInputField(
                  leadingIcon = {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null)
                  },
                  value = email,
                  onValueChange = {
                    email = it
                    // Validate email in real-time as user types
                    emailError = if (it.isNotEmpty()) validateEmail(it) else null
                  },
                  label = { Text("Email") },
                  singleLine = true,
                  isError = emailError != null, // Show error state visually
                  modifier = Modifier.fillMaxWidth().testTag(SignUpScreenTestTags.EMAIL_FIELD))

              // Display email validation error if present
              if (emailError != null) {
                Text(
                    text = emailError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                start = Dimensions.Padding.extraLarge,
                                top = Dimensions.Padding.small))
              }

              Spacer(modifier = Modifier.height(Dimensions.Spacing.large))

              // Password input field with visibility toggle and validation
              FocusableInputField(
                  leadingIcon = {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                  },
                  value = password,
                  onValueChange = {
                    password = it
                    // Validate password in real-time as user types
                    passwordError = if (it.isNotEmpty()) validatePassword(it) else null
                    // Also re-validate confirm password if it's not empty
                    if (confirmPassword.isNotEmpty()) {
                      confirmPasswordError = validateConfirmPassword(it, confirmPassword)
                    }
                  },
                  label = { Text("Password") },
                  singleLine = true,
                  // Toggle between showing/hiding password based on passwordVisible state
                  visualTransformation =
                      if (passwordVisible) VisualTransformation.None
                      else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  isError = passwordError != null, // Show error state visually
                  trailingIcon = {
                    // Password visibility toggle button
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier =
                            Modifier.testTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE)) {
                          Icon(
                              imageVector =
                                  if (passwordVisible) Icons.Filled.Visibility
                                  else Icons.Filled.VisibilityOff,
                              contentDescription =
                                  if (passwordVisible) HIDE_PWD_TEXT else SHOW_PWD_TEXT)
                        }
                  },
                  modifier = Modifier.fillMaxWidth().testTag(SignUpScreenTestTags.PASSWORD_FIELD))

              // Display password validation error if present
              if (passwordError != null) {
                Text(
                    text = passwordError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                start = Dimensions.Padding.extraLarge,
                                top = Dimensions.Padding.small))
              }

              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))

              // Password confirmation field with visibility toggle and validation
              // This ensures users enter their password correctly by requiring them to type it
              // twice
              FocusableInputField(
                  leadingIcon = {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                  },
                  value = confirmPassword,
                  onValueChange = {
                    confirmPassword = it
                    // Validate confirm password in real-time as user types
                    confirmPasswordError =
                        if (it.isNotEmpty()) validateConfirmPassword(password, it) else null
                  },
                  label = { Text("Confirm Password") },
                  singleLine = true,
                  // Toggle between showing/hiding confirm password based on confirmPasswordVisible
                  // state
                  visualTransformation =
                      if (confirmPasswordVisible) VisualTransformation.None
                      else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  isError = confirmPasswordError != null, // Show error state visually
                  trailingIcon = {
                    // Confirm password visibility toggle button
                    IconButton(
                        onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                        modifier =
                            Modifier.testTag(
                                SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE)) {
                          Icon(
                              imageVector =
                                  if (confirmPasswordVisible) Icons.Filled.Visibility
                                  else Icons.Filled.VisibilityOff,
                              contentDescription =
                                  if (confirmPasswordVisible) HIDE_PWD_TEXT else SHOW_PWD_TEXT)
                        }
                  },
                  modifier =
                      Modifier.fillMaxWidth().testTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD))

              // Display password confirmation validation error if present
              if (confirmPasswordError != null) {
                Text(
                    text = confirmPasswordError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                start = Dimensions.Padding.extraLarge,
                                top = Dimensions.Padding.small))
              }

              Spacer(modifier = Modifier.height(Dimensions.Spacing.extraLarge))

              // Email/Password Registration Button
              Button(
                  onClick = {
                    // Perform comprehensive client-side validation before submitting
                    val emailValidation = validateEmail(email)
                    val passwordValidation = validatePassword(password)
                    val confirmPasswordValidation =
                        validateConfirmPassword(password, confirmPassword)

                    emailError = emailValidation
                    passwordError = passwordValidation
                    confirmPasswordError = confirmPasswordValidation

                    // Only proceed with registration if all validation passes
                    if (emailValidation == null &&
                        passwordValidation == null &&
                        confirmPasswordValidation == null) {
                      viewModel.registerWithEmail(email, password, onRegister)
                    }
                  },
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = AppColors.affirmative,
                          contentColor = AppColors.textIcons),
                  modifier =
                      Modifier.fillMaxWidth(0.6f).testTag(SignUpScreenTestTags.SIGN_UP_BUTTON),
                  enabled =
                      isFormValid &&
                          !uiState.isLoading // Enable only when form is valid and not loading
                  ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                          }
                      Spacer(modifier = Modifier.width(Dimensions.Spacing.extraLarge))

                      // Show loading indicator during authentication
                      if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(Dimensions.IconSize.small)
                                    .testTag(SignInScreenTestTags.LOADING_INDICATOR),
                            color = MaterialTheme.colorScheme.onPrimary)
                      }
                      Text(SIGN_UP_TEXT)
                    }
                  }

              // Divider between authentication methods
              Spacer(modifier = Modifier.height(Dimensions.Spacing.large))
              Text(
                  OPTION_TEXT,
                  style =
                      MaterialTheme.typography.bodyMedium.copy(
                          fontSize = Dimensions.TextSize.subtitle),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(vertical = Dimensions.Padding.small))

              // Google Sign Up Button
              OutlinedButton(
                  onClick = {
                    // Initiate Google sign-up flow through ViewModel
                    // Note: Google sign-up and sign-in use the same flow - if the account exists,
                    // it signs in, if it doesn't exist, it creates a new account
                    viewModel.googleSignIn(context, credentialManager, onRegister)
                  },
                  colors =
                      ButtonDefaults.outlinedButtonColors(
                          containerColor = AppColors.primary, contentColor = AppColors.textIcons),
                  border = BorderStroke(Dimensions.Elevation.low, AppColors.divider),
                  modifier =
                      Modifier.fillMaxWidth(0.6f)
                          .testTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON),
                  enabled = !uiState.isLoading // Disable during any authentication process
                  ) {
                    Icon(
                        painter = painterResource(id = R.drawable.google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.IconSize.standard),
                        tint = Color.Unspecified)
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.extraLarge))
                    Text("Connect with Google")
                  }

              // Push navigation link to bottom
              Spacer(modifier = Modifier.weight(1f))

              // Navigation to Sign In screen for existing users
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(ALREADY_HAVE_ACCOUNT_TEXT)
                Text(
                    text = LOG_IN_TEXT,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.testTag(SignUpScreenTestTags.SIGN_IN_BUTTON).clickable {
                          onLogInClick()
                        })
              }

              // Bottom spacing
              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))
            }
      }
}
