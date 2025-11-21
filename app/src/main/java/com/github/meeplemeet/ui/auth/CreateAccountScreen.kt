/** Documentation was written with the help of ChatGPT */
package com.github.meeplemeet.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.R
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.CreateAccountViewModel
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.discussions.AddDiscussionTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.utils.KeyboardUtils

/**
 * Finds the Activity from a Context by traversing the context hierarchy. Returns null if no
 * Activity is found.
 */
private fun Context.findActivity(): Activity? {
  var context = this
  while (context is ContextWrapper) {
    if (context is Activity) return context
    context = context.baseContext
  }
  return null
}

object CreateAccountTestTags {
  const val HANDLE_FIELD = "CreateAccountHandleField"
  const val HANDLE_ERROR = "CreateAccountHandleError"
  const val USERNAME_FIELD = "CreateAccountUsernameField"
  const val USERNAME_ERROR = "CreateAccountUsernameError"
  const val SUBMIT_BUTTON = "CreateAccountSubmitButton"
  const val CHECKBOX_OWNER = "CreateAccountCheckboxOwner"
  const val CHECKBOX_RENTER = "CreateAccountCheckboxRenter"
}

object CreateAccountScreenUi {
  val xxxLargePadding = Dimensions.Padding.xxxLarge
  val xxLargePadding = Dimensions.Padding.xxLarge
  val extraLargePadding = Dimensions.Padding.extraLarge
  val smallPadding = Dimensions.Padding.small
  val mediumPadding = Dimensions.Padding.medium
  val largePadding = Dimensions.Padding.large
  val tinyPadding = Dimensions.Padding.tiny
  val extraLargeSpacing = Dimensions.Spacing.extraLarge
  val mediumSpacing = Dimensions.Spacing.medium
  val xxLargeSpacing = Dimensions.Spacing.xxLarge
}

/**
 * Composable screen for completing account creation by selecting a handle and username.
 *
 * This screen collects a handle and username from the user, validates them, and interacts with the
 * [CreateAccountViewModel] to ensure the handle is available. Once valid, it triggers [onCreate] to
 * continue the account creation flow.
 *
 * @param account The [Account] object representing the user creating an account.
 * @param viewModel The ViewModel responsible for handling Firestore handle validation.
 * @param onCreate Callback function to be executed when account creation is successfully validated.
 * @param onBack Callback function to be executed when the user wants to go back to the previous
 *   screen.
 */
