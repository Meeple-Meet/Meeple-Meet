package com.github.meeplemeet.ui.auth
// Github copilot was used for this file
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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.style.TextAlign
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.R
import com.github.meeplemeet.model.auth.SignInViewModel
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions

object SignInScreenTestTags {
  const val EMAIL_FIELD = "email_field"
  const val PASSWORD_VISIBILITY_TOGGLE = "password_visibility_toggle"
  const val PASSWORD_FIELD = "password_field"
  const val SIGN_IN_BUTTON = "sign_in_button"
  const val GOOGLE_SIGN_IN_BUTTON = "google_sign_in_button"
  const val LOADING_INDICATOR = "loading_indicator"
  const val SIGN_UP_BUTTON = "sign_up_button"
}

object SignInScreenUi {
  val mediumSpacing = Dimensions.Spacing.medium
  val extraLargePadding = Dimensions.Padding.extraLarge
  val smallPadding = Dimensions.Padding.small
  val extraLargeSpacing = Dimensions.Spacing.extraLarge
  val mediumPadding = Dimensions.Padding.medium
}

/**
 * SignInScreen - User authentication interface for existing users
 *
 * This composable provides a complete sign-in experience with:
 * - Email/password authentication with validation
 * - Google sign-in integration via Credential Manager
 * - Real-time error handling and user feedback
 * - Loading states during authentication operations
 * - Navigation to sign-up screen for new users
 *
 * The screen integrates with AuthViewModel to handle authentication logic and displays appropriate
 * UI states based on the authentication status.
 *
 * @param viewModel Authentication view model that manages auth state and operations
 * @param context Android context, used for Credential Manager and other platform services
 * @param credentialManager Credential manager instance for Google sign-in
 */
