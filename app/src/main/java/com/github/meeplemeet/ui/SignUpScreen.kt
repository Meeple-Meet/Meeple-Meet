package com.github.meeplemeet.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.github.meeplemeet.model.viewmodels.AuthViewModel

object SignUpScreenTestTags {
  const val EMAIL_FIELD = "email_field"
  const val PASSWORD_VISIBILITY_TOGGLE = "password_visibility_toggle"
  const val PASSWORD_FIELD = "password_field"
  const val SIGN_UP_BUTTON = "sign_up_button"
  const val GOOGLE_SIGN_UP_BUTTON = "google_sign_up_button"
  const val LOADING_INDICATOR = "loading_indicator"
  const val CONFIRM_PASSWORD_FIELD = "confirm_password_field"
  const val CONFIRM_PASSWORD_VISIBILITY_TOGGLE = "confirm_password_visibility_toggle"
}

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
 * @param modifier Modifier for customizing the composable's appearance and behavior
 */
@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
    onLogInClick: () -> Unit = {},
    modifier: Modifier = Modifier
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

  // Observe authentication state from the ViewModel
  val uiState by viewModel.uiState.collectAsState()

  /**
   * Validates email format and emptiness
   *
   * @param email The email string to validate
   * @return Error message if invalid, null if valid
   */
  fun validateEmail(email: String): String? {
    return when {
      email.isBlank() -> "Email cannot be empty"
      !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Invalid email format"
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
      password.isBlank() -> "Password cannot be empty"
      password.length < 6 -> "Password is too weak"
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
      confirmPassword.isBlank() -> "Please confirm your password"
      password != confirmPassword -> "Passwords do not match"
      else -> null
    }
  }

  // Main UI layout using Column for vertical arrangement
  Column(
      modifier =
          modifier.fillMaxSize().padding(24.dp).background(MaterialTheme.colorScheme.background),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween) {
        // Top spacing
        Spacer(modifier = Modifier.height(16.dp))

        // Placeholder logo/branding area
        Box(modifier = Modifier.size(100.dp)) {
          Surface(color = Color.LightGray, modifier = Modifier.fillMaxSize()) {}
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Welcome message
        Text(
            "Welcome!",
            style = TextStyle(fontSize = 28.sp),
            modifier = Modifier.padding(bottom = 16.dp))

        // Email input field with validation
        OutlinedTextField(
            value = email,
            onValueChange = {
              email = it
              emailError = null // Clear validation error when user starts typing
            },
            label = { Text("Email") },
            singleLine = true,
            isError = emailError != null, // Show error state visually
            modifier =
                Modifier.fillMaxWidth().testTag(SignUpScreenTestTags.EMAIL_FIELD) // For UI testing
            )

        // Display email validation error if present
        if (emailError != null) {
          Text(
              text = emailError!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Password input field with visibility toggle and validation
        OutlinedTextField(
            value = password,
            onValueChange = {
              password = it
              passwordError = null // Clear validation error when user starts typing
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
                  modifier = Modifier.testTag(SignUpScreenTestTags.PASSWORD_VISIBILITY_TOGGLE)) {
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
                    .testTag(SignUpScreenTestTags.PASSWORD_FIELD) // For UI testing
            )

        // Display password validation error if present
        if (passwordError != null) {
          Text(
              text = passwordError!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Password confirmation field with visibility toggle and validation
        // This ensures users enter their password correctly by requiring them to type it twice
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
              confirmPassword = it
              confirmPasswordError = null // Clear validation error when user starts typing
            },
            label = { Text("Confirm Password") },
            singleLine = true,
            // Toggle between showing/hiding confirm password based on confirmPasswordVisible state
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
                      Modifier.testTag(SignUpScreenTestTags.CONFIRM_PASSWORD_VISIBILITY_TOGGLE)) {
                    Icon(
                        imageVector =
                            if (confirmPasswordVisible) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                        contentDescription =
                            if (confirmPasswordVisible) "Hide password" else "Show password")
                  }
            },
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD) // For UI testing
            )

        // Display password confirmation validation error if present
        if (confirmPasswordError != null) {
          Text(
              text = confirmPasswordError!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Email/Password Registration Button
        Button(
            onClick = {
              // Perform comprehensive client-side validation before submitting
              val emailValidation = validateEmail(email)
              val passwordValidation = validatePassword(password)
              val confirmPasswordValidation = validateConfirmPassword(password, confirmPassword)

              emailError = emailValidation
              passwordError = passwordValidation
              confirmPasswordError = confirmPasswordValidation

              // Only proceed with registration if all validation passes
              if (emailValidation == null &&
                  passwordValidation == null &&
                  confirmPasswordValidation == null) {
                viewModel.registerWithEmail(email, password)
              }
            },
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(SignUpScreenTestTags.SIGN_UP_BUTTON), // For UI testing
            enabled = !uiState.isLoading // Disable button during registration process
            ) {
              // Show loading indicator during registration
              if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.size(16.dp)
                            .testTag(SignUpScreenTestTags.LOADING_INDICATOR), // For UI testing
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
              }
              Text("Sign Up")
            }

        // Divider between authentication methods
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "OR",
            style = TextStyle(fontSize = 16.sp, color = Color.Gray),
            modifier = Modifier.padding(vertical = 4.dp))

        // Google Sign Up Button
        Button(
            onClick = {
              // Initiate Google sign-up flow through ViewModel
              // Note: Google sign-up and sign-in use the same flow - if the account exists, it
              // signs in,
              // if it doesn't exist, it creates a new account
              viewModel.googleSignIn(context, credentialManager)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON), // For UI testing
            enabled = !uiState.isLoading // Disable during any authentication process
            ) {
              Text("Connect with Google", color = Color.Black)
            }

        // Push navigation link to bottom
        Spacer(modifier = Modifier.weight(1f))

        // Navigation to Sign In screen for existing users
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
          Text("Already have an account? ")
          Text(
              text = "Log in.",
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.clickable { onLogInClick() })
        }

        // Bottom spacing
        Spacer(modifier = Modifier.height(8.dp))
      }

  // Server-side error message display for registration errors
  // This shows errors that come from Firebase Auth during the registration process
  if (uiState.errorMsg != null) {
    // Map common Firebase error codes to user-friendly messages
    val errorMessage =
        when {
          uiState.errorMsg!!.contains("email-already-in-use") -> "Email already in use"
          uiState.errorMsg!!.contains("weak-password") -> "Password is too weak"
          uiState.errorMsg!!.contains("invalid-email") -> "Invalid email format"
          else -> uiState.errorMsg!! // Show the original error message for other cases
        }

    // Display error in a Snackbar for non-intrusive feedback
    Snackbar(modifier = Modifier.padding(8.dp)) { Text(errorMessage) }
  }
}
