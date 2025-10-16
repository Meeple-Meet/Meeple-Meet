/** Documentation was written with the help of ChatGPT */
package com.github.meeplemeet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreHandlesViewModel
import com.github.meeplemeet.ui.theme.AppColors

/**
 * Composable screen for completing account creation by selecting a handle and username.
 *
 * This screen collects a handle and username from the user, validates them, and interacts with the
 * [FirestoreHandlesViewModel] to ensure the handle is available. Once valid, it triggers [onCreate]
 * to continue the account creation flow.
 *
 * @param viewModel The ViewModel responsible for handling Firestore handle validation.
 * @param currentAccount The [Account] object representing the user creating an account.
 * @param modifier Optional [Modifier] for styling and layout.
 * @param onCreate Lambda to be executed when account creation is successfully validated.
 */
@Composable
fun CreateAccountScreen(
    viewModel: FirestoreHandlesViewModel,
    currentAccount: Account,
    modifier: Modifier = Modifier,
    onCreate: () -> Unit = {},
) {
  var handle by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var usernameError by remember { mutableStateOf<String?>(null) }

  val errorMessage by viewModel.errorMessage.collectAsState()
  var showErrors by remember { mutableStateOf(false) }

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

  Column(
      modifier = modifier.fillMaxSize().background(AppColors.primary).padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.size(120.dp).background(Color(0xFFe0e0e0)))

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "You're almost there!",
            style = TextStyle(fontSize = 36.sp, color = AppColors.neutral),
            modifier = Modifier.padding(bottom = 16.dp))

        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it },
            label = { Text("Handle") },
            singleLine = true,
            textStyle = TextStyle(color = AppColors.textIcons),
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = AppColors.textIcons,
                    unfocusedIndicatorColor = AppColors.textIconsFade,
                    cursorColor = AppColors.textIcons,
                    focusedLabelColor = AppColors.textIcons,
                    unfocusedLabelColor = AppColors.textIconsFade,
                    focusedTextColor = AppColors.textIcons,
                    unfocusedTextColor = AppColors.textIconsFade),
            isError = showErrors && errorMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth())

        if (showErrors && errorMessage.isNotBlank()) {
          Text(
              text = errorMessage,
              color = AppColors.textIconsFade,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    unfocusedLabelColor = AppColors.textIconsFade,
                    focusedTextColor = AppColors.textIcons,
                    unfocusedTextColor = AppColors.textIconsFade),
            isError = usernameError != null,
            textStyle = TextStyle(color = AppColors.textIcons),
            modifier = Modifier.fillMaxWidth())

        if (usernameError != null) {
          Text(
              text = usernameError!!,
              color = AppColors.textIconsFade,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            enabled = handle.isNotBlank() && username.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
            shape = CircleShape,
            elevation =
                ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 0.dp),
            onClick = {
              showErrors = true
              validateHandle(handle)
              val usernameValidation = validateUsername(username)
              usernameError = usernameValidation

              if ((errorMessage.isBlank()) && usernameValidation == null) {
                viewModel.createAccountHandle(account = currentAccount, handle = handle)
                if (errorMessage.isBlank()) onCreate()
              }
            },
            modifier = Modifier.fillMaxWidth(0.3f)) {
              Text("Let's go!")
            }
      }
}
