/** Documentation was written with the help of ChatGPT */
package com.github.meeplemeet.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.meeplemeet.R
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.HandlesViewModel
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.ui.discussions.AddDiscussionTestTags
import com.github.meeplemeet.ui.theme.AppColors

object CreateAccountTestTags {
  const val HANDLE_FIELD = "CreateAccountHandleField"
  const val HANDLE_ERROR = "CreateAccountHandleError"
  const val USERNAME_FIELD = "CreateAccountUsernameField"
  const val USERNAME_ERROR = "CreateAccountUsernameError"
  const val SUBMIT_BUTTON = "CreateAccountSubmitButton"
  const val CHECKBOX_OWNER = "CreateAccountCheckboxOwner"
  const val CHECKBOX_RENTER = "CreateAccountCheckboxRenter"
}

/**
 * Composable screen for completing account creation by selecting a handle and username.
 *
 * This screen collects a handle and username from the user, validates them, and interacts with the
 * [HandlesViewModel] to ensure the handle is available. Once valid, it triggers [onCreate] to
 * continue the account creation flow.
 *
 * @param account The [Account] object representing the user creating an account.
 * @param discussionVM The viewModel for managing discussions and account details.
 * @param handlesVM The ViewModel responsible for handling Firestore handle validation.
 * @param onCreate Callback function to be executed when account creation is successfully validated.
 * @param onBack Callback function to be executed when the user wants to go back to the previous
 *   screen.
 */
@Composable
fun CreateAccountScreen(
    account: Account,
    discussionVM: DiscussionViewModel,
    handlesVM: HandlesViewModel,
    onCreate: () -> Unit = {},
    onBack: () -> Unit = {}
) {
  var handle by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var usernameError by remember { mutableStateOf<String?>(null) }

  val errorMessage by handlesVM.errorMessage.collectAsState()
  var showErrors by remember { mutableStateOf(false) }

  var isShopChecked by remember { mutableStateOf(false) }
  var isSpaceRented by remember { mutableStateOf(false) }

  /** Checks the handle availability and updates the ViewModel state. */
  fun validateHandle(handle: String) {
    showErrors = true
    handlesVM.checkHandleAvailable(handle)
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              OutlinedButton(
                  onClick = onBack,
                  modifier = Modifier.weight(1f).testTag(AddDiscussionTestTags.DISCARD_BUTTON),
                  shape = RoundedCornerShape(percent = 50),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Back", style = MaterialTheme.typography.bodySmall)
                  }

              Button(
                  enabled = handle.isNotBlank() && username.isNotBlank() && errorMessage.isBlank(),
                  colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
                  shape = CircleShape,
                  elevation =
                      ButtonDefaults.buttonElevation(
                          defaultElevation = 4.dp, pressedElevation = 0.dp),
                  onClick = {
                    showErrors = true
                    validateHandle(handle)
                    val usernameValidation = validateUsername(username)
                    usernameError = usernameValidation

                    /** Create the handle and call onCreate if there are no errors */
                    if ((errorMessage.isBlank()) && usernameValidation == null) {
                      handlesVM.createAccountHandle(account = account, handle = handle)
                      discussionVM.setAccountName(account, username)
                      discussionVM.setAccountRole(
                          account, isSpaceRenter = isSpaceRented, isShopOwner = isShopChecked)
                      onCreate()
                    }
                  },
                  modifier = Modifier.weight(1f).testTag(CreateAccountTestTags.SUBMIT_BUTTON)) {
                    Text("Let's go!")
                  }
            }
      }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(AppColors.primary).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {

              // App logo displayed on top of text.
              val isDarkTheme = isSystemInDarkTheme()
              Box(modifier = Modifier.size(250.dp)) {
                Image(
                    painter =
                        painterResource(
                            id = if (isDarkTheme) R.drawable.logo_dark else R.drawable.logo_clear),
                    contentDescription = "Meeple Meet Logo",
                    modifier = Modifier.fillMaxSize())
              }
              /** Title text shown below the image placeholder. */
              Text(
                  "You're almost there!",
                  style = TextStyle(fontSize = 36.sp, color = AppColors.neutral),
                  modifier = Modifier.padding(bottom = 16.dp))

              /** Input field for entering the user's unique handle. */
              OutlinedTextField(
                  value = handle,
                  onValueChange = {
                    handle = it
                    if (it.isNotBlank()) {
                      showErrors = true
                      handlesVM.checkHandleAvailable(it)
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
                  modifier = Modifier.fillMaxWidth().testTag(CreateAccountTestTags.HANDLE_FIELD))

              /** Error message displayed if handle validation fails. */
              if (showErrors && errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    color = AppColors.textIconsFade,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp)
                            .testTag(CreateAccountTestTags.HANDLE_ERROR))
              }

              Spacer(modifier = Modifier.height(16.dp))

              /** Input field for entering the user's display username. */
              OutlinedTextField(
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
                  modifier = Modifier.fillMaxWidth().testTag(CreateAccountTestTags.USERNAME_FIELD))

              /** Error message displayed if username validation fails. */
              if (usernameError != null) {
                Text(
                    text = usernameError!!,
                    color = AppColors.textIconsFade,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp)
                            .testTag(CreateAccountTestTags.USERNAME_ERROR))
              }

              // Spacing between input fields and text
              Spacer(modifier = Modifier.height(24.dp))

              Text(
                  text = "I also want to:",
                  color = AppColors.textIcons,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))

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
      verticalAlignment = Alignment.Top,
      modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!isChecked) }) {
        Checkbox(
            checked = isChecked,
            modifier = Modifier.testTag(testTag).padding(top = 2.dp),
            onCheckedChange = onCheckedChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = AppColors.affirmative,
                    uncheckedColor = AppColors.textIcons,
                    checkmarkColor = AppColors.textIcons))
        Column(
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
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
