@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
const val BASELINE_SCREEN_HEIGTH = 840
const val MIN_SCALE = 0.82f
const val IMAGE_SCALING_FACTOR = 0.3f

@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel = viewModel(),
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
    onLogInClick: () -> Unit = {},
    onRegister: () -> Unit = {},
) {
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var confirmPasswordVisible by remember { mutableStateOf(false) }

  var emailError by remember { mutableStateOf<String?>(null) }
  var passwordError by remember { mutableStateOf<String?>(null) }
  var confirmPasswordError by remember { mutableStateOf<String?>(null) }

  val focusManager = LocalFocusManager.current
  val uiState by viewModel.uiState.collectAsState()

  val isFormValid =
      remember(email, password, confirmPassword) {
        email.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
            password.isNotBlank() &&
            password.length >= MINIMAL_PWD_LENGTH &&
            confirmPassword.isNotBlank() &&
            password == confirmPassword
      }

  val snackbarHostState = remember { SnackbarHostState() }

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

  fun validateEmail(email: String): String? {
    return when {
      email.isBlank() -> CANNOT_BE_EMPTY_EMAIL_TEXT
      !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> INVALID_EMAIL_TEXT
      else -> null
    }
  }

  fun validatePassword(password: String): String? {
    return when {
      password.isBlank() -> EMPTY_PWD_TEXT
      password.length < MINIMAL_PWD_LENGTH -> WEAK_PWD_TEXT
      else -> null
    }
  }

  fun validateConfirmPassword(password: String, confirmPassword: String): String? {
    return when {
      confirmPassword.isBlank() -> PWD_CONFIRMATION_TEXT
      password != confirmPassword -> PWD_MISSMATCH_TEXT
      else -> null
    }
  }

  Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
          val rawScale = maxHeight.value / BASELINE_SCREEN_HEIGTH
          val scale = rawScale.coerceIn(MIN_SCALE, 1f)

          fun Dp.s(): Dp = this * scale
          fun TextUnit.s(): TextUnit = this * scale

          val outerPadding = Dimensions.Padding.xxLarge.s()

          val baseLogoSize =
              (Dimensions.IconSize.massive.times(other = 3)) + Dimensions.Padding.extraLarge
          val logoSize = minOf(baseLogoSize.s(), (maxHeight * IMAGE_SCALING_FACTOR))

          Column(
              modifier =
                  Modifier.fillMaxSize()
                      .imePadding()
                      .verticalScroll(rememberScrollState())
                      .padding(outerPadding)
                      .background(MaterialTheme.colorScheme.background)
                      .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                      },
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.SpaceBetween) {
                val isDarkTheme = isSystemInDarkTheme()

                Image(
                    painter =
                        painterResource(
                            id = if (isDarkTheme) R.drawable.logo_dark else R.drawable.logo_clear),
                    contentDescription = "Meeple Meet Logo",
                    modifier = Modifier.size(logoSize).fillMaxSize())

                Text(
                    SIGN_UP_TEXT,
                    style = TextStyle(fontSize = Dimensions.TextSize.displayMedium.s()),
                    color = AppColors.neutral,
                    modifier =
                        Modifier.padding(bottom = Dimensions.Padding.extraLarge.s())
                            .testTag(NavigationTestTags.SCREEN_TITLE))

                FocusableInputField(
                    leadingIcon = {
                      Icon(imageVector = Icons.Default.Email, contentDescription = null)
                    },
                    value = email,
                    onValueChange = {
                      email = it
                      emailError = if (it.isNotEmpty()) validateEmail(it) else null
                    },
                    label = { Text("Email") },
                    singleLine = true,
                    isError = emailError != null,
                    modifier = Modifier.fillMaxWidth().testTag(SignUpScreenTestTags.EMAIL_FIELD))

                if (emailError != null) {
                  Text(
                      text = emailError!!,
                      color = MaterialTheme.colorScheme.error,
                      style = MaterialTheme.typography.bodySmall,
                      modifier =
                          Modifier.fillMaxWidth()
                              .padding(
                                  start = Dimensions.Padding.extraLarge.s(),
                                  top = Dimensions.Padding.small.s()))
                }

                FocusableInputField(
                    leadingIcon = {
                      Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    },
                    value = password,
                    onValueChange = {
                      password = it
                      passwordError = if (it.isNotEmpty()) validatePassword(it) else null
                      if (confirmPassword.isNotEmpty()) {
                        confirmPasswordError = validateConfirmPassword(it, confirmPassword)
                      }
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = passwordError != null,
                    trailingIcon = {
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

                if (passwordError != null) {
                  Text(
                      text = passwordError!!,
                      color = MaterialTheme.colorScheme.error,
                      style = MaterialTheme.typography.bodySmall,
                      modifier =
                          Modifier.fillMaxWidth()
                              .padding(
                                  start = Dimensions.Padding.extraLarge.s(),
                                  top = Dimensions.Padding.small.s()))
                }

                FocusableInputField(
                    leadingIcon = {
                      Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    },
                    value = confirmPassword,
                    onValueChange = {
                      confirmPassword = it
                      confirmPasswordError =
                          if (it.isNotEmpty()) validateConfirmPassword(password, it) else null
                    },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation =
                        if (confirmPasswordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirmPasswordError != null,
                    trailingIcon = {
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
                        Modifier.fillMaxWidth()
                            .testTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD))

                if (confirmPasswordError != null) {
                  Text(
                      text = confirmPasswordError!!,
                      color = MaterialTheme.colorScheme.error,
                      style = MaterialTheme.typography.bodySmall,
                      modifier =
                          Modifier.fillMaxWidth()
                              .padding(
                                  start = Dimensions.Padding.extraLarge.s(),
                                  top = Dimensions.Padding.small.s()))
                }

                Spacer(modifier = Modifier.height(Dimensions.Spacing.extraLarge.s()))

                Button(
                    onClick = {
                      val emailValidation = validateEmail(email)
                      val passwordValidation = validatePassword(password)
                      val confirmPasswordValidation =
                          validateConfirmPassword(password, confirmPassword)

                      emailError = emailValidation
                      passwordError = passwordValidation
                      confirmPasswordError = confirmPasswordValidation

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
                    enabled = isFormValid && !uiState.isLoading) {
                      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterStart)) {
                              Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                            }
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.extraLarge.s()))

                        if (uiState.isLoading) {
                          CircularProgressIndicator(
                              modifier =
                                  Modifier.size(Dimensions.IconSize.small.s())
                                      .testTag(SignInScreenTestTags.LOADING_INDICATOR),
                              color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(SIGN_UP_TEXT)
                      }
                    }

                Spacer(modifier = Modifier.height(Dimensions.Spacing.medium.s()))
                Text(
                    OPTION_TEXT,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = Dimensions.TextSize.subtitle.s()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimensions.Padding.small.s()))

                OutlinedButton(
                    onClick = { viewModel.googleSignIn(context, credentialManager, onRegister) },
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = AppColors.primary, contentColor = AppColors.textIcons),
                    border = BorderStroke(Dimensions.Elevation.low.s(), AppColors.divider),
                    modifier =
                        Modifier.fillMaxWidth(0.6f)
                            .testTag(SignUpScreenTestTags.GOOGLE_SIGN_UP_BUTTON),
                    enabled = !uiState.isLoading) {
                      Icon(
                          painter = painterResource(id = R.drawable.google_logo),
                          contentDescription = null,
                          modifier = Modifier.size(Dimensions.IconSize.standard.s()),
                          tint = Color.Unspecified)
                      Spacer(modifier = Modifier.width(Dimensions.Spacing.extraLarge.s()))
                      Text("Connect with Google")
                    }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center) {
                      Text(ALREADY_HAVE_ACCOUNT_TEXT)
                      Text(
                          text = LOG_IN_TEXT,
                          color = MaterialTheme.colorScheme.primary,
                          modifier =
                              Modifier.testTag(SignUpScreenTestTags.SIGN_IN_BUTTON).clickable {
                                onLogInClick()
                              })
                    }

                Spacer(modifier = Modifier.height(Dimensions.Spacing.medium.s()))
              }
        }
      }
}
