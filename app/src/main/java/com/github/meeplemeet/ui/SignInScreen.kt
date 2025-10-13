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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.github.meeplemeet.model.viewmodels.AuthViewModel

object SignInScreenTestTags {
  const val EMAIL_FIELD = "email_field"
  const val PASSWORD_VISIBILITY_TOGGLE = "password_visibility_toggle"
  const val PASSWORD_FIELD = "password_field"
  const val SIGN_IN_BUTTON = "sign_in_button"
  const val GOOGLE_SIGN_IN_BUTTON = "google_sign_in_button"
  const val LOADING_INDICATOR = "loading_indicator"
  const val SIGN_UP_BUTTON = "sign_up_button"
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
 * @param navController Navigation controller for screen transitions
 * @param viewModel Authentication view model that manages auth state and operations
 * @param context Android context, used for Credential Manager and other platform services
 * @param credentialManager Credential manager instance for Google sign-in
 * @param modifier Modifier for customizing the composable's appearance and behavior
 */
@Composable
fun SignInScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    navController: NavController = NavController(LocalContext.current),
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
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

  // Observe authentication state from the ViewModel
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                Modifier.fillMaxWidth().testTag(SignInScreenTestTags.EMAIL_FIELD) // For UI testing
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
              modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display authentication errors from ViewModel (server-side errors)
        if (uiState.errorMsg != null) {
          val errorMessage = uiState.errorMsg ?: "An unknown error occurred"
          Card(
              modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp))
              }
          Spacer(modifier = Modifier.height(8.dp))
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
                viewModel.loginWithEmail(email, password)
              }
            },
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(SignInScreenTestTags.SIGN_IN_BUTTON), // For UI testing
            enabled = !uiState.isLoading // Disable button during authentication process
            ) {
              // Show loading indicator during authentication
              if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.size(16.dp)
                            .testTag(SignInScreenTestTags.LOADING_INDICATOR), // For UI testing
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
              }
              Text("Sign In")
            }

        // Divider between authentication methods
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "OR",
            style = TextStyle(fontSize = 16.sp, color = Color.Gray),
            modifier = Modifier.padding(vertical = 4.dp))

        // Google Sign In Button
        Button(
            onClick = {
              // Initiate Google sign-in flow through ViewModel
              viewModel.googleSignIn(context, credentialManager)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(SignInScreenTestTags.GOOGLE_SIGN_IN_BUTTON), // For UI testing
            enabled = !uiState.isLoading // Disable during any authentication process
            ) {
              Text("Connect with Google", color = Color.Black)
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
                    navController.navigate("sign_up")
                  } // Navigate to sign-up screen
              )
        }

        // Bottom spacing
        Spacer(modifier = Modifier.height(8.dp))
      }
}