@Composable
fun CreateAccountScreen(
    account: Account,
    viewModel: CreateAccountViewModel = viewModel(),
    onCreate: () -> Unit = {},
    onBack: () -> Unit = {}
) {
  var handle by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var usernameError by remember { mutableStateOf<String?>(null) }

  val errorMessage by viewModel.errorMessage.collectAsState()
  var showErrors by remember { mutableStateOf(false) }

  var isShopChecked by remember { mutableStateOf(false) }
  var isSpaceRented by remember { mutableStateOf(false) }
  var isInputFocused by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val scrollState = rememberScrollState()
  val activity = LocalContext.current.findActivity()

  DisposableEffect(Unit) {
    val unregister =
        activity?.let { act ->
          KeyboardUtils.registerOnKeyboardHidden(act) { isInputFocused = false }
        }
    onDispose { unregister?.invoke() }
  }

  /** Checks the handle availability and updates the ViewModel state. */
  fun validateHandle(handle: String) {
    showErrors = true
    viewModel.checkHandleAvailable(handle)
  }

  /**
   * Validates the username for non-emptiness.
   *
   * @param username The input username string.
   * @return Error message if username is empty; null if valid.
   */
  fun validateUsername(username: String): String? {
    return when {
      username.isBlank() -> "Username cannot be empty"
      else -> null
    }
  }

  /** Root layout column for aligning all UI components vertically. */
  Scaffold(
      bottomBar = {
        val shouldHide = UiBehaviorConfig.hideBottomBarWhenInputFocused
        if (!(shouldHide && isInputFocused)) {
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          horizontal = CreateAccountScreenUi.xxxLargePadding,
                          vertical = CreateAccountScreenUi.xxLargePadding),
              horizontalArrangement =
                  Arrangement.spacedBy(CreateAccountScreenUi.extraLargeSpacing)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).testTag(AddDiscussionTestTags.DISCARD_BUTTON),
                    shape = RoundedCornerShape(percent = 50),
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                      Icon(
                          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                          contentDescription = null)
                      Spacer(modifier = Modifier.width(CreateAccountScreenUi.mediumSpacing))
                      Text(text = "Back", style = MaterialTheme.typography.bodySmall)
                    }

                Button(
                    enabled =
                        handle.isNotBlank() && username.isNotBlank() && errorMessage.isBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
                    shape = CircleShape,
                    elevation =
                        ButtonDefaults.buttonElevation(
                            defaultElevation = Dimensions.Elevation.high,
                            pressedElevation = Dimensions.Elevation.none),
                    onClick = {
                      showErrors = true
                      validateHandle(handle)
                      val usernameValidation = validateUsername(username)
                      usernameError = usernameValidation

                      /** Create the handle and call onCreate if there are no errors */
                      if ((errorMessage.isBlank()) && usernameValidation == null) {
                        viewModel.createAccountHandle(
                            account = account,
                            handle = handle,
                            username = username,
                            spaceRenter = isSpaceRented,
                            shopOwner = isShopChecked)
                        onCreate()
                      }
                    },
                    modifier = Modifier.weight(1f).testTag(CreateAccountTestTags.SUBMIT_BUTTON)) {
                      Text("Let's go!")
                    }
              }
        }
      }) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .background(AppColors.primary)
                    .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                    .padding(CreateAccountScreenUi.xxLargePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top) {

              // App logo displayed on top of text.
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
              /** Title text shown below the image placeholder. */
              Text(
                  "You're almost there!",
                  style =
                      TextStyle(
                          fontSize = Dimensions.TextSize.extraLarge, color = AppColors.neutral),
                  modifier = Modifier.padding(bottom = CreateAccountScreenUi.extraLargePadding))

              /** Input field for entering the user's unique handle. */
              FocusableInputField(
                  value = handle,
                  onValueChange = {
                    handle = it
                    if (it.isNotBlank()) {
                      showErrors = true
                      viewModel.checkHandleAvailable(it)
                    } else {
                      showErrors = false
                    }
                  },
                  label = { Text("Handle") },
                  singleLine = true,
                  textStyle = TextStyle(color = AppColors.textIcons),
                  colors =
                      TextFieldDefaults.colors(
                          focusedIndicatorColor = AppColors.textIcons,
                          unfocusedIndicatorColor = AppColors.textIconsFade,
                          cursorColor = AppColors.textIcons,
                          focusedLabelColor = AppColors.textIcons,
                          unfocusedContainerColor = Color.Transparent,
                          focusedContainerColor = Color.Transparent,
                          errorContainerColor = Color.Transparent,
                          disabledContainerColor = Color.Transparent,
                          unfocusedLabelColor = AppColors.textIconsFade,
                          focusedTextColor = AppColors.textIcons,
                          unfocusedTextColor = AppColors.textIconsFade),
                  isError = showErrors && errorMessage.isNotBlank(),
                  modifier =
                      Modifier.fillMaxWidth()
                          .onFocusChanged { isInputFocused = it.isFocused }
                          .testTag(CreateAccountTestTags.HANDLE_FIELD))

              /** Error message displayed if handle validation fails. */
              if (showErrors && errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    color = AppColors.textIconsFade,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                start = CreateAccountScreenUi.extraLargePadding,
                                top = CreateAccountScreenUi.smallPadding)
                            .testTag(CreateAccountTestTags.HANDLE_ERROR))
              }

              Spacer(modifier = Modifier.height(CreateAccountScreenUi.extraLargeSpacing))

              /** Input field for entering the user's display username. */
              FocusableInputField(
                  value = username,
                  onValueChange = {
                    username = it
                    usernameError = validateUsername(username)
                  },
                  label = { Text("Username") },
                  singleLine = true,
                  colors =
                      TextFieldDefaults.colors(
                          focusedIndicatorColor = AppColors.textIcons,
                          unfocusedIndicatorColor = AppColors.textIconsFade,
                          cursorColor = AppColors.textIcons,
                          focusedLabelColor = AppColors.textIcons,
                          unfocusedContainerColor = Color.Transparent,
                          focusedContainerColor = Color.Transparent,
                          errorContainerColor = Color.Transparent,
                          disabledContainerColor = Color.Transparent,
                          unfocusedLabelColor = AppColors.textIconsFade,
                          focusedTextColor = AppColors.textIcons,
                          unfocusedTextColor = AppColors.textIconsFade),
                  isError = usernameError != null,
                  textStyle = TextStyle(color = AppColors.textIcons),
                  modifier =
                      Modifier.fillMaxWidth()
                          .onFocusChanged { isInputFocused = it.isFocused }
                          .testTag(CreateAccountTestTags.USERNAME_FIELD))

              /** Error message displayed if username validation fails. */
              if (usernameError != null) {
                Text(
                    text = usernameError!!,
                    color = AppColors.textIconsFade,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                start = CreateAccountScreenUi.extraLargePadding,
                                top = CreateAccountScreenUi.smallPadding)
                            .testTag(CreateAccountTestTags.USERNAME_ERROR))
              }

              // Spacing between input fields and text
              Spacer(modifier = Modifier.height(CreateAccountScreenUi.xxLargeSpacing))

              Text(
                  text = "I also want to:",
                  color = AppColors.textIcons,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier =
                      Modifier.fillMaxWidth().padding(bottom = CreateAccountScreenUi.mediumPadding))

              RoleCheckBox(
                  isChecked = isShopChecked,
                  onCheckedChange = { checked: Boolean -> isShopChecked = checked },
                  label = "Sell items",
                  description = "List your shop and the games it offers.",
                  testTag = CreateAccountTestTags.CHECKBOX_OWNER)

              RoleCheckBox(
                  isChecked = isSpaceRented,
                  onCheckedChange = { checked: Boolean -> isSpaceRented = checked },
                  label = "Rent out spaces",
                  description = "Offer your play spaces for other players to book.",
                  testTag = CreateAccountTestTags.CHECKBOX_RENTER)
            }
      }
}

/**
 * Composable representing a checkbox that gives the user's it's roles
 *
 * @param isChecked If the checkbox is checked
 * @param onCheckedChange lambda when the user interacts with the checkbox
 * @param label Main text appearing to t he right of the checkbox
 * @param description Secondary text appearing below the label
 * @param testTag Tag to append for UI testing
 */
@Composable
fun RoleCheckBox(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    description: String,
    testTag: String
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!isChecked) }) {
        Checkbox(
            checked = isChecked,
            modifier = Modifier.testTag(testTag),
            onCheckedChange = onCheckedChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = AppColors.affirmative,
                    uncheckedColor = AppColors.textIcons,
                    checkmarkColor = AppColors.textIcons))
        Column(
            modifier = Modifier.padding(start = CreateAccountScreenUi.mediumPadding),
            verticalArrangement = Arrangement.Center) {
              Text(
                  text = label,
                  color = AppColors.textIcons,
                  style = MaterialTheme.typography.bodyMedium)
              Text(
                  text = description,
                  color = AppColors.textIconsFade,
                  style = MaterialTheme.typography.bodySmall)
            }
      }
}
