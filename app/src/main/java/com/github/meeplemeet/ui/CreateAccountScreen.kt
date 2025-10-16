package com.github.meeplemeet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
import com.github.meeplemeet.ui.theme.appShapes

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

    fun validateHandle(handle: String) {
        showErrors = true
        viewModel.checkHandleAvailable(handle)
    }

    fun validateUsername(username: String): String? {
        return when {
            username.isBlank() -> "Username cannot be empty"
            else -> null
        }
    }

    // Main column centered vertically
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(AppColors.primary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Spacer above square to push content into center
        Spacer(modifier = Modifier.height(24.dp))

        // Square box placeholder (colored properly)
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFFe0e0e0))
        )

        Spacer(modifier = Modifier.height(32.dp))

        // "You're almost there!" text
        Text(
            "You're almost there!",
            style = TextStyle(fontSize = 36.sp, color = AppColors.neutral),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Handle input field
        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it },
            label = { Text("Handle") },
            singleLine = true,
            textStyle = TextStyle(color = AppColors.textIcons),
            isError = showErrors && errorMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        if (showErrors && errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = AppColors.textIconsFade,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Username input field
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                usernameError = validateUsername(username)
            },
            label = { Text("Username") },
            singleLine = true,
            isError = usernameError != null,
            textStyle = TextStyle(color = AppColors.textIcons),
            modifier = Modifier.fillMaxWidth()
        )

        if (usernameError != null) {
            Text(
                text = usernameError!!,
                color = AppColors.textIconsFade,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action button
        Button(
            enabled = handle.isNotBlank() && username.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
            shape = appShapes.medium,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 0.dp),
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Let's go!")
        }
    }
}