@Composable
fun SignInScreen(
    viewModel: SignInViewModel = viewModel(),
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
    onSignUpClick: () -> Unit = {},
    onSignIn: () -> Unit = {},
) {
  // Local state management for form inputs and validation
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) } // Controls password visibility toggle
  var emailError by remember {
    mutableStateOf<String?>(null)
  } // Client-side email validation errors
  var passwordError by remember {
    mutableStateOf<String?>(null)
  } // Client-side password validation errors

  val focusManager = LocalFocusManager.current

  LaunchedEffect(Unit) { focusManager.clearFocus(true) }

  // Observe authentication state from the ViewModel
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Check if credentials are valid in real-time
  val isFormValid =
      remember(email, password) {
        email.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
            password.isNotBlank()
      }

  /**
   * Validates email format and emptiness
   *
   * @param email The email string to validate
   * @return Error message if invalid, null if valid
   */
  fun validateEmail(email: String): String? {
    return when {
      email.isBlank() -> "Email cannot be empty"
      !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Invalid email format"
      else -> null
    }
  }

  /**
   * Validates password emptiness (additional rules can be added here)
   *
   * @param password The password string to validate
   * @return Error message if invalid, null if valid
   */
  fun validatePassword(password: String): String? {
    return when {
      password.isBlank() -> "Password cannot be empty"
      else -> null
    }
  }

  // Main UI layout using Column for vertical arrangement
  Column(
      modifier =
          Modifier.fillMaxSize()
              .imePadding()
              .background(MaterialTheme.colorScheme.background)
              .verticalScroll(rememberScrollState())
              .padding(Dimensions.Padding.large)
              .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween) {
        // Top spacing
        Spacer(modifier = Modifier.height(SignInScreenUi.mediumSpacing))

        // App logo - changes based on theme
        val isDarkTheme = isSystemInDarkTheme()

        Image(
            painter =
                painterResource(
                    id = if (isDarkTheme) R.drawable.logo_dark else R.drawable.logo_clear),
            contentDescription = "Meeple Meet Logo",
            modifier = Modifier.size(Dimensions.IconSize.massive.times(3)).fillMaxSize())

        // Welcome message
        Text(
            "Welcome!",
            color = AppColors.neutral,
            style = TextStyle(fontSize = Dimensions.TextSize.displayMedium),
            modifier =
                Modifier.padding(bottom = SignInScreenUi.extraLargePadding)
                    .testTag(NavigationTestTags.SCREEN_TITLE))

        // Email input field with validation
        FocusableInputField(
            leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = null) },
            value = email,
            onValueChange = {
              email = it
              // Validate email in real-time as user types
              emailError = if (email.isNotEmpty()) validateEmail(it) else null
            },
            label = { Text("Email") },
            singleLine = true,
            isError = emailError != null, // Show error state visually
            modifier =
                Modifier.fillMaxWidth().testTag(SignInScreenTestTags.EMAIL_FIELD) // For UI testing
            )

        // Display email validation error if present
        if (emailError != null) {
          Text(
              text = emailError!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          start = SignInScreenUi.extraLargePadding,
                          top = SignInScreenUi.smallPadding))
        }

        Spacer(modifier = Modifier.height(SignInScreenUi.mediumSpacing))

        // Password input field with visibility toggle and validation
        FocusableInputField(
            leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null) },
            value = password,
            onValueChange = {
              password = it
              // Validate password in real-time as user types
              passwordError = if (password.isNotEmpty()) validatePassword(it) else null
            },
            label = { Text("Password") },
            singleLine = true,
            // Toggle between showing/hiding password based on passwordVisible state
            visualTransformation =
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = passwordError != null, // Show error state visually
            trailingIcon = {
              // Password visibility toggle button
              IconButton(
                  onClick = { passwordVisible = !passwordVisible },
                  modifier = Modifier.testTag(SignInScreenTestTags.PASSWORD_VISIBILITY_TOGGLE)) {
                    Icon(
                        imageVector =
                            if (passwordVisible) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                        contentDescription =
                            if (passwordVisible) "Hide password" else "Show password")
                  }
            },
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(SignInScreenTestTags.PASSWORD_FIELD) // For UI testing
            )

        // Display password validation error if present
        if (passwordError != null) {
          Text(
              text = passwordError!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          start = SignInScreenUi.extraLargePadding,
                          top = SignInScreenUi.smallPadding))
        }

        Spacer(modifier = Modifier.height(SignInScreenUi.extraLargeSpacing))

        // Display authentication errors from ViewModel (server-side errors)
        if (uiState.errorMsg != null) {
          val errorMessage = uiState.errorMsg ?: "An unknown error occurred"
          Card(
              modifier = Modifier.fillMaxWidth().padding(vertical = SignInScreenUi.mediumPadding),
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(SignInScreenUi.extraLargePadding))
              }
          Spacer(modifier = Modifier.height(SignInScreenUi.mediumSpacing))
        }

        // Email/Password Sign In Button
        Button(
            onClick = {
              // Clear any previous authentication errors before new attempt
              viewModel.clearErrorMsg()

              // Perform client-side validation before submitting
              val emailValidation = validateEmail(email)
              val passwordValidation = validatePassword(password)

              emailError = emailValidation
              passwordError = passwordValidation

              // Only proceed with authentication if validation passes
              if (emailValidation == null && passwordValidation == null) {
                viewModel.loginWithEmail(email, password, onSignIn)
              }
            },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColors.affirmative, contentColor = AppColors.textIcons),
            modifier =
                Modifier.fillMaxWidth(0.6f)
                    .testTag(SignInScreenTestTags.SIGN_IN_BUTTON), // For UI testing
            enabled =
                isFormValid && !uiState.isLoading // Enable only when form is valid and not loading
            ) {
              Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterStart)) {
                      Icon(imageVector = Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    }
                Spacer(modifier = Modifier.width(SignInScreenUi.extraLargeSpacing))

                // Show loading indicator during authentication
                if (uiState.isLoading) {
                  CircularProgressIndicator(
                      modifier =
                          Modifier.size(Dimensions.IconSize.small)
                              .testTag(SignInScreenTestTags.LOADING_INDICATOR), // For UI testing
                      color = MaterialTheme.colorScheme.onPrimary)
                }
                Text("Sign In")
              }
            }

        // Divider between authentication methods
        Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))
        Text(
            "OR",
            style =
                MaterialTheme.typography.bodyMedium.copy(fontSize = Dimensions.TextSize.subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = SignInScreenUi.smallPadding))

        // Google Sign In Button
        OutlinedButton(
            onClick = {
              // Initiate Google sign-in flow through ViewModel
              viewModel.googleSignIn(context, credentialManager, onSignIn)
            },
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = AppColors.primary, contentColor = AppColors.textIcons),
            border = BorderStroke(Dimensions.DividerThickness.standard, AppColors.divider),
            modifier =
                Modifier.fillMaxWidth(0.6f)
                    .testTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON), // For UI testing
            enabled = !uiState.isLoading // Disable during any authentication process
            ) {
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Start,
                  modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        painter = painterResource(id = R.drawable.google_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier =
                            Modifier.size(
                                Dimensions.IconSize.standard.plus(Dimensions.Spacing.extraSmall)))
                    Text(
                        "Connect with Google",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center)
                  }
            }

        // Push navigation link to bottom
        Spacer(modifier = Modifier.weight(1f))

        // Navigation to Sign Up screen for new users
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
          Text("I'm a new user. ")
          Text(
              text = "Sign up.",
              color = MaterialTheme.colorScheme.primary,
              modifier =
                  Modifier.testTag(SignInScreenTestTags.SIGN_UP_BUTTON).clickable {
                    onSignUpClick()
                  } // Navigate to sign-up screen
              )
        }

        // Bottom spacing
        Spacer(modifier = Modifier.height(SignInScreenUi.mediumSpacing))
      }
}
